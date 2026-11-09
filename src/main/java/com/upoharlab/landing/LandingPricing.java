package com.upoharlab.landing;

import java.util.Collection;
import java.util.List;

/** The single source of amounts for landing orders, always worked out from saved configuration. */
public final class LandingPricing {
  public static final String INSIDE = "INSIDE";
  public static final String OUTSIDE = "OUTSIDE";

  /** {@code optionValues} holds one chosen value per option, in option order. */
  public record Selection(
      int packageIndex, List<Integer> optionValues, Collection<Integer> upsells, String area) {}

  /** {@code delivery}, {@code total}, {@code advance} and {@code balance} wait for an area. */
  public record Quote(
      int packagePrice,
      int extras,
      Integer delivery,
      Integer total,
      Integer advance,
      Integer balance) {}

  private LandingPricing() {}

  /** Indexes must already be validated. */
  public static Quote quote(LandingConfig c, Selection s) {
    int price = c.packages().get(s.packageIndex()).price();
    int extras = 0;
    for (int i = 0; i < c.options().size() && i < s.optionValues().size(); i++)
      extras += c.options().get(i).values().get(s.optionValues().get(i)).priceDelta();
    for (int index : s.upsells()) extras += c.upsells().get(index).price();
    Integer delivery = delivery(c, s.area());
    if (delivery == null) return new Quote(price, extras, null, null, null, null);
    int total = price + extras + delivery;
    int advance = Math.min(c.advanceAmount(), total);
    return new Quote(price, extras, delivery, total, advance, total - advance);
  }

  private static Integer delivery(LandingConfig c, String area) {
    if (INSIDE.equals(area)) return c.delivery().insideDhakaCharge();
    if (OUTSIDE.equals(area)) return c.delivery().outsideDhakaCharge();
    return null;
  }
}
