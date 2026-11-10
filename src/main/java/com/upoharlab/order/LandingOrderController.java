package com.upoharlab.order;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfigCodec;
import com.upoharlab.landing.LandingPricing;
import com.upoharlab.order.LandingOrderValidator.Input;
import com.upoharlab.order.OrderService.*;
import com.upoharlab.product.ProductStore;
import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The one-page order site at /order/{slug}, rendered for any product from its configuration. */
@Controller
public class LandingOrderController {
  // Notices are site-text keys, so the owner can reword them in Admin → Site text.
  static final String REVIEW_MESSAGE = "order.msg.review";
  private static final String NO_STORE = "no-store";

  private final ProductStore products;
  private final SettingsService settings;
  private final LandingOrderValidator validator;
  private final LandingOrderService orders;
  private final OrderService legacyOrders;
  private final LandingPageModel pages;
  private final SmartValidator beans;
  private final RateLimiter limiter;
  private final int ordersPerHour;

  public LandingOrderController(
      ProductStore products,
      SettingsService settings,
      LandingOrderValidator validator,
      LandingOrderService orders,
      OrderService legacyOrders,
      LandingPageModel pages,
      SmartValidator beans,
      Clock clock,
      @Value("${app.orders.max-per-hour}") int ordersPerHour) {
    this.products = products;
    this.settings = settings;
    this.validator = validator;
    this.orders = orders;
    this.legacyOrders = legacyOrders;
    this.pages = pages;
    this.beans = beans;
    this.limiter = new RateLimiter(clock, Duration.ofHours(1), 10000);
    this.ordersPerHour = ordersPerHour;
  }

  private ProductStore.Page product(String slug) {
    ProductStore.Page product = products.bySlug(slug);
    if (!product.landingEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return product;
  }

  @GetMapping("/order/{slug}")
  public String page(
      @PathVariable String slug,
      @RequestParam(required = false) String expired,
      HttpSession session,
      HttpServletResponse res,
      Model m) {
    res.setHeader("Cache-Control", NO_STORE);
    var product = product(slug);
    var token = OrderSession.of(session).issue(product.id());
    return render(
        m,
        product,
        pages.defaults(product.orderLanding()),
        token,
        false,
        expired == null ? null : OrderController.EXPIRED_MESSAGE);
  }

  @PostMapping("/order/{slug}")
  public String order(
      @PathVariable String slug,
      @ModelAttribute("orderForm") LandingOrderForm form,
      BindingResult errors,
      @RequestParam(required = false) String action,
      HttpSession session,
      HttpServletRequest req,
      HttpServletResponse res,
      Model m) {
    res.setHeader("Cache-Control", NO_STORE);
    var product = product(slug);
    LandingConfig config = product.orderLanding();
    form.setOptions(chosenOptions(req, config));
    OrderSession access = OrderSession.of(session);
    var token = access.find(form.getSubmitToken(), product.id());
    boolean unknownToken = token == null;
    if (unknownToken) token = access.issue(product.id());
    if (!"submit".equals(action)) {
      if (token.orderCode != null) token = access.issue(product.id());
      return render(m, product, form, token, true, null);
    }
    // One click at a time per form, so repeat clicks find the first click's order.
    synchronized (token) {
      if (token.orderCode != null) return confirmation(token.orderCode);
      if (form.getWebsite() != null && !form.getWebsite().isEmpty())
        return render(m, product, form, token, true, "order.msg.blocked");
      beans.validate(form, errors);
      Input input = validator.validate(form, config, errors);
      if (!pages.orderable(product, settings.all()) || errors.hasErrors())
        return render(m, product, form, token, true, null);
      if (unknownToken || !sawCurrentTotal(token, config, input, form))
        return render(m, product, form, token, true, REVIEW_MESSAGE);
      if (!limiter.allow("ip:" + req.getRemoteAddr(), ordersPerHour)
          || !limiter.allow("phone:" + input.phone(), ordersPerHour)) {
        res.setStatus(429);
        return render(
            m, product, form, token, true, "order.msg.busy");
      }
      PlaceResult result;
      try {
        result = orders.place(product.id(), input, token.value, token.shownFingerprint);
      } catch (DuplicateKeyException ex) {
        // The token was issued to this session, so its first order belongs to this customer.
        Optional<String> existing = legacyOrders.codeForSubmitToken(token.value);
        if (existing.isEmpty()) throw ex;
        result = new Placed(existing.get());
      }
      return switch (result) {
        case Placed placed -> {
          token.orderCode = placed.code();
          access.allowViewing(placed.code());
          yield confirmation(placed.code());
        }
        case PricesChanged changed -> render(m, product(slug), form, token, true, REVIEW_MESSAGE);
        case Closed closed -> render(m, products.bySlug(slug), form, token, true, null);
      };
    }
  }

  /** One radio group per option. Bounded by the configuration, whatever the request contains. */
  private static List<String> chosenOptions(HttpServletRequest req, LandingConfig config) {
    List<String> chosen = new ArrayList<>();
    for (int i = 0; i < config.options().size(); i++) chosen.add(req.getParameter("opt" + i));
    return chosen;
  }

  /** The total on screen must match current saved prices and the customer's choices. */
  private boolean sawCurrentTotal(
      OrderSession.Token token, LandingConfig config, Input input, LandingOrderForm form) {
    if (!LandingConfigCodec.fingerprint(config).equals(token.shownFingerprint)) return false;
    int total = LandingPricing.quote(config, input.selection()).total();
    return Integer.toString(total).equals(form.getShownTotal());
  }

  private String render(
      Model m,
      ProductStore.Page product,
      LandingOrderForm form,
      OrderSession.Token token,
      boolean submitted,
      String message) {
    if (product.landing() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    pages.fill(m, product, form, token.value, submitted, false, message);
    token.shownFingerprint = LandingConfigCodec.fingerprint(product.orderLanding());
    return "landing/page";
  }

  private String confirmation(String code) {
    return "redirect:/order/" + code + "/success";
  }
}
