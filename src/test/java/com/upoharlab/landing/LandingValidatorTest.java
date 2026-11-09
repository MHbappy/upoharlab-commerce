package com.upoharlab.landing;

import static com.upoharlab.landing.LandingConfigTestData.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.landing.LandingConfig.PackageTier;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class LandingValidatorTest {
  private static LandingConfig edit(java.util.function.Consumer<ObjectNode> change) {
    ObjectNode node = (ObjectNode) JsonMapper.shared().readTree(LandingConfigCodec.write(complete()));
    change.accept(node);
    return LandingConfigCodec.read(node.toString());
  }

  @Test
  void completeConfigurationPasses() {
    assertThat(LandingValidator.basic(complete(), Set.of(IMAGE))).isEmpty();
    assertThat(LandingValidator.publishable(complete())).isEmpty();
  }

  @Test
  void imagesMustBelongToTheProduct() {
    assertThat(LandingValidator.basic(complete(), Set.of())).isNotEmpty();
  }

  @Test
  void badColourIsExplainedInPlainWords() {
    LandingConfig c =
        edit(n -> ((ObjectNode) n.withObject("preview").withArray("designs").get(0)).put("shadow", "red"));
    assertThat(LandingValidator.basic(c, Set.of(IMAGE)))
        .singleElement()
        .asString()
        .contains("preview design 1", "shadow colour")
        .doesNotContain("must match");
  }

  @Test
  void incompleteDesignSaysWhatIsMissing() {
    LandingConfig c =
        edit(n -> ((ObjectNode) n.withObject("preview").withArray("designs").get(0)).put("baseImage", ""));
    assertThat(LandingValidator.publishable(c))
        .anyMatch(m -> m.contains("preview design 1 still needs: Base image"));
  }

  @Test
  void shortColourSpellingsAreCompleted() {
    assertThat(LandingForm.color("2b211c")).isEqualTo("#2b211c");
    assertThat(LandingForm.color("#fff")).isEqualTo("#ffffff");
    assertThat(LandingForm.color(" abc ")).isEqualTo("#aabbcc");
    assertThat(LandingForm.color("")).isEmpty();
    assertThat(LandingForm.color("red")).isEqualTo("red");
  }

  private static ObjectNode hide(ObjectNode root, String... sections) {
    ObjectNode node = root.putObject("sections");
    for (String section : sections) node.put(section, false);
    return root;
  }

  @Test
  void sectionsNotStoredYetAreShown() {
    assertThat(complete().sections().hero()).isTrue();
    assertThat(complete().sections().order()).isTrue();
  }

  @Test
  void hiddenSectionsAreNotRequiredToPublish() {
    LandingConfig c =
        edit(
            n -> {
              hide(n, "hero", "benefits", "preview", "socialProof");
              n.putObject("hero").put("offerPrice", 899);
              n.putArray("benefits");
              n.putObject("preview");
            });
    assertThat(LandingValidator.publishable(c)).isEmpty();
  }

  @Test
  void hiddenPackageCardsStillNeedPackagesWhileOrderingIsOn() {
    LandingConfig c = edit(n -> { hide(n, "packages"); n.putArray("packages"); });
    assertThat(LandingValidator.publishable(c)).contains("Landing page: add at least one package.");
  }

  @Test
  void withOrderingHiddenOnlyAPriceIsNeeded() {
    LandingConfig c =
        edit(
            n -> {
              hide(n, "hero", "packages", "order");
              n.putObject("hero");
              n.putArray("packages");
              n.remove("advanceAmount");
              n.putObject("delivery");
            });
    assertThat(LandingValidator.publishable(c)).singleElement().asString().contains("shop list");
  }

  @Test
  void hiddenPreviewOrdersWithPlainDefaultsAndKeepsItsSetup() {
    LandingConfig c = edit(n -> { hide(n, "preview"); n.putObject("preview"); });
    LandingConfig ordering = c.forOrdering();
    assertThat(ordering.preview().designs()).hasSize(1);
    assertThat(ordering.preview().fonts()).containsExactly(LandingFonts.DEFAULT);
    assertThat(ordering.preview().maxChars()).isPositive();
    assertThat(c.preview().designs()).isEmpty();
    LandingConfig shown = complete();
    assertThat(shown.forOrdering()).isSameAs(shown);
  }

  @Test
  void hiddenStateSurvivesTheEditor() {
    LandingConfig c = edit(n -> hide(n, "socialProof", "gallery"));
    LandingConfig again = LandingForm.from(c).toConfig();
    assertThat(again.sections().socialProof()).isFalse();
    assertThat(again.sections().gallery()).isFalse();
    assertThat(again.sections().hero()).isTrue();
  }

  @Test
  void onlySiteFontsAreAllowed() {
    LandingConfig c = edit(n -> n.withObject("preview").putArray("fonts").add("Evil Font"));
    assertThat(LandingValidator.basic(c, Set.of(IMAGE))).contains("Landing page: unknown font.");
  }

  @Test
  void imageReferencesCannotCarryPaths() {
    LandingConfig c = edit(n -> n.withObject("hero").put("heroImage", "../../etc/passwd"));
    assertThat(LandingValidator.basic(c, Set.of("../../etc/passwd"))).isNotEmpty();
  }

  @Test
  void packagesCanBeAnyNumberUpToTwelve() {
    var one =
        edit(
            n -> {
              var list = n.putArray("packages");
              list.addObject().put("name", "এক").put("price", 500).put("isPopular", true);
            });
    assertThat(LandingValidator.publishable(one)).isEmpty();
    var thirteen =
        edit(
            n -> {
              var list = n.putArray("packages");
              for (int i = 0; i < 13; i++)
                list.addObject().put("name", "ক").put("price", 500).put("isPopular", i == 0);
            });
    assertThat(LandingValidator.basic(thirteen, Set.of(IMAGE)))
        .contains("Landing page: you can add up to 12 packages.");
  }

  @Test
  void firstPackageIsMarkedWhenTheMarkedOneIsGone() {
    LandingForm form = LandingForm.from(complete());
    form.popularPackage = 2;
    form.packages.get(2).name = "";
    form.packages.get(2).price = null;
    assertThat(form.toConfig().packages().get(0).isPopular()).isTrue();
  }

  @Test
  void customerPhotosGoUpToTwelve() {
    var twelve = edit(n -> { var list = n.withObject("socialProof").putArray("customerImages"); for (int i = 0; i < 12; i++) list.add(IMAGE); });
    assertThat(LandingValidator.basic(twelve, Set.of(IMAGE))).isEmpty();
    var thirteen = edit(n -> { var list = n.withObject("socialProof").putArray("customerImages"); for (int i = 0; i < 13; i++) list.add(IMAGE); });
    assertThat(LandingValidator.basic(thirteen, Set.of(IMAGE)))
        .contains("Landing page: you can add up to 12 customer photos.");
    assertThat(LandingForm.fieldForUpload("landingUpload/socialProof/customerImages(11)"))
        .isEqualTo("socialProof.customerImages[11]");
  }

  @Test
  void fontRowsKeepOrderAndDropBlanksAndRepeats() {
    LandingForm form = new LandingForm();
    form.preview.fonts = new java.util.ArrayList<>(List.of("Galada", "", "Hind Siliguri", "Galada"));
    assertThat(form.toConfig().preview().fonts()).containsExactly("Galada", "Hind Siliguri");
  }

  @Test
  void newProductStartsWithTheDefaultFont() {
    assertThat(LandingForm.from(null).toConfig().preview().fonts()).containsExactly(LandingFonts.DEFAULT);
  }

  @Test
  void firstHeroImageLeftBecomesTheMainOne() {
    LandingForm form = new LandingForm();
    form.hero.heroImage = "";
    form.hero.moreImages = new java.util.ArrayList<>(List.of("", IMAGE, "22222222-2222-2222-2222-222222222222.jpg"));
    var hero = form.toConfig().hero();
    assertThat(hero.heroImage()).isEqualTo(IMAGE);
    assertThat(hero.moreImages()).containsExactly("22222222-2222-2222-2222-222222222222.jpg");
  }

  @Test
  void heroImagesGoUpToEightAndMustBelongToTheProduct() {
    var eight = edit(n -> { var list = n.withObject("hero").putArray("moreImages"); for (int i = 0; i < 7; i++) list.add(IMAGE); });
    assertThat(LandingValidator.basic(eight, Set.of(IMAGE))).isEmpty();
    assertThat(LandingValidator.basic(eight, Set.of())).isNotEmpty();
    var nine = edit(n -> { var list = n.withObject("hero").putArray("moreImages"); for (int i = 0; i < 8; i++) list.add(IMAGE); });
    assertThat(LandingValidator.basic(nine, Set.of(IMAGE))).contains("Landing page: you can add up to 8 hero images.");
    assertThat(LandingForm.fieldForUpload("landingUpload/hero/moreImages(6)")).isEqualTo("hero.moreImages[6]");
  }

  @Test
  void firstReviewLeftTakesTheMainPlaceAndBlankRowsDrop() {
    LandingForm form = new LandingForm();
    LandingForm.ReviewRow blank = new LandingForm.ReviewRow();
    LandingForm.ReviewRow kept = new LandingForm.ReviewRow();
    kept.text = "ভালো";
    kept.name = "করিম";
    form.socialProof.moreReviews = new java.util.ArrayList<>(List.of(blank, kept));
    var proof = form.toConfig().socialProof();
    assertThat(proof.reviewText()).isEqualTo("ভালো");
    assertThat(proof.moreReviews()).isEmpty();
    assertThat(proof.allReviews()).hasSize(1);
  }

  @Test
  void everyReviewNeedsItsTextAndName() {
    LandingConfig noName =
        edit(n -> n.withObject("socialProof").putArray("moreReviews").addObject().put("text", "ভালো"));
    assertThat(LandingValidator.publishable(noName)).contains("Landing page: a review needs the reviewer's name.");
    LandingConfig noText =
        edit(n -> n.withObject("socialProof").putArray("moreReviews").addObject().put("name", "করিম"));
    assertThat(LandingValidator.publishable(noText)).contains("Landing page: a review needs its text.");
  }

  @Test
  void reviewsGoUpToTen() {
    var eleven = edit(n -> { var list = n.withObject("socialProof").putArray("moreReviews"); for (int i = 0; i < 10; i++) list.addObject().put("text", "ক").put("name", "খ"); });
    assertThat(LandingValidator.basic(eleven, Set.of(IMAGE))).contains("Landing page: you can add up to 10 reviews.");
  }

  @Test
  void firstUrgencyLineLeftTakesTheMainPlace() {
    LandingForm form = new LandingForm();
    form.urgencyLine = " ";
    form.moreUrgencyLines = new java.util.ArrayList<>(List.of("", "এক", "দুই"));
    LandingConfig config = form.toConfig();
    assertThat(config.urgencyLine()).isEqualTo("এক");
    assertThat(config.moreUrgencyLines()).containsExactly("দুই");
    assertThat(config.allUrgencyLines()).containsExactly("এক", "দুই");
  }

  @Test
  void urgencyLinesGoUpToFive() {
    var six = edit(n -> { var list = n.putArray("moreUrgencyLines"); for (int i = 0; i < 5; i++) list.add("ক"); });
    assertThat(LandingValidator.basic(six, Set.of(IMAGE))).contains("Landing page: you can add up to 5 urgency lines.");
  }

  @Test
  void benefitsCanBeAnyNumberUpToTwenty() {
    assertThat(LandingValidator.publishable(edit(n -> n.putArray("benefits").add("এক")))).isEmpty();
    var twenty = edit(n -> { var list = n.putArray("benefits"); for (int i = 0; i < 20; i++) list.add("লাইন"); });
    assertThat(LandingValidator.publishable(twenty)).isEmpty();
    assertThat(LandingValidator.basic(twenty, Set.of(IMAGE))).isEmpty();
  }

  @Test
  void aShownBenefitsSectionNeedsAtLeastOne() {
    assertThat(LandingValidator.publishable(edit(n -> n.putArray("benefits"))))
        .contains("Landing page: add at least one benefit.");
  }

  @Test
  void tooManyBenefitsAreExplainedInPlainWords() {
    var c = edit(n -> { var list = n.putArray("benefits"); for (int i = 0; i < 21; i++) list.add("লাইন"); });
    assertThat(LandingValidator.basic(c, Set.of(IMAGE))).contains("Landing page: you can add up to 20 benefits.");
  }

  @Test
  void requiresExactlyOnePopularPackage() {
    LandingConfig c =
        edit(n -> n.withArray("packages").forEach(p -> ((ObjectNode) p).put("isPopular", false)));
    assertThat(LandingValidator.publishable(c))
        .contains("Landing page: mark exactly one package as most popular.");
  }

  @Test
  void oldPriceMustBeAboveCurrentPrice() {
    LandingConfig c = edit(n -> n.withObject("hero").put("oldPrice", 899));
    assertThat(LandingValidator.publishable(c))
        .contains("Landing page: the old price must be higher than the offer price.");
  }

  @Test
  void draftNeedsOnlyTheBasics() {
    LandingConfig empty = LandingConfigCodec.read("{\"enabled\":true}");
    assertThat(LandingValidator.basic(empty, Set.of())).isEmpty();
    assertThat(LandingValidator.publishable(empty)).isNotEmpty();
  }

  @Test
  void aCountNeedsItsProofLine() {
    LandingConfig c = edit(n -> n.withObject("socialProof").put("countNumber", 500));
    assertThat(LandingValidator.publishable(c))
        .contains("Landing page: the customer count and its proof line go together.");
  }

  @Test
  void priceBoundsAreEnforced() {
    LandingConfig c = edit(n -> ((ObjectNode) n.withArray("packages").get(0)).put("price", 2000000));
    assertThat(LandingValidator.basic(c, Set.of(IMAGE))).isNotEmpty();
  }

  @Test
  void formDropsBlankRowsAndRoundTrips() {
    LandingForm form = LandingForm.from(complete());
    assertThat(form.toConfig()).isEqualTo(complete());
    assertThat(form.packages.stream().map(p -> p.name)).hasSize(4).endsWith((String) null);
    assertThat(List.of(new PackageTier("ক", "", 1, null, false))).hasSize(1);
  }
}
