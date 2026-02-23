package com.example.company_directory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企業名検索結果を保持するDTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanySearchResultDto {
    /** 企業名 */
    private String companyName;
    /** 法人番号 (あれば) */
    private String corporateNumber;
    /** 住所 */
    private String address;
    /** データソース ("法人番号API" or "Gemini") */
    private String source;
}
