package com.upoharlab.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductionChecksTest {
  @Test
  void acceptsSafeSettings() {
    assertThatCode(
            () ->
                new ProductionChecks("https://upoharlab.com", true, "native").afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void refusesUnsafeSettings() {
    assertThatIllegalStateException()
        .isThrownBy(() -> new ProductionChecks("", true, "native").afterPropertiesSet());
    assertThatIllegalStateException()
        .isThrownBy(
            () -> new ProductionChecks("http://upoharlab.com", true, "native").afterPropertiesSet());
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new ProductionChecks("https://upoharlab.com", false, "native")
                    .afterPropertiesSet());
    assertThatIllegalStateException()
        .isThrownBy(
            () -> new ProductionChecks("https://upoharlab.com", true, "none").afterPropertiesSet());
  }
}
