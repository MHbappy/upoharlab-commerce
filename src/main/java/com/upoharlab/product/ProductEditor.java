package com.upoharlab.product;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfig.Design;
import com.upoharlab.landing.LandingForm;
import com.upoharlab.landing.LandingValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

/** Saves details, photos and visibility together; failed submissions leave no partial product. */
@Service
public class ProductEditor {
  private final ProductStore products;
  public static final int MAX_PHOTOS = 12;
  private static final int MAX_LANDING_FILES = 16;
  private static final int DEFAULT_WIDTH = 1000, DEFAULT_HEIGHT = 500;
  private final ImageStorage images;

  public ProductEditor(ProductStore products, ImageStorage images) {
    this.products = products;
    this.images = images;
  }

  @Transactional(rollbackFor = IOException.class)
  public Long save(Long id, ProductForm form, Errors errors, List<MultipartFile> uploads)
      throws IOException {
    return save(id, form, null, errors, uploads);
  }

  @Transactional(rollbackFor = IOException.class)
  public Long save(
      Long id, ProductForm form, LandingForm landing, Errors errors, List<MultipartFile> uploads)
      throws IOException {
    return save(id, form, landing, errors, uploads, Map.of());
  }

  /**
   * {@code landing} is null, or not submitted, when the post did not come from the editor.
   * {@code allFiles} holds every file in the post; those from landing image boxes are stored here.
   */
  @Transactional(rollbackFor = IOException.class)
  public Long save(
      Long id,
      ProductForm form,
      LandingForm landing,
      Errors errors,
      List<MultipartFile> uploads,
      Map<String, MultipartFile> allFiles)
      throws IOException {
    var files = uploads == null ? List.<MultipartFile>of()
        : uploads.stream().filter(file -> !file.isEmpty()).toList();
    int savedPhotos = id == null ? 0 : products.get(id).photos().size();
    if (files.size() + savedPhotos > MAX_PHOTOS) {
      errors.reject(
          "photos", "A product can have up to " + MAX_PHOTOS + " photos. Remove some to add more.");
      return id;
    }
    if (form.getSlug() == null || form.getSlug().isBlank()) {
      if (id == null) form.setSlug("gift-" + UUID.randomUUID().toString().substring(0, 12));
      else form.setSlug(products.get(id).details().getSlug());
    }
    String status = form.getStatus();
    Long saved;
    try {
      // Keep the row private within this transaction until photos and all details are valid.
      form.setStatus("DRAFT");
      saved = products.save(id, form, errors);
      if (errors.hasErrors()) return id;
      for (MultipartFile file : files) images.upload(saved, file, form.getTitle());
    } finally {
      form.setStatus(status);
    }
    if (landing != null && landing.submitted && !saveLanding(saved, form, landing, errors, allFiles)) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return id;
    }
    // Customers can only buy through a landing page, so a public product must have one.
    if (landing != null && !"DRAFT".equals(status) && !(landing.submitted && landing.enabled))
      errors.reject("landing", "Turn on and complete the landing page to publish this product.");
    products.save(saved, form, errors, landing == null || !landing.submitted || landing.sections.details);
    if (errors.hasErrors()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return id;
    }
    return saved;
  }

  /** Runs inside the save, so a later failure removes these files along with everything else. */
  private void storeLandingImages(
      long id,
      ProductForm form,
      LandingForm landing,
      Errors errors,
      Map<String, MultipartFile> files)
      throws IOException {
    Map<String, MultipartFile> chosen = new LinkedHashMap<>();
    files.forEach(
        (name, file) -> {
          String field = LandingForm.fieldForUpload(name);
          if (field != null && !file.isEmpty()) chosen.put(field, file);
        });
    if (chosen.size() > MAX_LANDING_FILES) {
      errors.reject("landing", "Landing page: choose up to " + MAX_LANDING_FILES + " images at a time.");
      return;
    }
    for (var entry : chosen.entrySet()) {
      String description = slotName(entry.getKey()) + " - " + form.getTitle();
      landing.setImage(entry.getKey(), images.uploadLanding(id, entry.getValue(), description));
    }
  }

  /** The admin's image pickers list images by this description. */
  private static String slotName(String field) {
    if (field.startsWith("hero")) return "Hero image";
    if (field.startsWith("socialProof")) return "Customer photo";
    if (field.startsWith("options")) return "Option image";
    return "Design image";
  }

  private boolean saveLanding(
      long id,
      ProductForm form,
      LandingForm landing,
      Errors errors,
      Map<String, MultipartFile> files)
      throws IOException {
    storeLandingImages(id, form, landing, errors, files);
    if (errors.hasErrors()) return false;
    LandingConfig config = withImageSizes(landing.toConfig(), errors);
    if (errors.hasErrors()) return false;
    var page = products.get(id);
    Set<String> owned = new HashSet<>();
    page.photos().forEach(photo -> owned.add(photo.filename()));
    page.landingPhotos().forEach(photo -> owned.add(photo.filename()));
    List<String> problems = new ArrayList<>(LandingValidator.basic(config, owned));
    if (config.enabled() && !"DRAFT".equals(form.getStatus()))
      problems.addAll(LandingValidator.publishable(config));
    problems.stream().distinct().forEach(problem -> errors.reject("landing", problem));
    if (!problems.isEmpty()) return false;
    products.saveLanding(id, config);
    // The home page and old links show this price, so it follows the package most people choose.
    config.packages().stream()
        .filter(tier -> tier.isPopular() && tier.price() != null)
        .findFirst()
        .map(LandingConfig.PackageTier::price)
        .or(() -> config.packages().stream().map(LandingConfig.PackageTier::price).filter(java.util.Objects::nonNull).findFirst())
        .or(() -> java.util.Optional.ofNullable(config.hero().offerPrice()))
        .ifPresent(form::setPrice);
    return true;
  }

  /** The browser never supplies image sizes; they come from the stored file. */
  private LandingConfig withImageSizes(LandingConfig c, Errors errors) throws IOException {
    List<Design> designs = new ArrayList<>();
    for (Design d : c.preview().designs()) {
      int[] size = {DEFAULT_WIDTH, DEFAULT_HEIGHT};
      if (d.baseImage() != null && !d.baseImage().isBlank())
        try {
          size = images.dimensions(d.baseImage());
        } catch (IllegalArgumentException | IOException ex) {
          errors.reject("landing", "Landing page: a design's image cannot be read. Choose it again.");
        }
      designs.add(
          new Design(
              d.name(), d.baseImage(), d.textX(), d.textY(), d.maxWidth(), d.fontSize(),
              d.minFontSize(), d.color(), d.shadow(), size[0], size[1]));
    }
    var p = c.preview();
    return new LandingConfig(
        c.enabled(), c.hero(), c.benefits(), c.socialProof(), c.packages(), c.urgencyLine(),
        c.options(), c.upsells(), c.advanceAmount(), c.delivery(),
        new LandingConfig.Preview(designs, p.fonts(), p.defaultText(), p.maxChars()),
        c.sections(),
        c.moreUrgencyLines(),
        c.wording());
  }
}
