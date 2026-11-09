package com.upoharlab.landing;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class PreviewSvgRendererTest {
  private static LandingSelection selection(String text, String font, String color) {
    var d = new LandingConfig.Design("ক", "", 500, 250, 800, 110, 40, color, "", 1000, 500);
    return new LandingSelection(text, font, 77, d, "প্যাক", "", 999, List.of(), List.of());
  }

  @Test
  void customerMarkupBecomesPlainText() throws Exception {
    String attack = "<script>alert(1)</script>&\"'";
    String svg = PreviewSvgRenderer.svg(selection(attack, "Galada", "#000000"), null, null);
    assertThat(svg).doesNotContain("<script>");
    var doc =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    assertThat(doc.getElementsByTagName("text").item(0).getTextContent()).isEqualTo(attack);
  }

  @Test
  void keepsConjunctsAndPosition() {
    String svg = PreviewSvgRenderer.svg(selection("ক্ষেত্র", "Galada", "#123456"), null, null);
    assertThat(svg)
        .contains(">ক্ষেত্র<", "x=\"500\"", "y=\"250\"", "font-size=\"77\"", "fill=\"#123456\"", "font-family=\"Galada\"");
  }

  @Test
  void unknownFontOrColourCannotReachTheFile() {
    String svg = PreviewSvgRenderer.svg(selection("নাম", "\"><x>", "red\"><y>"), null, null);
    assertThat(svg).doesNotContain("<x>", "<y>").contains("font-family=\"Noto Sans Bengali\"", "fill=\"#000000\"");
  }

  @Test
  void characterIllegalInXmlIsDropped() {
    assertThat(PreviewSvgRenderer.escape("ক\u0001খ")).isEqualTo("কখ");
  }
}
