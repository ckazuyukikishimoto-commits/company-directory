package com.example.company_directory.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** アカウントの追加（仕様書「⑥アカウント管理」）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountCreateForm extends NewPasswordForm {

    public static final String USER_ID_FORMAT_MESSAGE = "ユーザーIDは半角英数字50文字以内で入力してください。";

    @NotBlank(message = "ユーザーIDが未入力です。")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = USER_ID_FORMAT_MESSAGE)
    @Size(max = 50, message = USER_ID_FORMAT_MESSAGE)
    private String userId;

    /** 管理者にするか（既定は一般）。 */
    private boolean admin;
}
