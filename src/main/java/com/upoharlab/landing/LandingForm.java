package com.upoharlab.landing;

import com.upoharlab.landing.LandingConfig.OptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.PropertyAccessorFactory;

/**
 * The admin editor's raw values. Public fields with direct field binding keep this class to what
 * the form holds; {@link #toConfig()} produces the validated, typed record.
 */
public class LandingForm {
  /** Present only when the page held the landing editor, so other posts leave the config alone. */
  public boolean submitted;

  public boolean enabled;
  public HeroRow hero = new HeroRow();
  public List<String> benefits = new ArrayList<>();
  public ProofRow socialProof = new ProofRow();
  public List<PackageRow> packages = new ArrayList<>();
  public String urgencyLine;
  public List<String> moreUrgencyLines = new ArrayList<>();
  public List<OptionRow> options = new ArrayList<>();
  public List<UpsellRow> upsells = new ArrayList<>();
  public Integer advanceAmount;
  public DeliveryRow delivery = new DeliveryRow();
  public PreviewRow preview = new PreviewRow();
  public SectionsRow sections = new SectionsRow();

  public static class SectionsRow {
    public boolean hero = true, preview = true, benefits = true, gallery = true, details = true,
        socialProof = true, packages = true, order = true;
  }

  public static class HeroRow {
    public String headline, subHeadline, heroImage;
    public Integer oldPrice, offerPrice;
    public List<String> moreImages = new ArrayList<>();
  }

  public static class ProofRow {
    public List<String> customerImages = new ArrayList<>();
    public String reviewText, reviewerName, reviewerArea, proofLine;
    public List<ReviewRow> moreReviews = new ArrayList<>();
    public Integer countNumber;
  }

  public static class ReviewRow {
    public String text, name, area;
  }

  public static class PackageRow {
    public String name, description;
    public Integer price, oldPrice;
  }

  /** Index of the package marked popular; a single radio group across the rows. */
  public Integer popularPackage;

  public static class OptionRow {
    public String label, type;
    public List<ValueRow> values = new ArrayList<>();
  }

  public static class ValueRow {
    public String name, image;
    public Integer priceDelta;
  }

  public static class UpsellRow {
    public String label;
    public Integer price;
  }

  public static class DeliveryRow {
    public Integer insideDhakaCharge, outsideDhakaCharge;
  }

  public static class PreviewRow {
    public List<DesignRow> designs = new ArrayList<>();
    public List<String> fonts = new ArrayList<>();
    public String defaultText;
    public Integer maxChars;
  }

  public static class DesignRow {
    public String name, baseImage, color, shadow;
    public Integer textX, textY, maxWidth, fontSize, minFontSize, imageWidth, imageHeight;
  }

  private static final String UPLOAD_PREFIX = "landingUpload/";
  private static final Pattern IMAGE_FIELD =
      Pattern.compile(
          "hero\\.heroImage"
              + "|hero\\.moreImages\\[[0-9]{1,2}\\]"
              + "|socialProof\\.customerImages\\[[0-9]{1,2}\\]"
              + "|options\\[[0-9]{1,2}\\]\\.values\\[[0-9]{1,2}\\]\\.image"
              + "|preview\\.designs\\[[0-9]\\]\\.baseImage");

  /** Name of the file box beside an image picker. Avoids the characters form binding reads. */
  public String uploadName(String field) {
    return UPLOAD_PREFIX + field.replace('.', '/').replace('[', '(').replace(']', ')');
  }

  /** The image field a file box belongs to, or null if the name is not a known image field. */
  public static String fieldForUpload(String name) {
    if (name == null || !name.startsWith(UPLOAD_PREFIX)) return null;
    String field =
        name.substring(UPLOAD_PREFIX.length()).replace('/', '.').replace('(', '[').replace(')', ']');
    return IMAGE_FIELD.matcher(field).matches() ? field : null;
  }

  /** Points an image field at a newly stored file. Only known image fields are accepted. */
  public void setImage(String field, String filename) {
    if (!IMAGE_FIELD.matcher(field).matches())
      throw new IllegalArgumentException("Unknown image field.");
    var access = PropertyAccessorFactory.forDirectFieldAccess(this);
    access.setAutoGrowNestedPaths(true);
    access.setPropertyValue(field, filename);
  }

  public static LandingForm from(LandingConfig c) {
    LandingForm f = new LandingForm();
    if (c != null) f.fill(c);
    else f.preview.fonts.add(LandingFonts.DEFAULT);
    f.addSpareRows();
    return f;
  }

  private void fill(LandingConfig c) {
    enabled = c.enabled();
    sections.hero = c.sections().hero();
    sections.preview = c.sections().preview();
    sections.benefits = c.sections().benefits();
    sections.gallery = c.sections().gallery();
    sections.details = c.sections().details();
    sections.socialProof = c.sections().socialProof();
    sections.packages = c.sections().packages();
    sections.order = c.sections().order();
    hero.headline = c.hero().headline();
    hero.subHeadline = c.hero().subHeadline();
    hero.heroImage = c.hero().heroImage();
    hero.oldPrice = c.hero().oldPrice();
    hero.offerPrice = c.hero().offerPrice();
    hero.moreImages.addAll(c.hero().moreImages());
    benefits.addAll(c.benefits());
    socialProof.customerImages.addAll(c.socialProof().customerImages());
    socialProof.reviewText = c.socialProof().reviewText();
    for (var review : c.socialProof().moreReviews()) {
      ReviewRow row = new ReviewRow();
      row.text = review.text();
      row.name = review.name();
      row.area = review.area();
      socialProof.moreReviews.add(row);
    }
    socialProof.reviewerName = c.socialProof().reviewerName();
    socialProof.reviewerArea = c.socialProof().reviewerArea();
    socialProof.countNumber = c.socialProof().countNumber();
    socialProof.proofLine = c.socialProof().proofLine();
    for (int i = 0; i < c.packages().size(); i++) {
      var tier = c.packages().get(i);
      PackageRow row = new PackageRow();
      row.name = tier.name();
      row.description = tier.description();
      row.price = tier.price();
      row.oldPrice = tier.oldPrice();
      packages.add(row);
      if (tier.isPopular()) popularPackage = i;
    }
    urgencyLine = c.urgencyLine();
    moreUrgencyLines.addAll(c.moreUrgencyLines());
    for (var option : c.options()) {
      OptionRow row = new OptionRow();
      row.label = option.label();
      row.type = option.type() == null ? null : option.type().name();
      for (var value : option.values()) {
        ValueRow v = new ValueRow();
        v.name = value.name();
        v.image = value.image();
        v.priceDelta = value.priceDelta();
        row.values.add(v);
      }
      options.add(row);
    }
    for (var upsell : c.upsells()) {
      UpsellRow row = new UpsellRow();
      row.label = upsell.label();
      row.price = upsell.price();
      upsells.add(row);
    }
    advanceAmount = c.advanceAmount();
    delivery.insideDhakaCharge = c.delivery().insideDhakaCharge();
    delivery.outsideDhakaCharge = c.delivery().outsideDhakaCharge();
    preview.fonts.addAll(c.preview().fonts());
    preview.defaultText = c.preview().defaultText();
    preview.maxChars = c.preview().maxChars();
    for (var d : c.preview().designs()) {
      DesignRow row = new DesignRow();
      row.name = d.name();
      row.baseImage = d.baseImage();
      row.textX = d.textX();
      row.textY = d.textY();
      row.maxWidth = d.maxWidth();
      row.fontSize = d.fontSize();
      row.minFontSize = d.minFontSize();
      row.color = d.color();
      row.shadow = d.shadow();
      row.imageWidth = d.imageWidth();
      row.imageHeight = d.imageHeight();
      preview.designs.add(row);
    }
  }

  /** Empty rows give the editor room to add entries, and work without JavaScript. */
  public final void addSpareRows() {
    while (benefits.size() < 4) benefits.add("");
    if (benefits.size() < 20) benefits.add("");
    while (socialProof.customerImages.size() < 3) socialProof.customerImages.add("");
    if (socialProof.customerImages.size() < 12) socialProof.customerImages.add("");
    if (socialProof.moreReviews.size() < 9) socialProof.moreReviews.add(new ReviewRow());
    if (hero.moreImages.size() < 7) hero.moreImages.add("");
    if (moreUrgencyLines.size() < 4) moreUrgencyLines.add("");
    while (preview.fonts.size() < LandingFonts.FILES.size()) preview.fonts.add("");
    while (packages.size() < 3) packages.add(new PackageRow());
    if (packages.size() < 12) packages.add(new PackageRow());
    options.add(new OptionRow());
    for (OptionRow option : options)
      do option.values.add(new ValueRow());
      while (option.values.size() < 4);
    upsells.add(new UpsellRow());
    preview.designs.add(new DesignRow());
  }

  /** Blank rows are dropped and text is trimmed. Bad choices are left for the validator. */
  public LandingConfig toConfig() {
    List<LandingConfig.PackageTier> tiers = new ArrayList<>();
    for (int i = 0; i < packages.size(); i++) {
      PackageRow row = packages.get(i);
      if (row == null || (blank(row.name) && row.price == null)) continue;
      tiers.add(
          new LandingConfig.PackageTier(
              clean(row.name), clean(row.description), row.price, row.oldPrice,
              Integer.valueOf(i).equals(popularPackage)));
    }
    // Removing the marked package must not block saving, so the first one takes over.
    if (!tiers.isEmpty() && tiers.stream().noneMatch(LandingConfig.PackageTier::isPopular)) {
      var first = tiers.get(0);
      tiers.set(
          0,
          new LandingConfig.PackageTier(
              first.name(), first.description(), first.price(), first.oldPrice(), true));
    }
    List<LandingConfig.Option> optionList = new ArrayList<>();
    for (OptionRow row : options) {
      if (row == null) continue;
      List<LandingConfig.OptionValue> values = new ArrayList<>();
      for (ValueRow v : row.values)
        if (v != null && !blank(v.name))
          values.add(new LandingConfig.OptionValue(clean(v.name), clean(v.image), v.priceDelta));
      if (blank(row.label) && values.isEmpty()) continue;
      optionList.add(new LandingConfig.Option(clean(row.label), type(row.type), values));
    }
    List<LandingConfig.Upsell> extras = new ArrayList<>();
    for (UpsellRow row : upsells)
      if (row != null && !(blank(row.label) && row.price == null))
        extras.add(new LandingConfig.Upsell(clean(row.label), row.price));
    List<LandingConfig.Design> designs = new ArrayList<>();
    for (DesignRow d : preview.designs)
      if (d != null && !(blank(d.name) && blank(d.baseImage)))
        designs.add(
            new LandingConfig.Design(
                clean(d.name), clean(d.baseImage), d.textX, d.textY, d.maxWidth, d.fontSize,
                d.minFontSize, color(d.color), color(d.shadow), d.imageWidth, d.imageHeight));
    // Removed rows leave gaps; the first review left takes the main place.
    List<LandingConfig.Review> reviews = new ArrayList<>();
    addReview(reviews, socialProof.reviewText, socialProof.reviewerName, socialProof.reviewerArea);
    for (ReviewRow row : socialProof.moreReviews)
      if (row != null) addReview(reviews, row.text, row.name, row.area);
    LandingConfig.Review firstReview =
        reviews.isEmpty() ? new LandingConfig.Review("", "", "") : reviews.get(0);
    List<String> urgency = new ArrayList<>();
    if (!blank(urgencyLine)) urgency.add(urgencyLine.strip());
    urgency.addAll(filled(moreUrgencyLines));
    // The first image left in the list is the main one, whichever row it was typed in.
    List<String> heroImages = new ArrayList<>();
    if (!blank(hero.heroImage)) heroImages.add(hero.heroImage.strip());
    heroImages.addAll(filled(hero.moreImages));
    return new LandingConfig(
        enabled,
        new LandingConfig.Hero(
            clean(hero.headline), clean(hero.subHeadline),
            heroImages.isEmpty() ? "" : heroImages.get(0), hero.oldPrice, hero.offerPrice,
            heroImages.isEmpty() ? List.of() : heroImages.subList(1, heroImages.size())),
        filled(benefits),
        new LandingConfig.SocialProof(
            filled(socialProof.customerImages), firstReview.text(), firstReview.name(),
            firstReview.area(), socialProof.countNumber, clean(socialProof.proofLine),
            reviews.isEmpty() ? List.of() : reviews.subList(1, reviews.size())),
        tiers,
        urgency.isEmpty() ? "" : urgency.get(0),
        optionList,
        extras,
        advanceAmount,
        new LandingConfig.Delivery(delivery.insideDhakaCharge, delivery.outsideDhakaCharge),
        new LandingConfig.Preview(
            designs, filled(preview.fonts).stream().distinct().toList(), clean(preview.defaultText), preview.maxChars),
        new LandingConfig.Sections(
            sections.hero, sections.preview, sections.benefits, sections.gallery, sections.details,
            sections.socialProof, sections.packages, sections.order),
        urgency.isEmpty() ? List.of() : urgency.subList(1, urgency.size()));
  }

  private static OptionType type(String raw) {
    try {
      return raw == null || raw.isBlank() ? null : OptionType.valueOf(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static List<String> filled(List<String> values) {
    return values.stream().filter(v -> !blank(v)).map(String::strip).toList();
  }

  private static void addReview(
      List<LandingConfig.Review> reviews, String text, String name, String area) {
    if (!blank(text) || !blank(name) || !blank(area))
      reviews.add(new LandingConfig.Review(clean(text), clean(name), clean(area)));
  }

  private static String clean(String value) {
    return value == null ? "" : value.strip();
  }

  /** Accepts the short ways people write a hex colour: 2B211C, #fff and fff. */
  static String color(String value) {
    String text = clean(value);
    if (text.matches("#?[0-9a-fA-F]{6}")) return text.startsWith("#") ? text : "#" + text;
    if (!text.matches("#?[0-9a-fA-F]{3}")) return text;
    return "#" + text.replace("#", "").replaceAll("(.)", "$1$1");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
