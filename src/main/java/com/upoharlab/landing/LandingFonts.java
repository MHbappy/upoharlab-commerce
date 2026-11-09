package com.upoharlab.landing;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fonts served by this site. Only these names can reach a page, an order or an SVG. */
public final class LandingFonts {
  public static final String DEFAULT = "Noto Sans Bengali";
  public static final Map<String, String> FILES;

  static {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("Noto Sans Bengali", "NotoSansBengali.woff2");
    files.put("Hind Siliguri", "HindSiliguri.woff2");
    files.put("Noto Serif Bengali", "NotoSerifBengali.woff2");
    files.put("Galada", "Galada.woff2");
    FILES = java.util.Collections.unmodifiableMap(files);
  }

  private LandingFonts() {}

  public static boolean allowed(String family) {
    return family != null && FILES.containsKey(family);
  }
}
