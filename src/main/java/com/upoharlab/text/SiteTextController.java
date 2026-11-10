package com.upoharlab.text;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin → Site text: every customer-facing wording, grouped by the page it appears on. */
@Controller
@RequestMapping("/admin/texts")
class SiteTextController {
  /** Form fields are named "t:" + key so they cannot clash with the CSRF field. */
  static final String PREFIX = "t:";

  private final SiteTexts texts;

  SiteTextController(SiteTexts texts) {
    this.texts = texts;
  }

  @GetMapping
  String show(Model m) {
    Map<String, String> current = new LinkedHashMap<>();
    texts.catalog().groups().forEach(g -> g.entries().forEach(e -> current.put(e.key(), texts.raw(e.key()))));
    return render(m, current, Map.of());
  }

  @PostMapping
  String save(@RequestParam MultiValueMap<String, String> params, Model m, RedirectAttributes flash) {
    Map<String, String> submitted = new LinkedHashMap<>();
    params.forEach((name, values) -> {
      if (name.startsWith(PREFIX) && !values.isEmpty())
        submitted.put(name.substring(PREFIX.length()), values.get(0));
    });
    Map<String, String> problems = texts.save(submitted);
    if (!problems.isEmpty()) {
      Map<String, String> current = new LinkedHashMap<>();
      texts.catalog().groups().forEach(g -> g.entries().forEach(e ->
          current.put(e.key(), submitted.getOrDefault(e.key(), texts.raw(e.key())))));
      return render(m, current, problems);
    }
    flash.addFlashAttribute("message", "Saved. Customers see the new wording right away.");
    return "redirect:/admin/texts";
  }

  @PostMapping("/reset")
  String resetAll(RedirectAttributes flash) {
    texts.resetAll();
    flash.addFlashAttribute("message", "Every text is back to its default wording.");
    return "redirect:/admin/texts";
  }

  private String render(Model m, Map<String, String> current, Map<String, String> problems) {
    var edited = texts.overrides().keySet();
    Map<String, Long> changedByGroup = new LinkedHashMap<>();
    texts.catalog().groups().forEach(g -> changedByGroup.put(
        g.title(), g.entries().stream().filter(e -> edited.contains(e.key())).count()));
    m.addAttribute("groups", texts.catalog().groups());
    m.addAttribute("current", current);
    m.addAttribute("edited", edited);
    m.addAttribute("changedByGroup", changedByGroup);
    m.addAttribute("problems", problems);
    m.addAttribute("total", texts.catalog().size());
    return "admin/texts";
  }
}
