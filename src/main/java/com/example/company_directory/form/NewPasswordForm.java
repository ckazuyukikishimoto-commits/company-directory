package com.example.company_directory.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新しいパスワードと、その確認（仕様書「⑥アカウント管理」のバリデーション）。
 * 管理者によるパスワード再設定で使い、追加・本人の変更のフォームもこれを受け継ぐ。
 * 確認と一致するかはコントローラーで確かめる（エラーを確認欄に出すため）。
 */
@Data
public class NewPasswordForm {

    public static final String PASSWORD_SIZE_MESSAGE = "パスワードは8文字以上64文字以内で入力してください。";
    public static final String CONFIRM_MISMATCH_MESSAGE = "確認用のパスワードが一致しません。";

    @NotNull(message = PASSWORD_SIZE_MESSAGE)
    @Size(min = 8, max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String password;

    private String passwordConfirm;

    /** 確認と一致するか。 */
    public boolean confirmMatches() {
        return password != null && password.equals(passwordConfirm);
    }
}
