package com.upoharlab.shared;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNormalizerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "01712345678",
        "০১৭১২৩৪৫৬৭৮",
        "+880 1712-345678",
        "8801712345678",
        " 01712 345678 ",
        "+৮৮০ ১৭১২-৩৪৫৬৭৮",
        "01712\u00a0345678"
      })
  void acceptsSupportedFormats(String value) {
    assertThat(PhoneNormalizer.normalize(value)).isEqualTo("01712345678");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "01212345678",
        "0171234567",
        "017123456789",
        "+911712345678",
        "hello01712345678",
        "++8801712345678",
        "01712.345678"
      })
  void rejectsInvalidNumbers(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> PhoneNormalizer.normalize(value));
  }

  @Test
  void rejectsMissingNumber() {
    assertThatIllegalArgumentException().isThrownBy(() -> PhoneNormalizer.normalize(null));
  }
}
