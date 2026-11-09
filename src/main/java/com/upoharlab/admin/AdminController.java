package com.upoharlab.admin;

import com.upoharlab.landing.LandingFonts;
import com.upoharlab.landing.LandingForm;
import com.upoharlab.order.LandingPageModel;
import com.upoharlab.product.*;
import com.upoharlab.settings.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
  private final ProductStore products;
  private final SettingsService settings;
  private final ImageStorage images;
  private final ProductEditor editor;
  private final LandingPageModel landingPages;

  public AdminController(
      ProductStore p,
      SettingsService s,
      ImageStorage i,
      ProductEditor editor,
      LandingPageModel landingPages) {
    products = p;
    settings = s;
    images = i;
    this.editor = editor;
    this.landingPages = landingPages;
  }

  /** The landing editor's rows are plain public fields. */
  @InitBinder("landingForm")
  void landingBinder(org.springframework.web.bind.WebDataBinder binder) {
    binder.initDirectFieldAccess();
  }

  private void landingEditor(Model m, Long id, LandingForm form) {
    m.addAttribute("landingForm", form);
    m.addAttribute("fontChoices", LandingFonts.FILES.keySet());
    List<ProductStore.Photo> landing = id == null ? List.of() : products.get(id).landingPhotos();
    List<ProductStore.Photo> gallery = id == null ? List.of() : products.get(id).photos();
    List<ProductStore.Photo> all = new ArrayList<>(landing);
    all.addAll(gallery);
    m.addAttribute("landingPhotos", landing);
    m.addAttribute("imageChoices", all);
  }

  @GetMapping("/login")
  String login() {
    return "admin/login";
  }

  @GetMapping("/products")
  String list(Model m) {
    m.addAttribute("products", products.list(true));
    return "admin/products";
  }

  @GetMapping("/products/new")
  String create(Model m) {
    ProductForm form = new ProductForm();
    form.setStatus("ACTIVE");
    m.addAttribute("productForm", form);
    m.addAttribute("photos", List.of());
    LandingForm landing = LandingForm.from(null);
    landing.enabled = true;
    landingEditor(m, null, landing);
    return "admin/product-edit";
  }

  @GetMapping("/products/{id}")
  String edit(@PathVariable long id, Model m) {
    var p = products.get(id);
    m.addAttribute("productForm", p.details());
    m.addAttribute("productId", id);
    m.addAttribute("photos", p.photos());
    landingEditor(m, id, LandingForm.from(p.landing()));
    return "admin/product-edit";
  }

  @PostMapping({"/products/new", "/products/{id}"})
  String save(
      @PathVariable(required = false) Long id,
      @Valid @ModelAttribute ProductForm productForm,
      BindingResult errors,
      @ModelAttribute("landingForm") LandingForm landingForm,
      BindingResult landingErrors,
      @RequestParam(required = false) List<MultipartFile> photos,
      @RequestParam Map<String, MultipartFile> files,
      Model m,
      RedirectAttributes flash) {
    Long saved = null;
    landingErrors.getFieldErrors().stream()
        .map(e -> "Landing page: enter a whole number for " + e.getField() + ".")
        .distinct()
        .forEach(message -> errors.reject("landing", message));
    if (!errors.hasErrors())
      try {
        saved = editor.save(id, productForm, landingForm, errors, photos, files);
      } catch (org.springframework.dao.DuplicateKeyException ex) {
        errors.rejectValue("slug", "duplicate", "This URL is already used.");
      } catch (IOException | IllegalArgumentException ex) {
        errors.reject("photos", ex instanceof IOException
            ? "Unable to read or store a photo. Please choose another JPEG or PNG." : ex.getMessage());
      }
    if (errors.hasErrors()) {
      m.addAttribute("reselectPhotos", files.values().stream().anyMatch(file -> !file.isEmpty()));
      m.addAttribute("productId", id);
      m.addAttribute("photos", id == null ? List.of() : products.get(id).photos());
      landingEditor(m, id, LandingForm.from(landingForm.toConfig()));
      return "admin/product-edit";
    }
    flash.addFlashAttribute("message", "ACTIVE".equals(productForm.getStatus()) ? "Product saved and published in your shop." : "Product saved.");
    return "redirect:/admin/products/" + saved;
  }

  @PostMapping("/products/{id}/photos")
  String upload(
      @PathVariable long id,
      @RequestParam MultipartFile photo,
      @RequestParam String alt,
      RedirectAttributes flash) {
    try {
      images.upload(id, photo, alt);
      flash.addFlashAttribute("message", "Photo added.");
    } catch (IOException | IllegalArgumentException ex) {
      flash.addFlashAttribute(
          "error",
          ex instanceof IOException
              ? "Unable to read or store that photo. Please try another JPEG or PNG."
              : ex.getMessage());
    }
    return "redirect:/admin/products/" + id;
  }

  /** Renders the customer page for any status, with ordering switched off. */
  @GetMapping("/products/{id}/landing-preview")
  String landingPreview(@PathVariable long id, Model m, RedirectAttributes flash) {
    var product = products.get(id);
    if (product.landing() == null) {
      flash.addFlashAttribute("error", "Save the landing page section first.");
      return "redirect:/admin/products/" + id;
    }
    landingPages.fill(
        m, product, landingPages.defaults(product.orderLanding()), null, false, true, null);
    return "landing/page";
  }

  @PostMapping("/products/{id}/photos/{photo}/remove")
  String remove(@PathVariable long id, @PathVariable long photo, RedirectAttributes flash) {
    try {
      var page = products.get(id);
      var filename =
          java.util.stream.Stream.concat(page.photos().stream(), page.landingPhotos().stream())
              .filter(p -> p.id() == photo)
              .map(ProductStore.Photo::filename)
              .findFirst();
      products.removePhoto(id, photo);
      if (filename.isPresent())
        try {
          images.removeFiles(filename.get());
        } catch (IOException ignored) {
        }
      flash.addFlashAttribute("message", "Photo removed from the product.");
    } catch (IllegalArgumentException ex) {
      flash.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/admin/products/" + id;
  }

  @PostMapping("/products/{id}/photos/{photo}/move")
  String move(@PathVariable long id, @PathVariable long photo, @RequestParam String direction) {
    products.movePhoto(id, photo, direction);
    return "redirect:/admin/products/" + id;
  }

  @GetMapping("/settings")
  String settings(Model m) {
    SettingsForm form = new SettingsForm();
    form.setValues(settings.all());
    m.addAttribute("settingsForm", form);
    return "admin/settings";
  }

  @PostMapping("/settings")
  String saveSettings(
      @ModelAttribute SettingsForm settingsForm, BindingResult errors, RedirectAttributes flash) {
    settings.save(settingsForm, errors);
    if (errors.hasErrors()) return "admin/settings";
    flash.addFlashAttribute("message", "Settings saved.");
    return "redirect:/admin/settings";
  }
}
