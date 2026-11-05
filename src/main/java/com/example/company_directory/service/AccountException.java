package com.example.company_directory.service;

/**
 * アカウント管理で、操作を受け付けられないときの例外。
 * メッセージはそのまま画面に出すので、日本語で書き、パスワードなどの秘密の値は入れない。
 */
public class AccountException extends RuntimeException {

    public AccountException(String message) {
        super(message);
    }
}
