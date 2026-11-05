package com.upoharlab.shared;

@org.springframework.stereotype.Component("bn")
public class DisplayFormat {
  private static final java.time.format.DateTimeFormatter ADMIN_TIME =
      java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", java.util.Locale.ENGLISH)
          .withZone(java.time.ZoneId.of("Asia/Dhaka"));

  public String money(Integer amount) {
    return amount == null ? "" : BnDigits.money(amount);
  }

  /** Admin date and time in business time. */
  public String time(java.time.Instant instant) {
    return instant == null ? "" : ADMIN_TIME.format(instant);
  }

  public String digits(Object value) {
    return value == null ? "" : BnDigits.toBangla(value.toString());
  }
}
