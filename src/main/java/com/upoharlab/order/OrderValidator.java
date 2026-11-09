package com.upoharlab.order;

import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.text.Normalizer;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class OrderValidator {
  static final int MAX_NAME = 100, MAX_ENGRAVING = 1000, MAX_ADDRESS = 500, MAX_NOTE = 500;

  /** Cleaned values, safe to save. */
  public record OrderInput(
      int quantity,
      String engraving,
      String name,
      String phone,
      String area,
      String address,
      String note) {}

  /** Returns 1–10, or null when the text is not a valid quantity. Accepts Bangla digits. */
  public Integer quantity(String raw) {
    String value = BnDigits.toLatin(Objects.toString(raw, "")).strip();
    if (!value.matches("[0-9]{1,2}")) return null;
    int quantity = Integer.parseInt(value);
    return quantity >= 1 && quantity <= 10 ? quantity : null;
  }

  public String area(String raw) {
    return PricingService.INSIDE.equals(raw) || PricingService.OUTSIDE.equals(raw) ? raw : null;
  }

  public void validateQuote(OrderForm form, Errors errors, boolean areaRequired) {
    if (quantity(form.getQuantity()) == null)
      errors.rejectValue(
          "quantity", "invalid", "পরিমাণ ১ থেকে ১০-এর মধ্যে লিখুন। বেশি লাগলে আমাদের জানান।");
    if (area(form.getArea()) == null && (areaRequired || !isBlank(form.getArea())))
      errors.rejectValue("area", "required", "ঢাকার ভেতরে না বাইরে, একটি বেছে নিন।");
  }

  /** Validates everything; returns the cleaned input, or null when there are errors. */
  public OrderInput validateOrder(OrderForm form, Errors errors) {
    validateQuote(form, errors, true);
    String engraving = text(form.getEngraving(), true);
    String name = text(form.getName(), false);
    String address = text(form.getAddress(), true);
    String note = text(form.getNote(), true);
    if (length(engraving) > MAX_ENGRAVING) tooLong(errors, "engraving", MAX_ENGRAVING);
    required(errors, "name", name, MAX_NAME, "আপনার নাম লিখুন।");
    required(errors, "address", address, MAX_ADDRESS, "পুরো ঠিকানা লিখুন।");
    if (length(note) > MAX_NOTE) tooLong(errors, "note", MAX_NOTE);
    String phone = null;
    try {
      phone = PhoneNormalizer.normalize(form.getPhone());
    } catch (IllegalArgumentException ex) {
      errors.rejectValue("phone", "invalid", ex.getMessage());
    }
    if (errors.hasErrors()) return null;
    return new OrderInput(
        quantity(form.getQuantity()),
        engraving,
        name,
        phone,
        area(form.getArea()),
        address,
        note.isEmpty() ? null : note);
  }

  private void required(Errors errors, String field, String value, int max, String message) {
    if (value.isEmpty()) errors.rejectValue(field, "required", message);
    else if (length(value) > max) tooLong(errors, field, max);
  }

  private void tooLong(Errors errors, String field, int max) {
    errors.rejectValue(
        field, "length", "সর্বোচ্চ " + BnDigits.toBangla(Integer.toString(max)) + " অক্ষর লেখা যাবে।");
  }

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

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
