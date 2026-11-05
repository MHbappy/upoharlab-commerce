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
    resetShop(db);
  }

  @AfterEach
  void tearDown() {
    clear(db);
  }

  @Test
  void onlyNewOrdersUseTheAllowance() throws Exception {
    var browser = new Browser(port).open();
    for (int i = 0; i < 5; i++) browser.order("quote", Map.of());
    var first = location(browser.order("submit", Map.of()));
    for (int i = 0; i < 5; i++)
      assertThat(location(browser.order("submit", Map.of()))).isEqualTo(first);

    browser.open();
    browser.order("quote", Map.of());
    assertThat(browser.order("submit", Map.of()).statusCode()).isEqualTo(302);
    assertThat(orderCount(db)).isEqualTo(2);

    browser.open();
    browser.order("quote", Map.of());
    var blocked = browser.order("submit", Map.of());
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.body()).contains("WhatsApp", "গোপন-গ্রাহক");
    assertThat(orderCount(db)).isEqualTo(2);
  }
}
