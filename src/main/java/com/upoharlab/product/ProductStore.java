package com.upoharlab.product;

import com.upoharlab.landing.LandingConfig;
import com.upoharlab.landing.LandingConfigCodec;
import com.upoharlab.landing.LandingValidator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductStore {
  private final JdbcTemplate db;
  private final NamedParameterJdbcTemplate named;

  public ProductStore(JdbcTemplate db) {
    this.db = db;
    this.named = new NamedParameterJdbcTemplate(db);
  }

  public record Photo(long id, String filename, String alt, int sortOrder) {}

  /** {@code landing} is null for products without a landing page configuration. */
  public record Page(
      long id,
      ProductForm details,
      List<Photo> photos,
      LandingConfig landing,
      List<Photo> landingPhotos) {
    /** The landing page as customers order against it, or null without one. */
    public LandingConfig orderLanding() {
      return landing == null ? null : landing.forOrdering();
    }

    public boolean landingEnabled() {
      return landing != null && landing.enabled();
    }

    /** Where customers should be sent to buy this product. */
    public String orderPath() {
      return "/order/" + details.getSlug();
    }

    /**
     * The picture shown in listings: the first gallery photo, otherwise the landing page's main
     * image, so a product sold only through its landing page never shows an empty card.
     */
    public String cardImage() {
      if (!photos.isEmpty()) return photos.get(0).filename();
      String hero = landing == null || landing.hero() == null ? null : landing.hero().heroImage();
      return hero == null || hero.isBlank() ? null : hero;
    }

    public String cardImageAlt() {
      return photos.isEmpty() ? details.getTitle() : photos.get(0).alt();
    }

    /** Whole-percent discount for the card badge, or null without a real discount. */
    public Integer cardDiscountPercent() {
      Integer old = cardOldPrice();
      if (old == null) return null;
      int percent = (int) Math.round((old - details.getPrice()) * 100.0 / old);
      return percent >= 1 ? percent : null;
    }

    /** The landing page's crossed-out price, only when it is really above the listed price. */
    public Integer cardOldPrice() {
      Integer old = landing == null || landing.hero() == null ? null : landing.hero().oldPrice();
      Integer price = details.getPrice();
      return old != null && price != null && old > price ? old : null;
    }
  }

  public List<Page> list(boolean admin) {
    return db
        .query(
            "SELECT * FROM product "
                + (admin ? "" : "WHERE status='ACTIVE' AND landing_config->>'enabled'='true' ")
                + "ORDER BY sort_order,id",
            (r, n) -> row(r))
        .stream()
        .map(this::withPhotos)
        .toList();
  }

  public Page get(long id) {
    return db.query("SELECT * FROM product WHERE id=?", (r, n) -> row(r), id).stream()
        .findFirst()
        .map(this::withPhotos)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public Page bySlug(String slug) {
    return db
        .query("SELECT * FROM product WHERE slug=? AND status<>'DRAFT'", (r, n) -> row(r), slug)
        .stream()
        .findFirst()
        .map(this::withPhotos)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private record Row(long id, ProductForm details, LandingConfig landing) {}

  private Row row(ResultSet r) throws SQLException {
    ProductForm f = new ProductForm();
    f.setTitle(r.getString("title_bn"));
    f.setSlug(r.getString("slug"));
    f.setShortDescription(r.getString("short_description_bn"));
    f.setDescription(r.getString("description_bn"));
    f.setIncludes(r.getString("includes_bn"));
    f.setMaterial(r.getString("material_bn"));
    f.setSize(r.getString("size_bn"));
    f.setEngravingHelp(r.getString("engraving_help_bn"));
    f.setCare(r.getString("care_bn"));
    f.setQuantityHelp(r.getString("quantity_help_bn"));
    f.setPrice((Integer) r.getObject("price"));
    f.setMakingDaysMin((Integer) r.getObject("making_days_min"));
    f.setMakingDaysMax((Integer) r.getObject("making_days_max"));
    f.setIndoorOnly(r.getBoolean("indoor_only"));
    f.setStatus(r.getString("status"));
    f.setSortOrder(r.getInt("sort_order"));
    return new Row(r.getLong("id"), f, landing(r.getString("landing_config")));
  }

  /** A damaged configuration must not take the shop down, so that product simply has no page. */
  private static LandingConfig landing(String json) {
    if (json == null) return null;
    try {
      return LandingConfigCodec.read(json);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * Must run after the product query has finished, never inside its row mapper. A nested query
   * holds one pooled connection while waiting for another, which freezes the site under load.
   */
  private Page withPhotos(Row row) {
    return new Page(
        row.id(),
        row.details(),
        photos(row.id(), "GALLERY"),
        row.landing(),
        photos(row.id(), "LANDING"));
  }

  private List<Photo> photos(long product, String purpose) {
    return db.query(
        "SELECT * FROM product_image WHERE product_id=? AND purpose=? ORDER BY sort_order,id",
        (i, n) ->
            new Photo(
                i.getLong("id"),
                i.getString("filename"),
                i.getString("alt_bn"),
                i.getInt("sort_order")),
        product,
        purpose);
  }

  /** The caller validates first. Cast because deployments differ in how they send strings. */
  public void saveLanding(long id, LandingConfig config) {
    db.update(
        "UPDATE product SET landing_config=CAST(? AS jsonb), updated_at=now() WHERE id=?",
        LandingConfigCodec.write(config),
        id);
  }

  /** Holds the product row for this transaction. An unknown product is a 404. */
  public void lock(long id) {
    if (db.queryForList("SELECT id FROM product WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @Transactional
  public Long save(Long id, ProductForm f, Errors errors) {
    return save(id, f, errors, true);
  }

  /** {@code detailsShown} is false when the page hides Product details, making that text optional. */
  public Long save(Long id, ProductForm f, Errors errors, boolean detailsShown) {
    if (id != null) {
      lock(id);
      if (get(id).details().getIndoorOnly()) f.setIndoorOnly(true);
      // No longer edited, but stored text is kept.
      f.setQuantityHelp(get(id).details().getQuantityHelp());
      if (f.getPrice() == null) f.setPrice(get(id).details().getPrice());
    }
    if (db.queryForObject(
            "SELECT count(*) FROM product WHERE slug=? AND id<>?",
            Integer.class,
            f.getSlug(),
            id == null ? 0 : id)
        > 0) errors.rejectValue("slug", "duplicate", "This URL is already used.");
    if (f.getMakingDaysMin() != null
        && f.getMakingDaysMax() != null
        && f.getMakingDaysMax() < f.getMakingDaysMin())
      errors.rejectValue("makingDaysMax", "range", "Maximum days must be at least the minimum.");
    if (!"DRAFT".equals(f.getStatus())) {
      for (String field :
          detailsShown
              ? List.of("shortDescription", "includes", "material", "size", "engravingHelp")
              : List.<String>of()) {
        String value =
            switch (field) {
              case "shortDescription" -> f.getShortDescription();
              case "includes" -> f.getIncludes();
              case "material" -> f.getMaterial();
              case "size" -> f.getSize();
              default -> f.getEngravingHelp();
            };
        if (value == null || value.isBlank())
          errors.rejectValue(field, "required", "Complete this before publishing.");
      }
      if (f.getPrice() == null)
        errors.rejectValue("price", "required", "Set a price before publishing.");
      if (f.getMakingDaysMin() == null)
        errors.rejectValue("makingDaysMin", "required", "Set making time.");
      if (f.getMakingDaysMax() == null)
        errors.rejectValue("makingDaysMax", "required", "Set making time.");
      if (id == null || get(id).photos().isEmpty())
        errors.rejectValue(
            "status", "photos", "Choose at least one product photo before publishing.");
    }
    if (errors.hasErrors()) return id;
    var params = new BeanPropertySqlParameterSource(f);
    if (id == null)
      return named.queryForObject(
          "INSERT INTO"
              + " product(title_bn,slug,short_description_bn,description_bn,includes_bn,material_bn,size_bn,engraving_help_bn,care_bn,quantity_help_bn,price,making_days_min,making_days_max,indoor_only,status,sort_order)"
              + " VALUES"
              + " (:title,:slug,:shortDescription,:description,:includes,:material,:size,:engravingHelp,:care,:quantityHelp,:price,:makingDaysMin,:makingDaysMax,:indoorOnly,:status,:sortOrder)"
              + " RETURNING id",
          params,
          Long.class);
    var map = new MapSqlParameterSource();
    for (String n : params.getReadablePropertyNames()) map.addValue(n, params.getValue(n));
    map.addValue("id", id);
    named.update(
        "UPDATE product SET"
            + " title_bn=:title,slug=:slug,short_description_bn=:shortDescription,description_bn=:description,includes_bn=:includes,material_bn=:material,size_bn=:size,engraving_help_bn=:engravingHelp,care_bn=:care,quantity_help_bn=:quantityHelp,price=:price,making_days_min=:makingDaysMin,making_days_max=:makingDaysMax,indoor_only=:indoorOnly,status=:status,sort_order=:sortOrder,updated_at=now()"
            + " WHERE id=:id",
        map);
    return id;
  }

  @Transactional
  public void removePhoto(long product, long photo) {
    lock(product);
    Page p = get(product);
    boolean gallery = p.photos().stream().anyMatch(item -> item.id() == photo);
    if (gallery && !p.details().getStatus().equals("DRAFT") && p.photos().size() <= 1)
      throw new IllegalArgumentException("Set the product to draft before removing its last photo.");
    if (p.landing() != null)
      for (Photo item : java.util.stream.Stream.concat(p.photos().stream(), p.landingPhotos().stream()).toList())
        if (item.id() == photo && LandingValidator.images(p.landing()).contains(item.filename()))
          throw new IllegalArgumentException(
              "This image is used on the landing page. Choose a different one there first.");
    db.update("DELETE FROM product_image WHERE id=? AND product_id=?", photo, product);
  }

  @Transactional
  public void movePhoto(long product, long photo, String direction) {
    lock(product);
    var photos = get(product).photos();
    for (int i = 0; i < photos.size(); i++)
      if (photos.get(i).id() == photo) {
        int next = i + ("up".equals(direction) ? -1 : 1);
        if (next < 0 || next >= photos.size()) return;
        Collections.swap(photos, i, next);
        for (int j = 0; j < photos.size(); j++)
          db.update("UPDATE product_image SET sort_order=? WHERE id=?", j, photos.get(j).id());
        return;
      }
  }
}
