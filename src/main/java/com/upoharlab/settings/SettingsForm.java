package com.upoharlab.settings;

import java.util.*;

public class SettingsForm {
  private Map<String, String> values = new LinkedHashMap<>();

  public Map<String, String> getValues() {
    return values;
  }

  public void setValues(Map<String, String> values) {
    this.values = values;
  }
}
