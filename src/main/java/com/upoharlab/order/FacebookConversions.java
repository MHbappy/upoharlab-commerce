package com.upoharlab.order;

import com.upoharlab.FacebookPixel;
import com.upoharlab.landing.LandingConfigCodec;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Facebook Conversions API. Events use the same IDs as the browser Pixel, so Facebook counts each
 * once. Sending happens in the background and can never affect a page or a saved order.
 */
@Component
public class FacebookConversions implements org.springframework.beans.factory.DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(FacebookConversions.class);
  private static final String CLICK_SESSION_KEY = "fbClickCookie";

  public record Reply(int status, String body) {}

  /** Separate from delivery so tests can observe what would be sent. */
  public interface Sender {
    Reply post(String pixelId, String token, String dataJson, String testCode) throws Exception;
  }

  /** What identifies a browser to Facebook, captured during the request. */
  public record Visitor(String ip, String userAgent, String fbp, String fbc, String url) {}

  public record TestResult(boolean ok, String message) {}

  private final Sender sender;
  private final FacebookPixel pixel;
  private final Clock clock;
  private final ExecutorService worker =
      new ThreadPoolExecutor(
          1,
          2,
          30,
          TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(500),
          runnable -> {
            Thread thread = new Thread(runnable, "facebook-events");
            thread.setDaemon(true);
            return thread;
          });

  public FacebookConversions(Sender sender, FacebookPixel pixel, Clock clock) {
    this.sender = sender;
    this.pixel = pixel;
    this.clock = clock;
  }

  /** Keeps the ad click from the landing address, for browsers that block Facebook's script. */
  public void rememberClick(HttpServletRequest request, HttpSession session) {
    String click = request.getParameter("fbclid");
    if (click != null && click.matches("[A-Za-z0-9_-]{1,500}"))
      session.setAttribute(CLICK_SESSION_KEY, "fb.1." + clock.millis() + "." + click);
  }

  public static Visitor visitor(HttpServletRequest request, HttpSession session) {
    String fbc = cookie(request, "_fbc");
    if (fbc == null && session != null && session.getAttribute(CLICK_SESSION_KEY) instanceof String kept)
      fbc = kept;
    return new Visitor(
        request.getRemoteAddr(),
        request.getHeader("User-Agent"),
        cookie(request, "_fbp"),
        fbc,
        request.getRequestURL().toString());
  }

  private static String cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) return null;
    for (Cookie cookie : request.getCookies())
      if (name.equals(cookie.getName()) && cookie.getValue().length() <= 500) return cookie.getValue();
    return null;
  }

  static boolean isBot(String userAgent) {
    return userAgent == null
        || userAgent.isBlank()
        || userAgent.matches("(?i).*(bot|crawl|spider|slurp|facebookexternalhit|preview|headless).*");
  }

  /** Bots and Facebook's own ad checker are skipped, so they do not look like shoppers. */
  public void viewContent(String eventId, String slug, String title, Integer value, Visitor visitor) {
    if (!pixel.serverEnabled() || isBot(visitor.userAgent())) return;
    send("ViewContent", event("ViewContent", eventId, visitor, userData(visitor, null), custom(slug, title, value)));
  }

  /** The order code is the event ID, matching the browser's Purchase on the thank-you page. */
  public void purchase(String code, String slug, String title, Integer total, String phone, Visitor visitor) {
    if (!pixel.serverEnabled()) return;
    send("Purchase", event("Purchase", code, visitor, userData(visitor, phone), custom(slug, title, total)));
  }

  /** Marked as a test, so Facebook shows it in Test Events and never counts it for ads. */
  public TestResult sendTest(Visitor visitor) {
    if (!pixel.serverEnabled())
      return new TestResult(false, "Save the Pixel ID and access token first.");
    if (pixel.testCode().isBlank())
      return new TestResult(false, "Add the test event code from Meta Events Manager first.");
    var event =
        event(
            "ViewContent", "test-" + clock.millis(), visitor, userData(visitor, null),
            custom("settings-test", "Upohar Lab settings test", 1));
    try {
      Reply reply = sender.post(pixel.id(), pixel.token(), LandingConfigCodec.write(List.of(event)), pixel.testCode());
      if (reply.status() / 100 == 2)
        return new TestResult(true, "Facebook accepted the test event. Check Test Events in Events Manager.");
      return new TestResult(false, "Facebook refused it: " + facebookMessage(reply.body()));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return new TestResult(false, "The test was interrupted.");
    } catch (Exception ex) {
      return new TestResult(false, "Could not reach Facebook. Try again.");
    }
  }

  @SuppressWarnings("unchecked")
  private static String facebookMessage(String body) {
    try {
      Object error = LandingConfigCodec.readAs(body, Map.class).get("error");
      if (error instanceof Map<?, ?> details && details.get("message") instanceof String message)
        return message.length() > 300 ? message.substring(0, 300) : message;
    } catch (IllegalArgumentException | NullPointerException notJson) {
      // Falls through to the plain message.
    }
    return "check the Pixel ID and access token.";
  }

  private Map<String, Object> event(
      String name, String id, Visitor visitor, Map<String, Object> user, Map<String, Object> custom) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("event_name", name);
    event.put("event_time", clock.instant().getEpochSecond());
    event.put("event_id", id);
    event.put("action_source", "website");
    event.put("event_source_url", visitor.url());
    event.put("user_data", user);
    event.put("custom_data", custom);
    return event;
  }

  private static Map<String, Object> userData(Visitor visitor, String phone) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("client_ip_address", visitor.ip());
    user.put("client_user_agent", visitor.userAgent());
    if (visitor.fbp() != null) user.put("fbp", visitor.fbp());
    if (visitor.fbc() != null) user.put("fbc", visitor.fbc());
    if (phone != null) {
      String hashed = sha256("88" + phone);
      user.put("ph", List.of(hashed));
      user.put("external_id", List.of(hashed));
      user.put("country", List.of(sha256("bd")));
    }
    return user;
  }

  private static Map<String, Object> custom(String slug, String title, Integer value) {
    Map<String, Object> custom = new LinkedHashMap<>();
    custom.put("content_ids", List.of(slug));
    custom.put("content_type", "product");
    custom.put("content_name", title);
    if (value != null) {
      custom.put("value", value);
      custom.put("currency", "BDT");
    }
    return custom;
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** Live events never carry the test code, so a forgotten code cannot hide real sales. */
  private void send(String name, Map<String, Object> event) {
    String pixelId = pixel.id(), token = pixel.token();
    try {
      worker.execute(
          () -> {
            try {
              Reply reply = sender.post(pixelId, token, LandingConfigCodec.write(List.of(event)), null);
              if (reply.status() / 100 != 2)
                log.warn("Facebook {} event got status {}", name, reply.status());
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (Exception ex) {
              log.warn("Facebook {} event failed: {}", name, ex.getClass().getSimpleName());
            }
          });
    } catch (RejectedExecutionException ex) {
      log.warn("Facebook {} event dropped: queue full", name);
    }
  }

  @Override
  public void destroy() {
    worker.shutdown();
  }

  @Component
  public static class GraphSender implements Sender {
    private static final String API_VERSION = "v26.0";
    private final HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public Reply post(String pixelId, String token, String dataJson, String testCode) throws Exception {
      String form = "data=" + encode(dataJson) + "&access_token=" + encode(token);
      if (testCode != null) form += "&test_event_code=" + encode(testCode);
      var request =
          HttpRequest.newBuilder(
                  URI.create("https://graph.facebook.com/" + API_VERSION + "/" + pixelId + "/events"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), response.body());
    }

    private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
  }
}
