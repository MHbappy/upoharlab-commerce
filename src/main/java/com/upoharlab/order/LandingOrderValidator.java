package com.upoharlab.order;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingFonts;
import com.upoharlab.landing.LandingPricing;
import com.upoharlab.landing.LandingPricing.Selection;
import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** The rules that depend on a product's configuration. Bean Validation covers required fields. */
@Component
public class LandingOrderValidator {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Dhaka");
  private final Clock clock;

  public LandingOrderValidator(Clock clock) {
    this.clock = clock;
  }

  /** Cleaned values, safe to save. */
  public record Input(
      String text,
      String font,
      int fontSize,
      int design,
      Selection selection,
      LocalDate neededBy,
      String name,
      String phone,
      String address,
      String note) {}

  /** Chosen package, options and extras for the running total; null while any is unusable. */
  public Selection selection(LandingOrderForm form, LandingConfig c) {
    Integer pack = index(form.getPackageIndex(), c.packages().size());
    if (pack == null) return null;
    List<Integer> options = options(form, c);
    if (options == null) return null;
    Set<Integer> upsells = upsells(form, c);
    if (upsells == null) return null;
    return new Selection(pack, options, upsells, area(form.getArea()));
  }

  /** Returns the cleaned input, or null when there are errors. */
  public Input validate(LandingOrderForm form, LandingConfig c, Errors errors) {
    String text = OrderText.text(form.getEngraving(), false);
    String confirm = OrderText.text(form.getConfirmEngraving(), false);
    if (!text.isEmpty()
        && !confirm.isEmpty()
        && !text.equals(confirm))
      errors.rejectValue("confirmEngraving", "mismatch", "দুটি লেখা মিলছে না। আবার দেখে লিখুন।");
    if (OrderText.length(text) > c.preview().maxChars())
      tooLong(errors, "engraving", c.preview().maxChars());
    if (!LandingFonts.allowed(form.getFont()) || !c.preview().fonts().contains(form.getFont()))
      errors.rejectValue("font", "invalid", "একটি ফন্ট বেছে নিন।");
    Integer design = index(form.getDesign(), c.preview().designs().size());
    if (design == null) errors.rejectValue("design", "invalid", "একটি ডিজাইন বেছে নিন।");
    if (index(form.getPackageIndex(), c.packages().size()) == null)
      errors.rejectValue("packageIndex", "invalid", "একটি প্যাকেজ বেছে নিন।");
    if (options(form, c) == null) errors.rejectValue("options", "invalid", "সব অপশন বেছে নিন।");
    if (upsells(form, c) == null)
      errors.rejectValue("upsells", "invalid", "অতিরিক্ত পণ্যের তালিকাটি আবার দেখুন।");
    LocalDate neededBy = neededBy(form.getNeededBy(), errors);
    String name = OrderText.text(form.getName(), false);
    if (OrderText.length(name) > OrderText.MAX_NAME)
      tooLong(errors, "name", OrderText.MAX_NAME);
    String address = OrderText.text(form.getAddress(), true);
    if (OrderText.length(address) > OrderText.MAX_ADDRESS)
      tooLong(errors, "address", OrderText.MAX_ADDRESS);
    String note = OrderText.text(form.getNote(), true);
    if (OrderText.length(note) > OrderText.MAX_NOTE)
      tooLong(errors, "note", OrderText.MAX_NOTE);
    String phone = phone(form.getPhone(), errors);
    if (errors.hasErrors()) return null;
    return new Input(
        text,
        form.getFont(),
        fontSize(form.getFontSize(), c.preview().designs().get(design)),
        design,
        selection(form, c),
        neededBy,
        name,
        phone,
        address,
        note.isEmpty() ? null : note);
  }

  /** The page reports the size it shrank to. Without one, the smallest size is sure to fit. */
  static int fontSize(String raw, LandingConfig.Design design) {
    Integer value = number(raw);
    if (value == null) return design.minFontSize();
    return Math.max(design.minFontSize(), Math.min(design.fontSize(), value));
  }

  private String phone(String raw, Errors errors) {
    if (raw == null || raw.isBlank()) return null; // already reported as required
    try {
      return PhoneNormalizer.normalize(raw);
    } catch (IllegalArgumentException ex) {
      errors.rejectValue("phone", "invalid", ex.getMessage());
      return null;
    }
  }

  private LocalDate neededBy(String raw, Errors errors) {
    if (raw == null || raw.isBlank()) return null;
    try {
      LocalDate date = LocalDate.parse(BnDigits.toLatin(raw).strip());
      LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
      if (!date.isBefore(today) && date.isBefore(today.plusYears(1))) return date;
    } catch (DateTimeParseException ex) {
      // reported below
    }
    errors.rejectValue("neededBy", "invalid", "আজকের পরের একটি সঠিক তারিখ দিন, অথবা খালি রাখুন।");
    return null;
  }

  private static List<Integer> options(LandingOrderForm form, LandingConfig c) {
    List<String> raw = Objects.requireNonNullElse(form.getOptions(), List.of());
    if (raw.size() != c.options().size()) return null;
    List<Integer> chosen = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      Integer value = index(raw.get(i), c.options().get(i).values().size());
      if (value == null) return null;
      chosen.add(value);
    }
    return chosen;
  }

  private static Set<Integer> upsells(LandingOrderForm form, LandingConfig c) {
    Set<Integer> chosen = new LinkedHashSet<>();
    for (String raw : Objects.requireNonNullElse(form.getUpsells(), List.<String>of())) {
      Integer value = index(raw, c.upsells().size());
      if (value == null) return null;
      chosen.add(value);
    }
    return chosen;
  }

  private static String area(String raw) {
    return LandingPricing.INSIDE.equals(raw) || LandingPricing.OUTSIDE.equals(raw) ? raw : null;
  }

  private static Integer index(String raw, int size) {
    Integer value = number(raw);
    return value != null && value >= 0 && value < size ? value : null;
  }

  private static Integer number(String raw) {
    if (raw == null || !raw.strip().matches("[0-9]{1,4}")) return null;
    return Integer.valueOf(raw.strip());
  }

  private static void tooLong(Errors errors, String field, int max) {
    errors.rejectValue(
        field, "length", "সর্বোচ্চ " + BnDigits.toBangla(Integer.toString(max)) + " অক্ষর লেখা যাবে।");
  }
}
