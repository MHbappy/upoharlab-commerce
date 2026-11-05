package com.upoharlab.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded fixed-window limiter for one app instance. Entries are kept in insertion order, which is
 * also expiry order. When full it evicts the entry closest to expiry, so a flood of new keys cannot
 * deny service to everyone else.
 */
public final class RateLimiter {
  private final Clock clock;
  private final Duration window;
  private final int maxKeys;
  private final Map<String, Window> windows = new LinkedHashMap<>();

  private static final class Window {
    private final Instant expires;
    private int count = 1;

    private Window(Instant expires) {
      this.expires = expires;
    }
  }

  public RateLimiter(Clock clock, Duration window, int maxKeys) {
    if (maxKeys < 1) throw new IllegalArgumentException("maxKeys must be positive");
    this.clock = clock;
    this.window = window;
    this.maxKeys = maxKeys;
  }

  public synchronized boolean allow(String key, int limit) {
    if (limit < 1) return false;
    Instant now = clock.instant();
    Iterator<Window> oldest = windows.values().iterator();
    while (oldest.hasNext() && !oldest.next().expires.isAfter(now)) oldest.remove();
    Window current = windows.get(key);
    if (current == null) {
      if (windows.size() >= maxKeys) {
        Iterator<String> eldest = windows.keySet().iterator();
        eldest.next();
        eldest.remove();
      }
      windows.put(key, new Window(now.plus(window)));
      return true;
    }
    if (current.count >= limit) return false;
    current.count++;
    return true;
  }

  synchronized int size() {
    return windows.size();
  }
}
