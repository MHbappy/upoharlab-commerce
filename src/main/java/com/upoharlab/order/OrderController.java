package com.upoharlab.order;

import com.upoharlab.order.OrderService.*;
import com.upoharlab.order.OrderValidator.OrderInput;
import com.upoharlab.order.PricingService.Prices;
import com.upoharlab.order.PricingService.Quote;
import com.upoharlab.product.ProductStore;
import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class OrderController {
  static final String REVIEW_MESSAGE = "মোট হিসাব দেখে আবার অর্ডার পাঠান।";
  private static final String NO_STORE = "no-store";

  private final ProductStore products;
  private final SettingsService settings;
  private final PricingService pricing;
  private final OrderValidator validator;
  private final OrderService orders;
  private final RateLimiter limiter;
  private final int ordersPerHour;

  public OrderController(
      ProductStore products,
      SettingsService settings,
      PricingService pricing,
      OrderValidator validator,
      OrderService orders,
      Clock clock,
      @Value("${app.orders.max-per-hour}") int ordersPerHour) {
    this.products = products;
    this.settings = settings;
    this.pricing = pricing;
    this.validator = validator;
    this.orders = orders;
    this.limiter = new RateLimiter(clock, Duration.ofHours(1), 10000);
    this.ordersPerHour = ordersPerHour;
  }

  @GetMapping("/p/{slug}")
  public String product(
      @PathVariable String slug, HttpSession session, HttpServletResponse res, Model m) {
    res.setHeader("Cache-Control", NO_STORE);
    var product = products.bySlug(slug);
    var token = OrderSession.of(session).issue(product.id());
    return page(m, product, new OrderForm(), token, false, null);
  }

  @PostMapping("/p/{slug}/order")
  public String order(
      @PathVariable String slug,
      @ModelAttribute("orderForm") OrderForm form,
      BindingResult errors,
      @RequestParam(required = false) String action,
      HttpSession session,
      HttpServletRequest req,
      HttpServletResponse res,
      Model m) {
    res.setHeader("Cache-Control", NO_STORE);
    var product = products.bySlug(slug);
    OrderSession access = OrderSession.of(session);
    var token = access.find(form.getSubmitToken(), product.id());
    boolean unknownToken = token == null;
    if (unknownToken) token = access.issue(product.id());
    if (!"submit".equals(action)) {
      if (token.orderCode != null) token = access.issue(product.id());
      validator.validateQuote(form, errors, false);
      return page(m, product, form, token, true, null);
    }
    // One click at a time per form, so repeat clicks find the first click's order.
    synchronized (token) {
      if (token.orderCode != null) return confirmation(token.orderCode);
      if (form.getWebsite() != null && !form.getWebsite().isEmpty())
        return page(m, product, form, token, true, "অর্ডারটি পাঠানো যায়নি। WhatsApp-এ যোগাযোগ করুন।");
      OrderInput input = validator.validateOrder(form, errors);
      Map<String, String> values = settings.all();
      Optional<Prices> prices = pricing.prices(product.details().getPrice(), values);
      if (!orderable(product, values) || prices.isEmpty())
        return page(m, product, form, token, true, null);
      if (errors.hasErrors()) return page(m, product, form, token, true, null);
      if (unknownToken || !sawCurrentTotal(token, prices.get(), input, form))
        return page(m, product, form, token, true, REVIEW_MESSAGE);
      if (!limiter.allow("order:" + req.getRemoteAddr(), ordersPerHour)) {
        res.setStatus(429);
        return page(
            m,
            product,
            form,
            token,
            true,
            "এই মুহূর্তে অনেক অর্ডার এসেছে। একটু পরে আবার চেষ্টা করুন, অথবা WhatsApp-এ জানান।");
      }
      PlaceResult result;
      try {
        result = orders.place(product.id(), input, token.value, token.shownPrices);
      } catch (DuplicateKeyException ex) {
        // The token was issued to this session, so its first order belongs to this customer.
        Optional<String> existing = orders.codeForSubmitToken(token.value);
        if (existing.isEmpty()) throw ex;
        result = new Placed(existing.get());
      }
      return switch (result) {
        case Placed placed -> {
          token.orderCode = placed.code();
          access.allowViewing(placed.code());
          yield confirmation(placed.code());
        }
        case PricesChanged changed -> page(m, product, form, token, true, REVIEW_MESSAGE);
        case Closed closed -> page(m, products.bySlug(slug), form, token, true, null);
      };
    }
  }

  @GetMapping("/order/{code}/success")
  public String success(
      @PathVariable String code, HttpSession session, HttpServletResponse res, Model m) {
    res.setHeader("Cache-Control", NO_STORE);
    res.setHeader("X-Robots-Tag", "noindex");
    Map<String, String> values = settings.all();
    String whatsapp = settings.whatsapp(values);
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", whatsapp);
    // The session is checked first, so a guessed code cannot reveal whether an order exists.
    Optional<Saved> saved =
        OrderSession.of(session).canView(code) ? orders.saved(code) : Optional.empty();
    if (saved.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return "site/order-unavailable";
    }
    m.addAttribute("order", saved.get());
    if (whatsapp != null)
      m.addAttribute(
          "whatsappWithCode",
          whatsapp + "?text=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
    return "site/order-success";
  }

  private String confirmation(String code) {
    return "redirect:/order/" + code + "/success";
  }

  private boolean orderable(ProductStore.Page product, Map<String, String> values) {
    return "ACTIVE".equals(product.details().getStatus())
        && "true".equals(values.get("accepting_orders"));
  }

  /** The breakdown on screen must match the current prices and the chosen quantity and area. */
  private boolean sawCurrentTotal(
      OrderSession.Token token, Prices current, OrderInput input, OrderForm form) {
    if (!current.equals(token.shownPrices)) return false;
    Quote quote = pricing.quote(current, input.quantity(), input.area());
    return String.valueOf(quote.total()).equals(form.getShownTotal());
  }

  private String page(
      Model m,
      ProductStore.Page product,
      OrderForm form,
      OrderSession.Token token,
      boolean submitted,
      String message) {
    Map<String, String> values = settings.all();
    Optional<Prices> prices = pricing.prices(product.details().getPrice(), values);
    boolean orderable = orderable(product, values) && prices.isPresent();
    m.addAttribute("product", product);
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", settings.whatsapp(values));
    m.addAttribute("preview", false);
    m.addAttribute("orderable", orderable);
    // After a post, keep the customer's typed work on screen even if ordering has just paused.
    m.addAttribute("showForm", (orderable || submitted) && prices.isPresent());
    m.addAttribute("orderMessage", message);
    m.addAttribute("orderForm", form);
    m.addAttribute("submitToken", token.value);
    if (prices.isPresent()) {
      Quote quote =
          pricing.quote(
              prices.get(), validator.quantity(form.getQuantity()), validator.area(form.getArea()));
      token.shownPrices = prices.get();
      m.addAttribute("prices", prices.get());
      m.addAttribute("quote", quote);
      m.addAttribute("shownTotal", quote.total() == null ? "" : quote.total().toString());
    }
    return "site/product";
  }
}
