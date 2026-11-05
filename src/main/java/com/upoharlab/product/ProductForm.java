package com.upoharlab.product;

public class ProductForm {
  @jakarta.validation.constraints.NotBlank
  @jakarta.validation.constraints.Size(max = 200)
  private String title;

  @jakarta.validation.constraints.NotBlank
  @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
  @jakarta.validation.constraints.Size(max = 150)
  private String slug;

  @jakarta.validation.constraints.Size(max = 1000)
  private String shortDescription;

  @jakarta.validation.constraints.Size(max = 10000)
  private String description;

  @jakarta.validation.constraints.Size(max = 2000)
  private String includes;

  @jakarta.validation.constraints.Size(max = 1000)
  private String material;

  @jakarta.validation.constraints.Size(max = 1000)
  private String size;

  @jakarta.validation.constraints.Size(max = 2000)
  private String engravingHelp;

  @jakarta.validation.constraints.Size(max = 2000)
  private String care;

  @jakarta.validation.constraints.Min(1)
  @jakarta.validation.constraints.Max(1000000)
  private Integer price;

  @jakarta.validation.constraints.Min(0)
  @jakarta.validation.constraints.Max(365)
  private Integer makingDaysMin;

  @jakarta.validation.constraints.Min(0)
  @jakarta.validation.constraints.Max(365)
  private Integer makingDaysMax;

  private boolean indoorOnly;

  @jakarta.validation.constraints.Pattern(regexp = "DRAFT|ACTIVE|PAUSED")
  private String status = "DRAFT";

  @jakarta.validation.constraints.Min(0)
  private int sortOrder;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String value) {
    this.slug = value;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public void setShortDescription(String value) {
    this.shortDescription = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getIncludes() {
    return includes;
  }

  public void setIncludes(String value) {
    this.includes = value;
  }

  public String getMaterial() {
    return material;
  }

  public void setMaterial(String value) {
    this.material = value;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String value) {
    this.size = value;
  }

  public String getEngravingHelp() {
    return engravingHelp;
  }

  public void setEngravingHelp(String value) {
    this.engravingHelp = value;
  }

  public String getCare() {
    return care;
  }

  public void setCare(String value) {
    this.care = value;
  }

  public Integer getPrice() {
    return price;
  }

  public void setPrice(Integer value) {
    this.price = value;
  }

  public Integer getMakingDaysMin() {
    return makingDaysMin;
  }

  public void setMakingDaysMin(Integer value) {
    this.makingDaysMin = value;
  }

  public Integer getMakingDaysMax() {
    return makingDaysMax;
  }

  public void setMakingDaysMax(Integer value) {
    this.makingDaysMax = value;
  }

  public boolean getIndoorOnly() {
    return indoorOnly;
  }

  public void setIndoorOnly(boolean value) {
    this.indoorOnly = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int value) {
    this.sortOrder = value;
  }
}
