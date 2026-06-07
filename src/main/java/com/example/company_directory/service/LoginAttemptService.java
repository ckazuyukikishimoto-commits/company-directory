package com.example.company_directory.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.company_directory.entity.LoginAttempt;
import com.example.company_directory.repository.LoginAttemptRepository;

import lombok.RequiredArgsConstructor;

/**
 * ブルードフォース攻撃対策クラス
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;

    private static final int MAX_DELAY_SECONDS = 20;
    private static final int DELAY_THRESHOLD = 10; // 10回失敗から遅延開始
    private static final int DELAY_INCREMENT = 5; // 5回ごとに+5秒

    /**
     * IP失敗記録
     */
    @Transactional
    public void recordFailure(String ipAddress) {
        Optional<LoginAttempt> optAttempt = loginAttemptRepository.findById(ipAddress);

        LoginAttempt attempt;
        if (optAttempt.isPresent()) {
            attempt = optAttempt.get();
            attempt.setFailureCount(attempt.getFailureCount() + 1);
        } else {
            attempt = new LoginAttempt(ipAddress, 1, LocalDateTime.now());
        }
        attempt.setLastAttemptTime(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
    }

    /**
     * IP成功時リセット
     */
    @Transactional
    public void resetFailures(String ipAddress) {
        loginAttemptRepository.deleteById(ipAddress);
    }

    /**
     * 遅延秒数取得
     * 10回失敗: 5秒, 15回失敗: 10秒, 20回失敗: 15秒, 25回以上: 20秒
     */
    public int getDelaySeconds(String ipAddress) {
        Optional<LoginAttempt> optAttempt = loginAttemptRepository.findById(ipAddress);

        if (optAttempt.isEmpty()) {
            return 0;
        }

        int failures = optAttempt.get().getFailureCount();

        if (failures < DELAY_THRESHOLD) {
            return 0;
        }

        // 10回以上の場合、5回ごとに+5秒
        int over = failures - DELAY_THRESHOLD;
        int delay = ((over / 5) + 1) * DELAY_INCREMENT;

        return Math.min(delay, MAX_DELAY_SECONDS);
    }

    /**
     * 失敗回数取得
     */
    public int getFailureCount(String ipAddress) {
        return loginAttemptRepository.findById(ipAddress)
                .map(LoginAttempt::getFailureCount)
                .orElse(0);
    }
}
