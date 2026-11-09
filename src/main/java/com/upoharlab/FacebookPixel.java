package com.upoharlab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The optional Facebook Pixel. Anything other than a plain numeric ID counts as not configured. */
@Component("pixel")
public class FacebookPixel {
  private final String id;

  public FacebookPixel(@Value("${app.facebook.pixel-id:}") String id) {
    this.id = id != null && id.strip().matches("[0-9]{5,25}") ? id.strip() : null;
  }

  public boolean enabled() {
    return id != null;
  }

  public String id() {
    return id;
  }
}
