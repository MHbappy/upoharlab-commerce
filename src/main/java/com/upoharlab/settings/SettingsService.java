package com.upoharlab.settings;

import com.upoharlab.FacebookPixel;
import com.upoharlab.shared.PhoneNormalizer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

@Service
public class SettingsService {
  public static final List<String> KEYS =
      List.of(
          "phone",
          "whatsapp",
          "bkash_number",
          "business_email",
          "delivery_inside",
          "delivery_outside",
          "courier_days_inside_min",
          "courier_days_inside_max",
          "courier_days_outside_min",
          "courier_days_outside_max",
          "reply_hours",
          "accepting_orders",
          "pause_notice",
          FacebookPixel.ID_KEY,
          FacebookPixel.TEST_CODE_KEY);
  private static final String CLEAR_TOKEN = FacebookPixel.TOKEN_KEY + "_clear";
  private final JdbcTemplate db;

  public SettingsService(JdbcTemplate db) {
    this.db = db;
  }

  /** Never includes the Facebook access token: this map is handed to public page templates. */
  public Map<String, String> all() {
    Map<String, String> values = new LinkedHashMap<>();
    db.query(
        "SELECT key,value FROM setting",
        r -> {
          values.put(r.getString(1), r.getString(2));
        });
    values.remove(FacebookPixel.TOKEN_KEY);
    return values;
  }

  /**
   * Contact details for the shared footer. It also renders on error pages, so a database problem
   * must leave the footer without contacts rather than break the page.
   */
  public Map<String, String> publicContact() {
    Map<String, String> contact = new HashMap<>();
    try {
      Map<String, String> s = all();
      if (whatsapp(s) != null) contact.put("whatsappLink", whatsapp(s));
      if (!s.getOrDefault("phone", "").isBlank()) contact.put("phone", s.get("phone"));
      if (!s.getOrDefault("business_email", "").isBlank())
        contact.put("email", s.get("business_email"));
    } catch (org.springframework.dao.DataAccessException ignored) {
      return Map.of();
    }
    return contact;
  }

  public String whatsapp(Map<String, String> s) {
    String p = s.getOrDefault("whatsapp", "");
    return p.isBlank() ? null : "https://wa.me/88" + p;
  }

  @Transactional
  public void save(SettingsForm form, Errors errors) {
    Map<String, String> s = form.getValues();
    boolean open = "true".equals(s.get("accepting_orders"));
    s.put("accepting_orders", Boolean.toString(open));
    for (String k : KEYS) s.put(k, Objects.toString(s.get(k), "").strip());
    for (String k : List.of("phone", "whatsapp", "bkash_number")) {
      if (s.get(k).isBlank()) {
        if (open && !k.equals("bkash_number"))
          errors.rejectValue("values[" + k + "]", "required", "Required before accepting orders.");
      } else
        try {
          s.put(k, PhoneNormalizer.normalize(s.get(k)));
        } catch (IllegalArgumentException ex) {
          errors.rejectValue("values[" + k + "]", "phone", "Enter a valid BD mobile number.");
        }
    }
    String email = s.get("business_email");
    if (!email.isBlank()
        && (email.length() > 200 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
      errors.rejectValue("values[business_email]", "email", "Enter a valid email.");
    for (String k : KEYS)
      if (k.startsWith("delivery_") || k.startsWith("courier_") || k.equals("reply_hours")) {
        String v = s.get(k);
        boolean charge = k.startsWith("delivery_");
        if (v.isBlank()) {
          if (open)
            errors.rejectValue(
                "values[" + k + "]", "required", "Required before accepting orders.");
        } else
          try {
            int n = Integer.parseInt(v);
            if (n < (charge ? 0 : 1) || n > (charge ? 10000 : 365))
              throw new NumberFormatException();
          } catch (NumberFormatException ex) {
            errors.rejectValue("values[" + k + "]", "number", "Enter a valid whole number.");
          }
      }
    for (String area : List.of("inside", "outside")) {
      String min = "courier_days_" + area + "_min", max = "courier_days_" + area + "_max";
      if (s.get(min).isBlank() != s.get(max).isBlank())
        errors.rejectValue(
            "values[" + (s.get(min).isBlank() ? min : max) + "]",
            "pair",
            "Enter both minimum and maximum, or leave both empty.");
      try {
        if (Integer.parseInt(s.get("courier_days_" + area + "_max"))
            < Integer.parseInt(s.get("courier_days_" + area + "_min")))
          errors.rejectValue(
              "values[courier_days_" + area + "_max]",
              "range",
              "Maximum must be at least minimum.");
      } catch (NumberFormatException ignored) {
      }
    }
    if (s.get("pause_notice").length() > 500)
      errors.rejectValue("values[pause_notice]", "length", "Keep the notice under 500 characters.");
    if (!s.get(FacebookPixel.ID_KEY).isBlank() && !s.get(FacebookPixel.ID_KEY).matches("[0-9]{5,25}"))
      errors.rejectValue("values[" + FacebookPixel.ID_KEY + "]", "pixel", "Enter the numeric Pixel ID.");
    if (!s.get(FacebookPixel.TEST_CODE_KEY).matches("[A-Za-z0-9]{0,50}"))
      errors.rejectValue(
          "values[" + FacebookPixel.TEST_CODE_KEY + "]", "code", "Enter the code exactly as Facebook shows it.");
    String token = Objects.toString(s.get(FacebookPixel.TOKEN_KEY), "").strip();
    if (!token.isEmpty() && !token.matches("[A-Za-z0-9_|.-]{20,1000}"))
      errors.rejectValue(
          "values[" + FacebookPixel.TOKEN_KEY + "]", "token", "Paste the whole access token from Facebook.");
    if (errors.hasErrors()) return;
    for (String k : KEYS) db.update("UPDATE setting SET value=? WHERE key=?", s.get(k), k);
    // A blank box keeps the saved token, because the page never shows it.
    if ("true".equals(s.get(CLEAR_TOKEN)))
      db.update("UPDATE setting SET value='' WHERE key=?", FacebookPixel.TOKEN_KEY);
    else if (!token.isEmpty())
      db.update("UPDATE setting SET value=? WHERE key=?", token, FacebookPixel.TOKEN_KEY);
  }
}
