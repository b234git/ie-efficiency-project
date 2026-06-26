package thienloc.manage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@RequiredArgsConstructor
public class SecurityConfig {

        private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

        private final FeatureBasedAuthorizationManager featureBasedAuthorizationManager;

        @Bean
        public static PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                RequestMatcher apiMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
                CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

                http.authorizeHttpRequests(authorize -> authorize
                                .requestMatchers("/register/**").permitAll()
                                .requestMatchers("/login").permitAll()
                                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                                .anyRequest().access(featureBasedAuthorizationManager))
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
