package thienloc.manage.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public static PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http.authorizeHttpRequests((authorize) -> authorize.requestMatchers("/register/**")
                                .permitAll()
                                .requestMatchers("/login").permitAll()
                                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/masterdb/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                                .requestMatchers("/dashboard/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/report/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/entry/edit", "/entry/admin-delete", "/entry/delete")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/delete/**").hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/output", "/split-entry/output/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/articles", "/split-entry/articles/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                                .requestMatchers("/split-entry/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                                .requestMatchers("/entry/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                                .requestMatchers("/notifications/**").hasAnyRole("ADMIN", "MANAGER")
                                .anyRequest().authenticated())
                                .formLogin(
                                                form -> form
                                                                .loginPage("/login")
                                                                .loginProcessingUrl("/login")
                                                                .defaultSuccessUrl("/home", true)
                                                                .permitAll())
                                .logout(
                                                logout -> logout
                                                                .logoutUrl("/logout")
                                                                .permitAll());
                return http.build();
        }
}
