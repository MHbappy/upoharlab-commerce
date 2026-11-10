package com.upoharlab.text;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-facing wording: the owner's edits from the {@code site_text} table over the defaults in
 * {@link SiteTextCatalog}. Edits are cached in memory and reloaded after every save.
 *
 * <p>A text may contain placeholders such as {0} that the page fills in, for example a price.
 * Arguments are always passed as ready-made strings so numbers keep their Bangla formatting.
 */
@Service
public class SiteTexts {
  static final int MAX_LENGTH = 2000;

  private final JdbcTemplate db;
  private final SiteTextCatalog catalog;
  private volatile Map<String, String> overrides;

  public SiteTexts(JdbcTemplate db, SiteTextCatalog catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public SiteTextCatalog catalog() {
    return catalog;
  }

  /** The current wording with placeholders unfilled, or null for an unknown key. */
  public String raw(String key) {
    SiteTextCatalog.Entry entry = catalog.get(key);
    if (entry == null) return null;
    String edited = overrides().get(key);
    return edited != null ? edited : entry.defaultText();
  }

  /** The current wording with {0}, {1}… filled in. Unknown keys fail loudly in development. */
  public String text(String key, Object... args) {
    String pattern = raw(key);
    if (pattern == null) throw new IllegalArgumentException("Unknown site text " + key);
    return format(pattern, args);
  }

  /**
   * Only the texts the owner has changed. Error pages render these too, so a database problem
   * shows the defaults for that request instead of breaking the page.
   */
  public Map<String, String> overrides() {
    Map<String, String> current = overrides;
    if (current == null) {
      Map<String, String> loaded = new HashMap<>();
      try {
        db.query("SELECT key, value FROM site_text", r -> {
          loaded.put(r.getString(1), r.getString(2));
        });
      } catch (DataAccessException e) {
        return Map.of();
      }
      current = Map.copyOf(loaded);
      overrides = current;
    }
    return current;
  }

  /**
   * Saves submitted wording. Text equal to the default goes back to the default. An empty text
   * hides an optional text and goes back to the default for any other, so no label is ever blank.
   * Returns problems by key; nothing is saved while any remain.
   */
  @Transactional
  public Map<String, String> save(Map<String, String> submitted) {
    Map<String, String> problems = new LinkedHashMap<>();
    Map<String, String> clean = new LinkedHashMap<>();
    submitted.forEach((key, value) -> {
      if (!catalog.contains(key)) return;
      String text = normalize(value);
      String problem = problem(text);
      if (problem != null) problems.put(key, problem);
      else clean.put(key, text);
    });
    if (!problems.isEmpty()) return problems;
    clean.forEach((key, text) -> {
      SiteTextCatalog.Entry entry = catalog.get(key);
      if (text.equals(entry.defaultText()) || (text.isEmpty() && !entry.optional())) {
        db.update("DELETE FROM site_text WHERE key=?", key);
      } else {
        db.update(
            "INSERT INTO site_text (key, value) VALUES (?, ?) "
                + "ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, updated_at=now()",
            key,
            text);
      }
    });
    overrides = null;
    return problems;
  }

  /** Puts every text back to its default. */
  @Transactional
  public void resetAll() {
    db.update("DELETE FROM site_text");
    overrides = null;
  }

  static String normalize(String value) {
    if (value == null) return "";
    return value.replace("\r\n", "\n").replace('\r', '\n').strip();
  }

  /** Why a text cannot be saved, or null when it is fine. */
  static String problem(String text) {
    if (text.length() > MAX_LENGTH) return "Keep this under " + MAX_LENGTH + " characters.";
    try {
      new MessageFormat(escape(text), Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return "Curly brackets are only for placeholders such as {0}. Remove the other { or }.";
    }
    return null;
  }

  /** Apostrophes are ordinary characters in the owner's wording, not MessageFormat quoting. */
  static String escape(String pattern) {
    return pattern.replace("'", "''");
  }

  static String format(String pattern, Object... args) {
    if (args == null || args.length == 0) return pattern;
    return new MessageFormat(escape(pattern), Locale.ROOT).format(args);
  }
}
