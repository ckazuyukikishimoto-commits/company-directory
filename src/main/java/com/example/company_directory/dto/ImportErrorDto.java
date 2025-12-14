package com.example.company_directory.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorDto {
    private int rowNum;           // 何行目で失敗したか
    private List<String> messages; // 複数のエラー内容
}