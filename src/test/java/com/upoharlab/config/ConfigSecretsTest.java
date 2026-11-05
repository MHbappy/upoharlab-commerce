package com.upoharlab.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Packaged configuration must take credentials from the environment, never hold them. */
class ConfigSecretsTest {
  @Test
  void packagedConfigurationHoldsNoCredentials() throws Exception {
    for (String file : new String[] {"application.yml", "application-prod.yml"}) {
      String yaml =
          new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
      yaml.lines()
          .filter(line -> line.strip().startsWith("password:"))
          .forEach(
              line ->
                  assertThat(line.strip()).as(file).matches("password: \\$\\{[A-Z_]+:?\\}"));
    }
    String main = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);
    assertThat(main)
        .contains("username: ${DB_USER:upoharlab}", "username: ${ADMIN_USERNAME:}");
  }
}
