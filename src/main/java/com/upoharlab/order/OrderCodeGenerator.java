package com.upoharlab.order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Must run inside the order transaction, so a failed order does not use up a number. */
@Component
public class OrderCodeGenerator {
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM");
  private final JdbcTemplate db;
  private final Clock clock;

  public OrderCodeGenerator(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public String next() {
    return next(LocalDate.now(clock));
  }

  String next(LocalDate businessDate) {
    String month = MONTH.format(businessDate);
    Long value =
        db.queryForObject(
            "INSERT INTO order_code_counter (year_month, last_value) VALUES (?, 1) ON CONFLICT"
                + " (year_month) DO UPDATE SET last_value = order_code_counter.last_value + 1"
                + " RETURNING last_value",
            Long.class,
            month);
    return "UL-%s-%03d".formatted(month, value);
  }
}
