package com.upoharlab.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** A browser session for HTTP tests, plus a known product and open-shop settings. */
final class OrderTestSupport {
  static final String SLUG = "flow-keyring";

  private OrderTestSupport() {}

  static long resetShop(JdbcTemplate db) {
    clear(db);
    Long id =
        db.queryForObject(
            "INSERT INTO product (slug,title_bn,short_description_bn,includes_bn,material_bn,"
                + "size_bn,engraving_help_bn,price,making_days_min,making_days_max,status)"
                + " VALUES (?,'পরীক্ষার চাবির রিং','ছোট','একটি','কাঠ','ছোট','নাম',399,1,2,'ACTIVE')"
                + " RETURNING id",
            Long.class,
            SLUG);
    setting(db, "accepting_orders", "true");
    setting(db, "whatsapp", "01700000000");
    setting(db, "reply_hours", "6");
    setting(db, "delivery_inside", "60");
    setting(db, "delivery_outside", "110");
    return id;
  }

  static void clear(JdbcTemplate db) {
    db.update("DELETE FROM payment");
    db.update("DELETE FROM order_event");
    db.update("DELETE FROM orders");
    db.update("DELETE FROM order_code_counter");
    db.update("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE slug=?)", SLUG);
    db.update("DELETE FROM product WHERE slug=?", SLUG);
    setting(db, "accepting_orders", "false");
    setting(db, "whatsapp", "");
    setting(db, "reply_hours", "");
  }

  static void setting(JdbcTemplate db, String key, String value) {
    db.update("UPDATE setting SET value=? WHERE key=?", value, key);
  }

  static int orderCount(JdbcTemplate db) {
    return db.queryForObject("SELECT count(*) FROM orders", Integer.class);
  }

  static final class Browser {
    private final String base;
    private final HttpClient client;
    String csrf, submitToken, shownTotal;

    Browser(int port) {
      base = "http://localhost:" + port;
      var cookies = new CookieManager();
      cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
      client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    HttpResponse<String> get(String path) throws Exception {
      return remember(
          client.send(
              HttpRequest.newBuilder(URI.create(base + path)).build(),
              HttpResponse.BodyHandlers.ofString()));
    }

    HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
      StringBuilder body = new StringBuilder("_csrf=" + encode(csrf));
      fields.forEach(
          (k, v) -> {
            if (v != null) body.append('&').append(k).append('=').append(encode(v));
          });
      return remember(
          client.send(
              HttpRequest.newBuilder(URI.create(base + path))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                  .build(),
              HttpResponse.BodyHandlers.ofString()));
    }

    HttpResponse<String> multipart(String path, Map<String, String> fields) throws Exception {
      String boundary = "test-boundary";
      StringBuilder body = new StringBuilder();
      fields.forEach(
          (k, v) ->
              body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
                  .append(k).append("\"\r\n\r\n").append(v).append("\r\n"));
      body.append("--").append(boundary).append("--\r\n");
      return remember(
          client.send(
              HttpRequest.newBuilder(URI.create(base + path))
                  .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                  .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                  .build(),
              HttpResponse.BodyHandlers.ofString()));
    }

    /** A multipart post with one file per entry of {@code files}, as a browser sends it. */
    HttpResponse<String> multipart(
        String path, Map<String, String> fields, Map<String, byte[]> files) throws Exception {
      String boundary = "test-boundary-files";
      var body = new java.io.ByteArrayOutputStream();
      for (var f : fields.entrySet())
        body.write(
            ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey()
                    + "\"\r\n\r\n" + f.getValue() + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
      for (var f : files.entrySet()) {
        body.write(
            ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey()
                    + "\"; filename=\"a.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(f.getValue());
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
      }
      body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      return remember(
          client.send(
              HttpRequest.newBuilder(URI.create(base + path))
                  .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                  .build(),
              HttpResponse.BodyHandlers.ofString()));
    }

    private HttpResponse<String> remember(HttpResponse<String> response) {
      String body = response.body();
      csrf = hidden(body, "_csrf", csrf);
      submitToken = hidden(body, "submitToken", submitToken);
      shownTotal = hidden(body, "shownTotal", shownTotal);
      return response;
    }

    private static String hidden(String html, String name, String fallback) {
      var value =
          Pattern.compile("name=\"" + name + "\"[^>]*?value=\"([^\"]*)\"").matcher(html);
      return value.find() ? value.group(1) : fallback;
    }

    private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
  }

  static String location(HttpResponse<String> response) {
    return response.headers().firstValue("location").orElse("");
  }

  /** A saved order for admin and tracking tests; total = 399 x quantity + delivery. */
  record OrderInput(
      int quantity, String engraving, String name, String phone, String area, String address, String note) {}

  private static final java.util.concurrent.atomic.AtomicLong NEXT_CODE =
      new java.util.concurrent.atomic.AtomicLong(1000);

  static String placeOrder(JdbcTemplate db, long productId, OrderInput in) {
    String code = "UL-202601-" + NEXT_CODE.incrementAndGet();
    int delivery = "INSIDE".equals(in.area()) ? 60 : 110;
    Long id =
        db.queryForObject(
            "INSERT INTO orders (code, submit_token, product_id, product_title_snapshot, quantity,"
                + " engraving_text, customer_name, phone, area, address, note, unit_price_snapshot,"
                + " delivery_charge_snapshot, total, advance_required, status)"
                + " VALUES (?,?,?,'পরীক্ষার চাবির রিং',?,?,?,?,?,?,?,399,?,?,0,'NEW') RETURNING id",
            Long.class,
            code, "t" + code, productId, in.quantity(), in.engraving(), in.name(), in.phone(),
            in.area(), in.address(), in.note(), delivery, 399 * in.quantity() + delivery);
    db.update(
        "INSERT INTO order_event (order_id, event_type, to_status, actor)"
            + " VALUES (?, 'CREATED', 'NEW', 'customer')",
        id);
    return code;
  }
}
