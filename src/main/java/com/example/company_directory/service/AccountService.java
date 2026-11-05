package com.example.company_directory.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.company_directory.entity.User;
import com.example.company_directory.repository.RememberMeTokenRepository;
import com.example.company_directory.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * アカウント管理画面・パスワード変更画面の処理（仕様書「⑥アカウント管理・パスワード変更」）。
 *
 * <p>
 * 入力の形（文字数・半角英数字）は画面のフォームで確かめ、ここでは
 * 「重複していないか」「自分自身を無効にしていないか」など、DB の状態を見て決まることを確かめる。
 * </p>
 * <p>
 * 管理者向けの操作は、最初に「操作する人が今も有効な管理者か」を DB で確かめる。
 * 権限はログインした時点のものがセッションに残るため、別の PC で管理者を外された・無効にされた人が
 * 開いたままの画面から操作を続けて、管理者が 0 人になるのを防ぐ。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /** 一覧（ユーザーIDの昇順）。 */
    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAllByOrderByUserIdAsc();
    }

    /** アカウントを追加する。 */
    @Transactional
    public void createUser(String currentUserId, String userId, String rawPassword, boolean admin) {
        requireActiveAdmin(currentUserId);
        if (userRepository.existsById(userId)) {
            throw new AccountException("このユーザーIDはすでに使われています。");
        }
        User user = new User();
        user.setUserId(userId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAdmin(admin);
        user.setEnabled(true);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    /** 管理者がパスワードを再設定する。あわせてロックを解き、ログイン保持をすべて消す。 */
    @Transactional
    public void resetPassword(String currentUserId, String targetUserId, String rawPassword) {
        requireActiveAdmin(currentUserId);
        User user = find(targetUserId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        rememberMeTokenRepository.deleteByUsername(targetUserId);
    }

    /** ロックを解く。 */
    @Transactional
    public void unlock(String currentUserId, String targetUserId) {
        requireActiveAdmin(currentUserId);
        User user = find(targetUserId);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    /** 有効／無効を切り替える。自分自身は無効にできない。無効にしたらログイン保持を消す。 */
    @Transactional
    public void setEnabled(String currentUserId, String targetUserId, boolean enabled) {
        requireActiveAdmin(currentUserId);
        if (!enabled && targetUserId.equals(currentUserId)) {
            throw new AccountException("自分自身のアカウントは無効にできません。");
        }
        User user = find(targetUserId);
        user.setEnabled(enabled);
        userRepository.save(user);
        if (!enabled) {
            rememberMeTokenRepository.deleteByUsername(targetUserId);
        }
    }

    /** 管理者にする／外す。自分自身の管理者は外せない（管理者が 0 人になるのを防ぐ）。 */
    @Transactional
    public void setAdmin(String currentUserId, String targetUserId, boolean admin) {
        requireActiveAdmin(currentUserId);
        if (!admin && targetUserId.equals(currentUserId)) {
            throw new AccountException("自分自身の管理者は外せません。");
        }
        User user = find(targetUserId);
        user.setAdmin(admin);
        userRepository.save(user);
    }

    /**
     * 本人がパスワードを変える。
     *
     * @param currentSeries 今使っている「ログイン保持」のシリーズ（無ければ null）。これ以外のログイン保持を消す
     */
    @Transactional
    public void changeOwnPassword(String userId, String currentPassword, String newPassword, String currentSeries) {
        User user = find(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountException("現在のパスワードが違います。");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        if (currentSeries == null) {
            rememberMeTokenRepository.deleteByUsername(userId);
        } else {
            rememberMeTokenRepository.deleteByUsernameAndSeriesNot(userId, currentSeries);
        }
    }

    /** 操作する人が、今も DB 上で有効な管理者かを確かめる。 */
    private void requireActiveAdmin(String currentUserId) {
        User me = userRepository.findByUserId(currentUserId).orElse(null);
        if (me == null || !me.isAdmin() || !me.isEnabled()) {
            throw new NotAdminException();
        }
    }

    private User find(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountException("アカウントが見つかりません。"));
    }
}
