package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.location;
import static org.assertj.core.api.Assertions.assertThat;

import com.upoharlab.order.OrderTestSupport.Browser;
import java.nio.file.Files;
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

/** The owner rewords customer-facing text in Admin → Site text and customers see it at once. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SiteTextAdminTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    String uploads = Files.createTempDirectory("upoharlab-text-test").toString();
    registry.add("app.uploads-directory", () -> uploads);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired com.upoharlab.text.SiteTexts texts;
  @LocalServerPort int port;
  private static Browser admin;

  @BeforeEach
  void signIn() throws Exception {
    if (admin != null) return;
    admin = new Browser(port);
    admin.get("/admin/login");
    var res = admin.post("/admin/login", Map.of("username", "test-owner", "password", "test-only-password-2026"));
    assertThat(location(res)).endsWith("/admin");
  }

  @AfterEach
  void resetTexts() {
    texts.resetAll();
  }

  @Test
  void editorListsEveryTextByPage() throws Exception {
    var page = admin.get("/admin/texts");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Home page", "Product page: order form", "name=\"t:home.title.line1\"",
        "আপনার গল্প।", "Leave empty to hide");
    assertThat(new Browser(port).get("/admin/texts").statusCode()).isNotEqualTo(200);
  }

  @Test
  void savedWordingReplacesTheDefaultOnTheSite() throws Exception {
    admin.get("/admin/texts");
    var saved = admin.post("/admin/texts", Map.of("t:home.title.line1", "প্রিয়জনের জন্য", "t:site.name", "আমার দোকান"));
    assertThat(location(saved)).endsWith("/admin/texts");
    String home = new Browser(port).get("/").body();
    assertThat(home).contains("প্রিয়জনের জন্য", "<title>আপনার গল্প, আপনার ছোঁয়ায় — আমার দোকান</title>")
        .doesNotContain("আপনার গল্প।");
    assertThat(db.queryForObject("SELECT count(*) FROM site_text", Integer.class)).isEqualTo(2);

    // Saving the default text again, or an empty box, goes back to the default.
    admin.get("/admin/texts");
    admin.post("/admin/texts", Map.of("t:home.title.line1", "আপনার গল্প।", "t:site.name", ""));
    assertThat(db.queryForObject("SELECT count(*) FROM site_text", Integer.class)).isZero();
    assertThat(new Browser(port).get("/").body()).contains("আপনার গল্প।", "উপহার ল্যাব");
  }

  @Test
  void brokenPlaceholdersAreRefusedAndNothingIsSaved() throws Exception {
    admin.get("/admin/texts");
    var res = admin.post("/admin/texts", Map.of("t:product.saving", "{0 সাশ্রয়", "t:home.cta", "দেখুন"));
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.body()).contains("Nothing was saved", "Curly brackets", "{0 সাশ্রয়");
    assertThat(db.queryForObject("SELECT count(*) FROM site_text", Integer.class)).isZero();
  }

  @Test
  void anOptionalTextCanBeEmptiedToHideIt() throws Exception {
    assertThat(new Browser(port).get("/policies/returns").body()).contains("পুরো শর্ত এখনো এখানে লেখা হয়নি");
    admin.get("/admin/texts");
    admin.post("/admin/texts", Map.of("t:returns.terms.note", ""));
    String page = new Browser(port).get("/policies/returns").body();
    assertThat(page).doesNotContain("পুরো শর্ত এখনো এখানে লেখা হয়নি").doesNotContain("notice-warning");
  }

  @Test
  void resetPutsEveryTextBack() throws Exception {
    admin.get("/admin/texts");
    admin.post("/admin/texts", Map.of("t:nav.help", "সাহায্য"));
    assertThat(new Browser(port).get("/").body()).contains(">সাহায্য<");
    admin.get("/admin/texts");
    admin.post("/admin/texts/reset", Map.of());
    assertThat(new Browser(port).get("/").body()).doesNotContain(">সাহায্য<").contains(">জিজ্ঞাসা<");
  }
}
