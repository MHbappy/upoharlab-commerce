package com.upoharlab.order;

import com.upoharlab.order.AdminOrderService.RuleException;
import com.upoharlab.order.OrderBoard.Filter;
import com.upoharlab.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** The dashboard, order list, bulk work, exports and owner-entered orders. */
@Controller
@RequestMapping("/admin")
public class AdminOrderBoardController {
  /** The typed search stays in the session, so a phone number never appears in a URL. */
  private static final String SEARCH = "orderSearch";

  static final String LIST_QUERY = "orderListQuery";
  private static final int MAX_BULK = 100;

  private final OrderBoard board;
  private final AdminOrderService orders;
  private final ManualOrderService manual;
  private final SettingsService settings;
  private final Clock clock;

  public AdminOrderBoardController(
      OrderBoard board,
      AdminOrderService orders,
      ManualOrderService manual,
      SettingsService settings,
      Clock clock) {
    this.board = board;
    this.orders = orders;
    this.manual = manual;
    this.settings = settings;
    this.clock = clock;
  }

  @GetMapping({"", "/"})
  String dashboard(Model m) {
    m.addAttribute("stages", board.stageCounts());
    m.addAttribute("statuses", OrderStatus.values());
    m.addAttribute("queues", board.queueCounts());
    m.addAttribute("money", board.money());
    m.addAttribute("recent", board.recent(10));
    return "admin/dashboard";
  }

  @GetMapping("/orders")
  String list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String queue,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") String page,
      @RequestParam(required = false) String search,
      HttpServletRequest request,
      HttpSession session,
      Model m) {
    session.setAttribute(LIST_QUERY, request.getQueryString());
    boolean searched = "1".equals(search);
    String q = searched ? (String) session.getAttribute(SEARCH) : null;
    Filter filter = new Filter(status, queue, q, from, to, sort, page(page));
    OrderBoard.Results results = board.search(filter);
    m.addAttribute("results", results);
    m.addAttribute("q", q);
    m.addAttribute("selectedStatus", status);
    m.addAttribute("selectedQueue", queue);
    m.addAttribute("from", from);
    m.addAttribute("to", to);
    m.addAttribute("sort", sort);
    m.addAttribute("statuses", OrderStatus.values());
    m.addAttribute("queues", OrderBoard.Queue.values());
    m.addAttribute("stages", board.stageCounts());
    m.addAttribute(
        "searching",
        (q != null && !q.isBlank())
            || filled(status) || filled(queue) || filled(from) || filled(to));
    m.addAttribute("prevUrl", url(status, queue, from, to, sort, searched, results.page() - 1));
    m.addAttribute("nextUrl", url(status, queue, from, to, sort, searched, results.page() + 1));
    m.addAttribute(
        "exportUrl",
        UriComponentsBuilder.fromUriString(url(status, queue, from, to, sort, searched, 0))
            .replacePath("/admin/orders/export.csv")
            .build()
            .toUriString());
    return "admin/orders";
  }

  /** Redirects to a plain page, so the browser's Back button returns here without a warning. */
  @PostMapping("/orders/search")
  String search(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String queue,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") String page,
      HttpSession session) {
    boolean searching = filled(q);
    if (searching) session.setAttribute(SEARCH, q.strip());
    else session.removeAttribute(SEARCH);
    return "redirect:" + url(status, queue, from, to, sort, searching, page(page));
  }

  @GetMapping("/orders/export.csv")
  ResponseEntity<String> export(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String queue,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String search,
      HttpSession session) {
    String q = "1".equals(search) ? (String) session.getAttribute(SEARCH) : null;
    String csv =
        board.export(new Filter(status, queue, q, from, to, sort, 0)).stream()
            .map(row -> row.stream().map(AdminOrderBoardController::cell).collect(Collectors.joining(",")))
            .collect(Collectors.joining("\r\n"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("orders-" + LocalDate.now(clock) + ".csv")
                .build()
                .toString())
        .body("﻿" + csv + "\r\n"); // The mark makes Excel read Bangla correctly.
  }

  /** Quotes a value, and stops a spreadsheet from running customer text as a formula. */
  static String cell(String value) {
    String safe = value == null ? "" : value;
    if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
    return '"' + safe.replace("\"", "\"\"") + '"';
  }

  /** Each order follows the same rules as its own page; one refusal does not stop the others. */
  @PostMapping("/orders/bulk")
  String bulk(
      @RequestParam(required = false) List<String> codes,
      @RequestParam(required = false) String action,
      Principal admin,
      HttpSession session,
      RedirectAttributes flash) {
    String back = backToList(session);
    if (codes == null || codes.isEmpty()) {
      flash.addFlashAttribute("error", "Tick at least one order first.");
      return "redirect:" + back;
    }
    if (codes.size() > MAX_BULK) {
      flash.addFlashAttribute("error", "Choose up to " + MAX_BULK + " orders at a time.");
      return "redirect:" + back;
    }
    int done = 0;
    List<String> refused = new ArrayList<>();
    for (String code : codes) {
      try {
        switch (String.valueOf(action)) {
          case "START_MAKING" -> orders.changeStatus(code, "MAKING", null, null, null, admin.getName());
          case "MARK_DELIVERED" -> orders.changeStatus(code, "DELIVERED", null, null, null, admin.getName());
          case "COLLECT_BALANCE" -> orders.collectBalance(code, admin.getName());
          default -> throw new RuleException("Choose an action.");
        }
        done++;
      } catch (RuleException ex) {
        refused.add(code + ": " + ex.getMessage());
      } catch (org.springframework.web.server.ResponseStatusException ex) {
        refused.add(code + ": not found.");
      }
    }
    if (done > 0) flash.addFlashAttribute("message", done + (done == 1 ? " order updated." : " orders updated."));
    if (!refused.isEmpty()) flash.addFlashAttribute("error", String.join(" ", refused));
    return "redirect:" + back;
  }

  /** POST, because the ticked list can be long and shows who ordered what when printed. */
  @PostMapping("/orders/slips")
  String slips(@RequestParam(required = false) List<String> codes, HttpSession session, RedirectAttributes flash, Model m) {
    if (codes == null || codes.isEmpty() || codes.size() > MAX_BULK) {
      flash.addFlashAttribute("error", "Tick between 1 and " + MAX_BULK + " orders to print.");
      return "redirect:" + backToList(session);
    }
    return slipPage(codes, m);
  }

  @GetMapping("/orders/{code}/slip")
  String slip(@PathVariable String code, Model m) {
    return slipPage(List.of(code), m);
  }

  private String slipPage(List<String> codes, Model m) {
    m.addAttribute("slips", codes.stream().map(orders::detail).toList());
    m.addAttribute("shopPhone", settings.all().getOrDefault("phone", ""));
    return "admin/slips";
  }

  @GetMapping("/orders/new")
  String newOrder(Model m) {
    m.addAttribute("form", new ManualOrderService.Form());
    m.addAttribute("products", manual.products());
    return "admin/order-new";
  }

  @PostMapping("/orders/new")
  String createOrder(
      @ModelAttribute("form") ManualOrderService.Form form,
      Principal admin,
      RedirectAttributes flash,
      Model m) {
    try {
      String code = manual.create(form, admin.getName());
      flash.addFlashAttribute("message", "Order added.");
      return "redirect:/admin/orders/" + code;
    } catch (RuleException ex) {
      m.addAttribute("error", ex.getMessage());
      m.addAttribute("products", manual.products());
      return "admin/order-new";
    }
  }

  static String backToList(HttpSession session) {
    Object query = session.getAttribute(LIST_QUERY);
    return "/admin/orders" + (query == null ? "" : "?" + query);
  }

  private static boolean filled(String value) {
    return value != null && !value.isBlank();
  }

  private static int page(String raw) {
    try {
      return Math.max(0, Integer.parseInt(raw));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private static String url(
      String status, String queue, String from, String to, String sort, boolean search, int page) {
    UriComponentsBuilder url = UriComponentsBuilder.fromPath("/admin/orders");
    if (filled(status)) url.queryParam("status", status);
    if (filled(queue)) url.queryParam("queue", queue);
    if (filled(from)) url.queryParam("from", from);
    if (filled(to)) url.queryParam("to", to);
    if (filled(sort)) url.queryParam("sort", sort);
    if (search) url.queryParam("search", "1");
    if (page > 0) url.queryParam("page", page);
    return url.encode().build().toUriString();
  }
}
