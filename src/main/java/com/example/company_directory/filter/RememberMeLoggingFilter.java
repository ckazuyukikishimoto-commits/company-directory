package com.example.company_directory.filter;

import java.io.IOException;
import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RememberMeLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("remember-me".equals(cookie.getName())) {
                    try {
                        String decoded = new String(Base64.getDecoder().decode(cookie.getValue()));
                        String[] parts = decoded.split(":");
                        if (parts.length >= 2) {
                            log.info("RememberMe Browser Cookie: Series={}, Token={}", parts[0], parts[1]);
                        } else {
                            log.warn("RememberMe Browser Cookie format invalid: {}", decoded);
                        }
                    } catch (Exception e) {
                        log.error("Failed to decode remember-me cookie", e);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
