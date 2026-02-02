package com.example.company_directory.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.company_directory.dto.ImportResultDto;
import com.example.company_directory.dto.ImportRowDto;
import com.example.company_directory.service.ExcelImportService;

import lombok.RequiredArgsConstructor;

/**
 * 複数件登録（まとめて登録）に関するAPI
 */
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchApiController {

    private final ExcelImportService excelImportService;

    /**
     * 送信された複数件の企業データを一括バリデーションする
     * POST /api/batch/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ImportResultDto> validate(@RequestBody List<ImportRowDto> rows) {
        // バリデーションを実行して結果を返す
        ImportResultDto result = excelImportService.validateRows(rows);
        return ResponseEntity.ok(result);
    }
}
