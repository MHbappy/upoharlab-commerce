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
    assertThat(LandingValidator.publishable(c)).contains("Landing page: enter exactly 3 packages.");
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
  void requiresExactlyFourBenefits() {
    LandingConfig c = edit(n -> n.putArray("benefits").add("এক"));
    assertThat(LandingValidator.publishable(c)).contains("Landing page: enter exactly 4 benefits.");
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
    assertThat(form.packages.stream().map(p -> p.name)).hasSize(3);
    assertThat(List.of(new PackageTier("ক", "", 1, null, false))).hasSize(1);
  }
}
