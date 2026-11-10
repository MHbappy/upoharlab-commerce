package com.upoharlab.text;

import java.text.MessageFormat;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Serves site texts to Thymeleaf ({@code #{key}}) and to validation messages ({@code {key}}).
 * Keys that are not site texts fall through to {@code messages.properties}.
 */
public class SiteTextMessageSource extends AbstractMessageSource {
  // Resolved lazily: the context asks for the message source before the database is ready.
  private final ObjectProvider<SiteTexts> texts;

  public SiteTextMessageSource(ObjectProvider<SiteTexts> texts) {
    this.texts = texts;
  }

  @Override
  protected String resolveCodeWithoutArguments(String code, Locale locale) {
    return texts.getObject().raw(code);
  }

  @Override
  protected MessageFormat resolveCode(String code, Locale locale) {
    String raw = texts.getObject().raw(code);
    return raw == null ? null : new MessageFormat(SiteTexts.escape(raw), Locale.ROOT);
  }

  @Configuration(proxyBeanMethods = false)
  static class Config {
    @Bean
    MessageSource messageSource(ObjectProvider<SiteTexts> texts) {
      ResourceBundleMessageSource base = new ResourceBundleMessageSource();
      base.setBasename("messages");
      base.setDefaultEncoding("UTF-8");
      base.setFallbackToSystemLocale(false);
      SiteTextMessageSource source = new SiteTextMessageSource(texts);
      source.setParentMessageSource(base);
      return source;
    }
  }
}
