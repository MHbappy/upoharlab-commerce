package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.order.OrderService.Placed;
import com.upoharlab.order.OrderTestSupport.Browser;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class AdminOrderTest {
  private static final String ADMIN = "test-owner";
  private static final OrderInput INPUT =
      new OrderInput(
          1, "রাফি", "গ্রাহক", "01712345678", "INSIDE", "ঢাকা ঠিকানা", "গ্রাহকের নোট");

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> ADMIN);
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.orders.max-per-hour", () -> "100000");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired OrderService orders;
  @Autowired AdminOrderService admin;
  @LocalServerPort int port;
  long productId;
  int tokens;

  @BeforeEach
  void setUp() {
    db.update("DELETE FROM payment");
    productId = resetShop(db);
  }

  @AfterEach
  void tearDown() {
    db.update("DELETE FROM payment");
    clear(db);
  }

  private String newOrder() {
    return newOrder(INPUT);
  }

  private String newOrder(OrderInput input) {
    return placeOrder(db, productId, input);
  }

  private void move(String code, String to) {
    admin.changeStatus(code, to, null, null, null, ADMIN);
  }

  private void pay(String code, int amount, String ref) {
    admin.recordMoney(code, "PAYMENT", "BKASH", String.valueOf(amount), ref, ADMIN);
  }

  /** A paid, approved order in the requested state. */
  private String orderAt(String status) {
    String code = newOrder();
    if (status.equals("NEW")) return code;
    move(code, "DESIGN_SENT");
    if (status.equals("DESIGN_SENT")) return code;
    admin.approveDesign(code, ADMIN);
    pay(code, 230, "ADV" + code);
    move(code, "CONFIRMED");
    if (status.equals("CONFIRMED")) return code;
    move(code, "MAKING");
    if (status.equals("MAKING")) return code;
    admin.changeStatus(code, "SHIPPED", null, "Pathao", "PT-1", ADMIN);
    if (status.equals("SHIPPED")) return code;
    move(code, "DELIVERED");
    if (status.equals("DELIVERED")) return code;
    throw new IllegalArgumentException(status);
  }

  @Test
  void fullJourneyFromNewToDeliveredWithHistory() {
    String code = newOrder();
    move(code, "DESIGN_SENT");
    admin.approveDesign(code, ADMIN);
    pay(code, 230, "trx1");
    move(code, "CONFIRMED");
    move(code, "MAKING");
    admin.changeStatus(code, "SHIPPED", null, "Pathao", "PT-77", ADMIN);
    var shipped = admin.detail(code);
    assertThat(shipped.courier()).isEqualTo("Pathao");
    assertThat(shipped.balance()).isEqualTo(229L);
    move(code, "DELIVERED");

    var delivered = admin.detail(code);
    assertThat(delivered.status()).isEqualTo("DELIVERED");
    assertThat(delivered.deliveredAt()).isNotNull();
    assertThat(delivered.designApprovedAt()).isNotNull();
    assertThat(delivered.balance()).as("delivered does not imply paid").isEqualTo(229L);

    admin.recordMoney(code, "PAYMENT", "COD", "229", "", ADMIN);
    var settled = admin.detail(code);
    assertThat(settled.balance()).isZero();
    assertThat(settled.netPaid()).isEqualTo(459);
    assertThat(settled.payments()).extracting(p -> p.reference()).containsExactly("TRX1", null);
    assertThat(settled.history())
        .extracting(e -> e.type())
        .containsExactly(
            "CREATED",
            "STATUS_CHANGED",
            "DESIGN_APPROVED",
            "PAYMENT_RECORDED",
            "STATUS_CHANGED",
            "STATUS_CHANGED",
            "STATUS_CHANGED",
            "STATUS_CHANGED",
            "PAYMENT_RECORDED");
    assertThat(settled.history().get(1).actor()).isEqualTo(ADMIN);
  }

  @ParameterizedTest
  @CsvSource({
    "NEW,CONFIRMED",
    "NEW,MAKING",
    "NEW,SHIPPED",
    "NEW,DELIVERED",
    "NEW,NEW",
    "DESIGN_SENT,NEW",
    "DESIGN_SENT,MAKING",
    "DESIGN_SENT,SHIPPED",
    "CONFIRMED,DESIGN_SENT",
    "CONFIRMED,SHIPPED",
    "MAKING,CONFIRMED",
    "MAKING,DELIVERED",
    "SHIPPED,MAKING",
    "SHIPPED,CANCELLED",
    "DELIVERED,SHIPPED",
    "DELIVERED,CANCELLED"
  })
  void rejectsInvalidTransitions(String from, String to) {
    String code = orderAt(from);
    int events = admin.detail(code).history().size();
    assertThatThrownBy(
            () -> admin.changeStatus(code, to, "reason", "Courier", "Tracking", ADMIN))
        .isInstanceOf(RuleException.class);
    var after = admin.detail(code);
    assertThat(after.status()).isEqualTo(from);
    assertThat(after.history()).hasSize(events);
  }

  @Test
  void unknownStatusIsRejected() {
    String code = newOrder();
    assertThatThrownBy(() -> move(code, "PAID")).isInstanceOf(RuleException.class);
    assertThatThrownBy(() -> move(code, null)).isInstanceOf(RuleException.class);
  }

  @Test
  void missingGuestEngravingCanBeRecordedOnceBeforeApproval() {
    String code = newOrder();
    db.update("UPDATE orders SET engraving_text='' WHERE code=?", code);
    admin.completeEngraving(code, "নতুন নাম", ADMIN);
    assertThat(admin.detail(code).engraving()).isEqualTo("নতুন নাম");
    assertThatThrownBy(() -> admin.completeEngraving(code, "অন্য নাম", ADMIN))
        .hasMessageContaining("already recorded");
    String confirmed = orderAt("CONFIRMED");
    db.update("UPDATE orders SET engraving_text='' WHERE code=?", confirmed);
    assertThatThrownBy(() -> admin.completeEngraving(confirmed, "নাম", ADMIN))
        .hasMessageContaining("before design approval");
  }

  @Test
  void confirmingAndMakingNeedApprovalButNoAdvance() {
    String code = newOrder();
    move(code, "DESIGN_SENT");
    assertThatThrownBy(() -> move(code, "CONFIRMED"))
        .isInstanceOf(RuleException.class).hasMessageContaining("approval");
    admin.approveDesign(code, ADMIN);
    assertThat(admin.detail(code).missingForConfirmation()).isEmpty();
    assertThat(admin.detail(code).netPaid()).isZero();
    move(code, "CONFIRMED");
    move(code, "MAKING");
    assertThat(admin.detail(code).status()).isEqualTo("MAKING");
    assertThat(admin.detail(code).balance()).isEqualTo(459);
  }

  @Test
  void paymentWithoutApprovalCannotConfirm() {
    String code = newOrder();
    move(code, "DESIGN_SENT");
    pay(code, 459, "full");
    assertThatThrownBy(() -> move(code, "CONFIRMED")).hasMessageContaining("approval");
  }

  @Test
  void approvalOnlyAfterDesignSentAndOnlyOnce() {
    String code = newOrder();
    assertThatThrownBy(() -> admin.approveDesign(code, ADMIN)).isInstanceOf(RuleException.class);
    move(code, "DESIGN_SENT");
    admin.approveDesign(code, ADMIN);
    assertThatThrownBy(() -> admin.approveDesign(code, ADMIN)).isInstanceOf(RuleException.class);
  }

  @Test
  void refundDoesNotBlockCodProduction() {
    String code = orderAt("CONFIRMED");
    admin.recordMoney(code, "REFUND", "BKASH", "100", "ref-back", ADMIN);
    move(code, "MAKING");
    assertThat(admin.detail(code).status()).isEqualTo("MAKING");
  }

  @Test
  void shippingNeedsCourierAndTracking() {
    String code = orderAt("MAKING");
    assertThatThrownBy(() -> admin.changeStatus(code, "SHIPPED", null, "", "PT-1", ADMIN))
        .hasMessageContaining("courier");
    assertThatThrownBy(() -> admin.changeStatus(code, "SHIPPED", null, "Pathao", "  ", ADMIN))
        .hasMessageContaining("tracking");
    assertThat(admin.detail(code).status()).isEqualTo("MAKING");
    assertThat(admin.detail(code).courier()).isNull();
  }

  @Test
  void duplicateTransactionReferencesAreRejectedAcrossOrdersAndCase() {
    String first = newOrder(), second = newOrder();
    pay(first, 100, "abc123");
    assertThatThrownBy(() -> pay(second, 100, "ABC123"))
        .hasMessageContaining("already recorded");
    assertThatThrownBy(() -> pay(first, 100, " abc123 "))
        .hasMessageContaining("already recorded");
    assertThat(admin.detail(second).netPaid()).isZero();
  }

  @Test
  void bkashNeedsAReferenceAndAmountsMustBePositiveWholeTaka() {
    String code = newOrder();
    assertThatThrownBy(() -> pay(code, 100, " ")).hasMessageContaining("reference");
    for (String bad : new String[] {"0", "-5", "1.5", "abc", "", "99999999999"})
      assertThatThrownBy(() -> admin.recordMoney(code, "PAYMENT", "CASH", bad, "", ADMIN))
          .as(bad)
          .isInstanceOf(RuleException.class);
    assertThatThrownBy(() -> admin.recordMoney(code, "GIFT", "CASH", "5", "", ADMIN))
        .isInstanceOf(RuleException.class);
    assertThatThrownBy(() -> admin.recordMoney(code, "PAYMENT", "CHEQUE", "5", "", ADMIN))
        .isInstanceOf(RuleException.class);
    admin.recordMoney(code, "PAYMENT", "CASH", "১০০", "", ADMIN);
    assertThat(admin.detail(code).netPaid()).isEqualTo(100);
  }

  @Test
  void rejectsOverpaymentAndExcessRefund() {
    String code = newOrder();
    assertThatThrownBy(() -> pay(code, 460, "over")).hasMessageContaining("৳459");
    pay(code, 459, "all");
    assertThatThrownBy(() -> admin.recordMoney(code, "PAYMENT", "CASH", "1", "", ADMIN))
        .hasMessageContaining("overpay");
    assertThatThrownBy(() -> admin.recordMoney(code, "REFUND", "CASH", "460", "", ADMIN))
        .hasMessageContaining("৳459");
    admin.recordMoney(code, "REFUND", "CASH", "459", "", ADMIN);
    assertThat(admin.detail(code).netPaid()).isZero();
    assertThat(admin.detail(code).payments()).hasSize(2);
    assertThatThrownBy(() -> admin.recordMoney(code, "REFUND", "CASH", "1", "", ADMIN))
        .isInstanceOf(RuleException.class);
    admin.recordMoney(code, "PAYMENT", "CASH", "459", "", ADMIN);
    assertThat(admin.detail(code).balance()).isZero();
  }

  @Test
  void cancellingNeedsReasonShowsHeldMoneyAndAllowsOnlyRefunds() {
    String code = newOrder();
    move(code, "DESIGN_SENT");
    pay(code, 230, "held");
    assertThatThrownBy(() -> admin.changeStatus(code, "CANCELLED", " ", null, null, ADMIN))
        .hasMessageContaining("reason");
    admin.changeStatus(code, "CANCELLED", "গ্রাহক চান না", null, null, ADMIN);

    var cancelled = admin.detail(code);
    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    assertThat(cancelled.balance()).as("no misleading balance due").isNull();
    assertThat(cancelled.netPaid()).isEqualTo(230);
    assertThat(cancelled.canCancel()).isFalse();
    assertThat(cancelled.history().getLast().note()).isEqualTo("গ্রাহক চান না");

    assertThatThrownBy(() -> pay(code, 10, "late")).hasMessageContaining("cancelled");
    admin.recordMoney(code, "REFUND", "BKASH", "230", "back", ADMIN);
    assertThat(admin.detail(code).netPaid()).isZero();
  }

  @Test
  void contactCorrectionKeepsNoPersonalDataInHistoryAndStopsAtShipping() {
    String code = orderAt("MAKING");
    admin.updateContact(code, "নতুন নাম", "০১৮১২৩৪৫৬৭৮", "নতুন ঠিকানা", ADMIN);
    var after = admin.detail(code);
    assertThat(after.customerName()).isEqualTo("নতুন নাম");
    assertThat(after.phone()).isEqualTo("01812345678");
    assertThat(after.address()).isEqualTo("নতুন ঠিকানা");
    var event = after.history().getLast();
    assertThat(event.type()).isEqualTo("CONTACT_UPDATED");
    assertThat(event.note()).isNull();

    assertThatThrownBy(() -> admin.updateContact(code, "x", "123", "y", ADMIN))
        .hasMessageContaining("mobile");
    assertThatThrownBy(() -> admin.updateContact(code, " ", "01812345678", "y", ADMIN))
        .isInstanceOf(RuleException.class);
    admin.changeStatus(code, "SHIPPED", null, "Pathao", "PT-1", ADMIN);
    assertThatThrownBy(() -> admin.updateContact(code, "x", "01812345678", "y", ADMIN))
        .hasMessageContaining("before shipping");
    assertThat(admin.detail(code).customerName()).isEqualTo("নতুন নাম");
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM order_event WHERE note LIKE '%01812345678%'"
                    + " OR note LIKE '%ঠিকানা%'",
                Integer.class))
        .isZero();
  }

  @Test
  void courierCanOnlyBeCorrectedAfterShipping() {
    String code = orderAt("MAKING");
    assertThatThrownBy(() -> admin.updateCourier(code, "A", "B", ADMIN))
        .isInstanceOf(RuleException.class);
    admin.changeStatus(code, "SHIPPED", null, "Pathao", "PT-1", ADMIN);
    admin.updateCourier(code, "Steadfast", "SF-2", ADMIN);
    assertThat(admin.detail(code).courier()).isEqualTo("Steadfast");
  }

  @Test
  void concurrentPaymentsNeverOverpay() throws Exception {
    String code = newOrder();
    int threads = 12;
    AtomicInteger accepted = new AtomicInteger();
    runTogether(
        threads,
        i -> {
          try {
            pay(code, 100, "par" + i);
            accepted.incrementAndGet();
          } catch (RuleException expected) {
            assertThat(expected).hasMessageContaining("overpay");
          }
        });
    assertThat(accepted).hasValue(4);
    assertThat(admin.detail(code).netPaid()).isEqualTo(400);
  }

  @Test
  void concurrentUseOfOneReferenceRecordsItOnce() throws Exception {
    String first = newOrder(), second = newOrder();
    AtomicInteger accepted = new AtomicInteger();
    runTogether(
        8,
        i -> {
          try {
            pay(i % 2 == 0 ? first : second, 100, "same-ref");
            accepted.incrementAndGet();
          } catch (RuleException
              | org.springframework.dao.DuplicateKeyException expected) {
            // One of these two rejections is correct, depending on timing.
          }
        });
    assertThat(accepted).hasValue(1);
    assertThat(db.queryForObject("SELECT count(*) FROM payment", Integer.class)).isEqualTo(1);
  }

  @Test
  void concurrentStatusClicksApplyOnce() throws Exception {
    String code = newOrder();
    AtomicInteger accepted = new AtomicInteger();
    runTogether(
        8,
        i -> {
          try {
            move(code, "DESIGN_SENT");
            accepted.incrementAndGet();
          } catch (RuleException expected) {
            // The first click moved it, so later clicks are invalid.
          }
        });
    assertThat(accepted).hasValue(1);
    assertThat(admin.detail(code).history()).hasSize(2);
  }

  @Test
  void refundCannotRaceAStartOfMaking() throws Exception {
    String code = orderAt("CONFIRMED");
    runTogether(
        2,
        i -> {
          try {
            if (i == 0) move(code, "MAKING");
            else admin.recordMoney(code, "REFUND", "CASH", "230", "", ADMIN);
          } catch (RuleException expected) {
            // Whichever runs second sees the first one's result.
          }
        });
    var after = admin.detail(code);
    assertThat(after.status().equals("CONFIRMED") && after.netPaid() == 0
            || after.status().equals("MAKING"))
        .isTrue();
  }

  private interface Task {
    void run(int index) throws Exception;
  }

  private void runTogether(int threads, Task task) throws Exception {
    var pool = Executors.newFixedThreadPool(threads);
    var start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      int index = i;
      results.add(
          pool.submit(
              () -> {
                start.await();
                task.run(index);
                return null;
              }));
    }
    start.countDown();
    for (var result : results) result.get(30, TimeUnit.SECONDS);
    pool.shutdown();
  }

  /** One shared session, because the suite would otherwise reach the login rate limit. */
  private static Browser loggedIn;

  private Browser login() throws Exception {
    if (loggedIn != null) return loggedIn;
    var browser = new Browser(port);
    browser.get("/admin/login");
    var response =
        browser.post(
            "/admin/login",
            Map.of("username", ADMIN, "password", "test-only-password-2026"));
    assertThat(location(response)).endsWith("/admin");
    loggedIn = browser;
    return browser;
  }

  @Test
  void adminRoutesRequireLoginAndCsrf() throws Exception {
    String code = newOrder();
    var stranger = new Browser(port);
    for (String path : List.of("/admin", "/admin/orders", "/admin/orders/" + code)) {
      var response = stranger.get(path);
      assertThat(location(response)).as(path).endsWith("/admin/login");
      assertThat(response.body()).doesNotContain("01712345678");
    }
    var browser = login();
    browser.get("/admin/orders/" + code);
    String valid = browser.csrf;
    browser.csrf = "wrong";
    var forged =
        browser.post("/admin/orders/" + code + "/status", Map.of("to", "DESIGN_SENT"));
    assertThat(forged.statusCode()).isEqualTo(403);
    assertThat(admin.detail(code).status()).isEqualTo("NEW");
    browser.csrf = valid;
    assertThat(
            location(
                browser.post("/admin/orders/" + code + "/status", Map.of("to", "DESIGN_SENT"))))
        .endsWith("/admin/orders/" + code);
    assertThat(admin.detail(code).status()).isEqualTo("DESIGN_SENT");
    stranger.get("/admin/login");
    var anonymous = stranger.post("/admin/orders/" + code + "/status", Map.of("to", "CANCELLED"));
    assertThat(location(anonymous)).endsWith("/admin/login");
    assertThat(admin.detail(code).status()).isEqualTo("DESIGN_SENT");
  }

  @Test
  void unknownOrderIsNotFound() throws Exception {
    var browser = login();
    assertThat(browser.get("/admin/orders/UL-000000-999").statusCode()).isEqualTo(404);
  }

  @Test
  void listMasksPhonesAndDetailShowsThem() throws Exception {
    String code = newOrder();
    var browser = login();
    for (String path : List.of("/admin", "/admin/orders")) {
      var page = browser.get(path);
      assertThat(page.statusCode()).isEqualTo(200);
      assertThat(page.body()).contains(code, "01XXX-XXX678").doesNotContain("01712345678");
      assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    var detail = browser.get("/admin/orders/" + code);
    assertThat(detail.statusCode()).isEqualTo(200);
    assertThat(detail.body())
        .contains("01712345678", "রাফি", "গ্রাহকের নোট", "ঢাকা ঠিকানা", "৳459", "Cash on delivery")
        .contains("https://wa.me/8801712345678");
    assertThat(detail.headers().firstValue("Referrer-Policy").orElse("")).contains("no-referrer");
  }

  private HttpResponse<String> search(Browser browser, String q, String status, int page)
      throws Exception {
    return browser.post(
        "/admin/orders/search", Map.of("q", q, "status", status, "page", String.valueOf(page)));
  }

  @Test
  void searchesByCodeAndPhoneWithoutPuttingThemInUrls() throws Exception {
    String mine = newOrder();
    String other =
        newOrder(new OrderInput(1, "x", "y", "01898765432", "INSIDE", "z", null));
    var browser = login();
    browser.get("/admin/orders");

    for (String q : List.of("01712345678", "+৮৮০ ১৭১২-৩৪৫৬৭৮", "45678", mine, mine.toLowerCase())) {
      var found = search(browser, q, "", 0);
      assertThat(found.statusCode()).as(q).isEqualTo(200);
      assertThat(found.body()).as(q).contains(mine).doesNotContain(other);
      assertThat(found.uri().toString()).as(q).endsWith("/admin/orders/search");
    }
    assertThat(search(browser, "%", "", 0).body()).doesNotContain(mine).doesNotContain(other);
    assertThat(search(browser, "_", "", 0).body()).doesNotContain(mine);
    assertThat(search(browser, "'; DROP TABLE orders; --", "", 0).statusCode()).isEqualTo(200);
    assertThat(orderCount(db)).isEqualTo(2);
  }

  @Test
  void filtersByStatusAndKeepsSearchAcrossPages() throws Exception {
    String sent = newOrder();
    move(sent, "DESIGN_SENT");
    for (int i = 0; i < AdminOrderService.PAGE_SIZE + 2; i++) newOrder();
    newOrder(new OrderInput(1, "x", "y", "01898765432", "INSIDE", "z", null));

    var browser = login();
    assertThat(browser.get("/admin/orders?status=DESIGN_SENT").body())
        .contains(sent)
        .contains("1 order");
    assertThat(browser.get("/admin/orders?status=NOPE").statusCode()).isEqualTo(200);

    var first = search(browser, "01712345678", "NEW", 0);
    assertThat(first.body())
        .contains(AdminOrderService.PAGE_SIZE + 2 + " orders", "Page 1 of 2")
        .contains("name=\"q\" value=\"01712345678\"");
    var second = search(browser, "01712345678", "NEW", 1);
    assertThat(second.body())
        .contains("Page 2 of 2")
        .contains("name=\"q\" value=\"01712345678\"")
        .doesNotContain(sent);
    assertThat(second.uri().toString()).doesNotContain("01712345678");
    assertThat(search(browser, "01712345678", "NEW", 99).body()).contains("Page 2 of 2");
  }

  @Test
  void ruleErrorsAreExplainedOnThePageAndKeepTypedValues() throws Exception {
    String code = newOrder();
    var browser = login();
    browser.get("/admin/orders/" + code);
    browser.post(
        "/admin/orders/" + code + "/money",
        Map.of("kind", "PAYMENT", "method", "BKASH", "amount", "460", "reference", "TRX9"));
    var page = browser.get("/admin/orders/" + code);
    assertThat(page.body()).contains("overpay", "value=\"460\"", "value=\"TRX9\"");
    assertThat(admin.detail(code).payments()).isEmpty();

    browser.post(
        "/admin/orders/" + code + "/contact",
        Map.of("name", "টাইপ করা", "phone", "123", "address", "ঠিকানা"));
    var contact = browser.get("/admin/orders/" + code);
    assertThat(contact.body()).contains("valid Bangladeshi", "value=\"টাইপ করা\"");
    assertThat(admin.detail(code).customerName()).isEqualTo("গ্রাহক");
  }

  @Test
  void internalNotesStaySeparateFromTheCustomerNote() throws Exception {
    String code = newOrder();
    var browser = login();
    browser.get("/admin/orders/" + code);
    browser.post("/admin/orders/" + code + "/note", Map.of("note", "<b>নিজের</b> নোট"));
    var detail = admin.detail(code);
    assertThat(detail.customerNote()).isEqualTo("গ্রাহকের নোট");
    assertThat(detail.history().getLast().note()).isEqualTo("<b>নিজের</b> নোট");
    assertThat(browser.get("/admin/orders/" + code).body())
        .contains("&lt;b&gt;নিজের&lt;/b&gt;")
        .doesNotContain("<b>নিজের</b>");
    assertThatThrownBy(() -> admin.addNote(code, "  ", ADMIN)).isInstanceOf(RuleException.class);
  }

  @Test
  void whatsappMessagesAreEncodedAndCarryNoAddress() throws Exception {
    setting(db, "bkash_number", "01700000000");
    try {
      String code = orderAt("MAKING");
      admin.changeStatus(code, "SHIPPED", null, "Fast & Safe", "A 1", ADMIN);
      String html = login().get("/admin/orders/" + code).body();
      assertThat(html).contains("https://wa.me/8801712345678?text=", "%20");
      assertThat(html).contains("Fast%20%26%20Safe");
      var encoded =
          AdminOrderController.withText("https://wa.me/1", "ক & খ?=#");
      assertThat(encoded).isEqualTo("https://wa.me/1?text=%E0%A6%95%20%26%20%E0%A6%96?%3D%23");
      var links = java.util.regex.Pattern.compile("href=\"(https://wa\\.me/[^\"]+)\"").matcher(html);
      int found = 0;
      while (links.find()) {
        found++;
        assertThat(java.net.URLDecoder.decode(links.group(1), java.nio.charset.StandardCharsets.UTF_8))
            .doesNotContain("ঢাকা ঠিকানা", "রাফি", "গ্রাহকের নোট");
      }
      assertThat(found).isGreaterThanOrEqualTo(3);
    } finally {
      setting(db, "bkash_number", "");
    }
  }

  @Test
  void whatsappOffersOnlyMessagesThatFitTheOrderState() throws Exception {
    setting(db, "bkash_number", "01700000000");
    try {
      var browser = login();
      String fresh = newOrder();
      assertThat(browser.get("/admin/orders/" + fresh).body())
          .as("no request for money before the design is sent")
          .contains("Design message")
          .doesNotContain("Advance message");
      move(fresh, "DESIGN_SENT");
      admin.approveDesign(fresh, ADMIN);
      assertThat(browser.get("/admin/orders/" + fresh).body())
          .doesNotContain("Advance message", "Open advance message", "Record the advance");
      admin.changeStatus(fresh, "CANCELLED", "Customer changed their mind", null, null, ADMIN);
      assertThat(browser.get("/admin/orders/" + fresh).body())
          .contains("Open chat")
          .doesNotContain("Advance message", "Design message", "Record verified refund");
    } finally {
      setting(db, "bkash_number", "");
    }
  }

  @Test
  void cancelledOrderOffersOnlyARefundOfMoneyStillHeld() throws Exception {
    String code = orderAt("CONFIRMED");
    admin.changeStatus(code, "CANCELLED", "Customer changed their mind", null, null, ADMIN);
    assertThat(login().get("/admin/orders/" + code).body())
        .contains("Money still held", "Record verified refund", "You can refund up to ৳230")
        .doesNotContain("value=\"PAYMENT\"", "Remaining balance");
  }

  @Test
  void refundOnDeliveredOrderIsNotDescribedAsAwaitingSettlement() throws Exception {
    String code = orderAt("DELIVERED");
    var browser = login();
    assertThat(browser.get("/admin/orders/" + code).body()).contains("awaiting customer or courier");
    admin.recordMoney(code, "REFUND", "CASH", "10", "", ADMIN);
    assertThat(browser.get("/admin/orders/" + code).body())
        .contains("A refund is recorded")
        .doesNotContain("awaiting customer or courier");
  }

  @Test
  void emptyListsShowNoTableAndStatusesAreReadable() throws Exception {
    var browser = login();
    assertThat(browser.get("/admin").body()).contains("No orders yet.").doesNotContain("<table");
    assertThat(browser.get("/admin/orders").body()).contains("0 orders").doesNotContain("<table");
    orderAt("DESIGN_SENT");
    String list = browser.get("/admin/orders?status=DESIGN_SENT").body();
    assertThat(list).contains("Design sent", "Clear search", "value=\"DESIGN_SENT\"");
  }

  @Test
  void dashboardCountsMatchOrderStates() throws Exception {
    orderAt("NEW");
    orderAt("NEW");
    orderAt("MAKING");
    orderAt("SHIPPED");
    var counts = admin.counts();
    assertThat(counts.fresh()).isEqualTo(2);
    assertThat(counts.making()).isEqualTo(1);
    assertThat(counts.shipped()).isEqualTo(1);
    assertThat(login().get("/admin").statusCode()).isEqualTo(200);
  }
}
