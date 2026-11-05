package com.upoharlab.product;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Phones often store portrait photos rotated, with a JPEG EXIF tag saying how to display them. */
final class ExifOrientation {
  private static final int TAG_ORIENTATION = 0x0112;

  private ExifOrientation() {}

  /** Returns the orientation 1–8, or 1 when absent or unreadable. Never throws on bad input. */
  static int read(byte[] b) {
    try {
      if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return 1;
      int p = 2;
      while (p + 4 <= b.length) {
        if ((b[p] & 0xFF) != 0xFF) return 1;
        int marker = b[p + 1] & 0xFF;
        if (marker == 0xDA || marker == 0xD9) return 1;
        int length = ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
        if (length < 2) return 1;
        if (marker == 0xE1 && length >= 8 && isExif(b, p + 4))
          return readTiff(b, p + 10, Math.min(b.length, p + 2 + length));
        p += 2 + length;
      }
    } catch (IndexOutOfBoundsException ignored) {
      return 1;
    }
    return 1;
  }

  private static boolean isExif(byte[] b, int p) {
    return b[p] == 'E' && b[p + 1] == 'x' && b[p + 2] == 'i' && b[p + 3] == 'f' && b[p + 4] == 0;
  }

  private static int readTiff(byte[] b, int start, int end) {
    boolean little = b[start] == 'I' && b[start + 1] == 'I';
    if (!little && !(b[start] == 'M' && b[start + 1] == 'M')) return 1;
    if (u16(b, start + 2, little) != 42) return 1;
    long ifd = start + u32(b, start + 4, little);
    if (ifd + 2 > end) return 1;
    int entry = (int) ifd;
    int count = u16(b, entry, little);
    for (int i = 0; i < count; i++) {
      int e = entry + 2 + i * 12;
      if (e + 12 > end) return 1;
      if (u16(b, e, little) == TAG_ORIENTATION) {
        int value = u16(b, e + 8, little);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  private static int u16(byte[] b, int p, boolean little) {
    int a = b[p] & 0xFF, c = b[p + 1] & 0xFF;
    return little ? (c << 8) | a : (a << 8) | c;
  }

  private static long u32(byte[] b, int p, boolean little) {
    long high = u16(b, little ? p + 2 : p, little), low = u16(b, little ? p : p + 2, little);
    return (high << 16) | low;
  }

  static BufferedImage apply(BufferedImage image, int orientation) {
    if (orientation <= 1 || orientation > 8) return image;
    int w = image.getWidth(), h = image.getHeight();
    boolean swap = orientation >= 5;
    AffineTransform transform =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
          case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
          case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
          case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
          default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
    BufferedImage result =
        new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = result.createGraphics();
    try {
      g.drawImage(image, transform, null);
    } finally {
      g.dispose();
    }
    return result;
  }
}
