package com.upoharlab.shared;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BnDigitsTest {
  @Test
  void translatesDigitsWithoutChangingText() {
    assertThat(BnDigits.toBangla("মূল্য 0123456789")).isEqualTo("মূল্য ০১২৩৪৫৬৭৮৯");
    assertThat(BnDigits.toLatin("০১২৩৪৫৬৭৮৯")).isEqualTo("0123456789");
  }

  @Test
  void formatsMoney() {
    assertThat(BnDigits.money(399)).isEqualTo("৳৩৯৯");
    assertThat(BnDigits.money(0)).isEqualTo("৳০");
    assertThatIllegalArgumentException().isThrownBy(() -> BnDigits.money(-1));
  }

  @Test
  void usesDhakaDateAtUtcMonthBoundary() {
    assertThat(BnDigits.date(Instant.parse("2026-10-31T20:00:00Z"))).isEqualTo("১ নভেম্বর ২০২৬");
  }
}
