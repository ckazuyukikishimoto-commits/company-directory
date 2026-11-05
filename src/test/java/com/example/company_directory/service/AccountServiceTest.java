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

    @Test
    void createUser_savesHashedPasswordAndRole() {
        when(userRepository.existsById("taro")).thenReturn(false);

        service.createUser("taro", "password123", true);

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
        when(userRepository.existsById("taro")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser("taro", "password123", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("このユーザーIDはすでに使われています。");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_unlocksAndDeletesAllRememberMeTokens() {
        User u = user("hanako");
        u.setFailedLoginCount(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.resetPassword("hanako", "newpassword1");

        assertThat(passwordEncoder.matches("newpassword1", u.getPasswordHash())).isTrue();
        assertThat(u.getFailedLoginCount()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(rememberMeTokenRepository).deleteByUsername("hanako");
    }

    @Test
    void unlock_clearsFailuresAndLock() {
        User u = user("hanako");
        u.setFailedLoginCount(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.unlock("hanako");

        assertThat(u.getFailedLoginCount()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(userRepository).save(u);
    }

    @Test
    void setEnabled_false_rejectsSelf() {
        assertThatThrownBy(() -> service.setEnabled("admin1", "admin1", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("自分自身のアカウントは無効にできません。");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_false_deletesRememberMeTokens() {
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.setEnabled("admin1", "hanako", false);

        assertThat(u.isEnabled()).isFalse();
        verify(rememberMeTokenRepository).deleteByUsername("hanako");
    }

    @Test
    void setEnabled_true_keepsTokensAndAllowsSelf() {
        User u = user("admin1");
        u.setEnabled(false);
        when(userRepository.findByUserId("admin1")).thenReturn(Optional.of(u));

        service.setEnabled("admin1", "admin1", true);

        assertThat(u.isEnabled()).isTrue();
        verify(rememberMeTokenRepository, never()).deleteByUsername(anyString());
    }

    @Test
    void setAdmin_false_rejectsSelf() {
        assertThatThrownBy(() -> service.setAdmin("admin1", "admin1", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("自分自身の管理者は外せません。");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setAdmin_grantsAndRevokesOthers() {
        User u = user("hanako");
        when(userRepository.findByUserId("hanako")).thenReturn(Optional.of(u));

        service.setAdmin("admin1", "hanako", true);
        assertThat(u.isAdmin()).isTrue();

        service.setAdmin("admin1", "hanako", false);
        assertThat(u.isAdmin()).isFalse();
    }

    @Test
    void unknownUser_isReported() {
        when(userRepository.findByUserId("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock("nobody"))
                .isInstanceOf(AccountException.class)
                .hasMessage("アカウントが見つかりません。");
    }

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
        User admin = user("admin1");
        admin.setAdmin(true);

        assertThat(CustomUserDetailsService.authorities(normal)).extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(CustomUserDetailsService.authorities(admin)).extracting("authority")
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }
}
