package com.upoharlab.product;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Pixel sizes of stored photos for templates ({@code @photoSize}), which the photo viewer needs
 * to zoom smoothly. Stored photos never change once written, so sizes are cached.
 */
@Component("photoSize")
public class PhotoSizes {
  private final ImageStorage images;
  private final Map<String, int[]> cache = new ConcurrentHashMap<>();

  public PhotoSizes(ImageStorage images) {
    this.images = images;
  }

  /** Width of a stored photo, or null when it cannot be read. Accepts "thumb-" names too. */
  public Integer width(String filename) {
    int[] size = size(filename);
    return size == null ? null : size[0];
  }

  public Integer height(String filename) {
    int[] size = size(filename);
    return size == null ? null : size[1];
  }

  private int[] size(String filename) {
    if (filename == null || filename.isBlank()) return null;
    String full = filename.replaceFirst("^thumb-", "");
    int[] known = cache.get(full);
    if (known != null) return known;
    try {
      int[] read = images.dimensions(full);
      cache.put(full, read);
      return read;
    } catch (IOException | IllegalArgumentException e) {
      return null;
    }
  }
}
