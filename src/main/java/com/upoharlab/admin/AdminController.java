package com.upoharlab.admin;

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

  public AdminController(ProductStore p, SettingsService s, ImageStorage i) {
    products = p;
    settings = s;
    images = i;
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
    m.addAttribute("productForm", new ProductForm());
    m.addAttribute("photos", List.of());
    return "admin/product-edit";
  }

  @GetMapping("/products/{id}")
  String edit(@PathVariable long id, Model m) {
    var p = products.get(id);
    m.addAttribute("productForm", p.details());
    m.addAttribute("productId", id);
    m.addAttribute("photos", p.photos());
    return "admin/product-edit";
  }

  @PostMapping({"/products/new", "/products/{id}"})
  String save(
      @PathVariable(required = false) Long id,
      @Valid @ModelAttribute ProductForm productForm,
      BindingResult errors,
      Model m,
      RedirectAttributes flash) {
    Long saved = null;
    if (!errors.hasErrors())
      try {
        saved = products.save(id, productForm, errors);
      } catch (org.springframework.dao.DuplicateKeyException ex) {
        errors.rejectValue("slug", "duplicate", "This URL is already used.");
      }
    if (errors.hasErrors()) {
      m.addAttribute("productId", id);
      m.addAttribute("photos", id == null ? List.of() : products.get(id).photos());
      return "admin/product-edit";
    }
    flash.addFlashAttribute("message", "Product saved.");
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

  @PostMapping("/products/{id}/photos/{photo}/remove")
  String remove(@PathVariable long id, @PathVariable long photo, RedirectAttributes flash) {
    try {
      var filename =
          products.get(id).photos().stream()
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

  @GetMapping("/products/{id}/preview")
  String preview(@PathVariable long id, Model m) {
    m.addAttribute("product", products.get(id));
    m.addAttribute("settings", settings.all());
    m.addAttribute("whatsapp", settings.whatsapp(settings.all()));
    m.addAttribute("preview", true);
    m.addAttribute("orderable", false);
    m.addAttribute("showForm", false);
    return "site/product";
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
