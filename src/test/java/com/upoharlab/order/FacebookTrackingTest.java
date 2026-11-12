package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.upoharlab.FacebookPixel;
import com.upoharlab.order.FacebookConversions.Reply;
import com.upoharlab.order.OrderTestSupport.Browser;
import com.upoharlab.settings.SettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FacebookTrackingTest {
  private static final String ADMIN = "test-owner";
  private static final String PASSWORD = "test-only-password-2026";
  private static final String PIXEL = "9876543210";
  private static final String TOKEN = "EAAtesttoken_only.for|tests-123456";

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> ADMIN);
    registry.add("app.admin.password", () -> PASSWORD);
    registry.add("app.orders.max-per-hour", () -> "100000");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired FacebookPixel pixel;
  @Autowired SettingsService settings;
  @MockitoBean FacebookConversions.Sender sender;
  @MockitoBean OrderAlerts.Sender alerts;
  @LocalServerPort int port;

  @BeforeEach
  void setUp() throws Exception {
    long id = resetShop(db);
    db.update("UPDATE product SET landing_config=CAST(? AS jsonb) WHERE id=?", LandingOrderFlowTest.CONFIG, id);
    when(sender.post(any(), any(), any(), any())).thenReturn(new Reply(200, "{\"events_received\":1}"));
    tracking("", "", "");
  }

  @AfterEach
  void tearDown() {
    clear(db);
    tracking("", "", "");
  }

  private void tracking(String id, String token, String code) {
    setting(db, FacebookPixel.ID_KEY, id);
    setting(db, FacebookPixel.TOKEN_KEY, token);
    setting(db, FacebookPixel.TEST_CODE_KEY, code);
    pixel.refresh();
  }

  private Browser login() throws Exception {
    var browser = new Browser(port);
    browser.get("/admin/login");
    browser.post("/admin/login", Map.of("username", ADMIN, "password", PASSWORD));
    browser.get("/admin/settings");
    return browser;
  }

  private String saved(String key) {
    return db.queryForObject("SELECT value FROM setting WHERE key=?", String.class, key);
  }

  private String placeOrder(Browser b, String path) throws Exception {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("engraving", "নাম"); f.put("confirmEngraving", "নাম"); f.put("font", "Galada");
    f.put("design", "0"); f.put("packageIndex", "0"); f.put("opt0", "0"); f.put("name", "গ্রাহক");
    f.put("phone", "01712345678"); f.put("area", "INSIDE"); f.put("address", "ঠিকানা");
    f.put("shownTotal", "959"); f.put("action", "submit"); f.put("submitToken", b.submitToken);
    var saved = b.post(path, f);
    assertThat(saved.statusCode()).isEqualTo(302);
    String location = location(saved);
    return location.substring(location.indexOf("/order/"));
  }

  private String sentEvent(String name) throws Exception {
    var data = ArgumentCaptor.forClass(String.class);
    verify(sender, timeout(5000).atLeastOnce()).post(eq(PIXEL), eq(TOKEN), data.capture(), isNull());
    return data.getAllValues().stream()
        .filter(json -> json.contains("\"event_name\":\"" + name + "\""))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void savingAPixelIdInSettingsTurnsItOnWithoutARestart() throws Exception {
    var visitor = new Browser(port);
    String page = "/order/" + SLUG;
    var before = visitor.get(page);
    assertThat(before.body()).doesNotContain("data-pixel");
    assertThat(before.headers().firstValue("content-security-policy").orElseThrow())
        .doesNotContain("connect.facebook.net");

    var admin = login();
    admin.post("/admin/settings", Map.of("values[fb_pixel_id]", PIXEL));
    assertThat(saved(FacebookPixel.ID_KEY)).isEqualTo(PIXEL);
    setting(db, "accepting_orders", "true");

    var after = new Browser(port).get(page);
    assertThat(after.body()).contains("data-pixel=\"" + PIXEL + "\"");
    assertThat(after.headers().firstValue("content-security-policy").orElseThrow())
        .contains("https://connect.facebook.net");
    verify(sender, never()).post(any(), any(), any(), any());
  }

  @Test
  void theAccessTokenIsHiddenKeptWhenBlankAndRemovable() throws Exception {
    var admin = login();
    admin.post("/admin/settings", Map.of("values[fb_pixel_id]", PIXEL, "values[fb_capi_token]", TOKEN));
    assertThat(saved(FacebookPixel.TOKEN_KEY)).isEqualTo(TOKEN);
    assertThat(pixel.serverEnabled()).isTrue();
    assertThat(settings.all()).doesNotContainKey(FacebookPixel.TOKEN_KEY);

    String form = admin.get("/admin/settings").body();
    assertThat(form).contains("A token is saved").doesNotContain(TOKEN);
    assertThat(new Browser(port).get("/").body()).doesNotContain(TOKEN);

    admin.post("/admin/settings", Map.of("values[fb_pixel_id]", PIXEL));
    assertThat(saved(FacebookPixel.TOKEN_KEY)).isEqualTo(TOKEN);

    admin.post(
        "/admin/settings",
        Map.of("values[fb_pixel_id]", PIXEL, "values[fb_capi_token_clear]", "true"));
    assertThat(saved(FacebookPixel.TOKEN_KEY)).isEmpty();
    assertThat(pixel.serverEnabled()).isFalse();
  }

  @Test
  void badValuesAreRefusedAndNothingIsSaved() throws Exception {
    var admin = login();
    var page =
        admin.post(
            "/admin/settings",
            Map.of("values[fb_pixel_id]", "abc", "values[fb_capi_token]", "short token"));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Enter the numeric Pixel ID", "Paste the whole access token");
    assertThat(page.body()).doesNotContain("short token");
    assertThat(saved(FacebookPixel.ID_KEY)).isEmpty();
    assertThat(saved(FacebookPixel.TOKEN_KEY)).isEmpty();
  }

  @Test
  void aPageViewSendsViewContentWithTheBrowsersEventIdButNoPhone() throws Exception {
    tracking(PIXEL, TOKEN, "TEST123");
    String html = new Browser(port).get("/order/" + SLUG).body();
    var id = java.util.regex.Pattern.compile("data-event=\"([0-9a-f-]{36})\"").matcher(html);
    assertThat(id.find()).isTrue();

    String event = sentEvent("ViewContent");
    assertThat(event)
        .contains("\"event_id\":\"" + id.group(1) + "\"", "\"action_source\":\"website\"", "client_user_agent")
        .contains("/order/" + SLUG)
        .doesNotContain("\"ph\"", "TEST123");
  }

  @Test
  void anOrderSendsOnePurchaseWithAHashedPhoneAndTheOrderCode() throws Exception {
    tracking(PIXEL, TOKEN, "");
    var b = new Browser(port);
    String page = "/order/" + SLUG;
    b.get(page + "?fbclid=AdClick_123");
    String thanks = placeOrder(b, page);
    String code = thanks.split("/")[2];

    String event = sentEvent("Purchase");
    assertThat(event)
        .contains("\"event_id\":\"" + code + "\"", "\"value\":959", "\"currency\":\"BDT\"")
        .contains(FacebookConversions.sha256("8801712345678"), "AdClick_123")
        .doesNotContain("01712345678", "গ্রাহক", "ঠিকানা");
    assertThat(b.get(thanks).body()).contains("data-purchase=\"" + code + "\"");
  }

  @Test
  void aFacebookFailureNeverAffectsTheOrder() throws Exception {
    tracking(PIXEL, TOKEN, "");
    when(sender.post(any(), any(), any(), any())).thenThrow(new java.io.IOException("down"));
    var b = new Browser(port);
    String page = "/order/" + SLUG;
    b.get(page);
    assertThat(b.get(placeOrder(b, page)).statusCode()).isEqualTo(200);
    assertThat(orderCount(db)).isEqualTo(1);
  }

  @Test
  void withoutATokenNothingIsSentFromTheServer() throws Exception {
    tracking(PIXEL, "", "");
    var b = new Browser(port);
    String page = "/order/" + SLUG;
    b.get(page);
    b.get(placeOrder(b, page));
    verify(sender, after(300).never()).post(any(), any(), any(), any());
  }

  @Test
  void theTestButtonUsesTheCodeAndExplainsTheResult() throws Exception {
    tracking(PIXEL, TOKEN, "");
    var admin = login();
    admin.post("/admin/settings/facebook-test", Map.of());
    assertThat(admin.get("/admin/settings").body()).contains("Add the test event code");
    verify(sender, never()).post(any(), any(), any(), any());

    tracking(PIXEL, TOKEN, "TEST123");
    admin.post("/admin/settings/facebook-test", Map.of());
    verify(sender, timeout(2000)).post(eq(PIXEL), eq(TOKEN), any(), eq("TEST123"));
    assertThat(admin.get("/admin/settings").body()).contains("Facebook accepted the test event");

    when(sender.post(any(), any(), any(), any()))
        .thenReturn(new Reply(400, "{\"error\":{\"message\":\"Invalid token\"}}"));
    admin.post("/admin/settings/facebook-test", Map.of());
    assertThat(admin.get("/admin/settings").body()).contains("Facebook refused it: Invalid token");
    assertThat(location(new Browser(port).get("/admin/settings"))).endsWith("/admin/login");
  }

  @Test
  void botsAreSkippedAndBrowserCookiesAreUsed() {
    assertThat(FacebookConversions.isBot("facebookexternalhit/1.1")).isTrue();
    assertThat(FacebookConversions.isBot("Mozilla/5.0 (compatible; Googlebot/2.1)")).isTrue();
    assertThat(FacebookConversions.isBot(null)).isTrue();
    assertThat(FacebookConversions.isBot("Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile")).isFalse();

    var request = new MockHttpServletRequest("GET", "/order/x");
    request.addHeader("User-Agent", "Mozilla/5.0");
    request.setRemoteAddr("203.0.113.9");
    request.setCookies(
        new jakarta.servlet.http.Cookie("_fbp", "fb.1.1.2"),
        new jakarta.servlet.http.Cookie("_fbc", "fb.1.1.click"));
    var visitor = FacebookConversions.visitor(request, null);
    assertThat(visitor.ip()).isEqualTo("203.0.113.9");
    assertThat(visitor.fbp()).isEqualTo("fb.1.1.2");
    assertThat(visitor.fbc()).isEqualTo("fb.1.1.click");
  }
}
