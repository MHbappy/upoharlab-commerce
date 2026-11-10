package com.upoharlab.order;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfigCodec;
import com.upoharlab.landing.LandingFonts;
import com.upoharlab.landing.LandingPricing;
import com.upoharlab.landing.LandingPricing.Quote;
import com.upoharlab.landing.LandingPricing.Selection;
import com.upoharlab.product.ProductStore;
import com.upoharlab.settings.SettingsService;
import com.upoharlab.shared.BnDigits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Prepares the one order-page template for any product, for customers and the admin preview. */
@Component
public class LandingPageModel {
  private final SettingsService settings;
  private final LandingOrderValidator validator;

  public LandingPageModel(SettingsService settings, LandingOrderValidator validator) {
    this.settings = settings;
    this.validator = validator;
  }

  /** Starting choices: the popular package, first of everything else, and a blank engraving field. */
  public LandingOrderForm defaults(LandingConfig c) {
    LandingOrderForm form = new LandingOrderForm();
    form.setFont(c.preview().fonts().isEmpty() ? null : c.preview().fonts().get(0));
    int popular = 0;
    for (int i = 0; i < c.packages().size(); i++) if (c.packages().get(i).isPopular()) popular = i;
    form.setPackageIndex(Integer.toString(popular));
    List<String> options = new ArrayList<>();
    c.options().forEach(option -> options.add("0"));
    form.setOptions(options);
    return form;
  }

  /** A range like ১–২, or null until both ends are set in Settings. */
  private static String courierDays(Map<String, String> values, String area) {
    String min = values.get("courier_days_" + area + "_min");
    String max = values.get("courier_days_" + area + "_max");
    if (min == null || min.isBlank() || max == null || max.isBlank()) return null;
    return BnDigits.toBangla(min) + "–" + BnDigits.toBangla(max);
  }

  public boolean orderable(ProductStore.Page product, Map<String, String> values) {
    return "ACTIVE".equals(product.details().getStatus())
        && product.landingEnabled()
        && product.landing().sections().order()
        && "true".equals(values.get("accepting_orders"));
  }

  /** Returns the shown total as text, empty while the delivery area is still unknown. */
  public String fill(
      Model m,
      ProductStore.Page product,
      LandingOrderForm form,
      String submitToken,
      boolean submitted,
      boolean preview,
      String message) {
    LandingConfig c = product.orderLanding();
    Map<String, String> values = settings.all();
    boolean orderable = !preview && orderable(product, values);
    Selection selection = validator.selection(form, c);
    if (selection == null) selection = validator.selection(defaults(c), c);
    Quote quote = usable(c, selection) ? LandingPricing.quote(c, selection) : null;
    String shownTotal = quote == null || quote.total() == null ? "" : quote.total().toString();
    m.addAttribute("product", product);
    m.addAttribute("config", c);
    m.addAttribute("configJson", LandingConfigCodec.forScriptBlock(publicView(c, submitted)));
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", settings.whatsapp(values));
    m.addAttribute("preview", preview);
    m.addAttribute("orderable", orderable);
    m.addAttribute("showForm", orderable || submitted || preview);
    m.addAttribute("orderMessage", message);
    m.addAttribute("reviewNeeded", LandingOrderController.REVIEW_MESSAGE.equals(message));
    m.addAttribute("orderForm", form);
    m.addAttribute("submitToken", submitToken);
    m.addAttribute("quote", quote);
    m.addAttribute("selectedOldPrice", selection != null && quote != null && quote.extras() == 0
        ? c.packages().get(selection.packageIndex()).oldPrice() : null);
    m.addAttribute("selectedPackageName", selection == null ? null : c.packages().get(selection.packageIndex()).name());
    m.addAttribute("shownTotal", shownTotal);
    m.addAttribute("activeDesign", c.sections().preview() ? activeDesign(c, form) : null);
    m.addAttribute("showOrder", c.sections().order());
    m.addAttribute("deliveryInside", courierDays(values, "inside"));
    m.addAttribute("deliveryOutside", courierDays(values, "outside"));
    return shownTotal;
  }

  private static boolean usable(LandingConfig c, Selection s) {
    return s != null
        && c.advanceAmount() != null
        && c.packages().stream().allMatch(tier -> tier.price() != null)
        && c.options().stream()
            .allMatch(o -> o.values().stream().allMatch(v -> v.priceDelta() != null))
        && c.upsells().stream().allMatch(u -> u.price() != null)
        && c.delivery().insideDhakaCharge() != null
        && c.delivery().outsideDhakaCharge() != null;
  }

  private static LandingConfig.Design activeDesign(LandingConfig c, LandingOrderForm form) {
    var designs = c.preview().designs();
    if (designs.isEmpty()) return null;
    try {
      int index = Integer.parseInt(form.getDesign() == null ? "0" : form.getDesign());
      return index >= 0 && index < designs.size() ? designs.get(index) : designs.get(0);
    } catch (NumberFormatException ex) {
      return designs.get(0);
    }
  }

  private static Map<String, Object> publicView(LandingConfig c, boolean submitted) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("packages", c.packages());
    view.put("options", c.options());
    view.put("upsells", c.upsells());
    view.put("advanceAmount", c.advanceAmount());
    view.put("delivery", c.delivery());
    view.put("preview", c.preview());
    view.put("fontFiles", LandingFonts.FILES);
    view.put("submitted", submitted);
    return view;
  }
}
