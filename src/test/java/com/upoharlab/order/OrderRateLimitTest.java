package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.OrderTestSupport.Browser;
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
class OrderRateLimitTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.orders.max-per-hour", () -> "2");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
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

  private static final String PAGE = "/order/" + SLUG;
  private static final String TOTAL = "1369";

  private java.net.http.HttpResponse<String> submit(Browser b, String phone) throws Exception {
    b.get(PAGE);
    return send(b, phone);
  }

  private java.net.http.HttpResponse<String> send(Browser b, String phone) throws Exception {
    Map<String, String> f = new java.util.LinkedHashMap<>();
    f.put("engraving", "নাম"); f.put("confirmEngraving", "নাম"); f.put("font", "Galada");
    f.put("design", "0"); f.put("packageIndex", "1"); f.put("opt0", "1"); f.put("upsells", "0");
    f.put("name", "গোপন-গ্রাহক"); f.put("phone", phone); f.put("area", "OUTSIDE"); f.put("address", "ঢাকা");
    f.put("shownTotal", TOTAL); f.put("action", "submit"); f.put("submitToken", b.submitToken);
    return b.post(PAGE, f);
  }

  @Test
  void onlyNewOrdersUseTheAllowance() throws Exception {
    Browser b = new Browser(port);
    var first = submit(b, "01712345678");
    assertThat(first.statusCode()).isEqualTo(302);
    for (int i = 0; i < 5; i++)
      assertThat(send(b, "01712345678").statusCode()).as("repeat click").isEqualTo(302);
    assertThat(orderCount(db)).isEqualTo(1);
    assertThat(submit(b, "01812345678").statusCode()).isEqualTo(302);
    assertThat(orderCount(db)).isEqualTo(2);
    var blocked = submit(b, "01912345678");
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.body()).contains("WhatsApp", "গোপন-গ্রাহক");
    assertThat(orderCount(db)).isEqualTo(2);
  }
}
