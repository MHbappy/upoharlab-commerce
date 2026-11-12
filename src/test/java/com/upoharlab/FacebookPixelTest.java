package com.upoharlab;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FacebookPixelTest {
  @Test
  void isOffWhenEmpty() {
    assertThat(new FacebookPixel(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), "").enabled()).isFalse();
    assertThat(new FacebookPixel(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), null).enabled()).isFalse();
  }

  @Test
  void acceptsOnlyNumericIds() {
    assertThat(new FacebookPixel(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), "1234567890").id()).isEqualTo("1234567890");
    assertThat(new FacebookPixel(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), "12345\"><script>").enabled()).isFalse();
  }
}
