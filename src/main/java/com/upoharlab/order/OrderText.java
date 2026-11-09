package com.upoharlab.order;

import java.text.Normalizer;
import java.util.Objects;

/** Limits and cleaning shared by everything that saves customer or admin text on an order. */
final class OrderText {
  static final int MAX_NAME = 100, MAX_ENGRAVING = 1000, MAX_ADDRESS = 500, MAX_NOTE = 500;

  private OrderText() {}

  /** NFC, unified line endings, no control characters. PostgreSQL also rejects NUL in text. */
  static String text(String raw, boolean multiline) {
    String value = Normalizer.normalize(Objects.toString(raw, ""), Normalizer.Form.NFC);
    value = value.replace("\r\n", "\n").replace('\r', '\n');
    if (!multiline) value = value.replace('\n', ' ');
    return value.replaceAll("[\\p{Cc}&&[^\\n]]", "").strip();
  }

  static int length(String value) {
    return value.codePointCount(0, value.length());
  }
}
