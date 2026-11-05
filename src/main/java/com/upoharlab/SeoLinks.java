package com.upoharlab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds absolute public links from the configured site address, used in page metadata. */
@Component("seo")
public class SeoLinks {
  private final String baseUrl;

  public SeoLinks(@Value("${app.base-url:}") String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  /** Returns null when no site address is configured, so templates leave the tag out. */
  public String url(String path) {
    return baseUrl.isBlank() || path == null ? null : baseUrl + path;
  }
}
