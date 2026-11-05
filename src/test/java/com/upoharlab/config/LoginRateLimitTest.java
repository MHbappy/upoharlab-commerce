package com.upoharlab.config;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class LoginRateLimitTest {
  @Test
  void limitsUsernameAndIpBucketsIndependently() {
    var limiter =
        new LoginRateLimit(Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
    for (int i = 0; i < 10; i++) assertThat(limiter.allow("user:owner", 10)).isTrue();
    assertThat(limiter.allow("user:owner", 10)).isFalse();
    assertThat(limiter.allow("ip:127.0.0.1", 30)).isTrue();
  }
}
