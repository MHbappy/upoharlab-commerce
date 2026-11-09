package com.upoharlab.order;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** What this browser session may do: its submit tokens, and which orders it may view. */
public final class OrderSession implements Serializable {
  private static final String ATTRIBUTE = "upoharlab.orderSession";
  private static final int MAX_ENTRIES = 20;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** One rendered order form. Synchronize on it to handle repeat clicks one at a time. */
  public static final class Token implements Serializable {
    final String value;
    final long productId;
    String shownFingerprint;
    String orderCode;

    private Token(String value, long productId) {
      this.value = value;
      this.productId = productId;
    }
  }

  private final Map<String, Token> tokens =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Token> eldest) {
          return size() > MAX_ENTRIES;
        }
      };
  private final Set<String> viewableOrders = new LinkedHashSet<>();

  public static OrderSession of(HttpSession session) {
    synchronized (OrderSession.class) {
      OrderSession current = (OrderSession) session.getAttribute(ATTRIBUTE);
      if (current == null) {
        current = new OrderSession();
        session.setAttribute(ATTRIBUTE, current);
      }
      return current;
    }
  }

  public synchronized Token issue(long productId) {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    Token token = new Token(HexFormat.of().formatHex(bytes), productId);
    tokens.put(token.value, token);
    return token;
  }

  /** Only tokens issued to this session, for this product, are ever recognised. */
  public synchronized Token find(String value, long productId) {
    Token token = value == null ? null : tokens.get(value);
    return token != null && token.productId == productId ? token : null;
  }

  public synchronized void allowViewing(String code) {
    viewableOrders.add(code);
    if (viewableOrders.size() > MAX_ENTRIES) {
      var oldest = viewableOrders.iterator();
      oldest.next();
      oldest.remove();
    }
  }

  public synchronized boolean canView(String code) {
    return viewableOrders.contains(code);
  }
}
