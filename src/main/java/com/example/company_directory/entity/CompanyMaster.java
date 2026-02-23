package com.example.company_directory.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 企業マスタエンティティ。
 * 法人番号公表データなどをあらかじめインポートしておき、
 * 住所から企業名を検索するためのマスタテーブル。
 */
@Entity
@Table(name = "company_masters")
@Data
public class CompanyMaster {

    /** 法人番号（13桁、主キー） */
    @Id
    @Column(length = 13)
    private String corporateNumber;

    /** 企業名 */
    @Column(nullable = false, length = 200)
    private String companyName;

    /** 住所 */
    @Column(length = 300)
    private String address;

    /** 郵便番号 */
    @Column(length = 8)
    private String zipCode;
}
