package com.upoharlab.shared;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
  private Instant now = Instant.parse("2026-11-05T00:00:00Z");
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };

  @Test
  void blocksAtLimitAndResetsAfterWindow() {
    var limiter = new RateLimiter(clock, Duration.ofHours(1), 100);
    for (int i = 0; i < 5; i++) assertThat(limiter.allow("a", 5)).isTrue();
    assertThat(limiter.allow("a", 5)).isFalse();
    now = now.plus(Duration.ofHours(1));
    assertThat(limiter.allow("a", 5)).isTrue();
  }

  @Test
  void staysBoundedAndKeepsAcceptingNewKeysWhenFull() {
    var limiter = new RateLimiter(clock, Duration.ofHours(1), 3);
    for (int i = 0; i < 50; i++) assertThat(limiter.allow("key" + i, 1)).isTrue();
    assertThat(limiter.size()).isEqualTo(3);
    assertThat(limiter.allow("fresh", 1)).isTrue();
  }

  @Test
  void expiredEntriesAreRemoved() {
    var limiter = new RateLimiter(clock, Duration.ofMinutes(1), 100);
    limiter.allow("a", 1);
    limiter.allow("b", 1);
    now = now.plus(Duration.ofMinutes(2));
    limiter.allow("c", 1);
    assertThat(limiter.size()).isEqualTo(1);
  }
}
