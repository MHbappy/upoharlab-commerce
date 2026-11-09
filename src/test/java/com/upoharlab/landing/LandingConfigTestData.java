package com.upoharlab.landing;

import com.upoharlab.landing.LandingConfig.*;
import java.util.List;

/** A complete, publishable configuration for tests to adjust. */
final class LandingConfigTestData {
  static final String IMAGE = "11111111-1111-4111-8111-111111111111.jpg";

  private LandingConfigTestData() {}

  static LandingConfig complete() {
    return new LandingConfig(
        true,
        new Hero("শিরোনাম", "উপশিরোনাম", IMAGE, 1099, 899),
        List.of("এক", "দুই", "তিন", "চার"),
        new SocialProof(List.of(), "", "", "", null, ""),
        List.of(
            new PackageTier("ক", "বিবরণ", 899, 1099, false),
            new PackageTier("খ", "বিবরণ", 999, 1199, true),
            new PackageTier("গ", "বিবরণ", 1699, null, false)),
        "",
        List.of(
            new Option(
                "মাপ",
                OptionType.SIZE,
                List.of(new OptionValue("ছোট", "", 0), new OptionValue("বড়", "", 200)))),
        List.of(new Upsell("র‍্যাপিং", 80), new Upsell("বক্স", 60)),
        100,
        new Delivery(60, 110),
        new Preview(
            List.of(new Design("ক্লাসিক", IMAGE, 500, 250, 800, 110, 40, "#2B211C", "#FFFFFF", 1000, 500)),
            List.of("Noto Sans Bengali", "Galada"),
            "রহমান ভিলা",
            24),
        null);
  }
}
