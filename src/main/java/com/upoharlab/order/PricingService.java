package com.upoharlab.order;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The single source of order amounts, in whole taka, for both the page and submission. */
@Service
public class PricingService {
  public static final String INSIDE = "INSIDE";
  public static final String OUTSIDE = "OUTSIDE";

  public record Prices(int unit, int inside, int outside) implements java.io.Serializable {
    int delivery(String area) {
      return INSIDE.equals(area) ? inside : outside;
    }
  }

  /** Null fields mean quantity or area has not been validly chosen yet. */
  public record Quote(
      int unit,
      Integer quantity,
      Integer subtotal,
      Integer delivery,
      Integer total,
      Integer advance,
      Integer balance) {}

  public Optional<Prices> prices(Integer unitPrice, Map<String, String> settings) {
    Integer inside = charge(settings.get("delivery_inside"));
    Integer outside = charge(settings.get("delivery_outside"));
    if (unitPrice == null || inside == null || outside == null) return Optional.empty();
    return Optional.of(new Prices(unitPrice, inside, outside));
  }

  private static Integer charge(String value) {
    try {
      int charge = Integer.parseInt(value == null ? "" : value.strip());
      return charge < 0 ? null : charge;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public Quote quote(Prices prices, Integer quantity, String area) {
    Integer subtotal = quantity == null ? null : prices.unit() * quantity;
    Integer delivery =
        INSIDE.equals(area) || OUTSIDE.equals(area) ? prices.delivery(area) : null;
    if (subtotal == null || delivery == null)
      return new Quote(prices.unit(), quantity, subtotal, delivery, null, null, null);
    int total = subtotal + delivery;
    int advance = 0;
    return new Quote(prices.unit(), quantity, subtotal, delivery, total, advance, total - advance);
  }
}
