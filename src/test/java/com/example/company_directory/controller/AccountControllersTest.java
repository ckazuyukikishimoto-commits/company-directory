package com.example.company_directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.company_directory.config.CustomAuthenticationFailureHandler;
import com.example.company_directory.config.CustomAuthenticationSuccessHandler;
import com.example.company_directory.config.SecurityConfig;
import com.example.company_directory.entity.User;
import com.example.company_directory.service.AccountException;
import com.example.company_directory.service.AccountService;
import com.example.company_directory.service.NotAdminException;

import jakarta.servlet.http.Cookie;

/**
 * アカウント管理画面・パスワード変更画面。本物の SecurityConfig を読み込み、
 * 「/admin/** は管理者だけ」が効いていることも確かめる。
 */
@WebMvcTest({ AdminUserController.class, AccountController.class })
@Import(SecurityConfig.class)
class AccountControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    // SecurityConfig が必要とするもの
    @MockitoBean
    private CustomAuthenticationFailureHandler failureHandler;
    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static User user(String id, boolean admin) {
        User u = new User();
        u.setUserId(id);
        u.setPasswordHash("x");
        u.setAdmin(admin);
        return u;
    }

    // ---- 権限 ----

    @Test
    @WithMockUser(username = "hanako", roles = "USER")
    void adminPage_isForbiddenForNormalUsers() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/disable").param("userId", "taro").with(csrf()))
                .andExpect(status().isForbidden());
        verify(accountService, never()).setEnabled(anyString(), anyString(), anyBoolean());
    }

    @Test
    void adminPage_redirectsToLoginWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void adminPage_listsUsers() throws Exception {
        when(accountService.listUsers()).thenReturn(List.of(user("admin1", true), user("hanako", false)));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hanako")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("（自分）")));
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void postWithoutCsrf_isRejected() throws Exception {
        mockMvc.perform(post("/admin/users/unlock").param("userId", "hanako")).andExpect(status().isForbidden());
        verify(accountService, never()).unlock(anyString(), anyString());
    }

    // ---- 追加 ----

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void add_success_redirectsWithMessage() throws Exception {
        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "hanako").param("password", "password123")
                .param("passwordConfirm", "password123").param("admin", "false"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("successMessage", "アカウントを追加しました"));
        verify(accountService).createUser("admin1", "hanako", "password123", false);
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void add_validationErrors_showForm() throws Exception {
        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "はなこ").param("password", "short").param("passwordConfirm", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeHasFieldErrors("createForm", "userId", "password"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ユーザーIDは半角英数字50文字以内で入力してください。")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "パスワードは8文字以上64文字以内で入力してください。")))
                // 入力したパスワードを画面に戻さない
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("value=\"short\""))));
        verify(accountService, never()).createUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void add_confirmMismatch_isReported() throws Exception {
        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "hanako").param("password", "password123").param("passwordConfirm", "password124"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("createForm", "passwordConfirm"));
        verify(accountService, never()).createUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void add_duplicate_isShownOnUserId() throws Exception {
        doThrow(new AccountException("このユーザーIDはすでに使われています。"))
                .when(accountService).createUser(anyString(), eq("hanako"), anyString(), anyBoolean());

        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "hanako").param("password", "password123").param("passwordConfirm", "password123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("createForm", "userId"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void add_byOperatorWhoLostAdmin_showsGeneralError() throws Exception {
        doThrow(new NotAdminException())
                .when(accountService).createUser(anyString(), anyString(), anyString(), anyBoolean());

        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "hanako").param("password", "password123").param("passwordConfirm", "password123"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", NotAdminException.MESSAGE));
    }

    // ---- 行ごとの操作 ----

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void resetPassword_mismatch_doesNotCallService() throws Exception {
        mockMvc.perform(post("/admin/users/password").with(csrf())
                .param("userId", "hanako").param("password", "password123").param("passwordConfirm", "x"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "hanako：確認用のパスワードが一致しません。"));
        verify(accountService, never()).resetPassword(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void resetPassword_success() throws Exception {
        mockMvc.perform(post("/admin/users/password").with(csrf())
                .param("userId", "hanako").param("password", "password123").param("passwordConfirm", "password123"))
                .andExpect(flash().attribute("successMessage", "パスワードを再設定しました"));
        verify(accountService).resetPassword("admin1", "hanako", "password123");
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void disable_passesCurrentUserAndShowsServiceError() throws Exception {
        doThrow(new AccountException("自分自身のアカウントは無効にできません。"))
                .when(accountService).setEnabled("admin1", "admin1", false);

        mockMvc.perform(post("/admin/users/disable").with(csrf()).param("userId", "admin1"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "自分自身のアカウントは無効にできません。"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = { "USER", "ADMIN" })
    void grantAndRevokeAdmin_callService() throws Exception {
        mockMvc.perform(post("/admin/users/grant-admin").with(csrf()).param("userId", "hanako"))
                .andExpect(flash().attribute("successMessage", "管理者にしました"));
        mockMvc.perform(post("/admin/users/revoke-admin").with(csrf()).param("userId", "hanako"))
                .andExpect(flash().attribute("successMessage", "管理者を外しました"));
        verify(accountService).setAdmin("admin1", "hanako", true);
        verify(accountService).setAdmin("admin1", "hanako", false);
    }

    // ---- 本人のパスワード変更 ----

    @Test
    @WithMockUser(username = "hanako", roles = "USER")
    void ownPassword_pageIsOpenForNormalUsers() throws Exception {
        mockMvc.perform(get("/account/password"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"));
    }

    @Test
    @WithMockUser(username = "hanako", roles = "USER")
    void ownPassword_success_redirectsToList() throws Exception {
        mockMvc.perform(post("/account/password").with(csrf())
                .param("currentPassword", "oldpassword").param("password", "newpassword1")
                .param("passwordConfirm", "newpassword1"))
                .andExpect(redirectedUrl("/companies"))
                .andExpect(flash().attribute("successMessage", "パスワードを変更しました"));
        verify(accountService).changeOwnPassword(eq("hanako"), eq("oldpassword"), eq("newpassword1"), isNull());
    }

    @Test
    @WithMockUser(username = "hanako", roles = "USER")
    void ownPassword_passesCurrentRememberMeSeries() throws Exception {
        String cookie = Base64.getEncoder().encodeToString("series%2Babc:token123".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/account/password").with(csrf()).cookie(new Cookie("remember-me", cookie))
                .param("currentPassword", "oldpassword").param("password", "newpassword1")
                .param("passwordConfirm", "newpassword1"))
                .andExpect(redirectedUrl("/companies"));
        verify(accountService).changeOwnPassword("hanako", "oldpassword", "newpassword1", "series+abc");
    }

    @Test
    @WithMockUser(username = "hanako", roles = "USER")
    void ownPassword_wrongCurrent_isShownOnField() throws Exception {
        doThrow(new AccountException("現在のパスワードが違います。"))
                .when(accountService).changeOwnPassword(anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/account/password").with(csrf())
                .param("currentPassword", "wrong").param("password", "newpassword1")
                .param("passwordConfirm", "newpassword1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("現在のパスワードが違います。")));
    }

    @Test
    void currentRememberMeSeries_handlesMissingPaddingAndGarbage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String encoded = Base64.getEncoder().withoutPadding()
                .encodeToString("s1:t1".getBytes(StandardCharsets.UTF_8));
        request.setCookies(new Cookie("remember-me", encoded));
        assertThat(AccountController.currentRememberMeSeries(request)).isEqualTo("s1");

        MockHttpServletRequest garbage = new MockHttpServletRequest();
        garbage.setCookies(new Cookie("remember-me", "!!!not-base64!!!"));
        assertThat(AccountController.currentRememberMeSeries(garbage)).isNull();

        assertThat(AccountController.currentRememberMeSeries(new MockHttpServletRequest())).isNull();
    }
}
