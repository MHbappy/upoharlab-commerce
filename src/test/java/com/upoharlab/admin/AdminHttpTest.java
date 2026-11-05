package com.upoharlab.admin;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

/** Drives the admin through real HTTP with CSRF, the way a browser does. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminHttpTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");
  static Path uploads;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    uploads = Files.createTempDirectory("upoharlab-admin-test");
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.uploads-directory", uploads::toString);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @LocalServerPort int port;

  @AfterEach
  void clean() {
    db.update("DELETE FROM product_image");
    db.update("DELETE FROM product WHERE slug LIKE 'http-%'");
    for (String key : new String[] {"courier_days_inside_min", "courier_days_inside_max"})
      db.update("UPDATE setting SET value='' WHERE key=?", key);
  }

  private String base() {
    return "http://localhost:" + port;
  }

  private String csrf(String html) {
    var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private HttpResponse<String> get(HttpClient c, String path) throws Exception {
    return c.send(
        HttpRequest.newBuilder(URI.create(base() + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(HttpClient c, String path, String form) throws Exception {
    return c.send(
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpClient login() throws Exception {
    var cookies = new CookieManager();
    cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String token = csrf(get(client, "/admin/login").body());
    var login =
        post(
            client,
            "/admin/login",
            "username=test-owner&password=test-only-password-2026&_csrf=" + enc(token));
    assertThat(location(login)).endsWith("/admin");
    return client;
  }

  private static String location(HttpResponse<String> response) {
    return response.headers().firstValue("Location").orElse("");
  }

  private String productForm(String token, String status, String price) {
    return "_csrf="
        + enc(token)
        + "&title="
        + enc("পরীক্ষা")
        + "&slug=http-test&sortOrder=0&status="
        + status
        + "&price="
        + price;
  }

  private long createProduct(HttpClient c) throws Exception {
    String token = csrf(get(c, "/admin/products/new").body());
    var saved = post(c, "/admin/products/new", productForm(token, "DRAFT", "500"));
    assertThat(saved.statusCode()).isEqualTo(302);
    return Long.parseLong(location(saved).replaceAll(".*/", ""));
  }

  private HttpResponse<String> upload(HttpClient c, long id, String token, byte[] file)
      throws Exception {
    String boundary = "testBoundary";
    var body = new ByteArrayOutputStream();
    String head =
        "--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n"
            + token
            + "\r\n--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"alt\"\r\n\r\nছবি\r\n--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"a.jpg\"\r\n"
            + "Content-Type: image/jpeg\r\n\r\n";
    body.write(head.getBytes(StandardCharsets.UTF_8));
    body.write(file);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return c.send(
        HttpRequest.newBuilder(URI.create(base() + "/admin/products/" + id + "/photos"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Accept", "text/html")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private byte[] rotatedJpeg() throws Exception {
    var plain = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(100, 80, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "JPEG",
        plain);
    byte[] jpeg = plain.toByteArray();
    byte[] exif = {
      'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 1, 0x12, 0, 3, 0, 0, 0, 1, 0, 6,
      0, 0, 0, 0, 0, 0
    };
    var out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.write(0);
    out.write(exif.length + 2);
    out.write(exif);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  @Test
  void savingProductWithoutCsrfIsRejectedAndWithCsrfWorks() throws Exception {
    var client = login();
    assertThat(post(client, "/admin/products/new", "title=x&slug=http-test").statusCode())
        .isEqualTo(403);
    assertThat(createProduct(client)).isPositive();
  }

  @Test
  void phonePhotoIsUploadedThroughTheFormAndStoredUpright() throws Exception {
    var client = login();
    long id = createProduct(client);
    String token = csrf(get(client, "/admin/products/" + id).body());
    var response = upload(client, id, token, rotatedJpeg());
    assertThat(response.statusCode()).isEqualTo(302);
    String name =
        db.queryForObject(
            "SELECT filename FROM product_image WHERE product_id=?", String.class, id);
    var stored = javax.imageio.ImageIO.read(uploads.resolve(name).toFile());
    assertThat(stored.getWidth()).isEqualTo(80);
    assertThat(stored.getHeight()).isEqualTo(100);
  }

  @Test
  void photoOverFiveMegabytesGetsMessageOnTheEditor() throws Exception {
    var client = login();
    long id = createProduct(client);
    String token = csrf(get(client, "/admin/products/" + id).body());
    var response = upload(client, id, token, new byte[6 * 1024 * 1024]);
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(location(response)).endsWith("/admin/products/" + id);
    assertThat(get(client, "/admin/products/" + id).body()).contains("up to 5 MB");
  }

  @Test
  void photoOverTheRequestLimitGetsAReadablePage() throws Exception {
    var client = login();
    long id = createProduct(client);
    String token = csrf(get(client, "/admin/products/" + id).body());
    var response = upload(client, id, token, new byte[21 * 1024 * 1024]);
    assertThat(response.statusCode()).isIn(403, 413);
    assertThat(response.body()).doesNotContain("\"timestamp\"");
  }

  @Test
  void pausingRequiresTheSameDetailsAsPublishing() throws Exception {
    var client = login();
    long id = createProduct(client);
    String token = csrf(get(client, "/admin/products/" + id).body());
    var page = post(client, "/admin/products/" + id, productForm(token, "PAUSED", "500"));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Complete this before publishing");
    assertThat(
            db.queryForObject("SELECT status FROM product WHERE id=?", String.class, id))
        .isEqualTo("DRAFT");
    assertThat(get(HttpClient.newHttpClient(), "/p/http-test").statusCode()).isEqualTo(404);
    assertThatThrownBy(() -> db.update("UPDATE product SET status='PAUSED', price=NULL WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void courierDaysMustBeSavedAsAPair() throws Exception {
    var client = login();
    String token = csrf(get(client, "/admin/settings").body());
    var page =
        post(
            client,
            "/admin/settings",
            "_csrf=" + enc(token) + "&values%5Bcourier_days_inside_min%5D=2");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Enter both minimum and maximum");
    assertThat(
            db.queryForObject(
                "SELECT value FROM setting WHERE key='courier_days_inside_min'", String.class))
        .isEmpty();
  }

  @Test
  void quantityHelpFollowsTheProductNotItsUrl() {
    assertThat(
            db.queryForObject(
                "SELECT quantity_help_bn FROM product WHERE slug='keyring-gift-pair'",
                String.class))
        .contains("এক জোড়া");
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM product WHERE quantity_help_bn IS NOT NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void unknownPublicUrlsAre404NotAdminLogin() throws Exception {
    var client = HttpClient.newHttpClient();
    for (String path : new String[] {"/no-such-section", "/favicon.ico", "/no-such-page", "/p/no-such"}) {
      var response = get(client, path);
      assertThat(response.statusCode()).as(path).isEqualTo(404);
      assertThat(location(response)).isEmpty();
    }
    assertThat(location(get(client, "/admin/settings"))).endsWith("/admin/login");
    assertThat(location(get(client, "/admin/orders"))).endsWith("/admin/login");
  }
}
