package com.upoharlab.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every text a page, script or message uses must exist, so customers never see a raw key. */
class SiteTextCatalogTest {
  private static final Path MAIN = Path.of("src/main");
  private final SiteTextCatalog catalog = new SiteTextCatalog();

  @Test
  void defaultsLoadInPageGroupsAndAreAllSavable() {
    assertThat(catalog.size()).isGreaterThan(250);
    assertThat(catalog.groups()).extracting(SiteTextCatalog.Group::title)
        .contains("Whole site: name, menu and footer", "Home page", "Product page: order form");
    catalog.groups().forEach(g -> g.entries().forEach(e -> {
      assertThat(e.defaultText()).as(e.key()).isNotBlank();
      assertThat(SiteTexts.problem(e.defaultText())).as(e.key()).isNull();
    }));
  }

  @Test
  void everyKeyUsedInTemplatesExists() throws IOException {
    TreeSet<String> used = new TreeSet<>();
    for (Path file : files(MAIN.resolve("resources/templates"), ".html")) {
      String html = Files.readString(file, StandardCharsets.UTF_8);
      collect(Pattern.compile("#\\{([a-z0-9._-]+)"), html, used);
      collect(Pattern.compile("#messages\\.msg\\('([a-z0-9._-]+)'\\s*[,)]"), html, used);
    }
    // Keys built from a number in a loop.
    for (int n = 1; n <= 4; n++) {
      used.addAll(List.of("bulk.list" + n, "privacy.s" + n + ".title", "privacy.s" + n + ".text"));
    }
    for (int n = 1; n <= 3; n++) used.addAll(List.of("home.step" + n + ".title", "home.step" + n + ".text"));
    for (int n = 1; n <= 3; n++) used.addAll(List.of("faq.design.q" + n, "faq.design.a" + n));
    for (int n = 1; n <= 6; n++) used.addAll(List.of("home.faq" + n + ".q", "home.faq" + n + ".a"));
    for (int n = 1; n <= 3; n++) used.addAll(List.of("faq.other.q" + n, "faq.other.a" + n));
    for (int n = 1; n <= 2; n++) used.addAll(List.of("faq.money.q" + n, "faq.money.a" + n));
    assertThat(used).hasSizeGreaterThan(200);
    assertThat(used.stream().filter(k -> !catalog.contains(k))).as("missing site texts").isEmpty();
  }

  @Test
  void everyKeyUsedInJavaAndScriptsExists() throws IOException {
    TreeSet<String> used = new TreeSet<>();
    Pattern literal = Pattern.compile(
        "\"\\{?((?:order\\.error|order\\.msg|track\\.status|whatsapp|product)\\.[a-z0-9._-]+)\\}?\"");
    for (Path file : files(MAIN.resolve("java"), ".java"))
      collect(literal, Files.readString(file, StandardCharsets.UTF_8), used);
    assertThat(used).contains("order.error.text", "track.status.new", "whatsapp.design", "order.msg.review");
    assertThat(used.stream().filter(k -> !catalog.contains(k))).as("missing site texts").isEmpty();

    // The page script only receives the texts LandingPageModel sends it.
    String script = Files.readString(MAIN.resolve("resources/static/js/landing.js"));
    String model = Files.readString(MAIN.resolve("java/com/upoharlab/order/LandingPageModel.java"));
    TreeSet<String> scriptKeys = new TreeSet<>();
    collect(Pattern.compile("T\\(\"([a-z0-9._-]+)\""), script, scriptKeys);
    assertThat(scriptKeys).isNotEmpty();
    scriptKeys.forEach(k -> assertThat(model).as("sent to the script: " + k).contains("\"" + k + "\""));
  }

  @Test
  void ownerWordingIsTakenLiterallyExceptForPlaceholders() {
    assertThat(SiteTexts.format("Rahim's {0} gift", "৳৫০০")).isEqualTo("Rahim's ৳৫০০ gift");
    assertThat(SiteTexts.format("কোনো প্লেসহোল্ডার নেই, it's fine")).isEqualTo("কোনো প্লেসহোল্ডার নেই, it's fine");
    assertThat(SiteTexts.problem("দাম {0}")).isNull();
    assertThat(SiteTexts.problem("ভাঙা { বন্ধনী")).contains("Curly brackets");
    assertThat(SiteTexts.problem("x".repeat(2001))).contains("2000");
  }

  @Test
  void optionalTextsAreMarkedWithAQuestionMark() {
    var parsed = new SiteTextCatalog("## Page\na.b=এক\n?c.d=দুই\n");
    assertThat(parsed.get("a.b").optional()).isFalse();
    assertThat(parsed.get("c.d").optional()).isTrue();
    assertThat(parsed.get("c.d").group()).isEqualTo("Page");
    assertThat(catalog.get("returns.terms.note").optional()).isTrue();
  }

  private static void collect(Pattern pattern, String text, TreeSet<String> into) {
    Matcher m = pattern.matcher(text);
    while (m.find()) into.add(m.group(1));
  }

  private static List<Path> files(Path root, String suffix) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return new ArrayList<>(walk.filter(p -> p.toString().endsWith(suffix)).toList());
    }
  }
}
