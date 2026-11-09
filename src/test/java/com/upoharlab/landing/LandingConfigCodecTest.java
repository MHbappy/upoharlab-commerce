package com.upoharlab.landing;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LandingConfigCodecTest {
  @Test
  void roundTripsEveryField() {
    LandingConfig config = LandingConfigTestData.complete();
    assertThat(LandingConfigCodec.read(LandingConfigCodec.write(config))).isEqualTo(config);
  }

  @Test
  void keepsThePopularFlagUnderItsDocumentedName() {
    assertThat(LandingConfigCodec.write(LandingConfigTestData.complete()))
        .contains("\"isPopular\":true");
  }

  @Test
  void rejectsMalformedAndUnknownData() {
    assertThatThrownBy(() -> LandingConfigCodec.read("{nope"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LandingConfigCodec.read("{\"surprise\":1}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingSectionsBecomeEmptyRatherThanNull() {
    LandingConfig config = LandingConfigCodec.read("{\"enabled\":false}");
    assertThat(config.packages()).isEmpty();
    assertThat(config.hero()).isNotNull();
    assertThat(config.preview().designs()).isEmpty();
  }

  @Test
  void scriptBlockJsonCannotEscapeItsTagAndStaysValid() {
    var map = java.util.Map.of("text", "</script><b>'& ");
    String block = LandingConfigCodec.forScriptBlock(map);
    assertThat(block).doesNotContain("<", ">", "&", "'", " ");
    assertThat(LandingConfigCodec.readAs(block, java.util.Map.class)).isEqualTo(map);
  }

  @Test
  void fingerprintFollowsPriceChanges() {
    LandingConfig a = LandingConfigTestData.complete();
    String changed = LandingConfigCodec.write(a).replace("\"advanceAmount\":100", "\"advanceAmount\":150");
    assertThat(LandingConfigCodec.fingerprint(LandingConfigCodec.read(changed)))
        .isNotEqualTo(LandingConfigCodec.fingerprint(a));
  }
}
