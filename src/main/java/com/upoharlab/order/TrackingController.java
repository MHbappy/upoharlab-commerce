package com.upoharlab.order;

import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.RateLimiter;
import com.upoharlab.text.SiteTexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TrackingController {
  /** Site-text keys for what customers see at each step. */
  private static final Map<String, String> LABELS =
      Map.of(
          "NEW", "track.status.new",
          "DESIGN_SENT", "track.status.design.sent",
          "CONFIRMED", "track.status.confirmed",
          "MAKING", "track.status.making",
          "SHIPPED", "track.status.shipped",
          "DELIVERED", "track.status.delivered",
          "CANCELLED", "track.status.cancelled");

  private static final String APPROVED = "track.status.approved.paid";
  private static final String APPROVED_AWAITING_ADVANCE = "track.status.approved.waiting";

  private final SiteTexts texts;

  private final TrackingService tracking;
  private final SettingsService settings;
  private final RateLimiter attemptsByIp;
  private final RateLimiter failuresByCode;
  private final int maxPerIp;
  private final int maxFailuresPerCode;

  public TrackingController(
      TrackingService tracking,
      SettingsService settings,
      SiteTexts texts,
      Clock clock,
      @Value("${app.tracking.max-per-ip}") int maxPerIp,
      @Value("${app.tracking.max-failures-per-code}") int maxFailuresPerCode) {
    this.tracking = tracking;
    this.texts = texts;
    this.settings = settings;
    this.attemptsByIp = new RateLimiter(clock, Duration.ofMinutes(15), 10000);
    this.failuresByCode = new RateLimiter(clock, Duration.ofHours(1), 10000);
    this.maxPerIp = maxPerIp;
    this.maxFailuresPerCode = maxFailuresPerCode;
  }

  @GetMapping("/track")
  String form(HttpServletResponse res, Model m) {
    privateResponse(res);
    contact(m);
    return "site/track";
  }

  /** POST, so the order code and phone number never appear in a URL. */
  @PostMapping("/track")
  String lookup(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String phone,
      HttpServletRequest req,
      HttpServletResponse res,
      Model m) {
    privateResponse(res);
    contact(m);
    m.addAttribute("code", code);
    m.addAttribute("phone", phone);
    String normalized = TrackingService.normalizeCode(code);
    boolean blocked =
        !attemptsByIp.allow("ip:" + req.getRemoteAddr(), maxPerIp)
            || (normalized != null && failuresByCode.isBlocked(normalized, maxFailuresPerCode));
    if (blocked) {
      res.setStatus(429);
      m.addAttribute("rateLimited", true);
      return "site/track";
    }
    Optional<TrackingService.Status> found =
        normalized == null ? Optional.empty() : tracking.find(normalized, phone);
    if (found.isEmpty()) {
      if (normalized != null) failuresByCode.allow(normalized, Integer.MAX_VALUE);
      m.addAttribute("notFound", true);
      return "site/track";
    }
    m.addAttribute("result", found.get());
    m.addAttribute("statusLabel", label(found.get()));
    return "site/track";
  }

  /** Approval is recorded separately; the owner confirms the order before production. */
  private String label(TrackingService.Status order) {
    if ("NEW".equals(order.status()) && !order.needsDesign())
      return texts.text("track.status.new.plain");
    if (!"DESIGN_SENT".equals(order.status()) || order.approvedAt() == null)
      return texts.text(LABELS.get(order.status()));
    return texts.text(order.advanceDue() ? APPROVED_AWAITING_ADVANCE : APPROVED);
  }

  private void contact(Model m) {
    Map<String, String> values = settings.all();
    m.addAttribute("whatsapp", settings.whatsapp(values));
    m.addAttribute("businessPhone", values.getOrDefault("phone", ""));
  }

  private void privateResponse(HttpServletResponse res) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Robots-Tag", "noindex");
  }
}
