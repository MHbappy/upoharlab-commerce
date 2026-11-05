package com.upoharlab.shared;

public final class PhoneNormalizer {
  private PhoneNormalizer() {}

  /** For lists: 01712345678 becomes 01XXX-XXX678. */
  public static String mask(String phone) {
    if (phone == null || phone.length() < 3) return "";
    return "01XXX-XXX" + phone.substring(phone.length() - 3);
  }

  public static String normalize(String input) {
    if (input == null) throw new IllegalArgumentException("ফোন নম্বর লিখুন।");
    String phone = BnDigits.toLatin(input).replaceAll("[\\s\\p{Zs}-]", "");
    if (phone.startsWith("+880")) phone = "0" + phone.substring(4);
    else if (phone.startsWith("880")) phone = "0" + phone.substring(3);
    if (!phone.matches("01[3-9][0-9]{8}")) {
      throw new IllegalArgumentException("সঠিক বাংলাদেশি মোবাইল নম্বর লিখুন।");
    }
    return phone;
  }
}
