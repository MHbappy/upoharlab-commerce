package com.upoharlab.order;

import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.shared.BnDigits;
import com.upoharlab.shared.PhoneNormalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orders the owner takes by phone, Facebook or in person. They join the normal workflow as NEW. */
@Service
public class ManualOrderService {
  private static final int MAX_MONEY = 1_000_000;

  public record ProductChoice(long id, String title, Integer price) {}

  /** The owner's typed values, kept as text so a refused form can be shown again unchanged. */
  public static class Form {
    public String productId, quantity = "1", unitPrice, extras, name, phone, area = "INSIDE";
    public String delivery, address, engraving, advance, note, neededBy;

    public String getProductId() { return productId; }
    public void setProductId(String v) { productId = v; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String v) { quantity = v; }
    public String getUnitPrice() { return unitPrice; }
    public void setUnitPrice(String v) { unitPrice = v; }
    public String getExtras() { return extras; }
    public void setExtras(String v) { extras = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getArea() { return area; }
    public void setArea(String v) { area = v; }
    public String getDelivery() { return delivery; }
    public void setDelivery(String v) { delivery = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getEngraving() { return engraving; }
    public void setEngraving(String v) { engraving = v; }
    public String getAdvance() { return advance; }
    public void setAdvance(String v) { advance = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }
    public String getNeededBy() { return neededBy; }
    public void setNeededBy(String v) { neededBy = v; }
  }

  private final JdbcTemplate db;
  private final OrderCodeGenerator codes;

  public ManualOrderService(JdbcTemplate db, OrderCodeGenerator codes) {
    this.db = db;
    this.codes = codes;
  }

  public List<ProductChoice> products() {
    return db.query(
        "SELECT id, title_bn, price FROM product WHERE status<>'DRAFT' ORDER BY sort_order, id",
        (r, n) -> new ProductChoice(r.getLong(1), r.getString(2), r.getObject(3, Integer.class)));
  }

  @Transactional
  public String create(Form form, String actor) {
    long productId = number(form.productId, 1, Long.MAX_VALUE, null, "Choose a product.");
    ProductChoice product =
        db.query(
                "SELECT id, title_bn, price FROM product WHERE id=? AND status<>'DRAFT' FOR SHARE",
                (r, n) ->
                    new ProductChoice(r.getLong(1), r.getString(2), r.getObject(3, Integer.class)),
                productId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuleException("Choose a product."));
    int quantity = (int) number(form.quantity, 1, 10, null, "Enter a quantity from 1 to 10.");
    int unitPrice =
        (int)
            number(
                form.unitPrice, 1, MAX_MONEY,
                product.price() == null ? null : product.price().longValue(),
                "Enter the unit price in whole taka.");
    int extras = (int) number(form.extras, 0, MAX_MONEY, 0L, "Enter extras in whole taka.");
    if (!List.of("INSIDE", "OUTSIDE").contains(Objects.toString(form.area, "")))
      throw new RuleException("Choose the delivery area.");
    int delivery =
        (int)
            number(
                form.delivery, 0, MAX_MONEY, savedDelivery(form.area),
                "Enter the delivery charge, or set it in Settings.");
    int total = unitPrice * quantity + extras + delivery;
    int advance = (int) number(form.advance, 0, MAX_MONEY, 0L, "Enter the advance in whole taka.");
    if (advance > total) throw new RuleException("The advance cannot be more than the ৳" + total + " total.");

    String name = limited(form.name, false, OrderText.MAX_NAME, "Enter a name of up to 100 characters.");
    if (name.isEmpty()) throw new RuleException("Enter a name of up to 100 characters.");
    String address = limited(form.address, true, OrderText.MAX_ADDRESS, "Enter an address of up to 500 characters.");
    if (address.isEmpty()) throw new RuleException("Enter an address of up to 500 characters.");
    String phone;
    try {
      phone = PhoneNormalizer.normalize(form.phone);
    } catch (IllegalArgumentException ex) {
      throw new RuleException("Enter a valid Bangladeshi mobile number.");
    }
    String engraving = limited(form.engraving, true, OrderText.MAX_ENGRAVING, "Keep the engraving under 1000 characters.");
    String note = limited(form.note, true, OrderText.MAX_NOTE, "Keep the note under 500 characters.");
    LocalDate neededBy = neededBy(form.neededBy);

    String code = codes.next();
    Long id =
        db.queryForObject(
            "INSERT INTO orders (code, submit_token, product_id, product_title_snapshot, quantity,"
                + " engraving_text, customer_name, phone, area, address, note, unit_price_snapshot,"
                + " extras_snapshot, delivery_charge_snapshot, total, advance_required, status,"
                + " needed_by, needs_design)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW',?,?) RETURNING id",
            Long.class,
            code,
            "admin-" + UUID.randomUUID(),
            product.id(),
            product.title(),
            quantity,
            engraving,
            name,
            phone,
            form.area,
            address,
            note.isEmpty() ? null : note,
            unitPrice,
            extras,
            delivery,
            total,
            advance,
            neededBy,
            !engraving.isEmpty());
    db.update(
        "INSERT INTO order_event (order_id, event_type, to_status, note, actor)"
            + " VALUES (?, 'CREATED', 'NEW', 'Entered by the owner.', ?)",
        id,
        actor);
    return code;
  }

  private Long savedDelivery(String area) {
    return db
        .queryForList(
            "SELECT value FROM setting WHERE key=?",
            String.class,
            "INSIDE".equals(area) ? "delivery_inside" : "delivery_outside")
        .stream()
        .filter(value -> value != null && value.strip().matches("[0-9]{1,7}"))
        .map(value -> Long.valueOf(value.strip()))
        .findFirst()
        .orElse(null);
  }

  /** A blank box takes the fallback; with no fallback it is an error. Bangla digits are accepted. */
  private static long number(String raw, long min, long max, Long fallback, String message) {
    String value = BnDigits.toLatin(Objects.toString(raw, "")).strip();
    if (value.isEmpty() && fallback != null) return fallback;
    if (!value.matches("[0-9]{1,18}")) throw new RuleException(message);
    long number = Long.parseLong(value);
    if (number < min || number > max) throw new RuleException(message);
    return number;
  }

  private static String limited(String raw, boolean multiline, int max, String message) {
    String value = OrderText.text(raw, multiline);
    if (OrderText.length(value) > max) throw new RuleException(message);
    return value;
  }

  private static LocalDate neededBy(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return LocalDate.parse(raw.strip());
    } catch (DateTimeParseException ex) {
      throw new RuleException("Enter the needed-by date as a calendar date.");
    }
  }
}
