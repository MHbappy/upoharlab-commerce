package com.upoharlab.order;

import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.PricingService.Prices;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.validation.BeanPropertyBindingResult;

class OrderRulesTest {
  private final PricingService pricing = new PricingService();
  private final OrderValidator validator = new OrderValidator();
  private final Prices prices = new Prices(399, 60, 110);

  @ParameterizedTest
  @CsvSource({
    "1,INSIDE,399,60,459,0,459",
    "1,OUTSIDE,399,110,509,0,509",
    "2,INSIDE,798,60,858,0,858",
    "10,OUTSIDE,3990,110,4100,0,4100"
  })
  void calculatesFullCashOnDeliveryTotals(
      int quantity, String area, int subtotal, int delivery, int total, int advance, int balance) {
    var quote = pricing.quote(prices, quantity, area);
    assertThat(quote.subtotal()).isEqualTo(subtotal);
    assertThat(quote.delivery()).isEqualTo(delivery);
    assertThat(quote.total()).isEqualTo(total);
    assertThat(quote.advance()).isEqualTo(advance);
    assertThat(quote.balance()).isEqualTo(balance);
  }

  @Test
  void totalWaitsForAQuantityAndArea() {
    assertThat(pricing.quote(prices, 2, null).total()).isNull();
    assertThat(pricing.quote(prices, 2, null).subtotal()).isEqualTo(798);
    assertThat(pricing.quote(prices, null, "INSIDE").total()).isNull();
    assertThat(pricing.quote(prices, 2, "MARS").delivery()).isNull();
  }

  @Test
  void missingPriceOrChargeMeansNoPrices() {
    var charges = Map.of("delivery_inside", "60", "delivery_outside", "110");
    assertThat(pricing.prices(399, charges)).contains(prices);
    assertThat(pricing.prices(null, charges)).isEmpty();
    assertThat(pricing.prices(399, Map.of("delivery_inside", "60"))).isEmpty();
    assertThat(pricing.prices(399, Map.of("delivery_inside", "x", "delivery_outside", "1")))
        .isEmpty();
    assertThat(pricing.prices(399, Map.of("delivery_inside", "-1", "delivery_outside", "1")))
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"1,1", "১০,10", "' ৫ ',5"})
  void acceptsValidQuantities(String raw, int expected) {
    assertThat(validator.quantity(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"0", "11", "১১", "-1", "1.5", "abc", "999999999999"})
  void rejectsInvalidQuantities(String raw) {
    assertThat(validator.quantity(raw)).isNull();
    assertThat(validator.quantity(null)).isNull();
  }

  private OrderForm valid() {
    var form = new OrderForm();
    form.setEngraving("নাম");
    form.setName("গ্রাহক");
    form.setPhone("01712345678");
    form.setArea("INSIDE");
    form.setAddress("ঠিকানা");
    return form;
  }

  @Test
  void limitsCountBanglaCharactersNotBytes() {
    var form = valid();
    form.setEngraving("ক".repeat(OrderValidator.MAX_ENGRAVING));
    form.setName("ক".repeat(OrderValidator.MAX_NAME));
    var errors = new BeanPropertyBindingResult(form, "orderForm");
    assertThat(validator.validateOrder(form, errors)).isNotNull();

    form.setEngraving("ক".repeat(OrderValidator.MAX_ENGRAVING + 1));
    form.setNote("ক".repeat(OrderValidator.MAX_NOTE + 1));
    errors = new BeanPropertyBindingResult(form, "orderForm");
    assertThat(validator.validateOrder(form, errors)).isNull();
    assertThat(errors.getFieldError("engraving")).isNotNull();
    assertThat(errors.getFieldError("note")).isNotNull();
    assertThat(OrderValidator.length("😀")).isEqualTo(1);
  }

  @Test
  void cleansTextBeforeSaving() {
    assertThat(OrderValidator.text("  নাম\u0000\u0007 ", true)).isEqualTo("নাম");
    assertThat(OrderValidator.text("এক\r\nদুই", true)).isEqualTo("এক\nদুই");
    assertThat(OrderValidator.text("এক\nদুই", false)).isEqualTo("এক দুই");
    assertThat(OrderValidator.text("é", true)).isEqualTo("é");
    assertThat(OrderValidator.text(null, true)).isEmpty();
    var form = valid();
    form.setNote("   ");
    var input = validator.validateOrder(form, new BeanPropertyBindingResult(form, "orderForm"));
    assertThat(input.note()).isNull();
  }

  @Test
  void quoteOnlyChecksQuantityAndChosenArea() {
    var form = new OrderForm();
    var errors = new BeanPropertyBindingResult(form, "orderForm");
    validator.validateQuote(form, errors, false);
    assertThat(errors.hasErrors()).isFalse();
    form.setArea("MARS");
    validator.validateQuote(form, errors, false);
    assertThat(errors.getFieldError("area")).isNotNull();
  }
}
