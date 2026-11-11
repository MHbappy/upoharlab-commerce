package com.upoharlab.order;

import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only views of all orders: the list, work queues, money totals, exports. */
@Service
public class OrderBoard {
  static final int PAGE_SIZE = 25;
  private static final int EXPORT_LIMIT = 5000;

  private static final String PAID =
      "(SELECT coalesce(sum(CASE WHEN p.kind='PAYMENT' THEN p.amount ELSE -p.amount END),0)"
          + " FROM payment p WHERE p.order_id=o.id AND p.voided_at IS NULL)";
  /** Waiting only on the advance or the owner's confirmation. */
  private static final String CONFIRMABLE =
      "((o.status='NEW' AND NOT o.needs_design)"
          + " OR (o.status='DESIGN_SENT' AND o.design_approved_at IS NOT NULL))";

  /** The owner's to-do lists. Conditions are fixed text, never built from a request. */
  public enum Queue {
    DESIGN_TO_SEND("Design to send", "o.status='NEW' AND o.needs_design"),
    AWAITING_APPROVAL(
        "Waiting for approval", "o.status='DESIGN_SENT' AND o.design_approved_at IS NULL"),
    ADVANCE_DUE("Advance to collect", CONFIRMABLE + " AND " + PAID + " < o.advance_required"),
    TO_CONFIRM("Ready to confirm", CONFIRMABLE + " AND " + PAID + " >= o.advance_required"),
    TO_MAKE("Ready to make", "o.status='CONFIRMED'"),
    DUE_SOON(
        "Needed within 2 days or late",
        "o.status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING')"
            + " AND o.needed_by <= (now() AT TIME ZONE 'Asia/Dhaka')::date + 2"),
    STUCK_SHIPPING(
        "Shipped over 7 days ago",
        "o.status='SHIPPED' AND (SELECT max(e.created_at) FROM order_event e"
            + " WHERE e.order_id=o.id AND e.to_status='SHIPPED') < now() - interval '7 days'"),
    TO_COLLECT(
        "Delivered, money to collect",
        "o.status='DELIVERED' AND " + PAID + " < o.total AND NOT EXISTS (SELECT 1 FROM payment p"
            + " WHERE p.order_id=o.id AND p.kind='REFUND' AND p.voided_at IS NULL)"),
    TO_REFUND("Money to refund", "o.status IN ('CANCELLED','RETURNED') AND " + PAID + " > 0");

    private final String label, condition;

    Queue(String label, String condition) {
      this.label = label;
      this.condition = condition;
    }

    public String label() {
      return label;
    }
  }

  public record Filter(
      String status, String queue, String q, String from, String to, String sort, int page) {}

  public record Row(
      String code,
      Instant createdAt,
      String productTitle,
      int quantity,
      String customerName,
      String maskedPhone,
      int total,
      long paid,
      LocalDate neededBy,
      String status) {
    /** Still owed by the customer; null once the order is cancelled or returned. */
    public Long due() {
      return OrderStatus.valueOf(status).closed() ? null : Long.valueOf(total - paid);
    }
  }

  public record Results(List<Row> rows, int page, int totalPages, int totalRows) {}

  public record QueueCount(String key, String label, int count) {}

  public record Money(
      int ordersThisMonth, long valueThisMonth, long receivedThisMonth, long inTransit, long toCollect, long toRefund) {}

  public record CustomerHistory(
      int others, int delivered, int returned, int cancelled, List<Row> recent) {}

  private final JdbcTemplate db;
  private final Clock clock;

  public OrderBoard(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** Every status with its order count, in workflow order. */
  public Map<String, Integer> stageCounts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (OrderStatus status : OrderStatus.values()) counts.put(status.name(), 0);
    db.query(
        "SELECT status, count(*) FROM orders GROUP BY status",
        r -> {
          counts.put(r.getString(1), r.getInt(2));
        });
    return counts;
  }

  public List<QueueCount> queueCounts() {
    StringBuilder sql = new StringBuilder("SELECT 0");
    for (Queue queue : Queue.values())
      sql.append(", count(*) FILTER (WHERE ").append(queue.condition).append(")");
    return db.queryForObject(
        sql + " FROM orders o",
        (r, n) -> {
          List<QueueCount> counts = new ArrayList<>();
          for (Queue queue : Queue.values())
            counts.add(new QueueCount(queue.name(), queue.label(), r.getInt(queue.ordinal() + 2)));
          return counts;
        });
  }

  public Money money() {
    Timestamp month =
        Timestamp.from(YearMonth.now(clock).atDay(1).atStartOfDay(clock.getZone()).toInstant());
    return db.queryForObject(
        "SELECT"
            + " (SELECT count(*) FROM orders WHERE created_at>=? AND status NOT IN ('CANCELLED','RETURNED')),"
            + " (SELECT coalesce(sum(total),0) FROM orders WHERE created_at>=? AND status NOT IN ('CANCELLED','RETURNED')),"
            + " (SELECT coalesce(sum(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END),0)"
            + "  FROM payment WHERE recorded_at>=? AND voided_at IS NULL),"
            + " (SELECT coalesce(sum(o.total-" + PAID + "),0) FROM orders o WHERE o.status='SHIPPED'),"
            + " (SELECT coalesce(sum(o.total-" + PAID + "),0) FROM orders o WHERE " + Queue.TO_COLLECT.condition + "),"
            + " (SELECT coalesce(sum(" + PAID + "),0) FROM orders o WHERE " + Queue.TO_REFUND.condition + ")",
        (r, n) ->
            new Money(r.getInt(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5), r.getLong(6)),
        month,
        month,
        month);
  }

  public List<Row> recent(int limit) {
    return db.query(
        "SELECT o.*, " + PAID + " AS paid FROM orders o ORDER BY o.created_at DESC, o.id DESC LIMIT ?",
        (r, n) -> row(r),
        limit);
  }

  public Results search(Filter filter) {
    List<Object> args = new ArrayList<>();
    String where = where(filter, args);
    int total =
        db.queryForObject("SELECT count(*) FROM orders o" + where, Integer.class, args.toArray());
    int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    int page = Math.max(0, Math.min(filter.page(), pages - 1));
    args.add(PAGE_SIZE);
    args.add(page * PAGE_SIZE);
    List<Row> rows =
        db.query(
            "SELECT o.*, " + PAID + " AS paid FROM orders o" + where + order(filter) + " LIMIT ? OFFSET ?",
            (r, n) -> row(r),
            args.toArray());
    return new Results(rows, page, pages, total);
  }

  /** Header first, then every matching order with full contact details for the courier. */
  public List<List<String>> export(Filter filter) {
    List<Object> args = new ArrayList<>();
    String where = where(filter, args);
    args.add(EXPORT_LIMIT);
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Order", "Placed", "Status", "Product", "Quantity", "Customer", "Phone", "Area",
            "Address", "Total", "Paid", "Due", "Needed by", "Courier", "Tracking"));
    rows.addAll(
        db.query(
            "SELECT o.*, " + PAID + " AS paid FROM orders o" + where + order(filter) + " LIMIT ?",
            (r, n) -> {
              OrderStatus status = OrderStatus.valueOf(r.getString("status"));
              int total = r.getInt("total");
              long paid = r.getLong("paid");
              return List.of(
                  r.getString("code"),
                  r.getTimestamp("created_at").toInstant().atZone(clock.getZone()).toLocalDate().toString(),
                  status.label(),
                  r.getString("product_title_snapshot"),
                  String.valueOf(r.getInt("quantity")),
                  r.getString("customer_name"),
                  r.getString("phone"),
                  "INSIDE".equals(r.getString("area")) ? "Inside Dhaka" : "Outside Dhaka",
                  r.getString("address"),
                  String.valueOf(total),
                  String.valueOf(paid),
                  status.closed() ? "" : String.valueOf(total - paid),
                  Objects.toString(r.getObject("needed_by", LocalDate.class), ""),
                  Objects.toString(r.getString("courier"), ""),
                  Objects.toString(r.getString("tracking_no"), ""));
            },
            args.toArray()));
    return rows;
  }

  /** The same phone's other orders, so risky repeat returns are visible before shipping. */
  public CustomerHistory customerHistory(String phone, String exceptCode) {
    List<Row> others =
        db.query(
            "SELECT o.*, " + PAID + " AS paid FROM orders o WHERE o.phone=? AND o.code<>?"
                + " ORDER BY o.created_at DESC, o.id DESC",
            (r, n) -> row(r),
            phone,
            exceptCode);
    return new CustomerHistory(
        others.size(),
        count(others, OrderStatus.DELIVERED),
        count(others, OrderStatus.RETURNED),
        count(others, OrderStatus.CANCELLED),
        others.stream().limit(5).toList());
  }

  private static int count(List<Row> rows, OrderStatus status) {
    return (int) rows.stream().filter(row -> status.name().equals(row.status())).count();
  }

  private String where(Filter filter, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE true");
    if (validStatus(filter.status())) {
      where.append(" AND o.status=?");
      args.add(filter.status());
    }
    Queue queue = queue(filter.queue());
    if (queue != null) where.append(" AND ").append(queue.condition);
    LocalDate from = date(filter.from()), to = date(filter.to());
    if (from != null) {
      where.append(" AND o.created_at>=?");
      args.add(Timestamp.from(from.atStartOfDay(clock.getZone()).toInstant()));
    }
    if (to != null) {
      where.append(" AND o.created_at<?");
      args.add(Timestamp.from(to.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
    }
    String query = Objects.toString(filter.q(), "").strip();
    if (query.length() > 40) query = query.substring(0, 40);
    if (!query.isEmpty()) {
      String like = "%" + escapeLike(query) + "%";
      where.append(
          " AND (o.code ILIKE ? ESCAPE '\\' OR o.customer_name ILIKE ? ESCAPE '\\'"
              + " OR o.courier ILIKE ? ESCAPE '\\' OR o.tracking_no ILIKE ? ESCAPE '\\'");
      args.addAll(List.of(like, like, like, like));
      String digits = phoneSearchDigits(query);
      if (digits != null) {
        where.append(" OR o.phone LIKE ?");
        args.add("%" + digits + "%");
      }
      where.append(")");
    }
    return where.toString();
  }

  private static String order(Filter filter) {
    return switch (Objects.toString(filter.sort(), "")) {
      case "oldest" -> " ORDER BY o.created_at, o.id";
      case "needed" -> " ORDER BY o.needed_by NULLS LAST, o.created_at, o.id";
      default -> " ORDER BY o.created_at DESC, o.id DESC";
    };
  }

  private static boolean validStatus(String status) {
    for (OrderStatus value : OrderStatus.values()) if (value.name().equals(status)) return true;
    return false;
  }

  private static Queue queue(String name) {
    for (Queue value : Queue.values()) if (value.name().equals(name)) return value;
    return null;
  }

  private static LocalDate date(String raw) {
    try {
      return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.strip());
    } catch (DateTimeParseException notADate) {
      return null;
    }
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

  private static Row row(ResultSet r) throws SQLException {
    return new Row(
        r.getString("code"),
        r.getTimestamp("created_at").toInstant(),
        r.getString("product_title_snapshot"),
        r.getInt("quantity"),
        r.getString("customer_name"),
        PhoneNormalizer.mask(r.getString("phone")),
        r.getInt("total"),
        r.getLong("paid"),
        r.getObject("needed_by", LocalDate.class),
        r.getString("status"));
  }
}
