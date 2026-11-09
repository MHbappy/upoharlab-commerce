package com.upoharlab.landing;

import java.util.List;

/**
 * What the customer chose, saved with the order. Holds parameters only, never markup, and keeps
 * its own copy of the design so later page edits cannot change an order already placed.
 */
public record LandingSelection(
    String text,
    String font,
    int fontSize,
    LandingConfig.Design design,
    String packageName,
    String packageDescription,
    int packagePrice,
    List<Chosen> options,
    List<Chosen> upsells) {

  /** A named choice and what it added to the price. */
  public record Chosen(String label, String value, int price) {}
}
