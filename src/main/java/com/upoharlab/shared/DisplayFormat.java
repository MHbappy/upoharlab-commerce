package com.upoharlab.shared;

@org.springframework.stereotype.Component("bn")
public class DisplayFormat {
  public String money(Integer amount) {
    return amount == null ? "" : BnDigits.money(amount);
  }

  public String digits(Object value) {
    return value == null ? "" : BnDigits.toBangla(value.toString());
  }
}
