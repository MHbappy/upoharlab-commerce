package com.upoharlab.product;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorage {
  private final Path root;
  private final JdbcTemplate db;
  private final ProductStore products;

  public ImageStorage(
      @Value("${app.uploads-directory}") String root, JdbcTemplate db, ProductStore products) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.db = db;
    this.products = products;
  }

  @Transactional
  public void upload(long product, MultipartFile file, String alt) throws IOException {
    products.lock(product);
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new IllegalArgumentException("Choose a JPEG or PNG up to 5 MB.");
    if (alt == null || alt.isBlank() || alt.length() > 250)
      throw new IllegalArgumentException("Add a short photo description (up to 250 characters).");
    BufferedImage decoded;
    try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext())
        throw new IllegalArgumentException("This is not a readable JPEG or PNG.");
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
          throw new IllegalArgumentException("Only JPEG and PNG photos are supported.");
        reader.setInput(input, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || (long) w * h > 20000000)
          throw new IllegalArgumentException("The photo must be at most 20 megapixels.");
        decoded = reader.read(0);
        if (decoded == null) throw new IllegalArgumentException("The photo cannot be read.");
        if (format.equalsIgnoreCase("JPEG"))
          decoded = ExifOrientation.apply(decoded, ExifOrientation.read(file.getBytes()));
      } finally {
        reader.dispose();
      }
    }
    Files.createDirectories(root);
    String name = UUID.randomUUID() + ".jpg";
    Path full = root.resolve(name), thumb = root.resolve("thumb-" + name);
    try {
      write(decoded, 1200, full);
      write(decoded, 600, thumb);
      db.update(
          "INSERT INTO product_image(product_id,filename,alt_bn,sort_order) VALUES (?,?,?,(SELECT"
              + " coalesce(max(sort_order),-1)+1 FROM product_image WHERE product_id=?))",
          product,
          name,
          alt.strip(),
          product);
      org.springframework.transaction.support.TransactionSynchronizationManager
          .registerSynchronization(
              new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                  if (status != STATUS_COMMITTED)
                    try {
                      Files.deleteIfExists(full);
                      Files.deleteIfExists(thumb);
                    } catch (IOException ignored) {
                    }
                }
              });
    } catch (IOException | RuntimeException ex) {
      Files.deleteIfExists(full);
      Files.deleteIfExists(thumb);
      throw ex;
    }
  }

  private void write(BufferedImage image, int max, Path path) throws IOException {
    double ratio = Math.min(1d, (double) max / Math.max(image.getWidth(), image.getHeight()));
    BufferedImage result =
        new BufferedImage(
            Math.max(1, (int) (image.getWidth() * ratio)),
            Math.max(1, (int) (image.getHeight() * ratio)),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D g = result.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, result.getWidth(), result.getHeight());
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), null);
    } finally {
      g.dispose();
    }
    if (!ImageIO.write(result, "JPEG", path.toFile()))
      throw new IOException("JPEG writer unavailable");
  }

  public void removeFiles(String filename) throws IOException {
    Files.deleteIfExists(path(filename));
    Files.deleteIfExists(path("thumb-" + filename));
  }

  public Path path(String filename) {
    if (!filename.matches("(?:thumb-)?[0-9a-f-]{36}\\.jpg"))
      throw new IllegalArgumentException("Invalid image path");
    return root.resolve(filename);
  }

  public boolean publiclyVisible(String filename) {
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM product_image i JOIN product p ON p.id=i.product_id WHERE"
                + " i.filename=? AND p.status IN ('ACTIVE','PAUSED'))",
            Boolean.class,
            filename.replaceFirst("^thumb-", "")));
  }

  public boolean exists(String filename) {
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM product_image WHERE filename=?)",
            Boolean.class,
            filename.replaceFirst("^thumb-", "")));
  }
}
