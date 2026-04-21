package thienloc.manage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import thienloc.manage.interceptor.RateLimitInterceptor;

import java.util.Locale;
import java.util.Map;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setDefaultLocale(new Locale("vi"));
        resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
        return resolver;
    }

    @Bean
    public org.springframework.context.MessageSource messageSource() {
        org.springframework.context.support.ResourceBundleMessageSource messageSource = new org.springframework.context.support.ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Bean
    public RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(Map.of(
            "/excel/preview",          10_000L,   // 1 req / 10s
            "/split-entry/import",     60_000L    // 1 req / 60s (covers /preview, /output/*, /articles/*)
        ));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        registry.addInterceptor(rateLimitInterceptor())
                .addPathPatterns("/excel/preview",
                                 "/split-entry/import/**");
    }
}
