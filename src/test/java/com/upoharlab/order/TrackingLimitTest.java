package com.upoharlab.order;

import static org.assertj.core.api.Assertions.*;

import com.upoharlab.order.OrderTestSupport.Browser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackingLimitTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.tracking.max-per-ip", () -> "3");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort int port;

  @Test
  void oneAddressIsLimitedAcrossDifferentCodes() throws Exception {
    var browser = new Browser(port);
    browser.get("/track");
    for (int i = 0; i < 3; i++)
      assertThat(
              browser
                  .post("/track", Map.of("code", "UL-000000-00" + i, "phone", "01712345678"))
                  .statusCode())
          .isEqualTo(200);
    var blocked = browser.post("/track", Map.of("code", "UL-000000-009", "phone", "01712345678"));
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.body()).contains("অনেকবার চেষ্টা");
  }
}
