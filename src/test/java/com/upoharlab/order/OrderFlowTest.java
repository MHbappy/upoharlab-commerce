package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.upoharlab.order.OrderService.*;
import com.upoharlab.order.OrderTestSupport.Browser;
import com.upoharlab.order.OrderValidator.OrderInput;
import com.upoharlab.order.PricingService.Prices;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.orders.max-per-hour", () -> "100000");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired OrderService orders;
  @MockitoBean OrderAlerts.Sender sender;
  @LocalServerPort int port;
  long productId;

  private static final Prices PRICES = new Prices(399, 60, 110);
  private static final OrderInput INPUT =
      new OrderInput(1, "নাম", "গ্রাহক", "01712345678", "INSIDE", "ঠিকানা", null);

  @BeforeEach
  void setUp() {
    productId = resetShop(db);
  }

  @AfterEach
  void tearDown() {
    clear(db);
  }

  private Browser browser() throws Exception {
    return new Browser(port).open();
  }

  private String placedOrder(Browser browser) throws Exception {
    browser.order("quote", Map.of());
    var response = browser.order("submit", Map.of());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
    return location(response).replaceAll(".*/order/([^/]+)/success$", "$1");
  }

  private Map<String, Object> saved() {
    return db.queryForMap("SELECT * FROM orders");
  }

  @Test
  void productPageLoadsVersionedOrderScript() throws Exception {
    var browser = new Browser(port).open();
    var script =
        java.util.regex.Pattern.compile("src=\"(/js/order-[0-9a-f]{32}\\.js)\"")
            .matcher(browser.get("/p/" + SLUG).body());
    assertThat(script.find()).as("hashed script link").isTrue();
    assertThat(browser.get(script.group(1)).statusCode()).isEqualTo(200);
  }

  @Test
  void summaryShowsCourierTimeOnlyForTheChosenArea() throws Exception {
    OrderTestSupport.setting(db, "courier_days_inside_min", "1");
    OrderTestSupport.setting(db, "courier_days_inside_max", "2");
    OrderTestSupport.setting(db, "courier_days_outside_min", "3");
    OrderTestSupport.setting(db, "courier_days_outside_max", "5");
    try {
      String line = "data-line=\"courier\">";
      assertThat(new Browser(port).open().get("/p/" + SLUG).body())
          .contains(line + "এলাকা বাছলে দেখাবে");
      assertThat(browser().order("quote", Map.of("quantity", "1", "area", "OUTSIDE")).body())
          .contains(line + "৩–৫ দিন");
    } finally {
      for (String key :
          new String[] {
            "courier_days_inside_min",
            "courier_days_inside_max",
            "courier_days_outside_min",
            "courier_days_outside_max"
          }) OrderTestSupport.setting(db, key, "");
    }
  }

  @Test
  void quoteShowsBreakdownAndNeverSaves() throws Exception {
    var page = browser().order("quote", Map.of("quantity", "2"));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("৳৮৫৮", "৳৪২৯", "৳৭৯৮");
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void noJavaScriptCustomerReviewsTotalThenOrderSaves() throws Exception {
    var browser = browser();
    assertThat(browser.shownTotal).isEmpty();
    var first = browser.order("submit", Map.of());
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.body()).contains(OrderController.REVIEW_MESSAGE, "৳৪৫৯", "গোপন-গ্রাহক");
    assertThat(orderCount(db)).isZero();

    var second = browser.order("submit", Map.of());
    assertThat(second.statusCode()).isEqualTo(302);
    var order = saved();
    assertThat(order.get("total")).isEqualTo(459);
    assertThat(order.get("advance_required")).isEqualTo(230);
    assertThat(order.get("unit_price_snapshot")).isEqualTo(399);
    assertThat(order.get("delivery_charge_snapshot")).isEqualTo(60);
    assertThat(order.get("product_title_snapshot")).isEqualTo("পরীক্ষার চাবির রিং");
    assertThat(order.get("status")).isEqualTo("NEW");
    assertThat(order.get("code").toString()).matches("UL-[0-9]{6}-001");
    assertThat(
            db.queryForObject(
                "SELECT event_type FROM order_event WHERE order_id=?",
                String.class,
                order.get("id")))
        .isEqualTo("CREATED");
  }

  @Test
  void javaScriptCustomerWhoSawTheRightTotalOrdersFirstTime() throws Exception {
    var response = browser().order("submit", Map.of("quantity", "2", "shownTotal", "908", "area", "OUTSIDE"));
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(saved().get("total")).isEqualTo(908);
  }

  @Test
  void browserAmountsAreNeverCharged() throws Exception {
    var browser = browser();
    var tampered =
        Map.of("shownTotal", "1", "total", "1", "unit_price_snapshot", "1", "advance_required", "1");
    var review = browser.order("submit", tampered);
    assertThat(review.statusCode()).isEqualTo(200);
    assertThat(review.body()).contains(OrderController.REVIEW_MESSAGE);
    assertThat(orderCount(db)).isZero();
    var accepted =
        browser.order("submit", Map.of("total", "1", "unit_price_snapshot", "1"));
    assertThat(accepted.statusCode()).isEqualTo(302);
    assertThat(saved().get("total")).isEqualTo(459);
    assertThat(saved().get("unit_price_snapshot")).isEqualTo(399);
  }

  @Test
  void acceptsBanglaDigitsAndNormalizesPhone() throws Exception {
    var browser = browser();
    var values = Map.of("quantity", "২", "phone", "+৮৮০ ১৭১২-৩৪৫৬৭৮");
    browser.order("quote", values);
    assertThat(browser.order("submit", values).statusCode()).isEqualTo(302);
    assertThat(saved().get("phone")).isEqualTo("01712345678");
    assertThat(saved().get("quantity")).isEqualTo(2);
  }

  @Test
  void invalidInputShowsBanglaErrorsKeepsValuesAndSavesNothing() throws Exception {
    var browser = browser();
    var bad = new HashMap<String, String>();
    bad.put("quantity", "11");
    bad.put("phone", "123");
    bad.put("engraving", "   ");
    bad.put("area", "MARS");
    bad.put("name", "টাইপ করা নাম");
    var page = browser.order("submit", bad);
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body())
        .contains("order-errors", "সঠিক বাংলাদেশি মোবাইল নম্বর লিখুন", "কী খোদাই হবে তা লিখুন")
        .contains("পরিমাণ ১ থেকে ১০-এর মধ্যে লিখুন", "ঢাকার ভেতরে না বাইরে")
        .contains("value=\"টাইপ করা নাম\"", "value=\"123\"", "গোপন-ঠিকানা, ঢাকা");
    assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(orderCount(db)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM order_code_counter", Integer.class)).isZero();
  }

  @Test
  void pausedProductOrClosedShopRejectsOrdersAndKeepsTypedWork() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    db.update("UPDATE product SET status='PAUSED' WHERE id=?", productId);
    var paused = browser.order("submit", Map.of());
    assertThat(paused.body()).contains("এই পণ্যের অর্ডার এখন বন্ধ আছে", "গোপন-গ্রাহক");
    assertThat(paused.body()).doesNotContain("value=\"submit\"");
    assertThat(orderCount(db)).isZero();

    db.update("UPDATE product SET status='ACTIVE' WHERE id=?", productId);
    setting(db, "accepting_orders", "false");
    var closed = browser.order("submit", Map.of());
    assertThat(closed.body()).doesNotContain("value=\"submit\"");
    assertThat(orderCount(db)).isZero();
    assertThat(new Browser(port).open().get("/p/" + SLUG).body()).doesNotContain("data-order-form");

    db.update("UPDATE product SET status='DRAFT' WHERE id=?", productId);
    assertThat(browser.order("submit", Map.of()).statusCode()).isEqualTo(404);
  }

  @Test
  void changedPricesNeedReviewAndOrderUsesNewPrice() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    db.update("UPDATE product SET price=500 WHERE id=?", productId);
    var review = browser.order("submit", Map.of());
    assertThat(review.statusCode()).isEqualTo(200);
    assertThat(review.body()).contains(OrderController.REVIEW_MESSAGE, "৳৫৬০");
    assertThat(orderCount(db)).isZero();
    assertThat(browser.order("submit", Map.of()).statusCode()).isEqualTo(302);
    assertThat(saved().get("total")).isEqualTo(560);
  }

  @Test
  void changedDeliveryChargeAlsoNeedsReviewEvenIfTotalWouldMatch() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    db.update("UPDATE product SET price=389 WHERE id=?", productId);
    setting(db, "delivery_inside", "70");
    assertThat(browser.order("submit", Map.of()).statusCode()).isEqualTo(200);
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void serviceRechecksPricesInsideItsTransaction() {
    assertThat(orders.place(productId, INPUT, "stale", new Prices(1, 60, 110)))
        .isInstanceOf(PricesChanged.class);
    setting(db, "accepting_orders", "false");
    assertThat(orders.place(productId, INPUT, "closed", PRICES)).isInstanceOf(Closed.class);
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void savedOrderIgnoresLaterProductChanges() throws Exception {
    placedOrder(browser());
    db.update("UPDATE product SET price=999, title_bn='নতুন নাম' WHERE id=?", productId);
    setting(db, "delivery_inside", "500");
    var order = saved();
    assertThat(order.get("unit_price_snapshot")).isEqualTo(399);
    assertThat(order.get("delivery_charge_snapshot")).isEqualTo(60);
    assertThat(order.get("total")).isEqualTo(459);
    assertThat(order.get("product_title_snapshot")).isEqualTo("পরীক্ষার চাবির রিং");
  }

  @Test
  void parallelOrdersGetUniqueSequentialCodes() throws Exception {
    int count = 20;
    var pool = Executors.newFixedThreadPool(count);
    var start = new CountDownLatch(1);
    List<Future<PlaceResult>> results = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String token = "parallel-" + i;
      results.add(
          pool.submit(
              () -> {
                start.await();
                return orders.place(productId, INPUT, token, PRICES);
              }));
    }
    start.countDown();
    Set<String> codes = new TreeSet<>();
    for (var result : results) codes.add(((Placed) result.get(30, TimeUnit.SECONDS)).code());
    pool.shutdown();
    assertThat(codes).hasSize(count);
    assertThat(codes.stream().map(c -> c.substring(c.length() - 3)).toList())
        .containsExactlyElementsOf(
            java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj("%03d"::formatted)
                .toList());
  }

  @Test
  void repeatedTokenRollsBackWithoutUsingUpACode() {
    assertThat(orders.place(productId, INPUT, "same", PRICES)).isInstanceOf(Placed.class);
    assertThatThrownBy(() -> orders.place(productId, INPUT, "same", PRICES))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(orderCount(db)).isEqualTo(1);
    var next = (Placed) orders.place(productId, INPUT, "other", PRICES);
    assertThat(next.code()).endsWith("-002");
  }

  @Test
  void repeatClickRedirectsToSameOrder() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    String first = location(browser.order("submit", Map.of()));
    String second = location(browser.order("submit", Map.of()));
    assertThat(second).isEqualTo(first);
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void concurrentClicksCreateOneOrder() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    int clicks = 6;
    var pool = Executors.newFixedThreadPool(clicks);
    var start = new CountDownLatch(1);
    List<Future<String>> results = new ArrayList<>();
    for (int i = 0; i < clicks; i++)
      results.add(
          pool.submit(
              () -> {
                start.await();
                var response = browser.order("submit", Map.of());
                assertThat(response.statusCode()).isEqualTo(302);
                return location(response);
              }));
    start.countDown();
    Set<String> locations = new HashSet<>();
    for (var result : results) locations.add(result.get(30, TimeUnit.SECONDS));
    pool.shutdown();
    assertThat(locations).hasSize(1);
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void manyVisitorsAtOnceDoNotFreezeTheSite() throws Exception {
    int visitors = 40;
    var pool = Executors.newFixedThreadPool(visitors);
    var start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < visitors; i++) {
      String path = i % 2 == 0 ? "/" : "/p/" + SLUG;
      results.add(
          pool.submit(
              () -> {
                start.await();
                return new Browser(port).get(path).statusCode();
              }));
    }
    start.countDown();
    for (var result : results) assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(200);
    pool.shutdown();
  }

  @Test
  void confirmationShowsSavedAmountsButNoPrivateDetails() throws Exception {
    var browser = browser();
    String code = placedOrder(browser);
    var page = browser.get("/order/" + code + "/success");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body())
        .contains("আপনার অর্ডার পেয়েছি", code, "৳৪৫৯", "৳২৩০", "৳২২৯", "৬ ঘণ্টার মধ্যে")
        .contains("https://wa.me/8801700000000?text=" + code)
        .doesNotContain("গোপন-গ্রাহক", "গোপন-ঠিকানা", "01712345678");
    assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(page.headers().firstValue("X-Robots-Tag").orElse("")).contains("noindex");
    assertThat(page.headers().firstValue("Referrer-Policy").orElse("")).contains("no-referrer");
  }

  @Test
  void anotherSessionCannotSeeOrClaimAnOrder() throws Exception {
    var owner = browser();
    owner.order("quote", Map.of());
    String token = owner.submitToken;
    String code =
        location(owner.order("submit", Map.of())).replaceAll(".*/order/([^/]+)/success$", "$1");

    var stranger = browser();
    var page = stranger.get("/order/" + code + "/success");
    assertThat(page.statusCode()).isEqualTo(404);
    assertThat(page.body()).doesNotContain(code, "৳৪৫৯", "পরীক্ষার চাবির রিং");
    var missing = stranger.get("/order/UL-000000-999/success");
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.body()).isEqualTo(page.body());

    var replay = stranger.order("submit", Map.of("submitToken", token));
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(location(replay)).isEmpty();
    assertThat(replay.body()).doesNotContain(code);
    assertThat(orderCount(db)).isEqualTo(1);
    assertThat(stranger.get("/order/" + code + "/success").statusCode()).isEqualTo(404);
  }

  @Test
  void honeypotSavesNothingAndGivesNoCode() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    var response = browser.order("submit", Map.of("website", "https://spam.example"));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("UL-");
    assertThat(orderCount(db)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM order_code_counter", Integer.class)).isZero();
    verify(sender, after(300).never()).send(any());
  }

  @Test
  void personalDataStaysOutOfRedirectsAndAlerts() throws Exception {
    var browser = browser();
    browser.order("quote", Map.of());
    String redirect = location(browser.order("submit", Map.of()));
    assertThat(redirect).doesNotContain("01712345678").doesNotContain("phone").doesNotContain("?");
    var alert = ArgumentCaptor.forClass(OrderPlaced.class);
    verify(sender, timeout(5000)).send(alert.capture());
    String message = OrderAlerts.TelegramSender.message(alert.getValue(), "https://upoharlab.com/");
    assertThat(message)
        .contains(alert.getValue().code(), "পরীক্ষার চাবির রিং", "৳459")
        .contains("https://upoharlab.com/admin/orders/" + alert.getValue().code())
        .doesNotContain("01712345678", "গোপন-গ্রাহক", "গোপন-ঠিকানা", "রাফি");
    assertThat(OrderPlaced.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("code", "productTitle", "quantity", "total");
  }

  @Test
  void failedAlertDoesNotLoseOrDuplicateTheOrder() throws Exception {
    doThrow(new java.io.IOException("telegram down")).when(sender).send(any());
    var browser = browser();
    browser.order("quote", Map.of());
    assertThat(browser.order("submit", Map.of()).statusCode()).isEqualTo(302);
    verify(sender, timeout(5000).times(1)).send(any());
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void codeUsesDhakaMonthAndRollsOver() {
    Clock lateNovemberUtc = Clock.fixed(Instant.parse("2026-11-30T18:30:00Z"), ZoneId.of("Asia/Dhaka"));
    var generator = new OrderCodeGenerator(db, lateNovemberUtc);
    assertThat(generator.next()).isEqualTo("UL-202612-001");
    assertThat(generator.next()).isEqualTo("UL-202612-002");
    assertThat(generator.next(LocalDate.of(2027, 1, 1))).isEqualTo("UL-202701-001");
    db.update("UPDATE order_code_counter SET last_value=999 WHERE year_month='202701'");
    assertThat(generator.next(LocalDate.of(2027, 1, 1))).isEqualTo("UL-202701-1000");
  }
}
