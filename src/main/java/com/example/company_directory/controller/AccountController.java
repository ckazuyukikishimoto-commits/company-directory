package com.example.company_directory.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.company_directory.form.NewPasswordForm;
import com.example.company_directory.form.PasswordChangeForm;
import com.example.company_directory.service.AccountException;
import com.example.company_directory.service.AccountService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 本人のパスワード変更（全員が使える。仕様書「⑥アカウント管理・パスワード変更」）。 */
@Controller
@RequestMapping("/account/password")
@RequiredArgsConstructor
public class AccountController {

    private static final String VIEW = "account/password";

    /** SecurityConfig の rememberMe で使っている Cookie の名前（Spring Security の既定）。 */
    static final String REMEMBER_ME_COOKIE = "remember-me";

    private final AccountService accountService;

    @GetMapping
    public String form(@ModelAttribute("passwordForm") PasswordChangeForm form) {
        return VIEW;
    }

    @PostMapping
    public String change(@Validated @ModelAttribute("passwordForm") PasswordChangeForm form,
            BindingResult bindingResult, Principal principal, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("password") && !form.confirmMatches()) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", NewPasswordForm.CONFIRM_MISMATCH_MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            return VIEW;
        }
        try {
            accountService.changeOwnPassword(principal.getName(), form.getCurrentPassword(), form.getPassword(),
                    currentRememberMeSeries(request));
        } catch (AccountException e) {
            bindingResult.rejectValue("currentPassword", "wrong", e.getMessage());
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("successMessage", "パスワードを変更しました");
        return "redirect:/companies";
    }

    /**
     * 今のリクエストの「ログイン保持」Cookie から、シリーズ（トークンの ID）を取り出す。
     * Cookie の値は「シリーズ:トークン」を URL エンコードして Base64 にしたもの（Spring Security の形式）。
     * 無い・読めないときは null。
     */
    static String currentRememberMeSeries(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (!REMEMBER_ME_COOKIE.equals(cookie.getName()) || cookie.getValue() == null) {
                continue;
            }
            try {
                String value = cookie.getValue();
                // Spring Security は末尾の「=」を落として書くことがあるので補う
                while (value.length() % 4 != 0) {
                    value += "=";
                }
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon <= 0) {
                    return null;
                }
                return URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
