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
    @Size(max = 20) List<@Size(max = 200) String> benefits,
    @Valid SocialProof socialProof,
    @Size(max = 12) List<@Valid PackageTier> packages,
    @Size(max = 300) String urgencyLine,
    @Size(max = 6) List<@Valid Option> options,
    @Size(max = 8) List<@Valid Upsell> upsells,
    @Min(0) @Max(100000) Integer advanceAmount,
    @Valid Delivery delivery,
    @Valid Preview preview,
    Sections sections,
    @Size(max = 4) List<@Size(max = 300) String> moreUrgencyLines,
    @Valid Wording wording) {

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
    moreUrgencyLines = list(moreUrgencyLines);
    delivery = delivery == null ? new Delivery(null, null) : delivery;
    preview = preview == null ? new Preview(null, null, null, null) : preview;
    sections = sections == null ? new Sections(null, null, null, null, null, null, null, null) : sections;
    wording = wording == null ? new Wording(null, null, null, null, null) : wording;
  }

  /**
   * What customers order against. With the name preview hidden its setup may be blank or half
   * done, so orders use its finished parts and plain defaults for the rest; the setup stays stored.
   */
  public LandingConfig forOrdering() {
    if (showsPreview()) return this;
    List<Design> designs =
        preview.designs().stream()
            .filter(LandingConfig::finished)
            .toList();
    if (designs.isEmpty()) designs = List.of(PLAIN_DESIGN);
    List<String> fonts = preview.fonts().isEmpty() ? List.of(LandingFonts.DEFAULT) : preview.fonts();
    int maxChars = preview.maxChars() == null ? DEFAULT_MAX_CHARS : preview.maxChars();
    return new LandingConfig(
        enabled, hero, benefits, socialProof, packages, urgencyLine, options, upsells, advanceAmount,
        delivery, new Preview(designs, fonts, preview.defaultText(), maxChars), sections, moreUrgencyLines,
        wording);
  }

  /** The live preview and the font and design choices appear only for products with customer text. */
  public boolean showsPreview() {
    return sections.preview() && wording.textMode() != TextMode.NONE;
  }

  /**
   * Whether an order needs a design the customer approves before it is made: only when the
   * product takes customer text and the customer wrote some.
   */
  public boolean needsDesign(String customerText) {
    return switch (wording.textMode()) {
      case REQUIRED -> true;
      case OPTIONAL -> customerText != null && !customerText.isBlank();
      case NONE -> false;
    };
  }

  /** The first urgency line, then the rest. */
  public List<String> allUrgencyLines() {
    List<String> all = new java.util.ArrayList<>();
    if (urgencyLine != null && !urgencyLine.isBlank()) all.add(urgencyLine);
    all.addAll(moreUrgencyLines);
    return all;
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
      @Min(0) @Max(1000000) Integer offerPrice,
      @Size(max = 7) List<@Pattern(regexp = IMAGE) String> moreImages) {
    public Hero {
      moreImages = list(moreImages);
    }

    public Hero(
        String headline, String subHeadline, String heroImage, Integer oldPrice, Integer offerPrice) {
      this(headline, subHeadline, heroImage, oldPrice, offerPrice, List.of());
    }

    /** The main image, then the rest of the slideshow. */
    public List<String> allImages() {
      List<String> all = new java.util.ArrayList<>();
      if (heroImage != null && !heroImage.isBlank()) all.add(heroImage);
      all.addAll(moreImages);
      return all;
    }
  }

  public record SocialProof(
      @Size(max = 12) List<@Pattern(regexp = IMAGE) String> customerImages,
      @Size(max = 600) String reviewText,
      @Size(max = 100) String reviewerName,
      @Size(max = 100) String reviewerArea,
      @Min(0) @Max(100000000) Integer countNumber,
      @Size(max = 200) String proofLine,
      @Size(max = 9) List<@Valid Review> moreReviews) {
    public SocialProof {
      customerImages = list(customerImages);
      moreReviews = list(moreReviews);
    }

    public SocialProof(
        List<String> customerImages,
        String reviewText,
        String reviewerName,
        String reviewerArea,
        Integer countNumber,
        String proofLine) {
      this(customerImages, reviewText, reviewerName, reviewerArea, countNumber, proofLine, List.of());
    }

    /** The first review, then the rest. */
    public List<Review> allReviews() {
      List<Review> all = new java.util.ArrayList<>();
      if (!blankText(reviewText) || !blankText(reviewerName) || !blankText(reviewerArea))
        all.add(new Review(reviewText, reviewerName, reviewerArea));
      all.addAll(moreReviews);
      return all;
    }

    private static boolean blankText(String value) {
      return value == null || value.isBlank();
    }
  }

  public record Review(
      @Size(max = 600) String text, @Size(max = 100) String name, @Size(max = 100) String area) {}

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

  /**
   * This product's own words for the customer's text, for example "কী প্রিন্ট হবে" on a mug.
   * Anything left blank uses the site-wide wording from Admin → Site text.
   */
  public record Wording(
      @Size(max = 100) String textLabel,
      @Size(max = 150) String textPlaceholder,
      @Size(max = 300) String textHelp,
      @Size(max = 150) String previewTitle,
      TextMode textMode) {
    public Wording {
      textMode = textMode == null ? TextMode.REQUIRED : textMode;
    }

    public Wording(String textLabel, String textPlaceholder, String textHelp, String previewTitle) {
      this(textLabel, textPlaceholder, textHelp, previewTitle, null);
    }
  }

  /**
   * Whether customers write their own text on this product: always (a name plate), if they like
   * (a mug that also looks good plain), or never (a ready-made item, so no text box or preview).
   */
  public enum TextMode {
    REQUIRED,
    OPTIONAL,
    NONE
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
