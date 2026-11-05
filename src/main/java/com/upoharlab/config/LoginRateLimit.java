package com.upoharlab.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimit extends OncePerRequestFilter {
  private final Clock clock;
  private final Map<String, Window> windows = new HashMap<>();

  private record Window(Instant expires, int count) {}

  public LoginRateLimit(Clock clock) {
    this.clock = clock;
  }

  synchronized boolean allow(String key, int limit) {
    Instant now = clock.instant();
    windows.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now));
    Window w = windows.get(key);
    if (w == null) {
      if (windows.size() >= 10000) return false;
      windows.put(key, new Window(now.plusSeconds(900), 1));
      return true;
    }
    if (w.count() >= limit) return false;
    windows.put(key, new Window(w.expires(), w.count() + 1));
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/admin")) {
      res.setHeader("X-Robots-Tag", "noindex");
      res.setHeader("Cache-Control", "no-store");
    }
    if (req.getMethod().equals("POST") && req.getRequestURI().equals("/admin/login")) {
      String username =
          Objects.toString(req.getParameter("username"), "").strip().toLowerCase(Locale.ROOT);
      boolean ipAllowed = allow("ip:" + req.getRemoteAddr(), 30);
      boolean userAllowed =
          allow("user:" + username.substring(0, Math.min(username.length(), 100)), 10);
      if (!ipAllowed || !userAllowed) {
        res.setStatus(429);
        res.setHeader("Retry-After", "900");
        res.setContentType("text/plain;charset=UTF-8");
        res.getWriter().write("Too many login attempts. Please try again in 15 minutes.");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
