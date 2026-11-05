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
    db.update("DELETE FROM order_event");
    db.update("DELETE FROM orders");
    db.update("DELETE FROM order_code_counter");
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

    /** Opens the product page and keeps its hidden form values, as a browser does. */
    Browser open() throws Exception {
      var page = get("/p/" + SLUG);
      assertThat(page.statusCode()).isEqualTo(200);
      return this;
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

    /** Sends the form with the hidden values currently on the page, plus these overrides. */
    HttpResponse<String> order(String action, Map<String, String> overrides) throws Exception {
      Map<String, String> fields = validOrder();
      fields.put("action", action);
      fields.put("submitToken", submitToken);
      fields.put("shownTotal", shownTotal);
      fields.putAll(overrides);
      return post("/p/" + SLUG + "/order", fields);
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

  /** Order fields only; hidden values come from the page. */
  static Map<String, String> validOrder() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("quantity", "1");
    fields.put("engraving", "রাফি");
    fields.put("name", "গোপন-গ্রাহক");
    fields.put("phone", "01712345678");
    fields.put("area", "INSIDE");
    fields.put("address", "গোপন-ঠিকানা, ঢাকা");
    return fields;
  }

  static String location(HttpResponse<String> response) {
    return response.headers().firstValue("Location").orElse("");
  }
}
