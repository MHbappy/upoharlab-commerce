package com.upoharlab.order;

import java.util.Set;

public enum OrderStatus {
  NEW,
  DESIGN_SENT,
  CONFIRMED,
  MAKING,
  SHIPPED,
  DELIVERED,
  CANCELLED;

  /** The only moves the server allows; extra conditions are checked in AdminOrderService. */
  public Set<OrderStatus> next() {
    return switch (this) {
      case NEW -> Set.of(DESIGN_SENT, CANCELLED);
      case DESIGN_SENT -> Set.of(CONFIRMED, CANCELLED);
      case CONFIRMED -> Set.of(MAKING, CANCELLED);
      case MAKING -> Set.of(SHIPPED, CANCELLED);
      case SHIPPED -> Set.of(DELIVERED);
      case DELIVERED, CANCELLED -> Set.of();
    };
  }

  public boolean beforeShipping() {
    return this == NEW || this == DESIGN_SENT || this == CONFIRMED || this == MAKING;
  }
}
