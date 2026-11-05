package com.upoharlab.product;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 150)
  private String slug;

  @Column(name = "title_bn", nullable = false, length = 200)
  private String title;

  @Column(name = "short_description_bn", columnDefinition = "text")
  private String shortDescription;

  @Column(name = "description_bn", columnDefinition = "text")
  private String description;

  @Column(name = "includes_bn", columnDefinition = "text")
  private String includes;

  @Column(name = "material_bn", columnDefinition = "text")
  private String material;

  @Column(name = "size_bn", columnDefinition = "text")
  private String size;

  @Column(name = "engraving_help_bn", columnDefinition = "text")
  private String engravingHelp;

  @Column(name = "care_bn", columnDefinition = "text")
  private String care;

  @Column(name = "indoor_only", nullable = false)
  private boolean indoorOnly;

  private Integer price;

  @Column(name = "making_days_min")
  private Integer makingDaysMin;

  @Column(name = "making_days_max")
  private Integer makingDaysMax;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Status status = Status.DRAFT;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Product() {}

  public enum Status {
    DRAFT,
    ACTIVE,
    PAUSED
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public Status getStatus() {
    return status;
  }
}
