package com.upoharlab.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Stops a production start with unsafe settings, rather than running insecurely unnoticed. */
@Component
@Profile("prod")
public class ProductionChecks implements InitializingBean {
  private final String baseUrl;
  private final boolean secureCookie;
  private final String forwardHeaders;

  public ProductionChecks(
      @Value("${app.base-url:}") String baseUrl,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie,
      @Value("${server.forward-headers-strategy:none}") String forwardHeaders) {
    this.baseUrl = baseUrl;
    this.secureCookie = secureCookie;
    this.forwardHeaders = forwardHeaders;
  }

  @Override
  public void afterPropertiesSet() {
    if (!baseUrl.startsWith("https://"))
      throw new IllegalStateException("APP_BASE_URL must be the site's https:// address.");
    if (!secureCookie) throw new IllegalStateException("Production session cookies must be secure.");
    if (!"native".equalsIgnoreCase(forwardHeaders))
      throw new IllegalStateException("Production must read the client address from Caddy.");
  }
}
