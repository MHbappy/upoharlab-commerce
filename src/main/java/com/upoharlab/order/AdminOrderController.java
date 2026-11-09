package com.upoharlab.order;

import com.upoharlab.order.AdminOrderService.Detail;
import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/admin")
public class AdminOrderController {
  private final AdminOrderService orders;
  private final SettingsService settings;

  public AdminOrderController(AdminOrderService orders, SettingsService settings) {
    this.orders = orders;
    this.settings = settings;
  }

  @GetMapping({"", "/"})
  String dashboard(Model m) {
    m.addAttribute("counts", orders.counts());
    m.addAttribute("recent", orders.recent(10));
    return "admin/dashboard";
  }

  @GetMapping("/orders")
  String list(@RequestParam(required = false) String status, Model m) {
    return results(status, null, 0, m);
  }

  /** POST, so a searched phone number never appears in a URL, including across pages. */
  @PostMapping("/orders/search")
  String search(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") String page,
      Model m) {
    int requested;
    try {
      requested = Integer.parseInt(page);
    } catch (NumberFormatException ex) {
      requested = 0;
    }
    return results(status, q, requested, m);
  }

  private String results(String status, String q, int page, Model m) {
    m.addAttribute("results", orders.search(status, q, page));
    m.addAttribute("selectedStatus", status);
    m.addAttribute("q", q);
    m.addAttribute("statuses", OrderStatus.values());
    m.addAttribute("searching", (q != null && !q.isBlank()) || (status != null && !status.isBlank()));
    return "admin/orders";
  }

  @GetMapping("/orders/{code}")
  String detail(@PathVariable String code, Model m) {
    Detail order = orders.detail(code);
    m.addAttribute("order", order);
    m.addAttribute("whatsappLinks", whatsappLinks(order, settings.all()));
    return "admin/order-detail";
  }

  private Map<String, String> whatsappLinks(Detail order, Map<String, String> values) {
    String base = "https://wa.me/88" + order.phone();
    Map<String, String> links = new LinkedHashMap<>();
    links.put("Open chat", base);
    OrderStatus status = OrderStatus.valueOf(order.status());
    if (status == OrderStatus.NEW || status == OrderStatus.DESIGN_SENT)
      links.put(
          "Design message",
          withText(
              base,
              "আপনার অর্ডার " + order.code() + "-এর ডিজাইন পাঠালাম। ডিজাইন ও বানান দেখে সম্মতি জানান।"));
    if (order.courier() != null && status != OrderStatus.CANCELLED)
      links.put(
          "Shipping message",
          withText(
              base,
              "আপনার অর্ডার "
                  + order.code()
                  + " পাঠানো হয়েছে। কুরিয়ার: "
                  + order.courier()
                  + ", ট্র্যাকিং: "
                  + order.trackingNo()));
    return links;
  }

  static String withText(String base, String text) {
    return base + "?text=" + UriUtils.encodeQueryParam(text, StandardCharsets.UTF_8);
  }

  @PostMapping("/orders/{code}/status")
  String status(
      @PathVariable String code,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String reason,
      @RequestParam(required = false) String courier,
      @RequestParam(required = false) String tracking,
      Principal admin,
      RedirectAttributes flash) {
    return act(
        code,
        flash,
        "Order updated.",
        () -> orders.changeStatus(code, to, reason, courier, tracking, admin.getName()));
  }

  @PostMapping("/orders/{code}/approve")
  String approve(@PathVariable String code, Principal admin, RedirectAttributes flash) {
    return act(
        code, flash, "Approval recorded.", () -> orders.approveDesign(code, admin.getName()));
  }

  @PostMapping("/orders/{code}/money")
  String money(
      @PathVariable String code,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String method,
      @RequestParam(required = false) String amount,
      @RequestParam(required = false) String reference,
      Principal admin,
      RedirectAttributes flash) {
    String target = "redirect:/admin/orders/" + code;
    try {
      orders.recordMoney(code, kind, method, amount, reference, admin.getName());
      flash.addFlashAttribute("message", "Saved. The balance below is updated.");
    } catch (RuleException ex) {
      keepMoneyForm(flash, kind, method, amount, reference);
      flash.addFlashAttribute("error", ex.getMessage());
    } catch (DuplicateKeyException ex) {
      keepMoneyForm(flash, kind, method, amount, reference);
      flash.addFlashAttribute("error", "This transaction reference is already recorded.");
    }
    return target;
  }

  private void keepMoneyForm(
      RedirectAttributes flash, String kind, String method, String amount, String reference) {
    flash.addFlashAttribute("moneyKind", kind);
    flash.addFlashAttribute("moneyMethod", method);
    flash.addFlashAttribute("moneyAmount", amount);
    flash.addFlashAttribute("moneyReference", reference);
  }

  @PostMapping("/orders/{code}/engraving")
  String engraving(@PathVariable String code, @RequestParam String engraving,
      Principal admin, RedirectAttributes flash) {
    return act(code, flash, "Engraving recorded.",
        () -> orders.completeEngraving(code, engraving, admin.getName()));
  }

  @PostMapping("/orders/{code}/contact")
  String contact(
      @PathVariable String code,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String address,
      Principal admin,
      RedirectAttributes flash) {
    try {
      orders.updateContact(code, name, phone, address, admin.getName());
      flash.addFlashAttribute("message", "Contact details corrected.");
    } catch (RuleException ex) {
      flash.addFlashAttribute("contactName", name);
      flash.addFlashAttribute("contactPhone", phone);
      flash.addFlashAttribute("contactAddress", address);
      flash.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/admin/orders/" + code;
  }

  @PostMapping("/orders/{code}/courier")
  String courier(
      @PathVariable String code,
      @RequestParam(required = false) String courier,
      @RequestParam(required = false) String tracking,
      Principal admin,
      RedirectAttributes flash) {
    return act(
        code,
        flash,
        "Courier details corrected.",
        () -> orders.updateCourier(code, courier, tracking, admin.getName()));
  }

  @PostMapping("/orders/{code}/note")
  String note(
      @PathVariable String code,
      @RequestParam(required = false) String note,
      Principal admin,
      RedirectAttributes flash) {
    return act(code, flash, "Note added.", () -> orders.addNote(code, note, admin.getName()));
  }

  private String act(String code, RedirectAttributes flash, String done, Runnable action) {
    try {
      action.run();
      flash.addFlashAttribute("message", done);
    } catch (RuleException ex) {
      flash.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/admin/orders/" + code;
  }
}
