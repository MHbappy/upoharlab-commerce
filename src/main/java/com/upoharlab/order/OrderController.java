package com.upoharlab.order;

import com.upoharlab.product.ProductStore;
import com.upoharlab.settings.SettingsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The thank-you page, and redirects that keep links to the former product pages working. */
@Controller
public class OrderController {
  static final String EXPIRED_MESSAGE = "order.msg.expired";

  private final ProductStore products;
  private final SettingsService settings;
  private final OrderService orders;
  private final LandingOrderService landingOrders;

  public OrderController(
      ProductStore products,
      SettingsService settings, OrderService orders, LandingOrderService landingOrders) {
    this.products = products;
    this.settings = settings;
    this.orders = orders;
    this.landingOrders = landingOrders;
  }

  @RequestMapping({"/p/{slug}", "/p/{slug}/order"})
  public String formerProductPage(@PathVariable String slug) {
    if (!products.bySlug(slug).landingEnabled())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return "redirect:/order/{slug}";
  }

  @GetMapping("/order/{code}/success")
  public String success(
      @PathVariable String code, HttpSession session, HttpServletResponse res, Model m) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Robots-Tag", "noindex");
    Map<String, String> values = settings.all();
    String whatsapp = settings.whatsapp(values);
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", whatsapp);
    // The session is checked first, so a guessed code cannot reveal whether an order exists.
    var saved = OrderSession.of(session).canView(code) ? orders.saved(code) : Optional.<OrderService.Saved>empty();
    var thanks = saved.isPresent() ? landingOrders.thanks(code) : Optional.<LandingOrderService.Thanks>empty();
    if (thanks.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return "site/order-unavailable";
    }
    m.addAttribute("order", saved.get());
    m.addAttribute("thanks", thanks.get());
    if (whatsapp != null)
      m.addAttribute(
          "whatsappWithCode", whatsapp + "?text=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
    return "site/order-thanks";
  }
}
