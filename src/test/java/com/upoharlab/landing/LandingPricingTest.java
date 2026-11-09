package com.upoharlab.landing;

import static org.assertj.core.api.Assertions.*;

import com.upoharlab.landing.LandingPricing.Selection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LandingPricingTest {
  private final LandingConfig config = LandingConfigTestData.complete();

  @Test
  void addsPackageOptionUpsellsAndDelivery() {
    var quote =
        LandingPricing.quote(config, new Selection(1, List.of(1), Set.of(0, 1), "OUTSIDE"));
    assertThat(quote.packagePrice()).isEqualTo(999);
    assertThat(quote.extras()).isEqualTo(200 + 80 + 60);
    assertThat(quote.delivery()).isEqualTo(110);
    assertThat(quote.total()).isEqualTo(999 + 340 + 110);
    assertThat(quote.advance()).isEqualTo(100);
    assertThat(quote.balance()).isEqualTo(1349);
  }

  @Test
  void waitsForAreaBeforeGivingATotal() {
    var quote = LandingPricing.quote(config, new Selection(0, List.of(0), Set.of(), null));
    assertThat(quote.total()).isNull();
    assertThat(quote.advance()).isNull();
    assertThat(quote.packagePrice()).isEqualTo(899);
  }

  @Test
  void insideDhakaUsesItsOwnCharge() {
    var quote = LandingPricing.quote(config, new Selection(0, List.of(0), Set.of(), "INSIDE"));
    assertThat(quote.total()).isEqualTo(959);
  }

  @Test
  void advanceNeverExceedsTheTotal() {
    String cheap =
        LandingConfigCodec.write(config).replace("\"advanceAmount\":100", "\"advanceAmount\":99999");
    var quote =
        LandingPricing.quote(
            LandingConfigCodec.read(cheap), new Selection(0, List.of(0), Set.of(), "INSIDE"));
    assertThat(quote.advance()).isEqualTo(quote.total());
    assertThat(quote.balance()).isZero();
  }
}
