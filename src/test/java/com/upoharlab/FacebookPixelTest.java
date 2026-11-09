package com.upoharlab;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FacebookPixelTest {
  @Test
  void isOffWhenEmpty() {
    assertThat(new FacebookPixel("").enabled()).isFalse();
    assertThat(new FacebookPixel(null).enabled()).isFalse();
  }

  @Test
  void acceptsOnlyNumericIds() {
    assertThat(new FacebookPixel("1234567890").id()).isEqualTo("1234567890");
    assertThat(new FacebookPixel("12345\"><script>").enabled()).isFalse();
  }
}
