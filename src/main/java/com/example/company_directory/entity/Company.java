package com.example.company_directory.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity // これがDBのテーブルになりますよ、という合図
@Table(name = "companies") // テーブル名を指定
@Data // Lombok: Getter/Setterを自動生成
public class Company {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto Increment (自動採番)
    private Integer companyId;

    @Column(nullable = false, length = 100) // NOT NULL, 最大100文字
    private String companyName;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 8)
    private String zipCode;

    @Column(nullable = false)
    private LocalDate registrationDate;

    @Column(columnDefinition = "TEXT") // TEXT型を指定
    private String remarks;

    // --- 論理削除用 ---
    
    @Column(nullable = false)
    private Boolean isDeleted = false; // デフォルトはfalse(有効)

    private LocalDateTime deletedAt;

    @Column(length = 50)
    private String deletedBy;
}