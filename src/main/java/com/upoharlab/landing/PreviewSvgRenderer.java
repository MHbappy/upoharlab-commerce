package com.upoharlab.landing;

import com.upoharlab.product.ImageStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Builds an order's SVG from its saved parameters alone. Every value is checked or escaped here,
 * and images and fonts are embedded so the file is complete when opened anywhere.
 */
@Component
public class PreviewSvgRenderer {
  private static final String COLOR = "#[0-9a-fA-F]{6}";
  private final ImageStorage images;

  public PreviewSvgRenderer(ImageStorage images) {
    this.images = images;
  }

  /** {@code withBase} false gives only the text at its real position, for the laser. */
  public String render(LandingSelection s, boolean withBase) throws IOException {
    return svg(s, withBase && hasBase(s) ? imageData(s.design().baseImage()) : null, fontData(s.font()));
  }

  private static boolean hasBase(LandingSelection s) {
    return s.design().baseImage() != null && !s.design().baseImage().isBlank();
  }

  /** Separate from file access so that escaping can be tested directly. */
  static String svg(LandingSelection s, String imageHref, String fontHref) {
    LandingConfig.Design d = s.design();
    String font = LandingFonts.allowed(s.font()) ? s.font() : "Noto Sans Bengali";
    String fill = color(d.color(), "#000000");
    StringBuilder out = new StringBuilder();
    out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
        .append(d.imageWidth()).append(' ').append(d.imageHeight())
        .append("\" width=\"").append(d.imageWidth())
        .append("\" height=\"").append(d.imageHeight()).append("\">");
    if (fontHref != null)
      out.append("<style>@font-face{font-family:\"").append(font)
          .append("\";src:url(").append(fontHref).append(")}</style>");
    if (imageHref != null)
      out.append("<image href=\"").append(imageHref)
          .append("\" width=\"100%\" height=\"100%\" preserveAspectRatio=\"xMidYMid slice\"/>");
    out.append("<text x=\"").append(d.textX())
        .append("\" y=\"").append(d.textY())
        .append("\" font-family=\"").append(escape(font))
        .append("\" font-size=\"").append(s.fontSize())
        .append("\" fill=\"").append(fill)
        .append("\" text-anchor=\"middle\" dominant-baseline=\"central\">")
        .append(escape(s.text()))
        .append("</text></svg>");
    return out.toString();
  }

  private String imageData(String filename) throws IOException {
    byte[] bytes = Files.readAllBytes(images.path(filename));
    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);
  }

  private static String fontData(String family) throws IOException {
    String file = LandingFonts.FILES.get(family);
    if (file == null) return null;
    byte[] bytes = new ClassPathResource("static/fonts/" + file).getContentAsByteArray();
    return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(bytes);
  }

  private static String color(String value, String fallback) {
    return value != null && value.matches(COLOR) ? value : fallback;
  }

  /** Also drops characters XML cannot carry at all, which would otherwise break the file. */
  static String escape(String value) {
    StringBuilder out = new StringBuilder();
    value
        .codePoints()
        .forEach(
            c -> {
              switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                  boolean legal =
                      c == 0x9 || c == 0xA || c == 0xD
                          || (c >= 0x20 && c <= 0xD7FF)
                          || (c >= 0xE000 && c <= 0xFFFD)
                          || (c >= 0x10000 && c <= 0x10FFFF);
                  if (legal) out.appendCodePoint(c);
                }
              }
            });
    return out.toString();
  }
}
