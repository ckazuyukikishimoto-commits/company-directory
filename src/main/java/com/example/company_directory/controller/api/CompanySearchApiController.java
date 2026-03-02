package com.example.company_directory.controller.api;

import com.example.company_directory.dto.CompanySearchResultDto;
import com.example.company_directory.service.CompanySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 住所から企業名候補を検索する REST API コントローラー
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanySearchApiController {

    private final CompanySearchService companySearchService;

    /**
     * 住所から企業名候補を検索
     * GET /api/companies/search-by-address?address=東京都千代田区丸の内１丁目
     */
    @GetMapping("/search-by-address")
    public ResponseEntity<?> searchByAddress(
            @RequestParam("address") String address,
            @RequestParam(value = "useDb", defaultValue = "true") boolean useDb,
            @RequestParam(value = "useGemini", defaultValue = "true") boolean useGemini,
            @RequestParam(value = "usePlaces", defaultValue = "true") boolean usePlaces) {
        if (address == null || address.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "住所を入力してください"));
        }

        List<CompanySearchResultDto> results = companySearchService.searchByAddress(address.trim(), useDb, useGemini,
                usePlaces);
        return ResponseEntity.ok(results);
    }
}
