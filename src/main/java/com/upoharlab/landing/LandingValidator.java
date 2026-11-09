package com.upoharlab.landing;

import com.upoharlab.landing.LandingConfig.Design;
import com.upoharlab.landing.LandingConfig.Option;
import com.upoharlab.landing.LandingConfig.OptionValue;
import com.upoharlab.landing.LandingConfig.PackageTier;
import com.upoharlab.landing.LandingConfig.Upsell;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Checks a configuration before saving. Messages are shown to the owner in the admin. */
public final class LandingValidator {
  private static final Validator BEANS = buildValidator();
  private static final java.util.regex.Pattern DESIGN_COLOUR =
      java.util.regex.Pattern.compile("preview\\.designs\\[(\\d+)]\\.(color|shadow)");

  private LandingValidator() {}

  private static Validator buildValidator() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator();
    }
  }

  /** Limits that apply even to an unfinished draft, plus image ownership and the font list. */
  public static List<String> basic(LandingConfig c, Set<String> ownedImages) {
    List<String> problems = new ArrayList<>();
    BEANS.validate(c).stream()
        .map(v -> message(v.getPropertyPath().toString(), v.getMessage()))
        .sorted()
        .forEach(problems::add);
    for (String image : images(c))
      if (!ownedImages.contains(image))
        problems.add("Landing page: an image no longer belongs to this product. Choose it again.");
    for (String font : c.preview().fonts())
      if (!LandingFonts.allowed(font)) problems.add("Landing page: unknown font.");
    return problems;
  }

  private static String message(String path, String detail) {
    if ("benefits".equals(path)) return "Landing page: you can add up to 20 benefits.";
    if ("socialProof.customerImages".equals(path))
      return "Landing page: you can add up to 12 customer photos.";
    if ("hero.moreImages".equals(path)) return "Landing page: you can add up to 8 hero images.";
    if ("socialProof.moreReviews".equals(path))
      return "Landing page: you can add up to 10 reviews.";
    if ("moreUrgencyLines".equals(path)) return "Landing page: you can add up to 5 urgency lines.";
    if ("packages".equals(path)) return "Landing page: you can add up to 12 packages.";
    var colour = DESIGN_COLOUR.matcher(path);
    if (!colour.matches()) return "Landing page: check " + path + " (" + detail + ").";
    String name = "color".equals(colour.group(2)) ? "text colour" : "shadow colour";
    return "Landing page: preview design "
        + (Integer.parseInt(colour.group(1)) + 1)
        + ": the "
        + name
        + " is not valid. Use the colour box beside it, or a code like #2B211C.";
  }

  /** Everything a live page needs. Applied when the page is enabled and the product is public. */
  public static List<String> publishable(LandingConfig c) {
    List<String> p = new ArrayList<>();
    var show = c.sections();
    if (show.hero()) hero(c, p);
    if (show.benefits() && c.benefits().isEmpty()) p.add("Landing page: add at least one benefit.");
    if (show.packages() || show.order()) packages(c, p);
    else if (c.packages().isEmpty() && c.hero().offerPrice() == null)
      p.add("Landing page: add a package price or an offer price; the shop list shows it.");
    if (show.order()) orderSetup(c, p);
    if (show.preview()) preview(c, p);
    if (show.socialProof()) proof(c, p);
    return p;
  }

  private static void hero(LandingConfig c, List<String> p) {
    require(p, c.hero().headline(), "Landing page: add a headline.");
    require(p, c.hero().heroImage(), "Landing page: add a hero image in Top of page.");
    if (c.hero().offerPrice() == null) p.add("Landing page: set the offer price.");
    if (c.hero().oldPrice() != null
        && c.hero().offerPrice() != null
        && c.hero().oldPrice() <= c.hero().offerPrice())
      p.add("Landing page: the old price must be higher than the offer price.");
  }

  private static void orderSetup(LandingConfig c, List<String> p) {
    for (Option option : c.options()) option(option, p);
    for (Upsell upsell : c.upsells())
      if (blank(upsell.label()) || upsell.price() == null)
        p.add("Landing page: each extra needs a label and a price.");
    if (c.advanceAmount() == null) p.add("Landing page: set the advance amount.");
    if (c.delivery().insideDhakaCharge() == null || c.delivery().outsideDhakaCharge() == null)
      p.add("Landing page: set both delivery charges.");
  }

  private static void packages(LandingConfig c, List<String> p) {
    if (c.packages().isEmpty()) {
      p.add("Landing page: add at least one package.");
      return;
    }
    long popular = c.packages().stream().filter(PackageTier::isPopular).count();
    if (popular != 1) p.add("Landing page: mark exactly one package as most popular.");
    for (PackageTier tier : c.packages()) {
      if (blank(tier.name()) || tier.price() == null)
        p.add("Landing page: each package needs a name and a price.");
      else if (tier.oldPrice() != null && tier.oldPrice() <= tier.price())
        p.add("Landing page: a package's old price must be higher than its price.");
    }
  }

  private static void option(Option option, List<String> p) {
    if (blank(option.label()) || option.type() == null || option.values().isEmpty())
      p.add("Landing page: each option needs a label, a type and at least one value.");
    for (OptionValue value : option.values())
      if (blank(value.name()) || value.priceDelta() == null)
        p.add("Landing page: each option value needs a name and a price change (0 for none).");
  }

  private static void preview(LandingConfig c, List<String> p) {
    var preview = c.preview();
    if (preview.designs().isEmpty()) p.add("Landing page: add at least one preview design.");
    if (preview.fonts().isEmpty()) p.add("Landing page: choose at least one preview font.");
    require(p, preview.defaultText(), "Landing page: fill in Sample text.");
    if (preview.maxChars() == null) p.add("Landing page: fill in Maximum characters.");
    else if (preview.defaultText() != null
        && preview.defaultText().codePointCount(0, preview.defaultText().length())
            > preview.maxChars())
      p.add(
          "Landing page: Sample text has "
              + preview.defaultText().codePointCount(0, preview.defaultText().length())
              + " characters but Maximum characters is "
              + preview.maxChars()
              + ". Shorten the text or raise the maximum.");
    int number = 0;
    for (Design d : preview.designs()) {
      number++;
      List<String> missing = missing(d);
      if (!missing.isEmpty()) {
        p.add(
            "Landing page: preview design " + number + " still needs: " + String.join(", ", missing)
                + (blank(d.baseImage()) ? ". Upload the Base image; the position and sizes then fill in by themselves." : "."));
        continue;
      }
      if (d.minFontSize() > d.fontSize())
        p.add("Landing page: a design's minimum font size is above its font size.");
    }
  }

  private static List<String> missing(Design d) {
    List<String> missing = new ArrayList<>();
    if (blank(d.name())) missing.add("Design name");
    if (blank(d.baseImage())) missing.add("Base image");
    if (d.textX() == null || d.textY() == null) missing.add("Text X and Y");
    if (d.maxWidth() == null) missing.add("Max width");
    if (d.fontSize() == null) missing.add("Font size");
    if (d.minFontSize() == null) missing.add("Smallest size");
    if (blank(d.color())) missing.add("Text colour");
    return missing;
  }

  /** A review is all or nothing, and a count needs words to go with it. */
  private static void proof(LandingConfig c, List<String> p) {
    var proof = c.socialProof();
    for (var review : proof.allReviews()) {
      if (!blank(review.text()) && blank(review.name()))
        p.add("Landing page: a review needs the reviewer's name.");
      if (blank(review.text())) p.add("Landing page: a review needs its text.");
    }
    if ((proof.countNumber() == null) != blank(proof.proofLine()))
      p.add("Landing page: the customer count and its proof line go together.");
  }

  /** Every uploaded image the configuration points at. */
  public static List<String> images(LandingConfig c) {
    List<String> all = new ArrayList<>();
    all.addAll(c.hero().allImages());
    all.addAll(c.socialProof().customerImages());
    c.options().forEach(o -> o.values().forEach(v -> all.add(v.image())));
    c.preview().designs().forEach(d -> all.add(d.baseImage()));
    return all.stream().filter(Predicate.not(LandingValidator::blank)).distinct().toList();
  }

  private static void require(List<String> problems, String value, String message) {
    if (blank(value)) problems.add(message);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
