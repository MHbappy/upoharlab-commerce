package com.upoharlab.config;

import com.upoharlab.shared.RateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimit extends OncePerRequestFilter {
  private static final int PER_IP = 30;
  private static final int PER_IP_AND_USERNAME = 10;
  private final RateLimiter limiter;

  public LoginRateLimit(Clock clock) {
    limiter = new RateLimiter(clock, Duration.ofMinutes(15), 10000);
  }

  /**
   * The IP is checked first, so a blocked IP adds no further entries. Username is only counted with
   * the IP, so nobody can lock the owner out from elsewhere by guessing against their username.
   */
  boolean allow(String ip, String username) {
    String name = Objects.toString(username, "").strip().toLowerCase(Locale.ROOT);
    name = name.substring(0, Math.min(name.length(), 100));
    return limiter.allow("ip:" + ip, PER_IP)
        && limiter.allow("user:" + ip + "|" + name, PER_IP_AND_USERNAME);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/admin")) {
      res.setHeader("X-Robots-Tag", "noindex");
      res.setHeader("Cache-Control", "no-store");
    }
    if (req.getMethod().equals("POST")
        && req.getRequestURI().equals("/admin/login")
        && !allow(req.getRemoteAddr(), req.getParameter("username"))) {
      res.setStatus(429);
      res.setHeader("Retry-After", "900");
      res.setContentType("text/plain;charset=UTF-8");
      res.getWriter().write("Too many login attempts. Please try again in 15 minutes.");
      return;
    }
    chain.doFilter(req, res);
  }
}
