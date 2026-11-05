package com.example.company_directory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.company_directory.entity.User;
import com.example.company_directory.repository.RememberMeTokenRepository;
import com.example.company_directory.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ADMIN = "admin1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RememberMeTokenRepository rememberMeTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // テストを速くするため強度を下げる

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, rememberMeTokenRepository, passwordEncoder);
    }

    private User user(String id) {
        User u = new User();
        u.setUserId(id);
        u.setPasswordHash(passwordEncoder.encode("oldpassword"));
        return u;
    }

    /** 操作する人（admin1）を、DB 上で有効な管理者にしておく。 */
    private User activeAdmin() {
        User me = user(ADMIN);
        me.setAdmin(true);
        when(userRepository.findByUserId(ADMIN)).thenReturn(Optional.of(me));
        return me;
    }

    // ---- 操作する人が今も有効な管理者か ----

    @Test
    void adminOperations_rejectOperatorWhoIsNoLongerAdmin() {
        User me = user(ADMIN); // 別の PC で管理者を外された（セッションには ROLE_ADMIN が残っている）
        when(userRepository.findByUserId(ADMIN)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.setAdmin(ADMIN, "hanako", false))
                .isInstanceOf(AccountException.class)
                .hasMessage(AccountService.NOT_ADMIN_MESSAGE);
        assertThatThrownBy(() -> service.setEnabled(ADMIN, "hanako", false))
                .isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> service.createUser(ADMIN, "taro", "password123", true))
                .isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> service.resetPassword(ADMIN, "hanako", "password123"))
                .isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> service.unlock(ADMIN, "hanako"))
                .isInstanceOf(AccountException.class);
        verify(userRepository, never()).save(any());
        verify(rememberMeTokenRepository, never()).deleteByUsername(anyString());
    }

    @Test
    void adminOperations_rejectDisabledAdmin() {
        User me = activeAdmin();
        me.setEnabled(false);

        assertThatThrownBy(() -> service.setAdmin(ADMIN, "hanako", true))
                .isInstanceOf(AccountException.class)
                .hasMessage(AccountService.NOT_ADMIN_MESSAGE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminOperations_rejectUnknownOperator() {
        when(userRepository.findByUserId(ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock(ADMIN, "hanako"))
                .isInstanceOf(AccountException.class)
                .hasMessage(AccountService.NOT_ADMIN_MESSAGE);
        assertThat(AccountService.isNotAdmin(new AccountException(AccountService.NOT_ADMIN_MESSAGE))).isTrue();
        assertThat(AccountService.isNotAdmin(new AccountException("このユーザーIDはすでに使われています。"))).isFalse();
    }

    // ---- 追加 ----

    @Test
    void createUser_savesHashedPasswordAndRole() {
        activeAdmin();
        when(userRepository.existsById("taro")).thenReturn(false);

        service.createUser(ADMIN, "taro", "password123", true);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("taro");
        assertThat(saved.getValue().isAdmin()).isTrue();
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createUser_rejectsDuplicateId() {
        activeAdmin();
        when(userRepository.existsById("taro")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(ADMIN, "taro", "password123", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("このユーザーIDはすでに使われています。");
        verify(userRepository, never()).save(any());
    }

    // ---- 再設定・ロック解除 ----

    @Test
    void resetPassword_unlocksAndDeletesAllRememberMeTokens() {
        activeAdmin();
        User u = user("hanako");
        u.setFailedLoginCount(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.resetPassword(ADMIN, "hanako", "newpassword1");

        assertThat(passwordEncoder.matches("newpassword1", u.getPasswordHash())).isTrue();
        assertThat(u.getFailedLoginCount()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(rememberMeTokenRepository).deleteByUsername("hanako");
    }

    @Test
    void unlock_clearsFailuresAndLock() {
        activeAdmin();
        User u = user("hanako");
        u.setFailedLoginCount(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.unlock(ADMIN, "hanako");

        assertThat(u.getFailedLoginCount()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(userRepository).save(u);
    }

    @Test
    void unknownTarget_isReported() {
        activeAdmin();
        when(userRepository.findByUserId("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock(ADMIN, "nobody"))
                .isInstanceOf(AccountException.class)
                .hasMessage("アカウントが見つかりません。");
    }

    // ---- 有効／無効 ----

    @Test
    void setEnabled_false_rejectsSelf() {
        activeAdmin();

        assertThatThrownBy(() -> service.setEnabled(ADMIN, ADMIN, false))
                .isInstanceOf(AccountException.class)
                .hasMessage("自分自身のアカウントは無効にできません。");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_false_deletesRememberMeTokens() {
        activeAdmin();
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.setEnabled(ADMIN, "hanako", false);

        assertThat(u.isEnabled()).isFalse();
        verify(rememberMeTokenRepository).deleteByUsername("hanako");
    }

    @Test
    void setEnabled_true_keepsTokens() {
        activeAdmin();
        User u = user("hanako");
        u.setEnabled(false);
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.setEnabled(ADMIN, "hanako", true);

        assertThat(u.isEnabled()).isTrue();
        verify(rememberMeTokenRepository, never()).deleteByUsername(anyString());
    }

    // ---- 管理者の付与／解除 ----

    @Test
    void setAdmin_false_rejectsSelf() {
        activeAdmin();

        assertThatThrownBy(() -> service.setAdmin(ADMIN, ADMIN, false))
                .isInstanceOf(AccountException.class)
                .hasMessage("自分自身の管理者は外せません。");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setAdmin_grantsAndRevokesOthers() {
        activeAdmin();
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.setAdmin(ADMIN, "hanako", true);
        assertThat(u.isAdmin()).isTrue();

        service.setAdmin(ADMIN, "hanako", false);
        assertThat(u.isAdmin()).isFalse();
    }

    // ---- 本人のパスワード変更（管理者でなくてよい） ----

    @Test
    void changeOwnPassword_rejectsWrongCurrentPassword() {
        User u = user("hanako");
        String before = u.getPasswordHash();
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.changeOwnPassword("hanako", "wrong", "newpassword1", null))
                .isInstanceOf(AccountException.class)
                .hasMessage("現在のパスワードが違います。");
        assertThat(u.getPasswordHash()).isEqualTo(before);
        verify(rememberMeTokenRepository, never()).deleteByUsername(anyString());
    }

    @Test
    void changeOwnPassword_keepsCurrentRememberMeSeries() {
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.changeOwnPassword("hanako", "oldpassword", "newpassword1", "series-1");

        assertThat(passwordEncoder.matches("newpassword1", u.getPasswordHash())).isTrue();
        verify(rememberMeTokenRepository).deleteByUsernameAndSeriesNot("hanako", "series-1");
        verify(rememberMeTokenRepository, never()).deleteByUsername(anyString());
    }

    @Test
    void changeOwnPassword_withoutRememberMe_deletesAllTokens() {
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.changeOwnPassword("hanako", "oldpassword", "newpassword1", null);

        verify(rememberMeTokenRepository).deleteByUsername("hanako");
    }

    @Test
    void authorities_addAdminRoleOnlyForAdmins() {
        User normal = user("hanako");
        User admin = user(ADMIN);
        admin.setAdmin(true);

        assertThat(CustomUserDetailsService.authorities(normal)).extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(CustomUserDetailsService.authorities(admin)).extracting("authority")
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void forms_doNotPrintPasswordsInToString() {
        com.example.company_directory.form.PasswordChangeForm form = new com.example.company_directory.form.PasswordChangeForm();
        form.setCurrentPassword("secret-current");
        form.setPassword("secret-new");
        form.setPasswordConfirm("secret-confirm");

        assertThat(form.toString()).doesNotContain("secret-current", "secret-new", "secret-confirm");
    }
}
