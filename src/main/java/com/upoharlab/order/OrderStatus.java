package com.upoharlab.order;

import java.util.Set;

public enum OrderStatus {
  NEW,
  DESIGN_SENT,
  CONFIRMED,
  MAKING,
  SHIPPED,
  DELIVERED,
  CANCELLED,
  RETURNED;

  /** The only moves the server allows; extra conditions are checked in AdminOrderService. */
  public Set<OrderStatus> next() {
    return switch (this) {
      // Straight to CONFIRMED only for orders without a design; AdminOrderService checks that.
      case NEW -> Set.of(DESIGN_SENT, CONFIRMED, CANCELLED);
      case DESIGN_SENT -> Set.of(CONFIRMED, CANCELLED);
      case CONFIRMED -> Set.of(MAKING, CANCELLED);
      case MAKING -> Set.of(SHIPPED, CANCELLED);
      case SHIPPED -> Set.of(DELIVERED, RETURNED);
      case DELIVERED -> Set.of(RETURNED);
      case CANCELLED, RETURNED -> Set.of();
    };
  }

  /** The owner-facing name, in English like the rest of the admin pages. */
  public String label() {
    return switch (this) {
      case NEW -> "New";
      case DESIGN_SENT -> "Design sent";
      case CONFIRMED -> "Confirmed";
      case MAKING -> "Being made";
      case SHIPPED -> "Shipped";
      case DELIVERED -> "Delivered";
      case CANCELLED -> "Cancelled";
      case RETURNED -> "Returned";
    };
  }

  /** The order is over and owes nothing more; only held money may still be refunded. */
  public boolean closed() {
    return this == CANCELLED || this == RETURNED;
  }

  public boolean beforeShipping() {
    return this == NEW || this == DESIGN_SENT || this == CONFIRMED || this == MAKING;
  }
}
