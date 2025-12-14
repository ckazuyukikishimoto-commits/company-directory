package com.example.company_directory.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ImportRowDto {
    private int rowNum;
    private String companyId;
    private String companyName;
    private String address;
    private String zipCode;
    private String registrationDate;
    private String remarks;
    private List<String> errorMessages = new ArrayList<>();
    private List<String> warningMessages = new ArrayList<>();
    private boolean hasError;
    private boolean hasWarning;
    private boolean isValid = true;
    private boolean isUpdate; // trueなら更新、falseなら新規

    public boolean isUpdate() {
        return isUpdate;
    }

    public boolean isHasWarning() {
        return hasWarning;
    }

    public boolean isValid() {
        return isValid;
    }

    public boolean isHasError() {
        return hasError;
    }

    public String getOperationType() {
        return isUpdate ? "更新" : "新規";
    }
}
