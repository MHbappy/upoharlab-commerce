package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.OrderTestSupport.Browser;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LandingOrderFlowTest {
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

  static final String CONFIG =
      "{\"enabled\":true,\"hero\":{\"headline\":\"শিরোনাম </script><b>\",\"subHeadline\":\"\",\"heroImage\":\"\",\"oldPrice\":1099,\"offerPrice\":899},"
          + "\"benefits\":[\"এক\",\"দুই\",\"তিন\",\"চার\"],"
          + "\"packages\":[{\"name\":\"ক\",\"description\":\"\",\"price\":899,\"isPopular\":false},"
          + "{\"name\":\"খ\",\"description\":\"\",\"price\":999,\"isPopular\":true},"
          + "{\"name\":\"গ\",\"description\":\"\",\"price\":1699,\"isPopular\":false}],"
          + "\"options\":[{\"label\":\"মাপ\",\"type\":\"SIZE\",\"values\":[{\"name\":\"ছোট\",\"image\":\"\",\"priceDelta\":0},{\"name\":\"বড়\",\"image\":\"\",\"priceDelta\":200}]}],"
          + "\"upsells\":[{\"label\":\"বক্স\",\"price\":60}],\"advanceAmount\":100,"
          + "\"delivery\":{\"insideDhakaCharge\":60,\"outsideDhakaCharge\":110},"
          + "\"preview\":{\"designs\":[{\"name\":\"ক </script><b>\",\"baseImage\":\"\",\"textX\":500,\"textY\":250,\"maxWidth\":800,\"fontSize\":110,\"minFontSize\":40,\"color\":\"#2B211C\",\"shadow\":\"#FFFFFF\",\"imageWidth\":1000,\"imageHeight\":500}],"
          + "\"fonts\":[\"Galada\",\"Hind Siliguri\"],\"defaultText\":\"রহমান ভিলা\",\"maxChars\":24}}";

  @Autowired JdbcTemplate db;
  @Autowired AdminOrderService admin;
  @MockitoBean OrderAlerts.Sender sender;
  @LocalServerPort int port;
  long productId;

  @BeforeEach
  void setUp() {
    productId = resetShop(db);
    db.update("UPDATE product SET landing_config=CAST(? AS jsonb) WHERE id=?", CONFIG, productId);
  }

  @AfterEach
  void tearDown() {
    clear(db);
  }

  private Map<String, String> valid(String total) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("engraving", "ক্ষেত্র");
    f.put("confirmEngraving", "ক্ষেত্র");
    f.put("font", "Galada");
    f.put("design", "0");
    f.put("packageIndex", "1");
    f.put("opt0", "1");
    f.put("upsells", "0");
    f.put("name", "গ্রাহক");
    f.put("phone", "01712345678");
    f.put("area", "OUTSIDE");
    f.put("address", "ঠিকানা");
    f.put("fontSize", "77");
    f.put("shownTotal", total);
    return f;
  }

  private static final String PAGE = "/order/" + SLUG;
  private static final int TOTAL = 999 + 200 + 60 + 110;

  private java.net.http.HttpResponse<String> post(Browser b, Map<String, String> fields, String action) throws Exception {
    b.get(PAGE);
    Map<String, String> all = new LinkedHashMap<>(fields);
    all.put("action", action);
    all.put("submitToken", b.submitToken);
    return b.post(PAGE, all);
  }

  private Map<String, Object> saved() {
    return db.queryForMap("SELECT * FROM orders");
  }

  @Test
  void rendersFromConfigurationWithSafelyEmbeddedJson() throws Exception {
    String html = new Browser(port).get(PAGE).body();
    assertThat(html).contains("আমার নামে বানাতে চাই", "সবচেয়ে জনপ্রিয়", "id=\"landing-config\"");
    assertThat(html).doesNotContain("</script><b>");
    assertThat(html).contains("\\u003c/script\\u003e");
  }

  @Test
  void salesSectionsLeadToTheFinalOrderForm() throws Exception {
    db.update("UPDATE product SET description_bn='পণ্যের বিবরণ', "
        + "landing_config=jsonb_set(landing_config,'{socialProof}',"
        + "'{\"reviews\":[{\"name\":\"ক্রেতা\",\"text\":\"পছন্দ হয়েছে\"}]}'::jsonb) WHERE id=?", productId);
    String html = new Browser(port).get(PAGE).body();
    int previous = -1;
    for (String section : List.of("class=\"lp-hook\"", "id=\"preview\"", "id=\"why-title\"",
        "id=\"info-title\"", "class=\"lp-section lp-proof\"", "id=\"packages\"", "id=\"order\"")) {
      int position = html.indexOf(section);
      assertThat(position).as(section + " follows the previous sales section").isGreaterThan(previous);
      previous = position;
    }
    assertThat(html).contains("id=\"lp-input\"", "data-pick-package=\"1\"", "id=\"order-customization\" open");
  }

  @Test
  void sampleTextIsNotSubmittedAsCustomerEngraving() throws Exception {
    Browser browser = new Browser(port);
    String html = browser.get(PAGE).body();
    var engraving = Pattern.compile("<input[^>]*id=\"engraving\"[^>]*>").matcher(html);
    assertThat(engraving.find()).isTrue();
    assertThat(engraving.group()).contains("value=\"\"");
    assertThat(html).contains("data-selected-price>৳৯৯৯", "class=\"lp-brand\" href=\"/\"", "data-payment-note");
    Map<String, String> fields = valid(Integer.toString(TOTAL));
    fields.put("engraving", "");
    fields.put("confirmEngraving", "");
    var missing = post(browser, fields, "submit");
    assertThat(missing.statusCode()).isEqualTo(200);
    assertThat(missing.body()).contains("কী খোদাই হবে তা লিখুন।");
    assertThat(orderCount(db)).isZero();
    fields.put("engraving", "নিজের লেখা");
    fields.remove("confirmEngraving");
    assertThat(post(browser, fields, "submit").statusCode()).isEqualTo(302);
    assertThat(saved().get("engraving_text")).isEqualTo("নিজের লেখা");
  }

  private void hide(String... sections) {
    String json = "{" + String.join(",", java.util.Arrays.stream(sections).map(n -> "\"" + n + "\":false").toList()) + "}";
    db.update(
        "UPDATE product SET landing_config=jsonb_set(landing_config,'{sections}',CAST(? AS jsonb)) WHERE id=?",
        json, productId);
  }

  @Test
  void aFourthPackageIsChargedAtItsOwnPrice() throws Exception {
    db.update(
        "UPDATE product SET landing_config=jsonb_insert(landing_config,'{packages,-1}',"
            + "'{\"name\":\"ঘ\",\"description\":\"\",\"price\":1500,\"isPopular\":false}'::jsonb,true) WHERE id=?",
        productId);
    int total = 1500 + 200 + 60 + 110;
    Map<String, String> fields = valid(Integer.toString(total));
    fields.put("packageIndex", "3");

    var response = post(new Browser(port), fields, "submit");

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(saved().get("total")).isEqualTo(total);
  }

  @Test
  void hiddenSectionsLeaveThePage() throws Exception {
    hide("hero", "benefits", "packages", "details");
    String page = new Browser(port).get(PAGE).body();
    assertThat(page)
        .contains("<h1>পরীক্ষার চাবির রিং</h1>", "id=\"order\"")
        .doesNotContain("lp-hook", "why-title", "pricing-title", "lp-specs");
  }

  @Test
  void orderingWorksWithThePreviewHiddenAndBlank() throws Exception {
    hide("preview");
    db.update(
        "UPDATE product SET landing_config=jsonb_set(landing_config,'{preview}','{}'::jsonb) WHERE id=?",
        productId);
    Browser b = new Browser(port);
    Map<String, String> fields = valid(Integer.toString(TOTAL));
    fields.put("font", com.upoharlab.landing.LandingFonts.DEFAULT);

    assertThat(b.get(PAGE).body()).doesNotContain("id=\"preview\"", "id=\"font\"");
    var response = post(b, fields, "submit");

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void hiddenOrderFormRefusesOrders() throws Exception {
    Browser b = new Browser(port);
    b.get(PAGE);
    hide("order");
    Map<String, String> all = new LinkedHashMap<>(valid(Integer.toString(TOTAL)));
    all.put("action", "submit");
    all.put("submitToken", b.submitToken);
    b.post(PAGE, all);

    assertThat(orderCount(db)).isZero();
    assertThat(b.get(PAGE).body()).doesNotContain("id=\"order\"", "href=\"#order\"");
  }

  @Test
  void productDetailsShowPhotosDescriptionAndDelivery() throws Exception {
    db.update(
        "UPDATE product SET description_bn='বড় বিবরণ', care_bn='মুছে রাখুন', indoor_only=true WHERE id=?",
        productId);
    db.update(
        "INSERT INTO product_image(product_id,filename,alt_bn,sort_order) VALUES (?,?,'প্রথম ছবি',0),(?,?,'দ্বিতীয় ছবি',1)",
        productId, "11111111-1111-1111-1111-111111111111.jpg",
        productId, "22222222-2222-2222-2222-222222222222.jpg");
    setting(db, "courier_days_inside_min", "1");
    setting(db, "courier_days_inside_max", "2");

    String page = new Browser(port).get(PAGE).body();

    assertThat(page)
        .contains("পণ্যের বিস্তারিত", "প্রথম ছবি", "দ্বিতীয় ছবি", "lp-gallery-thumb")
        .contains("বড় বিবরণ", "মুছে রাখুন", "শুধু ঘরের ভেতরে", "ঢাকায় ১–২ দিন");
  }

  @Test
  void emptyProductDetailsLeaveNoCards() throws Exception {
    db.update(
        "UPDATE product SET short_description_bn=NULL, includes_bn=' ', material_bn=NULL, size_bn=NULL,"
            + " engraving_help_bn=NULL WHERE id=?",
        productId);
    setting(db, "courier_days_inside_min", "");
    setting(db, "courier_days_inside_max", "");
    setting(db, "courier_days_outside_min", "");
    setting(db, "courier_days_outside_max", "");

    var response = new Browser(port).get(PAGE);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("id=\"lp-gallery\"", "কী পাবেন", "উপাদান", "কী লেখা যাবে", "lp-line").contains("বানাতে সময়");
  }

  @Test
  void savesServerCalculatedTotalAndPreviewParameters() throws Exception {
    var res = post(new Browser(port), valid(Integer.toString(TOTAL)), "submit");
    assertThat(res.statusCode()).isEqualTo(302);
    var order = saved();
    assertThat(order.get("total")).isEqualTo(TOTAL);
    assertThat(order.get("unit_price_snapshot")).isEqualTo(999);
    assertThat(order.get("extras_snapshot")).isEqualTo(260);
    assertThat(order.get("advance_required")).isEqualTo(100);
    assertThat(order.get("quantity")).isEqualTo(1);
    assertThat(order.get("landing_selection").toString())
        .contains("ক্ষেত্র", "Galada", "\"fontSize\": 77", "বড়", "বক্স");
  }

  @Test
  void tamperedPriceFieldsAreIgnored() throws Exception {
    Map<String, String> f = valid(Integer.toString(TOTAL));
    f.put("price", "1");
    f.put("total", "1");
    f.put("advanceAmount", "0");
    post(new Browser(port), f, "submit");
    assertThat(saved().get("total")).isEqualTo(TOTAL);
    assertThat(saved().get("advance_required")).isEqualTo(100);
  }

  @Test
  void aWrongShownTotalAsksForReviewInsteadOfSaving() throws Exception {
    var res = post(new Browser(port), valid("1"), "submit");
    assertThat(res.body()).contains(LandingOrderController.REVIEW_MESSAGE);
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void mismatchedTextKeepsEnteredValues() throws Exception {
    Map<String, String> f = valid(Integer.toString(TOTAL));
    f.put("confirmEngraving", "অন্য");
    var res = post(new Browser(port), f, "submit");
    assertThat(res.body()).contains("দুটি লেখা মিলছে না", "value=\"ক্ষেত্র\"");
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void requiredFieldsShowBanglaErrors() throws Exception {
    var res = post(new Browser(port), Map.of("packageIndex", "0", "opt0", "0", "font", "Galada"), "submit");
    assertThat(res.body()).contains("আপনার নাম লিখুন।", "পুরো ঠিকানা লিখুন।");
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void honeypotBlocksTheOrder() throws Exception {
    Map<String, String> f = valid(Integer.toString(TOTAL));
    f.put("website", "spam");
    post(new Browser(port), f, "submit");
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void repeatClickKeepsOneOrder() throws Exception {
    Browser b = new Browser(port);
    b.get(PAGE);
    Map<String, String> f = valid(Integer.toString(TOTAL));
    f.put("action", "submit");
    f.put("submitToken", b.submitToken);
    b.post(PAGE, f);
    b.post(PAGE, f);
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void aPriceEditBeforeSubmitForcesReview() throws Exception {
    Browser b = new Browser(port);
    b.get(PAGE);
    db.update(
        "UPDATE product SET landing_config=CAST(? AS jsonb) WHERE id=?",
        CONFIG.replace("\"price\":999", "\"price\":1099"),
        productId);
    Map<String, String> f = valid(Integer.toString(TOTAL));
    f.put("action", "submit");
    f.put("submitToken", b.submitToken);
    assertThat(b.post(PAGE, f).body()).contains(LandingOrderController.REVIEW_MESSAGE);
    assertThat(orderCount(db)).isZero();
  }

  @Test
  void thankYouPageShowsCodeAndRecap() throws Exception {
    Browser b = new Browser(port);
    var res = post(b, valid(Integer.toString(TOTAL)), "submit");
    String location = res.headers().firstValue("location").orElseThrow();
    String html = b.get(location.substring(location.indexOf("/order/"))).body();
    assertThat(html).contains(saved().get("code").toString(), "আমরা যোগাযোগ করে আপনার লেখা ও ডিজাইন নিশ্চিত করব।", "ক্ষেত্র");
  }

  @Test
  void customerTextIsEscapedOnTheThankYouPage() throws Exception {
    Map<String, String> f = valid(Integer.toString(TOTAL));
    String attack = "<img src=x>";
    f.put("engraving", attack);
    f.put("confirmEngraving", attack);
    Browser b = new Browser(port);
    var res = post(b, f, "submit");
    String location = res.headers().firstValue("location").orElseThrow();
    String html = b.get(location.substring(location.indexOf("/order/"))).body();
    assertThat(Pattern.compile("<img src=x>").matcher(html).find()).isFalse();
  }

  @Test
  void oldProductAddressRedirectsToTheOrderPage() throws Exception {
    var res = new Browser(port).get("/p/" + SLUG);
    assertThat(res.statusCode()).isEqualTo(302);
    assertThat(res.headers().firstValue("location").orElseThrow()).endsWith(PAGE);
  }

  @Test
  void aProductWithoutLandingPageHasNoOrderPage() throws Exception {
    db.update("UPDATE product SET landing_config=NULL WHERE id=?", productId);
    assertThat(new Browser(port).get(PAGE).statusCode()).isEqualTo(404);
    assertThat(new Browser(port).get("/p/" + SLUG).statusCode()).isEqualTo(404);
  }

  @Test
  void ownerAlertCarriesNoCustomerDetails() throws Exception {
    post(new Browser(port), valid(Integer.toString(TOTAL)), "submit");
    var sent = org.mockito.ArgumentCaptor.forClass(OrderService.OrderPlaced.class);
    org.mockito.Mockito.verify(sender, org.mockito.Mockito.timeout(5000)).send(sent.capture());
    assertThat(sent.getValue().total()).isEqualTo(TOTAL);
    assertThat(sent.getValue().toString()).doesNotContain("01712345678", "ঠিকানা", "গ্রাহক");
  }

  @Test
  void hiddenProductsStayOffTheShopAndCannotBeOrdered() throws Exception {
    db.update("UPDATE product SET landing_config=NULL WHERE id=?", productId);
    assertThat(new Browser(port).get("/").body()).doesNotContain("/order/" + SLUG);
    db.update("UPDATE product SET landing_config=CAST(? AS jsonb) WHERE id=?", CONFIG, productId);
    assertThat(new Browser(port).get("/").body()).contains("/order/" + SLUG);
  }

  @Test
  void searchEnginesCanReachOrderPagesButNotOrderCodes() throws Exception {
    Browser b = new Browser(port);
    assertThat(b.get("/robots.txt").body()).contains("Disallow: /order/UL-").doesNotContain("Disallow: /order\n");
    assertThat(b.get("/sitemap.xml").body()).contains(PAGE).doesNotContain("/p/" + SLUG);
  }

  @Autowired TrackingService tracking;

  @Test
  void trackingSaysWhenTheAdvanceIsStillNeeded() throws Exception {
    post(new Browser(port), valid(Integer.toString(TOTAL)), "submit");
    String code = saved().get("code").toString();
    assertThat(tracking.find(code, "01712345678").orElseThrow().advanceDue()).isTrue();
    admin.recordMoney(code, "PAYMENT", "BKASH", "100", "TX1", "test-owner");
    assertThat(tracking.find(code, "01712345678").orElseThrow().advanceDue()).isFalse();
  }

  @Test
  void confirmingNeedsTheAdvanceRecorded() throws Exception {
    post(new Browser(port), valid(Integer.toString(TOTAL)), "submit");
    String code = saved().get("code").toString();
    admin.changeStatus(code, "DESIGN_SENT", null, null, null, "test-owner");
    admin.approveDesign(code, "test-owner");
    assertThat(admin.detail(code).missingForConfirmation()).hasSize(1);
    assertThatThrownBy(() -> admin.changeStatus(code, "CONFIRMED", null, null, null, "test-owner"))
        .isInstanceOf(AdminOrderService.RuleException.class)
        .hasMessageContaining("advance");
    admin.recordMoney(code, "PAYMENT", "BKASH", "100", "TX2", "test-owner");
    assertThat(admin.detail(code).missingForConfirmation()).isEmpty();
    admin.changeStatus(code, "CONFIRMED", null, null, null, "test-owner");
    assertThat(admin.detail(code).status()).isEqualTo("CONFIRMED");
  }
}
