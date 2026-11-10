package com.upoharlab.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Raw browser values, kept as typed for redisplay. Holds choices, never prices. */
public class LandingOrderForm {
  // Required or not depends on the product; LandingOrderValidator checks it.
  private String engraving;

  // Accepted for older forms; the current page uses one engraving field and a live preview.
  private String confirmEngraving;

  private String font;
  private String design = "0";
  private String packageIndex;
  private List<String> options = new ArrayList<>();
  private List<String> upsells = new ArrayList<>();
  private String neededBy;
  private String fontSize;

  @NotBlank(message = "{order.error.name}")
  private String name;

  @NotBlank(message = "{order.error.phone.required}")
  private String phone;

  @NotBlank(message = "{order.error.area}")
  @Pattern(regexp = "INSIDE|OUTSIDE", message = "{order.error.area}")
  private String area;

  @NotBlank(message = "{order.error.address}")
  private String address;

  private String note;
  private String website;
  private String submitToken;
  private String shownTotal;

  public String getEngraving() { return engraving; }
  public void setEngraving(String v) { engraving = v; }
  public String getConfirmEngraving() { return confirmEngraving; }
  public void setConfirmEngraving(String v) { confirmEngraving = v; }
  public String getFont() { return font; }
  public void setFont(String v) { font = v; }
  public String getDesign() { return design; }
  public void setDesign(String v) { design = v; }
  public String getPackageIndex() { return packageIndex; }
  public void setPackageIndex(String v) { packageIndex = v; }
  public List<String> getOptions() { return options; }
  public void setOptions(List<String> v) { options = v; }
  public List<String> getUpsells() { return upsells; }
  public void setUpsells(List<String> v) { upsells = v; }
  public String getNeededBy() { return neededBy; }
  public void setNeededBy(String v) { neededBy = v; }
  public String getFontSize() { return fontSize; }
  public void setFontSize(String v) { fontSize = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { phone = v; }
  public String getArea() { return area; }
  public void setArea(String v) { area = v; }
  public String getAddress() { return address; }
  public void setAddress(String v) { address = v; }
  public String getNote() { return note; }
  public void setNote(String v) { note = v; }
  public String getWebsite() { return website; }
  public void setWebsite(String v) { website = v; }
  public String getSubmitToken() { return submitToken; }
  public void setSubmitToken(String v) { submitToken = v; }
  public String getShownTotal() { return shownTotal; }
  public void setShownTotal(String v) { shownTotal = v; }
}
