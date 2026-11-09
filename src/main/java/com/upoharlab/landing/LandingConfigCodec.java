package com.upoharlab.landing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Converts the stored JSON to and from typed records. Unknown or malformed data is rejected. */
public final class LandingConfigCodec {
  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private LandingConfigCodec() {}

  public static LandingConfig read(String json) {
    try {
      return MAPPER.readValue(json, LandingConfig.class);
    } catch (JacksonException ex) {
      // The parser's message can quote the input, so it is deliberately not passed on.
      throw new IllegalArgumentException("Invalid landing page configuration.");
    }
  }

  public static String write(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  public static <T> T readAs(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Invalid saved data.");
    }
  }

  /**
   * JSON that is safe inside an HTML script data block: nothing in it can close the block, open a
   * comment or start an entity, and the result is still valid JSON.
   */
  public static String forScriptBlock(Object value) {
    StringBuilder out = new StringBuilder();
    for (char c : write(value).toCharArray()) {
      if (c == '<' || c == '>' || c == '&' || c == '\'' || c == ' ' || c == ' ')
        out.append(String.format("\\u%04x", (int) c));
      else out.append(c);
    }
    return out.toString();
  }

  /** Changes whenever anything that affects the customer's total or choices changes. */
  public static String fingerprint(LandingConfig config) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(write(config).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
