package com.upoharlab.order;

import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner actions on saved orders. Every change locks the order row first, so concurrent clicks
 * cannot double-record money or bypass a status rule.
 */
@Service
public class AdminOrderService {
  static final int PAGE_SIZE = 25;
  private static final int MAX_AMOUNT = 100_000_000;

  /** A broken business rule, with a message written for the owner. */
  public static class RuleException extends RuntimeException {
    public RuleException(String message) {
      super(message);
    }
  }

  public record Counts(int fresh, int making, int shipped) {}

  public record Row(
      String code,
      Instant createdAt,
      String productTitle,
      int quantity,
      String customerName,
      String maskedPhone,
      int total,
      String status) {}

  public record Results(List<Row> rows, int page, int totalPages, int totalRows) {}

  public record Payment(
      String kind, String method, int amount, String reference, String recordedBy, Instant at) {}

  public record Event(
      String type, String fromStatus, String toStatus, String note, String actor, Instant at) {}

  public record Detail(
      String code,
      String status,
      String productTitle,
      int quantity,
      String engraving,
      String customerNote,
      String customerName,
      String phone,
      String area,
      String address,
      int unitPrice,
      int subtotal,
      int delivery,
      int total,
      int advanceRequired,
      Instant designApprovedAt,
      String courier,
      String trackingNo,
      Instant deliveredAt,
      Instant createdAt,
      long netPaid,
      Long balance,
      List<String> missingForConfirmation,
      boolean canEditContact,
      boolean canCancel,
      List<Payment> payments,
      List<Event> history,
      boolean needsDesign) {}

  private record Locked(
      long id,
      OrderStatus status,
      int total,
      int advanceRequired,
      boolean approved,
      String courier,
      String trackingNo,
      boolean needsDesign) {}

  private final JdbcTemplate db;

  public AdminOrderService(JdbcTemplate db) {
    this.db = db;
  }

  public Counts counts() {
    return new Counts(count("NEW"), count("MAKING"), count("SHIPPED"));
  }

  private int count(String status) {
    return db.queryForObject("SELECT count(*) FROM orders WHERE status=?", Integer.class, status);
  }

  public List<Row> recent(int limit) {
    return db.query(
        "SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT ?",
        (r, n) -> row(r),
        limit);
  }

  /** Searches by part of an order code, or by a full or partial phone number. */
  public Results search(String status, String rawQuery, int requestedPage) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE true");
    if (validStatus(status)) {
      where.append(" AND status=?");
      args.add(status);
    }
    String query = Objects.toString(rawQuery, "").strip();
    if (query.length() > 40) query = query.substring(0, 40);
    if (!query.isEmpty()) {
      where.append(" AND (code ILIKE ? ESCAPE '\\'");
      args.add("%" + escapeLike(query) + "%");
      String digits = phoneSearchDigits(query);
      if (digits != null) {
        where.append(" OR phone LIKE ?");
        args.add("%" + digits + "%");
      }
      where.append(")");
    }
    int total =
        db.queryForObject("SELECT count(*) FROM orders" + where, Integer.class, args.toArray());
    int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    int page = Math.max(0, Math.min(requestedPage, pages - 1));
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(PAGE_SIZE);
    pageArgs.add(page * PAGE_SIZE);
    List<Row> rows =
        db.query(
            "SELECT * FROM orders" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            (r, n) -> row(r),
            pageArgs.toArray());
    return new Results(rows, page, pages, total);
  }

  private static boolean validStatus(String status) {
    for (OrderStatus value : OrderStatus.values()) if (value.name().equals(status)) return true;
    return false;
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static String phoneSearchDigits(String query) {
    try {
      return PhoneNormalizer.normalize(query);
    } catch (IllegalArgumentException notAFullNumber) {
      String digits = BnDigits.toLatin(query).replaceAll("[\\s-]", "");
      return digits.matches("[0-9]{3,11}") ? digits : null;
    }
  }

  private Row row(ResultSet r) throws SQLException {
    return new Row(
        r.getString("code"),
        instant(r, "created_at"),
        r.getString("product_title_snapshot"),
        r.getInt("quantity"),
        r.getString("customer_name"),
        PhoneNormalizer.mask(r.getString("phone")),
        r.getInt("total"),
        r.getString("status"));
  }

  private static Instant instant(ResultSet r, String column) throws SQLException {
    Timestamp value = r.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  @Transactional(readOnly = true)
  public Detail detail(String code) {
    long id = orderId(code);
    long net = netPaid(id);
    List<Payment> payments =
        db.query(
            "SELECT p.*, a.username FROM payment p JOIN admin_user a ON a.id=p.recorded_by"
                + " WHERE p.order_id=? ORDER BY p.recorded_at, p.id",
            (r, n) ->
                new Payment(
                    r.getString("kind"),
                    r.getString("method"),
                    r.getInt("amount"),
                    r.getString("transaction_ref"),
                    r.getString("username"),
                    instant(r, "recorded_at")),
            id);
    List<Event> history =
        db.query(
            "SELECT * FROM order_event WHERE order_id=? ORDER BY created_at, id",
            (r, n) ->
                new Event(
                    r.getString("event_type"),
                    r.getString("from_status"),
                    r.getString("to_status"),
                    r.getString("note"),
                    r.getString("actor"),
                    instant(r, "created_at")),
            id);
    return db.queryForObject(
        "SELECT * FROM orders WHERE id=?",
        (r, n) -> {
          OrderStatus status = OrderStatus.valueOf(r.getString("status"));
          int total = r.getInt("total"), advance = r.getInt("advance_required");
          Instant approved = instant(r, "design_approved_at");
          return new Detail(
              r.getString("code"),
              status.name(),
              r.getString("product_title_snapshot"),
              r.getInt("quantity"),
              r.getString("engraving_text"),
              r.getString("note"),
              r.getString("customer_name"),
              r.getString("phone"),
              r.getString("area"),
              r.getString("address"),
              r.getInt("unit_price_snapshot"),
              r.getInt("unit_price_snapshot") * r.getInt("quantity"),
              r.getInt("delivery_charge_snapshot"),
              total,
              advance,
              approved,
              r.getString("courier"),
              r.getString("tracking_no"),
              instant(r, "delivered_at"),
              instant(r, "created_at"),
              net,
              status == OrderStatus.CANCELLED ? null : Long.valueOf(total - net),
              missingForConfirmation(r.getBoolean("needs_design"), approved != null, advance, net),
              status.beforeShipping(),
              status.beforeShipping(),
              payments,
              history,
              r.getBoolean("needs_design"));
        },
        id);
  }

  private static List<String> missingForConfirmation(
      boolean needsDesign, boolean approved, int advance, long paid) {
    List<String> missing = new ArrayList<>();
    if (needsDesign && !approved) missing.add("Customer design approval has not been recorded.");
    if (paid < advance)
      missing.add("The ৳" + advance + " advance has not been recorded in full (received ৳" + paid + ").");
    return missing;
  }

  private long orderId(String code) {
    return db.query("SELECT id FROM orders WHERE code=?", (r, n) -> r.getLong(1), code).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private Locked lock(String code) {
    return db
        .query(
            "SELECT id, status, total, advance_required, design_approved_at, courier, tracking_no,"
                + " needs_design FROM orders WHERE code=? FOR UPDATE",
            (r, n) ->
                new Locked(
                    r.getLong("id"),
                    OrderStatus.valueOf(r.getString("status")),
                    r.getInt("total"),
                    r.getInt("advance_required"),
                    r.getTimestamp("design_approved_at") != null,
                    r.getString("courier"),
                    r.getString("tracking_no"),
                    r.getBoolean("needs_design")),
            code)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private long netPaid(long orderId) {
    return db.queryForObject(
        "SELECT coalesce(sum(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END),0)"
            + " FROM payment WHERE order_id=?",
        Long.class,
        orderId);
  }

  private void event(
      long orderId, String type, OrderStatus from, OrderStatus to, String note, String actor) {
    db.update(
        "INSERT INTO order_event (order_id, event_type, from_status, to_status, note, actor)"
            + " VALUES (?,?,?,?,?,?)",
        orderId,
        type,
        from == null ? null : from.name(),
        to == null ? null : to.name(),
        note,
        actor);
  }

  @Transactional
  public void changeStatus(
      String code, String target, String reason, String courier, String tracking, String actor) {
    OrderStatus to;
    try {
      to = OrderStatus.valueOf(Objects.toString(target, ""));
    } catch (IllegalArgumentException ex) {
      throw new RuleException("Unknown order status.");
    }
    Locked order = lock(code);
    if (!order.status().next().contains(to))
      throw new RuleException("An order cannot move from " + order.status() + " to " + to + ".");
    if (order.status() == OrderStatus.NEW && to == OrderStatus.CONFIRMED && order.needsDesign())
      throw new RuleException("Send the design and record the customer's approval first.");
    if (to == OrderStatus.DESIGN_SENT && !order.needsDesign())
      throw new RuleException("This order has no customer text, so there is no design to send.");
    String note = null;
    switch (to) {
      case CONFIRMED, MAKING -> {
        List<String> missing =
            missingForConfirmation(
                order.needsDesign(), order.approved(), order.advanceRequired(), netPaid(order.id()));
        if (!missing.isEmpty()) throw new RuleException(String.join(" ", missing));
      }
      case SHIPPED -> saveCourier(order.id(), courier, tracking);
      case DELIVERED ->
          db.update("UPDATE orders SET delivered_at=now() WHERE id=?", order.id());
      case CANCELLED -> {
        note = required(reason, 500, "Enter a reason for cancelling.");
      }
      default -> {}
    }
    db.update("UPDATE orders SET status=?, updated_at=now() WHERE id=?", to.name(), order.id());
    event(order.id(), "STATUS_CHANGED", order.status(), to, note, actor);
  }

  private void saveCourier(long orderId, String courier, String tracking) {
    String name = required(courier, 100, "Enter the courier name.");
    String number = required(tracking, 150, "Enter the tracking or reference number.");
    db.update(
        "UPDATE orders SET courier=?, tracking_no=?, updated_at=now() WHERE id=?",
        name,
        number,
        orderId);
  }

  /** Records customer design approval obtained by phone or WhatsApp. */
  @Transactional
  public void approveDesign(String code, String actor) {
    Locked order = lock(code);
    if (order.status() != OrderStatus.DESIGN_SENT)
      throw new RuleException("Approval can only be recorded after the design is sent.");
    if (order.approved()) throw new RuleException("Approval is already recorded.");
    db.update(
        "UPDATE orders SET design_approved_at=now(), updated_at=now() WHERE id=?", order.id());
    event(order.id(), "DESIGN_APPROVED", null, null, null, actor);
  }

  /** The owner has already verified this money in their bKash account or actual settlement. */
  @Transactional
  public void recordMoney(
      String code, String kind, String method, String rawAmount, String rawRef, String actor) {
    boolean refund = "REFUND".equals(kind);
    if (!refund && !"PAYMENT".equals(kind)) throw new RuleException("Choose payment or refund.");
    if (!List.of("BKASH", "CASH", "COD").contains(Objects.toString(method, "")))
      throw new RuleException("Choose how the money moved.");
    if (refund && "COD".equals(method))
      throw new RuleException("A refund cannot be recorded as cash on delivery.");
    int amount = amount(rawAmount);
    String reference = OrderText.text(rawRef, false).toUpperCase(Locale.ROOT);
    if (reference.length() > 100) throw new RuleException("That reference is too long.");
    if ("BKASH".equals(method) && reference.isEmpty())
      throw new RuleException("A bKash record needs its transaction reference.");
    Locked order = lock(code);
    long net = netPaid(order.id());
    if (refund) {
      if (amount > net)
        throw new RuleException("A refund cannot exceed the ৳" + net + " currently held.");
    } else {
      if (order.status() == OrderStatus.CANCELLED)
        throw new RuleException("This order is cancelled, so it cannot take new payments.");
      if (net + amount > order.total())
        throw new RuleException(
            "This would overpay the order. Remaining balance is ৳" + (order.total() - net) + ".");
    }
    if (!reference.isEmpty()
        && db.queryForObject(
                "SELECT count(*) FROM payment WHERE transaction_ref=?", Integer.class, reference)
            > 0) throw new RuleException("This transaction reference is already recorded.");
    Long adminId =
        db.queryForObject("SELECT id FROM admin_user WHERE username=?", Long.class, actor);
    db.update(
        "INSERT INTO payment (order_id, kind, method, amount, transaction_ref, recorded_by)"
            + " VALUES (?,?,?,?,?,?)",
        order.id(),
        kind,
        method,
        amount,
        reference.isEmpty() ? null : reference,
        adminId);
    event(
        order.id(),
        refund ? "REFUND_RECORDED" : "PAYMENT_RECORDED",
        null,
        null,
        "৳" + amount + " " + method,
        actor);
  }

  private static int amount(String raw) {
    String value = BnDigits.toLatin(Objects.toString(raw, "")).strip();
    if (!value.matches("[0-9]{1,9}") || Integer.parseInt(value) < 1)
      throw new RuleException("Enter a whole-taka amount above zero.");
    int amount = Integer.parseInt(value);
    if (amount > MAX_AMOUNT) throw new RuleException("That amount is too large.");
    return amount;
  }

  /** Completes personalization omitted at guest checkout, before design approval. */
  @Transactional
  public void completeEngraving(String code, String engraving, String actor) {
    String clean = required(engraving, OrderText.MAX_ENGRAVING, "Enter the agreed engraving text.");
    Locked order = lock(code);
    if ((order.status() != OrderStatus.NEW && order.status() != OrderStatus.DESIGN_SENT)
        || order.approved())
      throw new RuleException("Engraving must be supplied before design approval.");
    String existing = db.queryForObject("SELECT engraving_text FROM orders WHERE id=?", String.class, order.id());
    if (existing != null && !existing.isBlank())
      throw new RuleException("Engraving is already recorded. Take a new agreed order for changes.");
    db.update("UPDATE orders SET engraving_text=?, updated_at=now() WHERE id=?", clean, order.id());
    event(order.id(), "NOTE", null, null, "Customer supplied the missing engraving details.", actor);
  }

  /** Corrects contact details before shipping. The history never copies the old or new values. */
  @Transactional
  public void updateContact(
      String code, String name, String rawPhone, String address, String actor) {
    String cleanName = OrderText.text(name, false);
    if (cleanName.isEmpty() || OrderText.length(cleanName) > OrderText.MAX_NAME)
      throw new RuleException("Enter a name of up to 100 characters.");
    String cleanAddress = OrderText.text(address, true);
    if (cleanAddress.isEmpty() || OrderText.length(cleanAddress) > OrderText.MAX_ADDRESS)
      throw new RuleException("Enter an address of up to 500 characters.");
    String phone;
    try {
      phone = PhoneNormalizer.normalize(rawPhone);
    } catch (IllegalArgumentException ex) {
      throw new RuleException("Enter a valid Bangladeshi mobile number.");
    }
    Locked order = lock(code);
    if (!order.status().beforeShipping())
      throw new RuleException("Contact details can only be corrected before shipping.");
    db.update(
        "UPDATE orders SET customer_name=?, phone=?, address=?, updated_at=now() WHERE id=?",
        cleanName,
        phone,
        cleanAddress,
        order.id());
    event(order.id(), "CONTACT_UPDATED", null, null, null, actor);
  }

  /** Corrects courier details on a shipped order; shipping itself sets them the first time. */
  @Transactional
  public void updateCourier(String code, String courier, String tracking, String actor) {
    Locked order = lock(code);
    if (order.status() != OrderStatus.SHIPPED)
      throw new RuleException("Courier details can only be corrected on a shipped order.");
    saveCourier(order.id(), courier, tracking);
    event(order.id(), "COURIER_UPDATED", null, null, null, actor);
  }

  @Transactional
  public void addNote(String code, String note, String actor) {
    String clean = required(note, 1000, "Write the note first.");
    Locked order = lock(code);
    event(order.id(), "NOTE", null, null, clean, actor);
  }

  private static String required(String raw, int max, String message) {
    String value = OrderText.text(raw, true);
    if (value.isEmpty()) throw new RuleException(message);
    if (OrderText.length(value) > max)
      throw new RuleException("Keep this under " + max + " characters.");
    return value;
  }
}
