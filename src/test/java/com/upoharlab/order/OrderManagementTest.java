package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.order.OrderTestSupport.Browser;
import java.util.List;
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

/** Returns, corrections, work queues, bulk work, exports and owner-entered orders. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderManagementTest {
  private static final String ADMIN = "test-owner";
  private static final String PASSWORD = "test-only-password-2026";
  private static final OrderInput INPUT =
      new OrderInput(1, "রাফি", "গ্রাহক", "01712345678", "INSIDE", "ঢাকা ঠিকানা", null);

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> ADMIN);
    registry.add("app.admin.password", () -> PASSWORD);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired AdminOrderService admin;
  @Autowired OrderBoard board;
  @Autowired ManualOrderService manual;
  @Autowired TrackingService tracking;
  @LocalServerPort int port;
  long productId;

  @BeforeEach
  void setUp() {
    productId = resetShop(db);
  }

  @AfterEach
  void tearDown() {
    clear(db);
  }

  private Browser login() throws Exception {
    var browser = new Browser(port);
    browser.get("/admin/login");
    browser.post("/admin/login", Map.of("username", ADMIN, "password", PASSWORD));
    browser.get("/admin/orders");
    return browser;
  }

  private void move(String code, String to) {
    admin.changeStatus(code, to, "reason", "Pathao", "PT-1", ADMIN);
  }

  /** Total 459. Reached through the real rules, with nothing paid. */
  private String orderAt(String status) {
    String code = placeOrder(db, productId, INPUT);
    if (status.equals("NEW")) return code;
    move(code, "DESIGN_SENT");
    admin.approveDesign(code, ADMIN);
    if (status.equals("DESIGN_SENT")) return code;
    move(code, "CONFIRMED");
    if (status.equals("CONFIRMED")) return code;
    move(code, "MAKING");
    if (status.equals("MAKING")) return code;
    move(code, "SHIPPED");
    if (status.equals("SHIPPED")) return code;
    move(code, "DELIVERED");
    return code;
  }

  private void pay(String code, int amount, String ref) {
    admin.recordMoney(code, "PAYMENT", "BKASH", String.valueOf(amount), ref, ADMIN);
  }

  private int queue(OrderBoard.Queue queue) {
    return board.queueCounts().stream()
        .filter(c -> c.key().equals(queue.name()))
        .findFirst()
        .orElseThrow()
        .count();
  }

  @Test
  void aRefusedParcelBecomesReturnedAndOnlyItsHeldMoneyCanMove() throws Exception {
    String code = orderAt("SHIPPED");
    pay(code, 100, "ADV1");
    assertThatThrownBy(() -> admin.changeStatus(code, "RETURNED", " ", null, null, ADMIN))
        .isInstanceOf(RuleException.class);
    admin.changeStatus(code, "RETURNED", "Customer refused", null, null, ADMIN);

    var detail = admin.detail(code);
    assertThat(detail.status()).isEqualTo("RETURNED");
    assertThat(detail.balance()).isNull();
    assertThat(detail.closed()).isTrue();
    assertThat(queue(OrderBoard.Queue.TO_REFUND)).isEqualTo(1);
    assertThatThrownBy(() -> pay(code, 10, "LATE1")).isInstanceOf(RuleException.class);
    assertThat(tracking.find(code, "01712345678").orElseThrow().status()).isEqualTo("RETURNED");
    assertThat(login().get("/admin/orders/" + code).body())
        .contains("Money still held", "Record verified refund", "You can refund up to ৳100")
        .doesNotContain("value=\"PAYMENT\"", "Shipping message");

    admin.recordMoney(code, "REFUND", "CASH", "100", "", ADMIN);
    assertThat(queue(OrderBoard.Queue.TO_REFUND)).isZero();
  }

  @Test
  void aDeliveredOrderCanStillComeBackButAnUnshippedOneCannot() {
    String delivered = orderAt("DELIVERED");
    admin.changeStatus(delivered, "RETURNED", "Damaged", null, null, ADMIN);
    assertThat(admin.detail(delivered).status()).isEqualTo("RETURNED");
    String making = orderAt("MAKING");
    assertThatThrownBy(() -> admin.changeStatus(making, "RETURNED", "x", null, null, ADMIN))
        .isInstanceOf(RuleException.class);
  }

  @Test
  void undoRestoresTheLastStepOnceAndLeavesHistory() {
    String code = orderAt("DELIVERED");
    assertThatThrownBy(() -> admin.undoLastStatus(code, "  ", ADMIN)).isInstanceOf(RuleException.class);
    admin.undoLastStatus(code, "Clicked by mistake", ADMIN);

    var detail = admin.detail(code);
    assertThat(detail.status()).isEqualTo("SHIPPED");
    assertThat(detail.deliveredAt()).isNull();
    assertThat(detail.courier()).isEqualTo("Pathao");
    assertThat(detail.undoTo()).isNull();
    assertThat(detail.history().getLast().type()).isEqualTo("STATUS_UNDONE");
    assertThatThrownBy(() -> admin.undoLastStatus(code, "again", ADMIN)).isInstanceOf(RuleException.class);

    move(code, "DELIVERED");
    assertThat(admin.detail(code).undoTo()).isEqualTo("SHIPPED");
  }

  @Test
  void undoingShipmentClearsCourierDetailsAndANewOrderHasNothingToUndo() {
    String code = orderAt("SHIPPED");
    admin.undoLastStatus(code, "Wrong parcel", ADMIN);
    var detail = admin.detail(code);
    assertThat(detail.status()).isEqualTo("MAKING");
    assertThat(detail.courier()).isNull();
    assertThat(detail.trackingNo()).isNull();

    String fresh = orderAt("NEW");
    assertThat(admin.detail(fresh).undoTo()).isNull();
    assertThatThrownBy(() -> admin.undoLastStatus(fresh, "x", ADMIN)).isInstanceOf(RuleException.class);
  }

  @Test
  void voidingStopsTheMoneyCountingAndFreesTheReference() {
    String code = orderAt("MAKING");
    pay(code, 200, "WRONGREF");
    long id = admin.detail(code).payments().getFirst().id();
    assertThatThrownBy(() -> admin.voidMoney(code, id, "", ADMIN)).isInstanceOf(RuleException.class);

    admin.voidMoney(code, id, "Typed the wrong amount", ADMIN);
    var detail = admin.detail(code);
    assertThat(detail.netPaid()).isZero();
    assertThat(detail.payments()).hasSize(1);
    assertThat(detail.payments().getFirst().voidReason()).isEqualTo("Typed the wrong amount");
    assertThatThrownBy(() -> admin.voidMoney(code, id, "twice", ADMIN)).isInstanceOf(RuleException.class);

    pay(code, 20, "WRONGREF");
    assertThat(admin.detail(code).netPaid()).isEqualTo(20);
    assertThatThrownBy(() -> pay(code, 20, "WRONGREF")).isInstanceOf(RuleException.class);
  }

  @Test
  void aVoidCannotLeaveImpossibleMoney() {
    String code = orderAt("MAKING");
    pay(code, 459, "FULL1");
    admin.recordMoney(code, "REFUND", "CASH", "100", "", ADMIN);
    pay(code, 100, "AGAIN1");
    var payments = admin.detail(code).payments();
    long refund = payments.stream().filter(p -> p.kind().equals("REFUND")).findFirst().orElseThrow().id();
    assertThatThrownBy(() -> admin.voidMoney(code, refund, "x", ADMIN))
        .isInstanceOf(RuleException.class)
        .hasMessageContaining("overpay");

    String other = orderAt("MAKING");
    pay(other, 100, "PAY2");
    admin.recordMoney(other, "REFUND", "CASH", "100", "", ADMIN);
    long payment = admin.detail(other).payments().getFirst().id();
    assertThatThrownBy(() -> admin.voidMoney(other, payment, "x", ADMIN))
        .isInstanceOf(RuleException.class)
        .hasMessageContaining("refund first");
    assertThatThrownBy(() -> admin.voidMoney(code, payment, "x", ADMIN))
        .as("a record belonging to another order")
        .isInstanceOf(RuleException.class);
  }

  @Test
  void queuesCountEachKindOfWaitingWork() {
    orderAt("NEW");
    String approved = orderAt("DESIGN_SENT");
    orderAt("CONFIRMED");
    String delivered = orderAt("DELIVERED");
    assertThat(queue(OrderBoard.Queue.DESIGN_TO_SEND)).isEqualTo(1);
    assertThat(queue(OrderBoard.Queue.TO_CONFIRM)).isEqualTo(1);
    assertThat(queue(OrderBoard.Queue.TO_MAKE)).isEqualTo(1);
    assertThat(queue(OrderBoard.Queue.TO_COLLECT)).isEqualTo(1);
    assertThat(board.money().toCollect()).isEqualTo(459);

    db.update("UPDATE orders SET advance_required=50 WHERE code=?", approved);
    assertThat(queue(OrderBoard.Queue.ADVANCE_DUE)).isEqualTo(1);
    assertThat(queue(OrderBoard.Queue.TO_CONFIRM)).isZero();

    db.update("UPDATE order_event SET created_at=now() - interval '9 days' WHERE to_status='SHIPPED'");
    String shipped = orderAt("SHIPPED");
    db.update(
        "UPDATE order_event SET created_at=now() - interval '9 days' WHERE to_status='SHIPPED'"
            + " AND order_id=(SELECT id FROM orders WHERE code=?)",
        shipped);
    assertThat(queue(OrderBoard.Queue.STUCK_SHIPPING)).isEqualTo(1);
  }

  @Test
  void filtersByQueueNameCourierAndDateWithBackLinkKept() throws Exception {
    String delivered = orderAt("DELIVERED");
    String fresh = orderAt("NEW");
    var browser = login();
    var list = browser.get("/admin/orders?queue=TO_COLLECT&sort=oldest");
    assertThat(list.body()).contains(delivered, "1 order").doesNotContain(fresh);
    assertThat(browser.get("/admin/orders/" + delivered).body())
        .contains("href=\"/admin/orders?queue=TO_COLLECT&amp;sort=oldest\"");

    var byCourier =
        browser.post("/admin/orders/search", Map.of("q", "pathao", "queue", "TO_COLLECT"));
    var found = browser.get(location(byCourier).replaceFirst("^https?://[^/]+", ""));
    assertThat(found.body()).contains(delivered).doesNotContain(fresh);

    assertThat(browser.get("/admin/orders?from=2020-01-01&to=2020-01-02").body()).contains("0 orders");
    assertThat(browser.get("/admin/orders?from=nonsense&queue=NOPE").body()).contains(delivered, fresh);
  }

  @Test
  void bulkActionsFollowTheNormalRulesOrderByOrder() throws Exception {
    String confirmed = orderAt("CONFIRMED");
    String fresh = orderAt("NEW");
    var browser = login();
    browser.post(
        "/admin/orders/bulk?codes=" + confirmed + "&codes=" + fresh, Map.of("action", "START_MAKING"));
    assertThat(admin.detail(confirmed).status()).isEqualTo("MAKING");
    assertThat(admin.detail(fresh).status()).isEqualTo("NEW");
    assertThat(browser.get("/admin/orders").body()).contains("1 order updated.", fresh + ": ");

    String delivered = orderAt("DELIVERED");
    pay(delivered, 100, "ADVX");
    browser.post("/admin/orders/bulk?codes=" + delivered, Map.of("action", "COLLECT_BALANCE"));
    var detail = admin.detail(delivered);
    assertThat(detail.balance()).isZero();
    assertThat(detail.payments().getLast().method()).isEqualTo("COD");
    assertThat(queue(OrderBoard.Queue.TO_COLLECT)).isZero();
    assertThat(browser.get("/admin/orders").body()).contains("1 order updated.");

    browser.post("/admin/orders/bulk?codes=" + delivered, Map.of("action", "COLLECT_BALANCE"));
    assertThat(admin.detail(delivered).payments()).hasSize(2);
    assertThat(browser.get("/admin/orders").body()).contains("Nothing is left to collect");
    browser.post("/admin/orders/bulk", Map.of("action", "COLLECT_BALANCE"));
    assertThat(browser.get("/admin/orders").body()).contains("Tick at least one order");
  }

  @Test
  void exportHasFullContactsAndNeutralisesSpreadsheetFormulas() throws Exception {
    placeOrder(db, productId, new OrderInput(1, "x", "=HYPERLINK(1)", "01712345678", "INSIDE", "-2+3", null));
    var csv = login().get("/admin/orders/export.csv");
    assertThat(csv.statusCode()).isEqualTo(200);
    assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
    assertThat(csv.headers().firstValue("Content-Disposition").orElse("")).contains("attachment");
    assertThat(csv.body()).startsWith("﻿").contains("01712345678", "\"'=HYPERLINK(1)\"", "\"'-2+3\"");
    assertThat(AdminOrderBoardController.cell("a\"b")).isEqualTo("\"a\"\"b\"");
    assertThat(new Browser(port).get("/admin/orders/export.csv").body()).doesNotContain("01712345678");
  }

  @Test
  void slipsShowWhatTheCourierMustCollect() throws Exception {
    String code = orderAt("MAKING");
    pay(code, 100, "SLIP1");
    var browser = login();
    assertThat(browser.get("/admin/orders/" + code + "/slip").body())
        .contains(code, "01712345678", "ঢাকা ঠিকানা", "৳359");
    assertThat(browser.post("/admin/orders/slips?codes=" + code, Map.of()).body()).contains("৳359");
  }

  @Test
  void ownerEnteredOrderUsesDefaultsAndJoinsTheWorkflow() throws Exception {
    var form = new ManualOrderService.Form();
    form.productId = String.valueOf(productId);
    form.quantity = "২";
    form.name = "ফোনের গ্রাহক";
    form.phone = "০১৭১২৩৪৫৬৭৮";
    form.area = "OUTSIDE";
    form.address = "খুলনা";
    form.extras = "50";
    form.advance = "100";
    form.neededBy = "2030-01-15";

    String code = manual.create(form, ADMIN);
    var detail = admin.detail(code);
    assertThat(detail.status()).isEqualTo("NEW");
    assertThat(detail.needsDesign()).isFalse();
    assertThat(detail.total()).isEqualTo(399 * 2 + 50 + 110);
    assertThat(detail.advanceRequired()).isEqualTo(100);
    assertThat(detail.phone()).isEqualTo("01712345678");
    assertThat(detail.history().getFirst().actor()).isEqualTo(ADMIN);

    form.engraving = "নাম";
    assertThat(admin.detail(manual.create(form, ADMIN)).needsDesign()).isTrue();
  }

  @Test
  void ownerEnteredOrderRefusesBadValuesAndKeepsTheForm() throws Exception {
    var browser = login();
    assertThat(browser.get("/admin/orders/new").body()).contains("পরীক্ষার চাবির রিং");
    var page =
        browser.post(
            "/admin/orders/new",
            Map.of(
                "productId", String.valueOf(productId), "quantity", "1", "name", "টাইপ করা",
                "phone", "123", "area", "INSIDE", "address", "ঠিকানা"));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("valid Bangladeshi", "value=\"টাইপ করা\"");
    assertThat(orderCount(db)).isZero();

    for (Map<String, String> bad :
        List.of(Map.of("quantity", "11"), Map.of("advance", "99999"), Map.of("neededBy", "soon"))) {
      var form = new ManualOrderService.Form();
      form.productId = String.valueOf(productId);
      form.name = "ক";
      form.phone = "01712345678";
      form.address = "খ";
      form.quantity = bad.getOrDefault("quantity", "1");
      form.advance = bad.get("advance");
      form.neededBy = bad.get("neededBy");
      assertThatThrownBy(() -> manual.create(form, ADMIN)).isInstanceOf(RuleException.class);
    }
  }

  @Test
  void orderPageWarnsAboutPastReturnsAndHidesTheFakeBkashLink() throws Exception {
    String returned = orderAt("SHIPPED");
    admin.changeStatus(returned, "RETURNED", "Refused", null, null, ADMIN);
    String current = orderAt("NEW");
    db.update("UPDATE orders SET needs_design=false, advance_required=50 WHERE code=?", current);
    String page = login().get("/admin/orders/" + current).body();
    assertThat(page).contains("1 returned parcel", returned, "Add your bKash number");
    assertThat(page).doesNotContain(">Missing bKash<");
  }

  @Test
  void anUnpaidAdvanceCanBeRecordedFromTheNextStepAndThenConfirmed() throws Exception {
    String code = orderAt("DESIGN_SENT");
    db.update("UPDATE orders SET advance_required=100 WHERE code=?", code);
    var browser = login();
    assertThat(browser.get("/admin/orders/" + code).body())
        .contains("Advance due: ৳100", "Record advance", "Continue without advance", "value=\"100\"")
        .doesNotContain("Confirm order");
    browser.post(
        "/admin/orders/" + code + "/money",
        Map.of("kind", "PAYMENT", "method", "CASH", "amount", "100", "reference", ""));
    assertThat(browser.get("/admin/orders/" + code).body())
        .contains("Confirm order")
        .doesNotContain("Advance due");
    move(code, "CONFIRMED");
    assertThat(admin.detail(code).status()).isEqualTo("CONFIRMED");
  }

  @Test
  void theOwnerCanContinueWithoutTheRestOfTheAdvance() throws Exception {
    String code = orderAt("DESIGN_SENT");
    db.update("UPDATE orders SET advance_required=100 WHERE code=?", code);
    pay(code, 40, "PART1");
    assertThatThrownBy(() -> move(code, "CONFIRMED")).isInstanceOf(RuleException.class);
    assertThatThrownBy(() -> admin.waiveAdvance(code, " ", ADMIN)).isInstanceOf(RuleException.class);

    var browser = login();
    browser.get("/admin/orders/" + code);
    browser.post("/admin/orders/" + code + "/advance/waive", Map.of("reason", "Regular customer"));
    var detail = admin.detail(code);
    assertThat(detail.advanceRequired()).isEqualTo(40);
    assertThat(detail.total()).isEqualTo(459);
    assertThat(detail.history().getLast().note()).contains("৳100", "৳40", "Regular customer");
    assertThat(browser.get("/admin/orders/" + code).body()).contains("Confirm order");
    assertThatThrownBy(() -> admin.waiveAdvance(code, "again", ADMIN)).isInstanceOf(RuleException.class);
    move(code, "CONFIRMED");

    String making = orderAt("MAKING");
    assertThatThrownBy(() -> admin.waiveAdvance(making, "late", ADMIN)).isInstanceOf(RuleException.class);
  }

  @Test
  void dashboardShowsWorkAndMoneyAndEveryNewPageNeedsLogin() throws Exception {
    orderAt("DELIVERED");
    assertThat(login().get("/admin").body())
        .contains("Delivered, money to collect", "Delivered, still to collect", "৳459", "Returned");
    var stranger = new Browser(port);
    for (String path : List.of("/admin/orders/new", "/admin/orders/export.csv"))
      assertThat(location(stranger.get(path))).as(path).endsWith("/admin/login");
  }
}
