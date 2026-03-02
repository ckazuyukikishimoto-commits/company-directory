package com.example.company_directory.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CompanyImageExtractionResponseDto {
    private List<CompanyImageExtractionDto> companies = new ArrayList<>();

    public CompanyImageExtractionResponseDto(List<CompanyImageExtractionDto> companies) {
        this.companies = companies != null ? companies : new ArrayList<>();
    }
}
