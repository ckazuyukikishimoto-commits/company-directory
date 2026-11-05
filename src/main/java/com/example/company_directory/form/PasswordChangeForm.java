package com.example.company_directory.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 本人のパスワード変更（仕様書「⑥アカウント管理・パスワード変更」）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PasswordChangeForm extends NewPasswordForm {

    @NotBlank(message = "現在のパスワードが未入力です。")
    @ToString.Exclude // ログにパスワードを出さない
    private String currentPassword;
}
