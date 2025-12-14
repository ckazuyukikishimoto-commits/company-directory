package com.example.company_directory.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportWorningDto {
    private int rowNum;
    private List<String> warnings;
}
