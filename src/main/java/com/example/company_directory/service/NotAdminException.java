package com.example.company_directory.service;

/**
 * 操作する人が、もう有効な管理者ではないときの例外。
 * 画面では入力欄のエラーではなく、全体のメッセージとして出すため、型で見分けられるよう分けている。
 */
public class NotAdminException extends AccountException {

    public static final String MESSAGE = "管理者の権限がなくなったため、この操作はできません。ログインし直してください。";

    public NotAdminException() {
        super(MESSAGE);
    }
}
