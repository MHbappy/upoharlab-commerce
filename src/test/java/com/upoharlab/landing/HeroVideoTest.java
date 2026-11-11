package com.upoharlab.landing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The owner pastes any YouTube link; pages only ever use the 11-character video id. */
class HeroVideoTest {
  private static LandingConfig.Hero hero(String link) {
    return new LandingConfig.Hero("শিরোনাম", "", "", null, 899, List.of(), link);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "https://youtube.com/watch?v=dQw4w9WgXcQ&t=10s",
    "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
    "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
    "https://youtu.be/dQw4w9WgXcQ",
    "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123",
    "youtu.be/dQw4w9WgXcQ",
    "https://www.youtube.com/shorts/dQw4w9WgXcQ",
    "https://youtube.com/shorts/dQw4w9WgXcQ?feature=share",
    "https://www.youtube.com/embed/dQw4w9WgXcQ",
    "https://www.youtube.com/live/dQw4w9WgXcQ",
    "  https://youtu.be/dQw4w9WgXcQ  "
  })
  void everyYouTubeLinkStyleGivesTheVideoId(String link) {
    assertThat(hero(link).videoId()).isEqualTo("dQw4w9WgXcQ");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "https://vimeo.com/123456789",
    "https://www.facebook.com/watch?v=123",
    "https://youtu.be/short",
    "https://evil.example/?u=https://youtu.be/dQw4w9WgXcQ",
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ\"><script>",
    "javascript:alert(1)",
    "just text"
  })
  void anythingElseGivesNoVideo(String link) {
    assertThat(hero(link).videoId()).isNull();
  }

  @Test
  void theVideoCountsAsTheFirstSlide() {
    assertThat(hero(null).slideCount()).isZero();
    assertThat(hero("https://youtu.be/dQw4w9WgXcQ").slideCount()).isEqualTo(1);
    var withImages = new LandingConfig.Hero("h", "", "a.jpg", null, 1, List.of("b.jpg"), "https://youtu.be/dQw4w9WgXcQ");
    assertThat(withImages.slideCount()).isEqualTo(3);
  }
}
