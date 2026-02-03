package com.example.company_directory.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyForm {

    private Integer id;

    @NotBlank(message = "企業名は必須です")
    @Size(max = 100, message = "{max}文字以内で入力してください")
    private String companyName;

    @NotBlank(message = "住所は必須です")
    @Size(max = 200, message = "{max}文字以内で入力してください")
    private String address;

    @NotBlank(message = "郵便番号は必須です")
    @Size(min = 8, max = 8, message = "郵便番号は{max}桁で入力してください")
    @Pattern(regexp = "\\d{3}-\\d{4}", message = "郵便番号の形式が正しくありません（例: 123-4567）")
    private String zipCode;

    @Size(max = 1000, message = "{max}文字以内で入力してください")
    private String remarks;

    private java.util.List<com.example.company_directory.dto.ImportRowDto> rows;
}
