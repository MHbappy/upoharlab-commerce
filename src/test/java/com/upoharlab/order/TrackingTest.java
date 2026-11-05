package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.OrderService.Placed;
import com.upoharlab.order.OrderTestSupport.Browser;
import com.upoharlab.order.OrderValidator.OrderInput;
import com.upoharlab.order.PricingService.Prices;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackingTest {
  private static final String PHONE = "01712345678";
  private static final String ADMIN = "test-owner";
  private static final java.util.concurrent.atomic.AtomicLong NEXT_CODE =
      new java.util.concurrent.atomic.AtomicLong(100);

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> ADMIN);
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.tracking.max-per-ip", () -> "100000");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired OrderService orders;
  @Autowired AdminOrderService admin;
  @LocalServerPort int port;
  long productId;
  String code;

  @BeforeEach
  void setUp() {
    db.update("DELETE FROM payment");
    productId = resetShop(db);
    // The failure limit lasts across tests, so each test needs its own order code.
    db.update(
        "INSERT INTO order_code_counter (year_month, last_value) VALUES (?, ?)",
        java.time.format.DateTimeFormatter.ofPattern("uuuuMM")
            .format(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Dhaka"))),
        NEXT_CODE.incrementAndGet());
    code =
        ((Placed)
                orders.place(
                    productId,
                    new OrderInput(
                        1, "গোপন-খোদাই", "গোপন-নাম", PHONE, "INSIDE", "গোপন-ঠিকানা", "গোপন-নোট"),
                    "track-" + System.nanoTime(),
                    new Prices(399, 60, 110)))
            .code();
  }

  @AfterEach
  void tearDown() {
    db.update("DELETE FROM payment");
    clear(db);
  }

  private java.net.http.HttpResponse<String> track(Browser browser, String code, String phone)
      throws Exception {
    browser.get("/track");
    return browser.post("/track", Map.of("code", code, "phone", phone));
  }

  @Test
  void correctCodeAndFullPhoneShowStatusOnly() throws Exception {
    admin.addNote(code, "গোপন-ভেতরের-নোট", ADMIN);
    admin.changeStatus(code, "DESIGN_SENT", null, null, null, ADMIN);
    admin.approveDesign(code, ADMIN);
    admin.recordMoney(code, "PAYMENT", "BKASH", "230", "গোপনREF", ADMIN);
    admin.changeStatus(code, "CONFIRMED", null, null, null, ADMIN);
    admin.changeStatus(code, "MAKING", null, null, null, ADMIN);
    admin.changeStatus(code, "SHIPPED", null, "Pathao", "PT-55", ADMIN);

    var page = track(new Browser(port), code, PHONE);
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body())
        .contains(code, "কুরিয়ারে পাঠানো হয়েছে", "Pathao", "PT-55")
        .doesNotContain("গোপন-", "গোপনREF", "৳230", "৳২৩০", "01712345678\"");
    assertThat(page.uri().toString()).endsWith("/track");
    assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(page.headers().firstValue("X-Robots-Tag").orElse("")).contains("noindex");
    assertThat(page.body()).contains("name=\"robots\" content=\"noindex\"");
  }

  @Test
  void acceptsBanglaDigitsAndPhoneFormatsAndLowercaseCode() throws Exception {
    var browser = new Browser(port);
    String banglaCode = com.upoharlab.shared.BnDigits.toBangla(code).toLowerCase();
    assertThat(track(browser, banglaCode, "+৮৮০ ১৭১২-৩৪৫৬৭৮").body()).contains("অর্ডার পেয়েছি");
  }

  @Test
  void lastFourDigitsAreNotEnough() throws Exception {
    var page = track(new Browser(port), code, "5678");
    assertThat(page.body()).contains("কোনো অর্ডার পাওয়া যায়নি").doesNotContain("অর্ডার পেয়েছি");
  }

  @Test
  void wrongCodeAndWrongPhoneGiveIdenticalResponses() throws Exception {
    var browser = new Browser(port);
    String missing = "UL-000000-999", otherPhone = "01898765432";
    var wrongPhone = track(browser, code, otherPhone);
    var wrongCode = track(browser, missing, PHONE);
    assertThat(wrongPhone.statusCode()).isEqualTo(wrongCode.statusCode());
    // The CSRF token is masked differently on every response, so it is left out of the comparison.
    String csrf = "name=\"_csrf\" value=\"[^\"]+\"";
    assertThat(wrongPhone.body().replace(code, "C").replace(otherPhone, "P").replaceAll(csrf, ""))
        .isEqualTo(wrongCode.body().replace(missing, "C").replace(PHONE, "P").replaceAll(csrf, ""));
  }

  @Test
  void repeatedFailuresBlockThatCodeEvenWithTheRightPhone() throws Exception {
    var browser = new Browser(port);
    for (int i = 0; i < 5; i++)
      assertThat(track(browser, code, "01898765432").statusCode()).isEqualTo(200);
    var blocked = track(browser, code, PHONE);
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.body()).doesNotContain("অর্ডার পেয়েছি");
    assertThat(track(browser, "UL-000000-777", PHONE).statusCode()).isEqualTo(200);
  }

  @Test
  void successfulLookupsDoNotUseTheFailureAllowance() throws Exception {
    var browser = new Browser(port);
    for (int i = 0; i < 8; i++) assertThat(track(browser, code, PHONE).statusCode()).isEqualTo(200);
  }

  @Test
  void malformedInputIsHandledQuietly() throws Exception {
    var browser = new Browser(port);
    for (String bad : new String[] {"", "x", "'; DROP TABLE orders; --", "UL-1"})
      assertThat(track(browser, bad, "abc").body()).contains("কোনো অর্ডার পাওয়া যায়নি");
    assertThat(orderCount(db)).isEqualTo(1);
  }
}
