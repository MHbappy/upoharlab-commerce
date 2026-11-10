package com.upoharlab.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArrivalEstimateTest {
  @Test
  void addsTwoDeliveryDaysToBothEndsOfTheMakingTime() {
    assertThat(ArrivalEstimate.of(1, 2)).isEqualTo(new ArrivalEstimate.Range(3, 4));
    assertThat(ArrivalEstimate.of(2, 3)).isEqualTo(new ArrivalEstimate.Range(4, 5));
  }

  @Test
  void sameMinimumAndMaximumIsASingleDay() {
    assertThat(ArrivalEstimate.of(3, 3).single()).isTrue();
  }

  @Test
  void noEstimateWithoutBothMakingDays() {
    assertThat(ArrivalEstimate.of(null, 4)).isNull();
    assertThat(ArrivalEstimate.of(2, null)).isNull();
  }
}
