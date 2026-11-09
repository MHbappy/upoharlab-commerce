package com.upoharlab.order;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared order results and lookups. */
@Service
public class OrderService {
  /** Holds no customer contact details by design, so alerts cannot leak them. */
  public record OrderPlaced(String code, String productTitle, int quantity, int total) {}

  public sealed interface PlaceResult {}

  public record Placed(String code) implements PlaceResult {}

  public record PricesChanged() implements PlaceResult {}

  public record Closed() implements PlaceResult {}

  public record Saved(String code, String productTitle) {}

  private final JdbcTemplate db;

  public OrderService(JdbcTemplate db) {
    this.db = db;
  }

  public Optional<String> codeForSubmitToken(String submitToken) {
    List<String> codes =
        db.queryForList("SELECT code FROM orders WHERE submit_token=?", String.class, submitToken);
    return codes.stream().findFirst();
  }

  public Optional<Saved> saved(String code) {
    return db
        .query(
            "SELECT code, product_title_snapshot FROM orders WHERE code=?",
            (r, n) -> new Saved(r.getString(1), r.getString(2)),
            code)
        .stream()
        .findFirst();
  }
}
