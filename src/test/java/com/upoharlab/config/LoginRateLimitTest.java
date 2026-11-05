package com.upoharlab.config;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class LoginRateLimitTest {
  private final LoginRateLimit limiter =
      new LoginRateLimit(Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void limitsOneIpGuessingOneUsername() {
    for (int i = 0; i < 10; i++) assertThat(limiter.allow("1.1.1.1", "owner")).isTrue();
    assertThat(limiter.allow("1.1.1.1", "owner")).isFalse();
  }

  @Test
  void attackerCannotLockOutOwnerFromAnotherIp() {
    for (int i = 0; i < 40; i++) limiter.allow("6.6.6.6", "owner");
    assertThat(limiter.allow("1.1.1.1", "owner")).isTrue();
  }

  @Test
  void floodOfUsernamesFromOneIpIsBlockedAndDoesNotAffectOthers() {
    for (int i = 0; i < 10050; i++) limiter.allow("6.6.6.6", "user" + i);
    assertThat(limiter.allow("6.6.6.6", "another")).isFalse();
    assertThat(limiter.allow("1.1.1.1", "owner")).isTrue();
  }

  @Test
  void floodFromManyIpsDoesNotDenyTheOwner() {
    for (int i = 0; i < 25000; i++) limiter.allow("ip-" + i, "user" + i);
    assertThat(limiter.allow("1.1.1.1", "owner")).isTrue();
  }
}
