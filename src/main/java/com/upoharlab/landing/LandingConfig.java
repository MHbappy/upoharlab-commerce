package com.upoharlab.landing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Everything a product's order page shows. Prices are whole taka. Stored as JSONB on product. */
public record LandingConfig(
    boolean enabled,
    @Valid Hero hero,
    @Size(max = 4) List<@Size(max = 200) String> benefits,
    @Valid SocialProof socialProof,
    @Size(max = 3) List<@Valid PackageTier> packages,
    @Size(max = 300) String urgencyLine,
    @Size(max = 6) List<@Valid Option> options,
    @Size(max = 8) List<@Valid Upsell> upsells,
    @Min(0) @Max(100000) Integer advanceAmount,
    @Valid Delivery delivery,
    @Valid Preview preview,
    Sections sections) {

  private static final String IMAGE = "(?:[0-9a-f-]{36}\\.jpg)?";
  private static final String COLOR = "(?:#[0-9a-fA-F]{6})?";
  private static final int DEFAULT_MAX_CHARS = 30;
  private static final Design PLAIN_DESIGN =
      new Design("", "", 500, 250, 800, 110, 40, "#2B211C", "", 1000, 500);

  public LandingConfig {
    hero = hero == null ? new Hero(null, null, null, null, null) : hero;
    benefits = list(benefits);
    socialProof = socialProof == null ? new SocialProof(null, null, null, null, null, null) : socialProof;
    packages = list(packages);
    options = list(options);
    upsells = list(upsells);
    delivery = delivery == null ? new Delivery(null, null) : delivery;
    preview = preview == null ? new Preview(null, null, null, null) : preview;
    sections = sections == null ? new Sections(null, null, null, null, null, null, null, null) : sections;
  }

  /**
   * What customers order against. With the name preview hidden its setup may be blank or half
   * done, so orders use its finished parts and plain defaults for the rest; the setup stays stored.
   */
  public LandingConfig forOrdering() {
    if (sections.preview()) return this;
    List<Design> designs =
        preview.designs().stream()
            .filter(LandingConfig::finished)
            .toList();
    if (designs.isEmpty()) designs = List.of(PLAIN_DESIGN);
    List<String> fonts = preview.fonts().isEmpty() ? List.of(LandingFonts.DEFAULT) : preview.fonts();
    int maxChars = preview.maxChars() == null ? DEFAULT_MAX_CHARS : preview.maxChars();
    return new LandingConfig(
        enabled, hero, benefits, socialProof, packages, urgencyLine, options, upsells, advanceAmount,
        delivery, new Preview(designs, fonts, preview.defaultText(), maxChars), sections);
  }

  private static boolean finished(Design d) {
    return d.textX() != null
        && d.textY() != null
        && d.maxWidth() != null
        && d.fontSize() != null
        && d.minFontSize() != null
        && d.imageWidth() != null
        && d.imageHeight() != null;
  }

  private static <T> List<T> list(List<T> value) {
    return value == null ? List.of() : List.copyOf(value);
  }

  public record Hero(
      @Size(max = 200) String headline,
      @Size(max = 300) String subHeadline,
      @Pattern(regexp = IMAGE) String heroImage,
      @Min(0) @Max(1000000) Integer oldPrice,
      @Min(0) @Max(1000000) Integer offerPrice) {}

  public record SocialProof(
      @Size(max = 3) List<@Pattern(regexp = IMAGE) String> customerImages,
      @Size(max = 600) String reviewText,
      @Size(max = 100) String reviewerName,
      @Size(max = 100) String reviewerArea,
      @Min(0) @Max(100000000) Integer countNumber,
      @Size(max = 200) String proofLine) {
    public SocialProof {
      customerImages = list(customerImages);
    }
  }

  public record PackageTier(
      @Size(max = 100) String name,
      @Size(max = 300) String description,
      @Min(1) @Max(1000000) Integer price,
      @Min(0) @Max(1000000) Integer oldPrice,
      boolean isPopular) {}

  public enum OptionType {
    SIZE,
    SHAPE,
    COLOR
  }

  public record Option(
      @Size(max = 100) String label, OptionType type, @Size(max = 12) List<@Valid OptionValue> values) {
    public Option {
      values = list(values);
    }
  }

  public record OptionValue(
      @Size(max = 100) String name,
      @Pattern(regexp = IMAGE) String image,
      @Min(0) @Max(100000) Integer priceDelta) {}

  public record Upsell(@Size(max = 150) String label, @Min(1) @Max(100000) Integer price) {}

  public record Delivery(
      @Min(0) @Max(10000) Integer insideDhakaCharge,
      @Min(0) @Max(10000) Integer outsideDhakaCharge) {}

  public record Preview(
      @Size(max = 8) List<@Valid Design> designs,
      @Size(max = 8) List<@Size(max = 60) String> fonts,
      @Size(max = 100) String defaultText,
      @Min(1) @Max(100) Integer maxChars) {
    public Preview {
      designs = list(designs);
      fonts = list(fonts);
    }
  }

  /** Which parts of the page customers see. Anything not stored yet is shown. */
  public record Sections(
      Boolean hero,
      Boolean preview,
      Boolean benefits,
      Boolean gallery,
      Boolean details,
      Boolean socialProof,
      Boolean packages,
      Boolean order) {
    public Sections {
      hero = hero == null || hero;
      preview = preview == null || preview;
      benefits = benefits == null || benefits;
      gallery = gallery == null || gallery;
      details = details == null || details;
      socialProof = socialProof == null || socialProof;
      packages = packages == null || packages;
      order = order == null || order;
    }
  }

  /** Text position is in pixels of the base image; width and height are read from that image. */
  public record Design(
      @Size(max = 100) String name,
      @Pattern(regexp = IMAGE) String baseImage,
      @Min(0) @Max(5000) Integer textX,
      @Min(0) @Max(5000) Integer textY,
      @Min(1) @Max(5000) Integer maxWidth,
      @Min(1) @Max(1000) Integer fontSize,
      @Min(1) @Max(1000) Integer minFontSize,
      @Pattern(regexp = COLOR) String color,
      @Pattern(regexp = COLOR) String shadow,
      @Min(1) @Max(5000) Integer imageWidth,
      @Min(1) @Max(5000) Integer imageHeight) {}
}
