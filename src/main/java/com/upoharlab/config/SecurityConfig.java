package com.upoharlab.config;

import java.time.Clock;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService adminUsers(JdbcTemplate db) {
    return name ->
        db
            .query(
                "SELECT username,password_hash,active FROM admin_user WHERE username=?",
                (rs, n) ->
                    User.withUsername(rs.getString(1))
                        .password(rs.getString(2))
                        .disabled(!rs.getBoolean(3))
                        .roles("ADMIN")
                        .build(),
                name.strip().toLowerCase(Locale.ROOT))
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, Clock clock) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/css/**",
                        "/js/**",
                        "/fonts/**",
                        "/p/**",
                        "/order/**",
                        "/track",
                        "/bulk",
                        "/faq",
                        "/about",
                        "/policies/**",
                        "/sitemap.xml",
                        "/robots.txt",
                        "/favicon*.svg",
                        "/media/**",
                        "/admin/login",
                        "/error")
                    .permitAll()
                    .requestMatchers("/admin/**", "/admin")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .formLogin(
            f ->
                f.loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login")
                    .defaultSuccessUrl("/admin", true)
                    .failureUrl("/admin/login?error"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      if (req.getRequestURI().startsWith("/admin"))
                        new LoginUrlAuthenticationEntryPoint("/admin/login")
                            .commence(req, res, ex);
                      else res.sendError(404);
                    }))
        .logout(
            l ->
                l.logoutUrl("/admin/logout")
                    .logoutSuccessUrl("/admin/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
        .addFilterBefore(new LoginRateLimit(clock), UsernamePasswordAuthenticationFilter.class)
        .headers(
            h ->
                h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(
                        t -> t.includeSubDomains(false).maxAgeInSeconds(15552000))
                    .contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; font-src 'self'; style-src 'self'; img-src"
                                    + " 'self'; frame-ancestors 'none'; form-action 'self';"
                                    + " base-uri 'self'")))
        .build();
  }
}
