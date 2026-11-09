package com.upoharlab.order;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfigCodec;
import com.upoharlab.landing.LandingPricing;
import com.upoharlab.landing.LandingPricing.Quote;
import com.upoharlab.landing.LandingPricing.Selection;
import com.upoharlab.landing.LandingSelection;
import com.upoharlab.order.LandingOrderValidator.Input;
import com.upoharlab.order.OrderService.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves landing-page orders, and reads back what was chosen for them. */
@Service
public class LandingOrderService {
  /** A landing order as shown on its thank-you page. */
  public record Thanks(LandingSelection selection, int total, int advance, int balance) {}

  private final JdbcTemplate db;
  private final OrderCodeGenerator codes;
  private final ApplicationEventPublisher events;

  public LandingOrderService(
      JdbcTemplate db, OrderCodeGenerator codes, ApplicationEventPublisher events) {
    this.db = db;
    this.codes = codes;
    this.events = events;
  }

  /**
   * Saves only if the shop is open and the configuration is the one the customer saw. Rows are
   * share-locked, so an owner's edit cannot slip in between the check and the save.
   *
   * @throws org.springframework.dao.DuplicateKeyException when this submit token was already used
   */
  @Transactional
  public PlaceResult place(long productId, Input input, String submitToken, String seenFingerprint) {
    record Row(String title, String status, String json) {}
    Optional<Row> product =
        db.query(
                "SELECT title_bn, status, landing_config FROM product WHERE id=? FOR SHARE",
                (r, n) -> new Row(r.getString(1), r.getString(2), r.getString(3)),
                productId)
            .stream()
            .findFirst();
    List<String> accepting =
        db.queryForList(
            "SELECT value FROM setting WHERE key='accepting_orders' FOR SHARE", String.class);
    if (product.isEmpty()
        || product.get().json() == null
        || !"ACTIVE".equals(product.get().status())
        || !accepting.equals(List.of("true"))) return new Closed();
    LandingConfig config = LandingConfigCodec.read(product.get().json()).forOrdering();
    if (!config.enabled() || !config.sections().order()) return new Closed();
    if (!LandingConfigCodec.fingerprint(config).equals(seenFingerprint)) return new PricesChanged();
    Selection selection = input.selection();
    Quote quote = LandingPricing.quote(config, selection);
    String code = codes.next();
    Long orderId =
        db.queryForObject(
            "INSERT INTO orders (code, submit_token, product_id, product_title_snapshot, quantity,"
                + " engraving_text, customer_name, phone, area, address, note,"
                + " unit_price_snapshot, extras_snapshot, delivery_charge_snapshot, total,"
                + " advance_required, status, landing_selection, needed_by)"
                + " VALUES (?,?,?,?,1,?,?,?,?,?,?,?,?,?,?,?,'NEW',CAST(? AS jsonb),?) RETURNING id",
            Long.class,
            code,
            submitToken,
            productId,
            product.get().title(),
            input.text(),
            input.name(),
            input.phone(),
            selection.area(),
            input.address(),
            input.note(),
            quote.packagePrice(),
            quote.extras(),
            quote.delivery(),
            quote.total(),
            quote.advance(),
            LandingConfigCodec.write(snapshot(config, input)),
            input.neededBy());
    db.update(
        "INSERT INTO order_event (order_id, event_type, to_status, actor)"
            + " VALUES (?, 'CREATED', 'NEW', 'customer')",
        orderId);
    events.publishEvent(new OrderPlaced(code, product.get().title(), 1, quote.total()));
    return new Placed(code);
  }

  static LandingSelection snapshot(LandingConfig c, Input input) {
    Selection s = input.selection();
    var tier = c.packages().get(s.packageIndex());
    List<LandingSelection.Chosen> options = new ArrayList<>();
    for (int i = 0; i < c.options().size(); i++) {
      var option = c.options().get(i);
      var value = option.values().get(s.optionValues().get(i));
      options.add(new LandingSelection.Chosen(option.label(), value.name(), value.priceDelta()));
    }
    List<LandingSelection.Chosen> upsells = new ArrayList<>();
    for (int index : s.upsells()) {
      var upsell = c.upsells().get(index);
      upsells.add(new LandingSelection.Chosen(upsell.label(), "", upsell.price()));
    }
    return new LandingSelection(
        input.text(),
        input.font(),
        input.fontSize(),
        c.preview().designs().get(input.design()),
        tier.name(),
        tier.description(),
        tier.price(),
        options,
        upsells);
  }

  /** Empty for orders placed through the older product page. */
  public Optional<LandingSelection> selection(String code) {
    return db
        .query(
            "SELECT landing_selection FROM orders WHERE code=? AND landing_selection IS NOT NULL",
            (r, n) -> LandingConfigCodec.readAs(r.getString(1), LandingSelection.class),
            code)
        .stream()
        .findFirst();
  }

  public Optional<java.time.LocalDate> neededBy(String code) {
    return db.query(
            "SELECT needed_by FROM orders WHERE code=? AND needed_by IS NOT NULL",
            (r, n) -> r.getObject(1, java.time.LocalDate.class),
            code)
        .stream()
        .findFirst();
  }

  public Optional<Thanks> thanks(String code) {
    return db
        .query(
            "SELECT landing_selection, total, advance_required FROM orders"
                + " WHERE code=? AND landing_selection IS NOT NULL",
            (r, n) ->
                new Thanks(
                    LandingConfigCodec.readAs(r.getString(1), LandingSelection.class),
                    r.getInt(2),
                    r.getInt(3),
                    r.getInt(2) - r.getInt(3)),
            code)
        .stream()
        .findFirst();
  }
}
