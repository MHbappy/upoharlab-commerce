package com.upoharlab;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The optional Facebook Pixel and server tracking. Admin Settings win; FACEBOOK_PIXEL_ID is only a
 * fallback. Every request checks this for its security header, so values are held in memory and
 * reloaded after the owner saves Settings.
 */
@Component("pixel")
public class FacebookPixel {
  public static final String ID_KEY = "fb_pixel_id";
  public static final String TOKEN_KEY = "fb_capi_token";
  public static final String TEST_CODE_KEY = "fb_test_event_code";
  private static final String VALID_ID = "[0-9]{5,25}";

  private record Current(String id, String token, String testCode) {}

  private final JdbcTemplate db;
  private final String fallbackId;
  private volatile Current current;

  public FacebookPixel(JdbcTemplate db, @Value("${app.facebook.pixel-id:}") String fallbackId) {
    this.db = db;
    this.fallbackId = valid(fallbackId);
  }

  private static String valid(String id) {
    return id != null && id.strip().matches(VALID_ID) ? id.strip() : null;
  }

  private Current current() {
    Current known = current;
    if (known != null) return known;
    try {
      Map<String, String> saved = new HashMap<>();
      db.query(
          "SELECT key, value FROM setting WHERE key IN (?,?,?)",
          r -> {
            saved.put(r.getString(1), r.getString(2));
          },
          ID_KEY,
          TOKEN_KEY,
          TEST_CODE_KEY);
      String id = valid(saved.get(ID_KEY));
      Current loaded =
          new Current(
              id != null ? id : fallbackId,
              saved.getOrDefault(TOKEN_KEY, ""),
              saved.getOrDefault(TEST_CODE_KEY, ""));
      current = loaded;
      return loaded;
    } catch (DataAccessException databaseNotReady) {
      return new Current(fallbackId, "", "");
    }
  }

  /** Call after Settings are saved, so the change applies without a restart. */
  public void refresh() {
    current = null;
  }

  public boolean enabled() {
    return current().id() != null;
  }

  public String id() {
    return current().id();
  }

  public boolean hasToken() {
    return !current().token().isBlank();
  }

  /** Server events need both the Pixel ID and the Conversions API access token. */
  public boolean serverEnabled() {
    return enabled() && hasToken();
  }

  public String token() {
    return current().token();
  }

  public String testCode() {
    return current().testCode();
  }
}
