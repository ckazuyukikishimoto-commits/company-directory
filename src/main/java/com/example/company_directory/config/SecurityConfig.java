package com.example.company_directory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.example.company_directory.security.CustomPersistentTokenBasedRememberMeServices;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
        private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
        private final PersistentTokenRepository persistentTokenRepository;
        private final com.example.company_directory.filter.RememberMeLoggingFilter rememberMeLoggingFilter;
        private final UserDetailsService userDetailsService;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .addFilterBefore(rememberMeLoggingFilter,
                                                org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/login", "/css/**", "/js/**", "/maintenance",
                                                                "/error")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .usernameParameter("username")
                                                .passwordParameter("password")
                                                .defaultSuccessUrl("/companies", true)
                                                .successHandler(customAuthenticationSuccessHandler)
                                                .failureHandler(customAuthenticationFailureHandler))
                                .rememberMe(rm -> rm
                                                .rememberMeServices(rememberMeServices()))
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .deleteCookies("JSESSIONID", "remember-me")
                                                .invalidateHttpSession(true))
                                .sessionManagement(session -> session
                                                .sessionFixation().changeSessionId()
                                                .maximumSessions(1))
                                .httpBasic(httpBasic -> httpBasic.disable());

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public CustomPersistentTokenBasedRememberMeServices rememberMeServices() {
                String rememberMeKey = "company-directory-remember-me-key";
                CustomPersistentTokenBasedRememberMeServices services = new CustomPersistentTokenBasedRememberMeServices(
                                rememberMeKey, userDetailsService, persistentTokenRepository);
                services.setTokenValiditySeconds(30 * 24 * 60 * 60); // 30 days
                services.setParameter("remember-me");
                return services;
        }
}
