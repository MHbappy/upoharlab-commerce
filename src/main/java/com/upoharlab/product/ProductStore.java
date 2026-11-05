package com.upoharlab.product;

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

  public record Page(long id, ProductForm details, List<Photo> photos) {}

  public List<Page> list(boolean admin) {
    return db
        .query(
            "SELECT * FROM product "
                + (admin ? "" : "WHERE status='ACTIVE' ")
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

  private record Row(long id, ProductForm details) {}

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
    return new Row(r.getLong("id"), f);
  }

  /**
   * Must run after the product query has finished, never inside its row mapper. A nested query
   * holds one pooled connection while waiting for another, which freezes the site under load.
   */
  private Page withPhotos(Row row) {
    return new Page(
        row.id(),
        row.details(),
        db.query(
            "SELECT * FROM product_image WHERE product_id=? ORDER BY sort_order,id",
            (i, n) ->
                new Photo(
                    i.getLong("id"),
                    i.getString("filename"),
                    i.getString("alt_bn"),
                    i.getInt("sort_order")),
            row.id()));
  }

  @Transactional
  public Long save(Long id, ProductForm f, Errors errors) {
    if (id != null) {
      db.queryForObject("SELECT id FROM product WHERE id=? FOR UPDATE", Long.class, id);
      if (get(id).details().getIndoorOnly()) f.setIndoorOnly(true);
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
          List.of("shortDescription", "includes", "material", "size", "engravingHelp")) {
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
            "status", "photos", "Save as draft and add at least one real photo before publishing.");
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
    db.queryForObject("SELECT id FROM product WHERE id=? FOR UPDATE", Long.class, product);
    Page p = get(product);
    if (!p.details().getStatus().equals("DRAFT") && p.photos().size() <= 1)
      throw new IllegalArgumentException("Set the product to draft before removing its last photo.");
    db.update("DELETE FROM product_image WHERE id=? AND product_id=?", photo, product);
  }

  @Transactional
  public void movePhoto(long product, long photo, String direction) {
    db.queryForObject("SELECT id FROM product WHERE id=? FOR UPDATE", Long.class, product);
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
