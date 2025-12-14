package com.example.company_directory.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ImportResultDto {
    private List<ImportRowDto> totalList = new ArrayList<>();
    private List<ImportRowDto> successList = new ArrayList<>();
    private List<ImportErrorDto> errorList = new ArrayList<>();
    private List<ImportWorningDto> warningList = new ArrayList<>();
    private int totalCount;
    private int successCount;
    private int warningCount;
    private int errorCount;

    // --- Strict Mode Helpers (Exclude Warnings) ---
    public int getStrictInsertCount() {
        if (totalList == null)
            return 0;
        return (int) totalList.stream()
                .filter(row -> row != null && row.isValid() && !row.isUpdate() && !row.isHasWarning())
                .count();
    }

    public List<Integer> getStrictInsertRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && row.isValid() && !row.isUpdate() && !row.isHasWarning())
                .map(ImportRowDto::getRowNum)
                .toList();
    }

    public int getStrictUpdateCount() {
        if (totalList == null)
            return 0;
        return (int) totalList.stream()
                .filter(row -> row != null && row.isValid() && row.isUpdate() && !row.isHasWarning())
                .count();
    }

    public List<Integer> getStrictUpdateRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && row.isValid() && row.isUpdate() && !row.isHasWarning())
                .map(ImportRowDto::getRowNum)
                .toList();
    }

    // --- All Mode Helpers (Include Warnings) ---
    public int getAllInsertCount() {
        if (totalList == null)
            return 0;
        return (int) totalList.stream()
                .filter(row -> row != null && row.isValid() && !row.isUpdate())
                .count();
    }

    public List<Integer> getAllInsertRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && row.isValid() && !row.isUpdate())
                .map(ImportRowDto::getRowNum)
                .toList();
    }

    public int getAllUpdateCount() {
        if (totalList == null)
            return 0;
        return (int) totalList.stream()
                .filter(row -> row != null && row.isValid() && row.isUpdate())
                .count();
    }

    public List<Integer> getAllUpdateRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && row.isValid() && row.isUpdate())
                .map(ImportRowDto::getRowNum)
                .toList();
    }

    // --- Excluded Rows Helpers ---
    public List<Integer> getStrictExcludedRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && (row.isHasError() || row.isHasWarning()))
                .map(ImportRowDto::getRowNum)
                .sorted()
                .toList();
    }

    public List<Integer> getAllExcludedRows() {
        if (totalList == null)
            return new ArrayList<>();
        return totalList.stream()
                .filter(row -> row != null && row.isHasError())
                .map(ImportRowDto::getRowNum)
                .sorted()
                .toList();
    }
}
