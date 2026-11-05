package com.upoharlab.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final String username, password;

  public AdminBootstrap(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${app.admin.username:}") String username,
      @Value("${app.admin.password:}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.execute("SELECT pg_advisory_xact_lock(741902)");
    if (db.queryForObject("SELECT count(*) FROM admin_user", Integer.class) > 0) return;
    if (!username.matches("[a-zA-Z0-9._-]{3,64}")
        || password.length() < 12
        || password.length() > 72)
      throw new IllegalStateException(
          "First startup requires ADMIN_USERNAME (3–64 letters/digits/._-) and ADMIN_PASSWORD"
              + " (12–72 characters). Set them in your private environment.");
    db.update(
        "INSERT INTO admin_user(username,password_hash) VALUES (?,?)",
        username.toLowerCase(java.util.Locale.ROOT),
        encoder.encode(password));
  }
}
