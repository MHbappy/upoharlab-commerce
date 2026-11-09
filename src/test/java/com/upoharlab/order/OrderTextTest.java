package com.upoharlab.order;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderTextTest {
  @Test
  void limitsCountCharactersNotUnits() {
    assertThat(OrderText.length("😀")).isEqualTo(1);
    assertThat(OrderText.length("ক্ষ")).isEqualTo(3);
  }

  @Test
  void cleansTextBeforeSaving() {
    assertThat(OrderText.text("  নাম\u0000\u0007 ", true)).isEqualTo("নাম");
    assertThat(OrderText.text("এক\r\nদুই", true)).isEqualTo("এক\nদুই");
    assertThat(OrderText.text("এক\nদুই", false)).isEqualTo("এক দুই");
    assertThat(OrderText.text("é", true)).isEqualTo("é");
    assertThat(OrderText.text(null, true)).isEmpty();
  }
}
