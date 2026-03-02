package com.example.company_directory.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.company_directory.dto.CompanyImageExtractionDto;
import com.example.company_directory.dto.CompanyImageExtractionResponseDto;
import com.example.company_directory.dto.ImportResultDto;
import com.example.company_directory.dto.ImportRowDto;
import com.example.company_directory.service.CompanyImageExtractionService;
import com.example.company_directory.service.ExcelImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 複数件登録（まとめて登録）に関するAPI
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchApiController {

    private final ExcelImportService excelImportService;
    private final CompanyImageExtractionService companyImageExtractionService;

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

    /**
     * 画像から企業情報を抽出
     * POST /api/batch/extract-companies-from-image
     */
    @PostMapping(value = "/extract-companies-from-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractCompaniesFromImage(@RequestParam("image") MultipartFile image) {
        try {
            List<CompanyImageExtractionDto> companies = companyImageExtractionService.extractCompaniesFromImage(image);
            return ResponseEntity.ok(new CompanyImageExtractionResponseDto(companies));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("画像からの企業情報抽出でエラー", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "画像解析中にエラーが発生しました。"));
        }
    }
}
