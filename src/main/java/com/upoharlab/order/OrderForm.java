package com.upoharlab.order;

/** Raw browser values, kept exactly as typed so they can be shown again after an error. */
public class OrderForm {
  private String quantity = "1";
  private String engraving;
  private String name;
  private String phone;
  private String area;
  private String address;
  private String note;
  private String website;
  private String submitToken;
  private String shownTotal;

  public String getQuantity() {
    return quantity;
  }

  public void setQuantity(String value) {
    quantity = value;
  }

  public String getEngraving() {
    return engraving;
  }

  public void setEngraving(String value) {
    engraving = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String value) {
    phone = value;
  }

  public String getArea() {
    return area;
  }

  public void setArea(String value) {
    area = value;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String value) {
    address = value;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String value) {
    note = value;
  }

  /** Honeypot: hidden from people, so only automated submissions fill it. */
  public String getWebsite() {
    return website;
  }

  public void setWebsite(String value) {
    website = value;
  }

  public String getSubmitToken() {
    return submitToken;
  }

  public void setSubmitToken(String value) {
    submitToken = value;
  }

  /** The total the customer was shown. Only compared; never used as the amount to charge. */
  public String getShownTotal() {
    return shownTotal;
  }

  public void setShownTotal(String value) {
    shownTotal = value;
  }
}
