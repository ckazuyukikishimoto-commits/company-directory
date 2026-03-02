package com.example.company_directory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyImageExtractionDto {
    private String companyName;
    private String zipCode;
    private String address;
}
