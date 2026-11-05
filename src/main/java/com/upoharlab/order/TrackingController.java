package com.upoharlab.order;

import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.RateLimiter;
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
  private static final Map<String, String> LABELS =
      Map.of(
          "NEW", "অর্ডার পেয়েছি। ডিজাইন পাঠানো হবে।",
          "DESIGN_SENT", "ডিজাইন পাঠানো হয়েছে। আপনার সম্মতির অপেক্ষায়।",
          "CONFIRMED", "অর্ডার নিশ্চিত হয়েছে। বানানো শুরু হবে।",
          "MAKING", "বানানো হচ্ছে।",
          "SHIPPED", "কুরিয়ারে পাঠানো হয়েছে।",
          "DELIVERED", "পৌঁছে গেছে।",
          "CANCELLED", "অর্ডারটি বাতিল হয়েছে।");

  private final TrackingService tracking;
  private final SettingsService settings;
  private final RateLimiter attemptsByIp;
  private final RateLimiter failuresByCode;
  private final int maxPerIp;
  private final int maxFailuresPerCode;

  public TrackingController(
      TrackingService tracking,
      SettingsService settings,
      Clock clock,
      @Value("${app.tracking.max-per-ip}") int maxPerIp,
      @Value("${app.tracking.max-failures-per-code}") int maxFailuresPerCode) {
    this.tracking = tracking;
    this.settings = settings;
    this.attemptsByIp = new RateLimiter(clock, Duration.ofMinutes(15), 10000);
    this.failuresByCode = new RateLimiter(clock, Duration.ofHours(1), 10000);
    this.maxPerIp = maxPerIp;
    this.maxFailuresPerCode = maxFailuresPerCode;
  }

  @GetMapping("/track")
  String form(HttpServletResponse res, Model m) {
    privateResponse(res);
    m.addAttribute("whatsapp", settings.whatsapp(settings.all()));
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
    m.addAttribute("whatsapp", settings.whatsapp(settings.all()));
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
    m.addAttribute("statusLabel", LABELS.get(found.get().status()));
    return "site/track";
  }

  private void privateResponse(HttpServletResponse res) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Robots-Tag", "noindex");
  }
}
