package com.upoharlab.order;

import com.upoharlab.order.OrderService.OrderPlaced;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Optional owner alert, sent after commit. A failure here can never affect the saved order. */
@Component
public class OrderAlerts implements org.springframework.beans.factory.DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(OrderAlerts.class);

  /** Separate from delivery so tests can observe what would be sent. */
  public interface Sender {
    void send(OrderPlaced order) throws Exception;
  }

  private final Sender sender;
  private final ExecutorService worker =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<>(100),
          runnable -> {
            Thread thread = new Thread(runnable, "order-alerts");
            thread.setDaemon(true);
            return thread;
          });

  public OrderAlerts(Sender sender) {
    this.sender = sender;
  }

  @TransactionalEventListener
  public void onOrderPlaced(OrderPlaced order) {
    try {
      worker.execute(
          () -> {
            try {
              sender.send(order);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (Exception ex) {
              log.warn("Order alert failed for {}: {}", order.code(), ex.getClass().getSimpleName());
            }
          });
    } catch (RejectedExecutionException ex) {
      log.warn("Order alert dropped for {}: queue full", order.code());
    }
  }

  @Override
  public void destroy() {
    worker.shutdown();
  }

  @Component
  public static class TelegramSender implements Sender {
    private final HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String token, chatId, baseUrl;

    public TelegramSender(
        @Value("${app.telegram.bot-token:}") String token,
        @Value("${app.telegram.chat-id:}") String chatId,
        @Value("${app.base-url:}") String baseUrl) {
      this.token = token;
      this.chatId = chatId;
      this.baseUrl = baseUrl;
    }

    @Override
    public void send(OrderPlaced order) throws Exception {
      if (token.isBlank() || chatId.isBlank()) return;
      String body =
          "chat_id="
              + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
              + "&text="
              + URLEncoder.encode(message(order, baseUrl), StandardCharsets.UTF_8);
      var request =
          HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      if (status / 100 != 2) log.warn("Order alert for {} got status {}", order.code(), status);
    }

    /** Sent as plain text with no parse mode, so product titles cannot act as markup. */
    static String message(OrderPlaced order, String baseUrl) {
      String link =
          baseUrl.isBlank()
              ? ""
              : "\n" + baseUrl.replaceAll("/+$", "") + "/admin/orders/" + order.code();
      return "New order "
          + order.code()
          + "\n"
          + order.productTitle()
          + " × "
          + order.quantity()
          + "\nTotal: ৳"
          + order.total()
          + link;
    }
  }
}
