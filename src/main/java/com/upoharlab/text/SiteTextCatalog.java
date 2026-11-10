package com.upoharlab.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Every customer-facing text with its default wording, in page order. Defaults live in
 * {@code site-text.properties}; a line starting with {@code ##} opens a group named after the page
 * where the texts appear, which is how the admin editor lists them. A key written as {@code ?key}
 * is optional: the owner may empty it to hide that text.
 */
@Component
public class SiteTextCatalog {
  static final String FILE = "site-text.properties";

  public record Entry(String key, String group, String defaultText, boolean optional) {}

  public record Group(String title, List<Entry> entries) {}

  private final List<Group> groups;
  private final Map<String, Entry> byKey;

  public SiteTextCatalog() {
    this(read());
  }

  SiteTextCatalog(String source) {
    Map<String, Entry> entries = new LinkedHashMap<>();
    Map<String, List<Entry>> grouped = new LinkedHashMap<>();
    String group = "General";
    int lineNo = 0;
    for (String line : source.split("\\R")) {
      lineNo++;
      String trimmed = line.strip();
      if (trimmed.startsWith("##")) {
        group = trimmed.substring(2).strip();
        continue;
      }
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      int eq = line.indexOf('=');
      if (eq <= 0) throw new IllegalStateException(FILE + " line " + lineNo + " has no key=value");
      String key = line.substring(0, eq).strip();
      boolean optional = key.startsWith("?");
      if (optional) key = key.substring(1);
      String value = unescape(line.substring(eq + 1).strip());
      Entry entry = new Entry(key, group, value, optional);
      if (entries.put(key, entry) != null)
        throw new IllegalStateException(FILE + " repeats the key " + key);
      grouped.computeIfAbsent(group, g -> new ArrayList<>()).add(entry);
    }
    List<Group> list = new ArrayList<>();
    grouped.forEach((title, items) -> list.add(new Group(title, List.copyOf(items))));
    this.groups = List.copyOf(list);
    this.byKey = Collections.unmodifiableMap(entries);
  }

  public List<Group> groups() {
    return groups;
  }

  public Entry get(String key) {
    return byKey.get(key);
  }

  public boolean contains(String key) {
    return byKey.containsKey(key);
  }

  public int size() {
    return byKey.size();
  }

  private static String unescape(String value) {
    return value.replace("\\n", "\n");
  }

  private static String read() {
    try {
      return new ClassPathResource(FILE).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + FILE, e);
    }
  }
}
