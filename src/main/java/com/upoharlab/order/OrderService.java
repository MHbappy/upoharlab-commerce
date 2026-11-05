package com.upoharlab.order;

import com.upoharlab.order.OrderValidator.OrderInput;
import com.upoharlab.order.PricingService.Prices;
import com.upoharlab.order.PricingService.Quote;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  /** Holds no customer contact details by design, so alerts cannot leak them. */
  public record OrderPlaced(String code, String productTitle, int quantity, int total) {}

  public sealed interface PlaceResult {}

  public record Placed(String code) implements PlaceResult {}

  public record PricesChanged() implements PlaceResult {}

  public record Closed() implements PlaceResult {}

  public record Saved(
      String code, String productTitle, int quantity, int total, int advance, int balance) {}

  private final JdbcTemplate db;
  private final PricingService pricing;
  private final OrderCodeGenerator codes;
  private final ApplicationEventPublisher events;

  public OrderService(
      JdbcTemplate db,
      PricingService pricing,
      OrderCodeGenerator codes,
      ApplicationEventPublisher events) {
    this.db = db;
    this.pricing = pricing;
    this.codes = codes;
    this.events = events;
  }

  /**
   * Saves the order only if the shop is open and prices still equal those the customer saw. The
   * rows are share-locked, so an owner's price edit cannot slip in between the check and the save.
   *
   * @throws org.springframework.dao.DuplicateKeyException when this submit token was already used
   */
  @Transactional
  public PlaceResult place(long productId, OrderInput input, String submitToken, Prices seen) {
    var product =
        db.query(
                "SELECT title_bn, price, status FROM product WHERE id=? FOR SHARE",
                (r, n) ->
                    new Object[] {
                      r.getString("title_bn"), r.getObject("price"), r.getString("status")
                    },
                productId)
            .stream()
            .findFirst();
    Map<String, String> settings = new HashMap<>();
    db.query(
        "SELECT key, value FROM setting WHERE key IN"
            + " ('accepting_orders','delivery_inside','delivery_outside') FOR SHARE",
        r -> {
          settings.put(r.getString(1), r.getString(2));
        });
    if (product.isEmpty()
        || !"ACTIVE".equals(product.get()[2])
        || !"true".equals(settings.get("accepting_orders"))) return new Closed();
    Optional<Prices> current = pricing.prices((Integer) product.get()[1], settings);
    if (current.isEmpty()) return new Closed();
    if (!current.get().equals(seen)) return new PricesChanged();
    Quote quote = pricing.quote(current.get(), input.quantity(), input.area());
    String title = (String) product.get()[0];
    String code = codes.next();
    Long orderId =
        db.queryForObject(
            "INSERT INTO orders (code, submit_token, product_id, product_title_snapshot, quantity,"
                + " engraving_text, customer_name, phone, area, address, note,"
                + " unit_price_snapshot, delivery_charge_snapshot, total, advance_required, status)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW') RETURNING id",
            Long.class,
            code,
            submitToken,
            productId,
            title,
            input.quantity(),
            input.engraving(),
            input.name(),
            input.phone(),
            input.area(),
            input.address(),
            input.note(),
            quote.unit(),
            quote.delivery(),
            quote.total(),
            quote.advance());
    db.update(
        "INSERT INTO order_event (order_id, event_type, to_status, actor)"
            + " VALUES (?, 'CREATED', 'NEW', 'customer')",
        orderId);
    events.publishEvent(new OrderPlaced(code, title, input.quantity(), quote.total()));
    return new Placed(code);
  }

  public Optional<String> codeForSubmitToken(String submitToken) {
    List<String> codes =
        db.queryForList("SELECT code FROM orders WHERE submit_token=?", String.class, submitToken);
    return codes.stream().findFirst();
  }

  public Optional<Saved> saved(String code) {
    return db
        .query(
            "SELECT code, product_title_snapshot, quantity, total, advance_required FROM orders"
                + " WHERE code=?",
            (r, n) ->
                new Saved(
                    r.getString(1),
                    r.getString(2),
                    r.getInt(3),
                    r.getInt(4),
                    r.getInt(5),
                    r.getInt(4) - r.getInt(5)),
            code)
        .stream()
        .findFirst();
  }
}
