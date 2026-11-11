package com.upoharlab.order;

import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Public status lookup. Reads only fields that are safe to show someone holding both values. */
@Service
public class TrackingService {
  public record Status(
      String code,
      String status,
      Instant placedAt,
      Instant approvedAt,
      Instant deliveredAt,
      String courier,
      String trackingNo,
      boolean advanceDue,
      boolean needsDesign) {}

  private final JdbcTemplate db;

  public TrackingService(JdbcTemplate db) {
    this.db = db;
  }

  /** Normalizes a typed code, accepting Bangla digits. Returns null when it is not a code. */
  public static String normalizeCode(String raw) {
    String code = BnDigits.toLatin(Objects.toString(raw, "")).strip().toUpperCase(Locale.ROOT);
    return code.matches("UL-[0-9]{6}-[0-9]{3,12}") ? code : null;
  }

  /** Both values must match the same order. One query, so both failures look the same. */
  public Optional<Status> find(String code, String rawPhone) {
    String phone;
    try {
      phone = PhoneNormalizer.normalize(rawPhone);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    return db
        .query(
            "SELECT code, status, created_at, design_approved_at, delivered_at, courier,"
                + " tracking_no, advance_required > (SELECT coalesce(sum(CASE WHEN p.kind='PAYMENT'"
                + " THEN p.amount ELSE -p.amount END),0) FROM payment p WHERE p.order_id=orders.id AND p.voided_at IS NULL)"
                + " AS advance_due, needs_design FROM orders WHERE code=? AND phone=?",
            (r, n) ->
                new Status(
                    r.getString("code"),
                    r.getString("status"),
                    instant(r.getTimestamp("created_at")),
                    instant(r.getTimestamp("design_approved_at")),
                    instant(r.getTimestamp("delivered_at")),
                    r.getString("courier"),
                    r.getString("tracking_no"),
                    r.getBoolean("advance_due"),
                    r.getBoolean("needs_design")),
            code,
            phone)
        .stream()
        .findFirst();
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
