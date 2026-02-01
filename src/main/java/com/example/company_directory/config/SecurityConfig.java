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
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
        private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
        private final PersistentTokenRepository persistentTokenRepository;
        private final com.example.company_directory.filter.RememberMeLoggingFilter rememberMeLoggingFilter;

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
                                                .tokenRepository(persistentTokenRepository)
                                                .tokenValiditySeconds(30 * 24 * 60 * 60) // 30日
                                                .rememberMeParameter("remember-me"))
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
}
