package com.upoharlab;

import com.upoharlab.product.ProductStore;
import com.upoharlab.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Short public pages, plus the files that help search engines find them. */
@Controller
public class InfoController {
  private static final List<String> PUBLIC_PAGES =
      List.of(
          "/",
          "/bulk",
          "/faq",
          "/about",
          "/policies/delivery",
          "/policies/returns",
          "/policies/privacy");

  private final SettingsService settings;
  private final ProductStore products;
  private final SeoLinks seo;

  public InfoController(SettingsService settings, ProductStore products, SeoLinks seo) {
    this.settings = settings;
    this.products = products;
    this.seo = seo;
  }

  private String page(String view, Model m) {
    Map<String, String> values = settings.all();
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", settings.whatsapp(values));
    return view;
  }

  @GetMapping("/bulk")
  String bulk(Model m) {
    return page("site/bulk", m);
  }

  @GetMapping("/faq")
  String faq(Model m) {
    return page("site/faq", m);
  }

  @GetMapping("/about")
  String about(Model m) {
    return page("site/about", m);
  }

  @GetMapping("/policies/delivery")
  String delivery(Model m) {
    return page("site/policy-delivery", m);
  }

  @GetMapping("/policies/returns")
  String returns(Model m) {
    return page("site/policy-returns", m);
  }

  @GetMapping("/policies/privacy")
  String privacy(Model m) {
    return page("site/policy-privacy", m);
  }

  private String base() {
    String configured = seo.url("/");
    return configured != null
        ? configured.replaceAll("/+$", "")
        : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  @ResponseBody
  String sitemap() {
    StringBuilder xml =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    String base = base();
    for (String page : PUBLIC_PAGES) xml.append(entry(base + page));
    for (var product : products.list(false))
      xml.append(entry(base + "/p/" + product.details().getSlug()));
    return xml.append("</urlset>\n").toString();
  }

  private static String entry(String url) {
    return "  <url><loc>" + url.replace("&", "&amp;") + "</loc></url>\n";
  }

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  String robots(HttpServletRequest req) {
    return "User-agent: *\nDisallow: /admin\nDisallow: /order\nDisallow: /track\n\nSitemap: "
        + base()
        + "/sitemap.xml\n";
  }
}
