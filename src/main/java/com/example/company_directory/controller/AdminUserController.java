package com.example.company_directory.controller;

import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.company_directory.form.AccountCreateForm;
import com.example.company_directory.form.NewPasswordForm;
import com.example.company_directory.service.AccountException;
import com.example.company_directory.service.AccountService;

import lombok.RequiredArgsConstructor;

/**
 * アカウント管理画面（管理者だけ。仕様書「⑥アカウント管理」）。
 * 管理者かどうかは SecurityConfig の「/admin/**」の設定でも止めている。
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final String VIEW = "admin/users";
    private static final String REDIRECT = "redirect:/admin/users";

    private final AccountService accountService;

    @GetMapping
    public String list(Model model, Principal principal) {
        if (!model.containsAttribute("createForm")) {
            model.addAttribute("createForm", new AccountCreateForm());
        }
        populate(model, principal);
        return VIEW;
    }

    @PostMapping("/add")
    public String add(@Validated @ModelAttribute("createForm") AccountCreateForm form, BindingResult bindingResult,
            Model model, Principal principal, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("password") && !form.confirmMatches()) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", NewPasswordForm.CONFIRM_MISMATCH_MESSAGE);
        }
        if (!bindingResult.hasErrors()) {
            try {
                accountService.createUser(principal.getName(), form.getUserId(), form.getPassword(), form.isAdmin());
                redirectAttributes.addFlashAttribute("successMessage", "アカウントを追加しました");
                return REDIRECT;
            } catch (AccountException e) {
                if (AccountService.isNotAdmin(e)) {
                    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                    return REDIRECT;
                }
                bindingResult.rejectValue("userId", "duplicate", e.getMessage());
            }
        }
        populate(model, principal);
        return VIEW;
    }

    @PostMapping("/password")
    public String resetPassword(@RequestParam String userId, @Validated NewPasswordForm form,
            BindingResult bindingResult, Principal principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    userId + "：" + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        if (!form.confirmMatches()) {
            redirectAttributes.addFlashAttribute("errorMessage", userId + "：" + NewPasswordForm.CONFIRM_MISMATCH_MESSAGE);
            return REDIRECT;
        }
        return run(redirectAttributes, "パスワードを再設定しました",
                () -> accountService.resetPassword(principal.getName(), userId, form.getPassword()));
    }

    @PostMapping("/unlock")
    public String unlock(@RequestParam String userId, Principal principal, RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "ロックを解除しました", () -> accountService.unlock(principal.getName(), userId));
    }

    @PostMapping("/enable")
    public String enable(@RequestParam String userId, Principal principal, RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "アカウントを有効にしました",
                () -> accountService.setEnabled(principal.getName(), userId, true));
    }

    @PostMapping("/disable")
    public String disable(@RequestParam String userId, Principal principal, RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "アカウントを無効にしました",
                () -> accountService.setEnabled(principal.getName(), userId, false));
    }

    @PostMapping("/grant-admin")
    public String grantAdmin(@RequestParam String userId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "管理者にしました",
                () -> accountService.setAdmin(principal.getName(), userId, true));
    }

    @PostMapping("/revoke-admin")
    public String revokeAdmin(@RequestParam String userId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "管理者を外しました",
                () -> accountService.setAdmin(principal.getName(), userId, false));
    }

    private String run(RedirectAttributes redirectAttributes, String successMessage, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (AccountException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT;
    }

    private void populate(Model model, Principal principal) {
        model.addAttribute("users", accountService.listUsers());
        model.addAttribute("currentUserId", principal.getName());
        model.addAttribute("now", LocalDateTime.now());
    }
}
