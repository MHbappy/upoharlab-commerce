package com.upoharlab.shared;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class BnDigits {
  private static final String LATIN = "0123456789";
  private static final String BANGLA = "০১২৩৪৫৬৭৮৯";
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("bn-BD"))
          .withZone(ZoneId.of("Asia/Dhaka"));

  private BnDigits() {}

  public static String toBangla(String value) {
    return translate(value, LATIN, BANGLA);
  }

  public static String toLatin(String value) {
    return translate(value, BANGLA, LATIN);
  }

  public static String money(int taka) {
    if (taka < 0) throw new IllegalArgumentException("Money cannot be negative");
    return "৳" + toBangla(Integer.toString(taka));
  }

  public static String date(Instant instant) {
    return toBangla(DATE.format(instant));
  }

  private static String translate(String value, String from, String to) {
    if (value == null) return null;
    StringBuilder result = new StringBuilder(value.length());
    for (char character : value.toCharArray()) {
      int index = from.indexOf(character);
      result.append(index >= 0 ? to.charAt(index) : character);
    }
    return result.toString();
  }
}
