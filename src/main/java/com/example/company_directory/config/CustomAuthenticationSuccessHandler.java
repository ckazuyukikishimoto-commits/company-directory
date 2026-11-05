package com.example.company_directory.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.company_directory.repository.UserRepository;
import com.example.company_directory.service.LoginAttemptService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();
        String ipAddress = getClientIP(request);

        // ユーザーの失敗カウントをリセット
        userRepository.findByUserId(username).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });

        // IP失敗カウントをリセット
        loginAttemptService.resetFailures(ipAddress);

        // 企業一覧へリダイレクト
        response.sendRedirect("/companies");
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
