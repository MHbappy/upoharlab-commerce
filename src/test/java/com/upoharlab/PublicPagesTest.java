package com.upoharlab;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
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
class PublicPagesTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.base-url", () -> "https://upoharlab.com/");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @LocalServerPort int port;

  @AfterEach
  void reset() {
    for (String key : new String[] {"whatsapp", "phone", "business_email"})
      db.update("UPDATE setting SET value='' WHERE key=?", key);
    db.update(
        "UPDATE product SET status='DRAFT', making_days_min=NULL, making_days_max=NULL"
            + " WHERE slug='door-name-plate'");
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
            HttpResponse.BodyHandlers.ofString());
  }

  private static final String[][] PAGES = {
    {"/", "উপহার ল্যাব"},
    {"/bulk", "বেশি পরিমাণে অর্ডার"},
    {"/faq", "সাধারণ প্রশ্ন"},
    {"/about", "আমাদের কথা"},
    {"/policies/delivery", "ডেলিভারি"},
    {"/policies/returns", "ফেরত ও বাতিল"},
    {"/policies/privacy", "গোপনীয়তা"},
    {"/track", "অর্ডার ট্র্যাক"}
  };

  @Test
  void everyPublicPageHasTitleDescriptionAndFooterLinks() throws Exception {
    for (String[] page : PAGES) {
      var response = get(page[0]);
      assertThat(response.statusCode()).as(page[0]).isEqualTo(200);
      assertThat(response.body())
          .as(page[0])
          .contains("lang=\"bn\"", page[1], "name=\"description\"", "href=\"/policies/privacy\"")
          .contains("href=\"#main\"", "id=\"main\"")
          .doesNotContain("th:", "approve", "advance");
      assertThat(response.body()).as(page[0] + " has no empty blocks").doesNotContainPattern("<p[^>]*>\\s*</p>");
      assertThat(Pattern.compile("<h1[ >]").matcher(response.body()).results().count())
          .as(page[0])
          .isEqualTo(1);
    }
  }

  @Test
  void indexablePagesHaveCanonicalAndPrivateOnesDoNot() throws Exception {
    assertThat(get("/faq").body())
        .contains("rel=\"canonical\" href=\"https://upoharlab.com/faq\"")
        .doesNotContain("noindex");
    assertThat(get("/track").body()).contains("noindex").doesNotContain("rel=\"canonical\"");
  }

  @Test
  void nothingInventedAppearsBeforeTheOwnerSetsIt() throws Exception {
    var returns = get("/policies/returns").body();
    assertThat(returns).contains("এখনো এখানে লেখা হয়নি");
    assertThat(returns).doesNotContain("ফ্রি রিমেক", "গ্যারান্টি", "১০০%");
    var bulk = get("/bulk").body();
    assertThat(bulk).contains("যোগাযোগের তথ্য শিগগিরই").doesNotContain("wa.me", "tel:");
    assertThat(get("/about").body()).doesNotContain("wa.me", "tel:", "mailto:");
    assertThat(get("/policies/delivery").body())
        .as("no note about estimates while no courier times are set")
        .doesNotContain("আনুমানিক");
  }

  @Test
  void contactDetailsAppearOnceSet() throws Exception {
    db.update("UPDATE setting SET value='01700000000' WHERE key IN ('whatsapp','phone')");
    db.update("UPDATE setting SET value='hello@upoharlab.com' WHERE key='business_email'");
    var bulk = get("/bulk").body();
    assertThat(bulk).contains("https://wa.me/8801700000000", "tel:01700000000");
    assertThat(get("/about").body()).contains("mailto:hello@upoharlab.com");
    assertThat(get("/").body()).contains("mailto:hello@upoharlab.com");
  }

  @Test
  void sitemapListsOnlyPublicPagesAndActiveProducts() throws Exception {
    var empty = get("/sitemap.xml");
    assertThat(empty.headers().firstValue("Content-Type").orElse("")).contains("xml");
    assertThat(empty.body())
        .contains("<loc>https://upoharlab.com/</loc>")
        .contains("<loc>https://upoharlab.com/policies/privacy</loc>")
        .doesNotContain("/admin", "/order", "/track", "/p/");
    db.update(
        "UPDATE product SET status='ACTIVE', price=899, making_days_min=1, making_days_max=2,"
            + " landing_config='{\"enabled\":true}'::jsonb WHERE slug='door-name-plate'");
    assertThat(get("/sitemap.xml").body())
        .contains("<loc>https://upoharlab.com/order/door-name-plate</loc>")
        .doesNotContain("keyring-gift-pair");
  }

  @Test
  void robotsExcludesPrivateRoutes() throws Exception {
    var robots = get("/robots.txt");
    assertThat(robots.headers().firstValue("Content-Type").orElse("")).contains("text/plain");
    assertThat(robots.body())
        .contains("Disallow: /admin", "Disallow: /order", "Disallow: /track")
        .contains("Sitemap: https://upoharlab.com/sitemap.xml");
  }

  @Test
  void staticAssetsAreVersionedAndCachedButPagesAreNot() throws Exception {
    var home = get("/");
    assertThat(home.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    var css = Pattern.compile("href=\"(/css/app-[0-9a-f]{32}\\.css)\"").matcher(home.body());
    assertThat(css.find()).as("hashed stylesheet link").isTrue();
    var stylesheet = get(css.group(1));
    assertThat(stylesheet.statusCode()).isEqualTo(200);
    assertThat(stylesheet.headers().firstValue("Cache-Control").orElse(""))
        .contains("max-age=31536000")
        .doesNotContain("no-store");
    var font = Pattern.compile("(/fonts/[^\"')]+\\.woff2)").matcher(stylesheet.body());
    assertThat(font.find()).as("font link in stylesheet").isTrue();
    assertThat(get(font.group(1)).statusCode()).isEqualTo(200);
    assertThat(get("/favicon.svg").statusCode()).isEqualTo(200);
    var icon = Pattern.compile("href=\"(/favicon-[0-9a-f]{32}\\.svg)\"").matcher(home.body());
    assertThat(icon.find()).as("hashed icon link").isTrue();
    assertThat(get(icon.group(1)).statusCode()).isEqualTo(200);
    assertThat(home.body()).as("order script loads only where it is used").doesNotContain("/js/order");
  }
}
