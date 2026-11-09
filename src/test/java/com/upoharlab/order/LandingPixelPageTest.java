package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.OrderTestSupport.Browser;
import java.util.*;
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

/** The pages must still render, and be allowed to load Facebook, once a Pixel ID is set. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LandingPixelPageTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.orders.max-per-hour", () -> "100000");
    registry.add("app.facebook.pixel-id", () -> "1234567890");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @MockitoBean OrderAlerts.Sender sender;
  @LocalServerPort int port;

  @BeforeEach
  void setUp() {
    long id = resetShop(db);
    db.update(
        "UPDATE product SET landing_config=CAST(? AS jsonb) WHERE id=?",
        LandingOrderFlowTest.CONFIG,
        id);
  }

  @AfterEach
  void tearDown() {
    clear(db);
  }

  @Test
  void orderAndThankYouPagesCarryThePixel() throws Exception {
    String page = "/order/" + SLUG;
    Browser b = new Browser(port);
    var landing = b.get(page);
    assertThat(landing.statusCode()).isEqualTo(200);
    assertThat(landing.body()).contains("data-pixel=\"1234567890\"", "/js/pixel");
    assertThat(landing.headers().firstValue("content-security-policy").orElseThrow())
        .contains("https://connect.facebook.net");
    Map<String, String> f = new LinkedHashMap<>();
    f.put("engraving", "নাম"); f.put("confirmEngraving", "নাম"); f.put("font", "Galada");
    f.put("design", "0"); f.put("packageIndex", "0"); f.put("opt0", "0"); f.put("name", "গ্রাহক");
    f.put("phone", "01712345678"); f.put("area", "INSIDE"); f.put("address", "ঠিকানা");
    f.put("shownTotal", "959"); f.put("action", "submit"); f.put("submitToken", b.submitToken);
    var saved = b.post(page, f);
    assertThat(saved.statusCode()).isEqualTo(302);
    String location = saved.headers().firstValue("location").orElseThrow();
    var thanks = b.get(location.substring(location.indexOf("/order/")));
    assertThat(thanks.statusCode()).isEqualTo(200);
    assertThat(thanks.body()).contains("data-purchase=\"UL-", "data-value=\"959\"");
  }
}
