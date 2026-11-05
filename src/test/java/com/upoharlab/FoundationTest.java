package com.upoharlab;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import org.flywaydb.core.Flyway;
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
class FoundationTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add(
        "app.uploads-directory",
        () ->
            System.getProperty("java.io.tmpdir") + "/upoharlab-test-" + postgres.getContainerId());
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @LocalServerPort int port;

  @Test
  void migrationsSeedOnlyDraftsAndDoNotOverwriteEdits() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product WHERE status = 'DRAFT'", Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM product WHERE price IS NULL", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_user", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT value FROM setting WHERE key = 'accepting_orders'", String.class))
        .isEqualTo("false");
    jdbc.update("UPDATE setting SET value = '75' WHERE key = 'delivery_inside'");
    try {
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT value FROM setting WHERE key = 'delivery_inside'", String.class))
          .isEqualTo("75");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Integer.class)).isEqualTo(4);
    } finally {
      jdbc.update("UPDATE setting SET value = '60' WHERE key = 'delivery_inside'");
    }
  }

  @Test
  void databaseRejectsInvalidProductData() {
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE product SET status = 'ACTIVE' WHERE slug = 'engraved-keyring'"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE product SET price = -1 WHERE slug = 'keyring-gift-pair'"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE product SET price = 1000001 WHERE slug = 'keyring-gift-pair'"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void publicPageRendersAndPrivateRoutesStayClosed() throws Exception {
    var client = HttpClient.newHttpClient();
    var home =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(home.statusCode()).isEqualTo(200);
    assertThat(home.body())
        .contains("lang=\"bn\"", "আপনার প্রিয়জনের নাম", "অনলাইনে অর্ডার নেওয়া শুরু হয়নি")
        .doesNotContain("৳৩৯৯", "th:replace");
    var admin =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(admin.statusCode()).isEqualTo(302);
    var post =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(post.statusCode()).isEqualTo(403);
  }

  @Autowired com.upoharlab.product.ProductStore products;
  @Autowired com.upoharlab.product.ImageStorage images;
  @Autowired com.upoharlab.settings.SettingsService settings;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

  @org.junit.jupiter.api.AfterEach
  void cleanTestProducts() {
    jdbc.update(
        "DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE slug LIKE"
            + " 'phase2-%')");
    jdbc.update("DELETE FROM product WHERE slug LIKE 'phase2-%'");
  }

  private com.upoharlab.product.ProductForm draft() {
    var f = new com.upoharlab.product.ProductForm();
    f.setTitle("পরীক্ষার পণ্য");
    f.setSlug("phase2-test");
    f.setPrice(500);
    return f;
  }

  private byte[] png() throws Exception {
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(100, 80, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "PNG",
        out);
    return out.toByteArray();
  }

  @Test
  void publishingRequiresDetailsAndPhotoAndPreservesIndoorWarning() throws Exception {
    var f = draft();
    f.setIndoorOnly(true);
    var errors = new org.springframework.validation.BeanPropertyBindingResult(f, "productForm");
    long id = products.save(null, f, errors);
    assertThat(errors.hasErrors()).isFalse();
    f.setStatus("ACTIVE");
    errors = new org.springframework.validation.BeanPropertyBindingResult(f, "productForm");
    products.save(id, f, errors);
    assertThat(errors.getFieldError("status")).isNotNull();
    assertThat(errors.getFieldError("material")).isNotNull();
    f.setShortDescription("Description");
    f.setIncludes("One item");
    f.setSize("5 inch");
    f.setMaterial("Wood");
    f.setEngravingHelp("Name");
    f.setMakingDaysMin(2);
    f.setMakingDaysMax(4);
    var photo =
        new org.springframework.mock.web.MockMultipartFile(
            "photo", "image.png", "image/png", png());
    images.upload(id, photo, "পণ্যের ছবি");
    images.upload(id, photo, "আরেকটি ছবি");
    var before = products.get(id).photos();
    products.movePhoto(id, before.get(1).id(), "up");
    assertThat(products.get(id).photos().get(0).id()).isEqualTo(before.get(1).id());
    f.setIndoorOnly(false);
    errors = new org.springframework.validation.BeanPropertyBindingResult(f, "productForm");
    products.save(id, f, errors);
    assertThat(errors.hasErrors()).isFalse();
    assertThat(products.get(id).details().getIndoorOnly()).isTrue();
    assertThat(products.bySlug("phase2-test").photos()).hasSize(2);
    products.removePhoto(id, before.get(0).id());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> products.removePhoto(id, before.get(1).id()));
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/p/phase2-test"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("শুধু ঘরের ভেতরের জন্য", "৳৫০০");
    assertThat(response.body())
        .as("shop is closed: the reason replaces ordering, and one photo needs no thumbnails")
        .contains("এখনো অনলাইনে অর্ডার নেওয়া শুরু হয়নি")
        .doesNotContain("href=\"#order\"", "id=\"order\"", "class=\"thumbnails\"");
    var home =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(home.body())
        .as("card shows the facts, and the home page says ordering is closed")
        .contains("পরীক্ষার পণ্য", "Description", "৳৫০০", "শুধু ঘরের ভেতরের জন্য")
        .contains("এখনো অনলাইনে অর্ডার নেওয়া শুরু হয়নি");
    f.setStatus("PAUSED");
    products.save(
        id, f, new org.springframework.validation.BeanPropertyBindingResult(f, "productForm"));
    assertThat(products.list(false)).noneMatch(p -> p.id() == id);
  }

  @Test
  void unknownProductIsNotFoundInsteadOfAServerError() {
    var f = draft();
    assertThatThrownBy(
            () ->
                products.save(
                    999999L,
                    f,
                    new org.springframework.validation.BeanPropertyBindingResult(f, "productForm")))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> products.movePhoto(999999L, 1L, "up"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                images.upload(
                    999999L,
                    new org.springframework.mock.web.MockMultipartFile(
                        "photo", "a.png", "image/png", new byte[] {1}),
                    "Photo"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void badImageAndDraftAccessAreRejected() throws Exception {
    var f = draft();
    long id =
        products.save(
            null,
            f,
            new org.springframework.validation.BeanPropertyBindingResult(f, "productForm"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                images.upload(
                    id,
                    new org.springframework.mock.web.MockMultipartFile(
                        "photo", "fake.jpg", "image/jpeg", "not an image".getBytes()),
                    "Photo"));
    assertThat(products.get(id).photos()).isEmpty();
    var client = HttpClient.newHttpClient();
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/p/phase2-test"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(404);
    images.upload(
        id,
        new org.springframework.mock.web.MockMultipartFile(
            "photo", "photo.png", "image/png", png()),
        "Private photo");
    String name = products.get(id).photos().getFirst().filename();
    response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/media/" + name))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void openingOrdersRequiresBusinessSetupAndBootstrapDoesNotResetPassword() {
    var form = new com.upoharlab.settings.SettingsForm();
    form.setValues(new java.util.LinkedHashMap<>(settings.all()));
    form.getValues().put("accepting_orders", "true");
    var errors = new org.springframework.validation.BeanPropertyBindingResult(form, "settingsForm");
    settings.save(form, errors);
    assertThat(errors.hasErrors()).isTrue();
    assertThat(settings.all().get("accepting_orders")).isEqualTo("false");
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM admin_user WHERE username='test-owner'", String.class);
    assertThat(encoder.matches("test-only-password-2026", hash)).isTrue();
    new com.upoharlab.admin.AdminBootstrap(jdbc, encoder, "replacement", "another-password")
        .run(null);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM admin_user WHERE username='test-owner'", String.class))
        .isEqualTo(hash);
  }

  private String csrf(String html) {
    var matcher =
        java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  @Test
  void loginProtectsAdminFormsAndRendersEditors() throws Exception {
    var cookies = new java.net.CookieManager();
    cookies.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String base = "http://localhost:" + port;
    var page =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/login")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(page.statusCode()).isEqualTo(200);
    String data =
        "username=test-owner&password=test-only-password-2026&_csrf="
            + java.net.URLEncoder.encode(
                csrf(page.body()), java.nio.charset.StandardCharsets.UTF_8);
    var login =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(302);
    assertThat(login.headers().firstValue("Location").orElse("")).endsWith("/admin");
    for (String path :
        java.util.List.of(
            "/admin",
            "/admin/settings",
            "/admin/products",
            "/admin/products/new",
            "/admin/products/1",
            "/admin/products/1/preview")) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create(base + path)).build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    var blocked =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/products/1"))
                .POST(HttpRequest.BodyPublishers.ofString("title=changed"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(blocked.statusCode()).isEqualTo(403);
  }

  @Test
  void repeatedLoginFailuresAreRateLimited() throws Exception {
    var cookies = new java.net.CookieManager();
    cookies.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String url = "http://localhost:" + port + "/admin/login";
    String token =
        csrf(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString())
                .body());
    String data =
        "username=invalid-rate-limit-user&password=incorrect&_csrf="
            + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    for (int attempt = 0; attempt < 11; attempt++) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create(url))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(data))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(attempt == 10 ? 429 : 302);
    }
  }

  @Test
  void oversizedPixelDimensionsAreRejectedBeforeDecoding() throws Exception {
    var f = draft();
    long id =
        products.save(
            null,
            f,
            new org.springframework.validation.BeanPropertyBindingResult(f, "productForm"));
    byte[] content = png();
    java.nio.ByteBuffer.wrap(content).putInt(16, 5001).putInt(20, 4000);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                images.upload(
                    id,
                    new org.springframework.mock.web.MockMultipartFile(
                        "photo", "large.png", "image/png", content),
                    "Photo"))
        .withMessageContaining("20 megapixels");
  }
}
