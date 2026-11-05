package com.example.company_directory.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @Column(length = 50)
    private String userId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private int failedLoginCount = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * 管理者かどうか。管理者はアカウント管理画面を使える（仕様書「⑥アカウント管理」）。
     * 列は仕様書の手順どおり、新しい版を起動する前に SQL で手で足す。足し忘れたときに備えて、
     * 自動で足す場合も既にある行が困らないよう DB 側の既定値（false）を付ける
     * （nullable は columnDefinition に書いたので付けない。not null が二重に出るのを避ける）。
     */
    @Column(name = "is_admin", columnDefinition = "boolean not null default false")
    private boolean admin = false;

    /** ID・パスワードで最後にログインした日時（自動ログインは数えない）。 */
    private LocalDateTime lastLoginAt;
}