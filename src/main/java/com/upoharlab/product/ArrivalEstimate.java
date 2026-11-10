package com.upoharlab.product;

/**
 * Roughly how many days until a customer holds the product: the product's own making days plus a
 * fixed {@value #DELIVERY_DAYS} days for delivery. For example, making 1–2 days shows 3–4 days.
 */
public final class ArrivalEstimate {
  /** Days added to the making time for delivery. */
  public static final int DELIVERY_DAYS = 2;

  /** Days from order to delivery; {@code min == max} when there is no spread. */
  public record Range(int min, int max) {
    public boolean single() {
      return min == max;
    }
  }

  private ArrivalEstimate() {}

  /** Null until the product has both making days. */
  public static Range of(Integer makingMin, Integer makingMax) {
    if (makingMin == null || makingMax == null) return null;
    return new Range(makingMin + DELIVERY_DAYS, makingMax + DELIVERY_DAYS);
  }
}
