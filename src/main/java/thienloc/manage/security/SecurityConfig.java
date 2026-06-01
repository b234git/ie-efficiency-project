package thienloc.manage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import thienloc.manage.dto.response.ErrorDto;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

        @Bean
        public static PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                RequestMatcher apiMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
                CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

                http.authorizeHttpRequests((authorize) -> authorize.requestMatchers("/register/**")
                                .permitAll()
                                .requestMatchers("/login").permitAll()
                                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                .requestMatchers("/api/v1/system-health/**").hasRole("ADMIN")
                                .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                                .requestMatchers("/masterdb/**", "/api/v1/masterdb/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/dashboard/**", "/api/v1/dashboard/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/report/**", "/api/v1/reports/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/entry/edit", "/entry/admin-delete", "/entry/delete")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/delete/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/split-entries/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers(HttpMethod.POST, "/api/v1/split-entries/bulk-delete")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/output", "/split-entry/output/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/articles", "/split-entry/articles/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/**", "/api/v1/split-entries/**")
                                .hasAnyRole("ADMIN", "MANAGER", "USER")
                                .requestMatchers("/entry/**", "/api/v1/entries/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/notifications/**", "/api/v1/notifications/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/eff-config/**", "/api/v1/eff-config/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/weekly-tracking/sixs/**", "/weekly-tracking/reprocess/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/weekly-tracking/**", "/api/v1/weekly-tracking/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/new-style/**", "/api/v1/new-styles/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/salary/**", "/api/v1/salary/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/api/v1/imports/**").hasAnyRole("ADMIN", "MANAGER")
                                .anyRequest().authenticated())
                                .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler))
                                .formLogin(
                                                form -> form
                                                                .loginPage("/login")
                                                                .loginProcessingUrl("/login")
                                                                .defaultSuccessUrl("/home", true)
                                                                .permitAll())
                                .logout(
                                                logout -> logout
                                                                .logoutUrl("/logout")
                                                                .permitAll())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(
                                                                buildAuthenticationEntryPoint(apiMatcher))
                                                .accessDeniedHandler(
                                                                buildAccessDeniedHandler(apiMatcher)));
                return http.build();
        }

        private AuthenticationEntryPoint buildAuthenticationEntryPoint(RequestMatcher apiMatcher) {
                AuthenticationEntryPoint json = (request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                                ErrorDto.of("UNAUTHORIZED", "Authentication required."));
                AuthenticationEntryPoint formLogin = new LoginUrlAuthenticationEntryPoint("/login");
                return (request, response, authException) -> {
                        if (apiMatcher.matches(request)) {
                                json.commence(request, response, authException);
                        } else {
                                formLogin.commence(request, response, authException);
                        }
                };
        }

        private AccessDeniedHandler buildAccessDeniedHandler(RequestMatcher apiMatcher) {
                AccessDeniedHandler json = (request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                                ErrorDto.of("FORBIDDEN", "Access denied."));
                AccessDeniedHandler defaultHandler = new AccessDeniedHandlerImpl();
                return (request, response, accessDeniedException) -> {
                        if (apiMatcher.matches(request)) {
                                json.handle(request, response, accessDeniedException);
                        } else {
                                defaultHandler.handle(request, response, accessDeniedException);
                        }
                };
        }

        private void writeError(HttpServletResponse response, int status, ErrorDto body) throws IOException {
                response.setStatus(status);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                ERROR_MAPPER.writeValue(response.getWriter(), body);
        }
}
