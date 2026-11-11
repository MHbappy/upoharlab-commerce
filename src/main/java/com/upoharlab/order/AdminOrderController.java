package com.upoharlab.order;

import com.upoharlab.text.SiteTexts;
import com.upoharlab.order.AdminOrderService.Detail;
import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.BnDigits;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import com.upoharlab.landing.PreviewSvgRenderer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/admin")
public class AdminOrderController {
  private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml;charset=UTF-8");

  private final AdminOrderService orders;
  private final SettingsService settings;
  private final LandingOrderService landingOrders;
  private final PreviewSvgRenderer svg;

  private final SiteTexts texts;
  private final OrderBoard board;

  public AdminOrderController(
      AdminOrderService orders,
      SettingsService settings,
      LandingOrderService landingOrders,
      PreviewSvgRenderer svg,
      SiteTexts texts,
      OrderBoard board) {
    this.texts = texts;
    this.board = board;
    this.orders = orders;
    this.settings = settings;
    this.landingOrders = landingOrders;
    this.svg = svg;
  }

  /** Rebuilt from saved parameters on every request. The download holds only the text layer. */
  @GetMapping("/orders/{code}/preview.svg")
  ResponseEntity<String> preview(
      @PathVariable String code, @RequestParam(defaultValue = "false") boolean download)
      throws java.io.IOException {
    var selection =
        landingOrders
            .selection(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var response = ResponseEntity.ok().contentType(SVG).cacheControl(CacheControl.noStore());
    if (download)
      response.header(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment().filename(code + ".svg").build().toString());
    return response.body(svg.render(selection, !download));
  }

  @GetMapping("/orders/{code}")
  String detail(@PathVariable String code, HttpSession session, Model m) {
    Detail order = orders.detail(code);
    m.addAttribute("order", order);
    m.addAttribute("backToList", AdminOrderBoardController.backToList(session));
    m.addAttribute("customerHistory", board.customerHistory(order.phone(), order.code()));
    m.addAttribute("selection", landingOrders.selection(code).orElse(null));
    m.addAttribute("neededBy", landingOrders.neededBy(code).orElse(null));
    m.addAttribute("whatsappLinks", whatsappLinks(order, settings.all()));
    return "admin/order-detail";
  }

  private Map<String, String> whatsappLinks(Detail order, Map<String, String> values) {
    String base = "https://wa.me/88" + order.phone();
    Map<String, String> links = new LinkedHashMap<>();
    links.put("Open chat", base);
    OrderStatus status = OrderStatus.valueOf(order.status());
    if (order.needsDesign() && (status == OrderStatus.NEW || status == OrderStatus.DESIGN_SENT))
      links.put(
          "Design message",
          withText(
              base,
              texts.text("whatsapp.design", order.code())
                  + (order.advanceRequired() > 0
                      ? " " + texts.text("whatsapp.design.advance", BnDigits.money(order.advanceRequired()))
                      : "")));
    // Payment details go out only after approval, so nobody is asked to pay for an unseen design.
    // Orders without a design are asked for the advance while still NEW.
    boolean advanceTime =
        order.needsDesign()
            ? status == OrderStatus.DESIGN_SENT && order.designApprovedAt() != null
            : status == OrderStatus.NEW;
    if (advanceTime && order.netPaid() < order.advanceRequired()) {
      String bkash = values.getOrDefault("bkash_number", "");
      String due = BnDigits.money(Math.toIntExact(order.advanceRequired() - order.netPaid()));
      String key = (order.needsDesign() ? "whatsapp.advance" : "whatsapp.advance.plain")
          + (bkash.isBlank() ? "" : ".bkash");
      links.put(
          "Advance message",
          withText(base, bkash.isBlank()
              ? texts.text(key, order.code(), due)
              : texts.text(key, order.code(), due, bkash)));
      if (bkash.isBlank()) links.put("Missing bKash", base);
    }
    if (order.courier() != null && (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED))
      links.put(
          "Shipping message",
          withText(
              base,
              texts.text("whatsapp.shipped", order.code(), order.courier(),
                  order.trackingNo() == null ? "" : order.trackingNo())));
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

  @PostMapping("/orders/{code}/advance/waive")
  String waiveAdvance(
      @PathVariable String code,
      @RequestParam(required = false) String reason,
      Principal admin,
      RedirectAttributes flash) {
    return act(code, flash, "Advance removed. You can continue.", () -> orders.waiveAdvance(code, reason, admin.getName()));
  }

  @PostMapping("/orders/{code}/undo")
  String undo(
      @PathVariable String code,
      @RequestParam(required = false) String reason,
      Principal admin,
      RedirectAttributes flash) {
    return act(code, flash, "Last step undone.", () -> orders.undoLastStatus(code, reason, admin.getName()));
  }

  @PostMapping("/orders/{code}/money/{payment}/void")
  String voidMoney(
      @PathVariable String code,
      @PathVariable long payment,
      @RequestParam(required = false) String reason,
      Principal admin,
      RedirectAttributes flash) {
    return act(code, flash, "Record voided.", () -> orders.voidMoney(code, payment, reason, admin.getName()));
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
