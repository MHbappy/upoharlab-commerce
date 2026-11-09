package com.upoharlab.order;

import static org.assertj.core.api.Assertions.*;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfigCodec;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class LandingOrderValidatorTest {
  private static final String JSON =
      "{\"enabled\":true,\"packages\":[{\"name\":\"ক\",\"price\":100,\"isPopular\":true}],"
          + "\"options\":[{\"label\":\"মাপ\",\"type\":\"SIZE\",\"values\":[{\"name\":\"ছোট\",\"priceDelta\":0}]}],"
          + "\"upsells\":[{\"label\":\"বক্স\",\"price\":60}],\"advanceAmount\":50,"
          + "\"delivery\":{\"insideDhakaCharge\":60,\"outsideDhakaCharge\":110},"
          + "\"preview\":{\"designs\":[{\"name\":\"ক\",\"baseImage\":\"\",\"textX\":1,\"textY\":1,\"maxWidth\":9,"
          + "\"fontSize\":100,\"minFontSize\":40,\"color\":\"#000000\",\"shadow\":\"\",\"imageWidth\":10,\"imageHeight\":10}],"
          + "\"fonts\":[\"Galada\"],\"defaultText\":\"নাম\",\"maxChars\":10}}";
  private final LandingConfig config = LandingConfigCodec.read(JSON);
  private final LandingOrderValidator validator =
      new LandingOrderValidator(
          Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC));

  private LandingOrderForm valid() {
    LandingOrderForm f = new LandingOrderForm();
    f.setEngraving("ক্ষেত্র");
    f.setConfirmEngraving("ক্ষেত্র");
    f.setFont("Galada");
    f.setDesign("0");
    f.setPackageIndex("0");
    f.setOptions(List.of("0"));
    f.setName("গ্রাহক");
    f.setPhone("০১৭১২৩৪৫৬৭৮");
    f.setArea("INSIDE");
    f.setAddress("ঠিকানা");
    return f;
  }

  private BeanPropertyBindingResult check(LandingOrderForm f) {
    var errors = new BeanPropertyBindingResult(f, "form");
    validator.validate(f, config, errors);
    return errors;
  }

  @Test
  void acceptsValidInputAndNormalizesPhone() {
    var f = valid();
    var result = validator.validate(f, config, new BeanPropertyBindingResult(f, "form"));
    assertThat(result.phone()).isEqualTo("01712345678");
    assertThat(result.text()).isEqualTo("ক্ষেত্র");
  }

  @Test
  void confirmationMustMatch() {
    var f = valid();
    f.setConfirmEngraving("অন্য");
    assertThat(check(f).getFieldError("confirmEngraving")).isNotNull();
  }

  @Test
  void textLimitCountsCharacters() {
    var f = valid();
    f.setEngraving("ক".repeat(11));
    f.setConfirmEngraving("ক".repeat(11));
    assertThat(check(f).getFieldError("engraving")).isNotNull();
  }

  @Test
  void rejectsChoicesNotOnTheProduct() {
    var f = valid();
    f.setFont("Arial");
    f.setDesign("7");
    f.setPackageIndex("9");
    f.setOptions(List.of("5"));
    f.setUpsells(List.of("3"));
    var e = check(f);
    for (String field : List.of("font", "design", "packageIndex", "options", "upsells"))
      assertThat(e.getFieldError(field)).as(field).isNotNull();
  }

  @Test
  void aBadPhoneIsRejected() {
    var f = valid();
    f.setPhone("12345");
    assertThat(check(f).getFieldError("phone")).isNotNull();
  }

  @Test
  void pastDatesAreRejectedAndTodayIsFine() {
    var f = valid();
    f.setNeededBy("2026-06-14");
    assertThat(check(f).getFieldError("neededBy")).isNotNull();
    f.setNeededBy("2026-06-15");
    assertThat(check(f).hasErrors()).isFalse();
    f.setNeededBy("not-a-date");
    assertThat(check(f).getFieldError("neededBy")).isNotNull();
  }

  @Test
  void fontSizeIsClampedToTheDesign() {
    var d = config.preview().designs().get(0);
    assertThat(LandingOrderValidator.fontSize("9999", d)).isEqualTo(100);
    assertThat(LandingOrderValidator.fontSize("1", d)).isEqualTo(40);
    assertThat(LandingOrderValidator.fontSize("70", d)).isEqualTo(70);
    assertThat(LandingOrderValidator.fontSize("<x>", d)).isEqualTo(40);
    assertThat(LandingOrderValidator.fontSize(null, d)).isEqualTo(40);
  }
}
