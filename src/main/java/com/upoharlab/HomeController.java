package com.upoharlab;

import com.upoharlab.product.*;
import com.upoharlab.settings.SettingsService;
import java.nio.file.Files;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {
  private final ProductStore products;
  private final SettingsService settings;
  private final ImageStorage images;

  public HomeController(ProductStore products, SettingsService settings, ImageStorage images) {
    this.products = products;
    this.settings = settings;
    this.images = images;
  }

  @GetMapping("/")
  public String home(Model m) {
    var list = products.list(false);
    var values = settings.all();
    // Per product: making days plus delivery days, or null while making days are missing.
    java.util.Map<Long, ArrivalEstimate.Range> arrival = new java.util.HashMap<>();
    list.forEach(p -> arrival.put(p.id(),
        ArrivalEstimate.of(p.details().getMakingDaysMin(), p.details().getMakingDaysMax())));
    m.addAttribute("products", list);
    m.addAttribute("arrival", arrival);
    m.addAttribute("settings", values);
    m.addAttribute("whatsapp", settings.whatsapp(settings.all()));
    return "site/home";
  }

  @GetMapping("/media/{filename}")
  public ResponseEntity<Resource> image(@PathVariable String filename, Authentication auth) {
    java.nio.file.Path path;
    try {
      path = images.path(filename);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    boolean publicPhoto = images.publiclyVisible(filename);
    boolean admin =
        auth != null
            && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    if (!Files.isRegularFile(path) || !images.exists(filename) || (!publicPhoto && !admin))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .header("Cache-Control", publicPhoto ? "public, max-age=86400" : "no-store")
        .body(new FileSystemResource(path));
  }
}
