package com.example.company_directory.service;

import com.example.company_directory.dto.CompanySearchResultDto;
import com.example.company_directory.entity.CompanyMaster;
import com.example.company_directory.repository.CompanyMasterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 住所から企業名候補を検索するサービス。
 * データベース（company_masters テーブル）と Gemini API (Google Search Grounding) を使用。
 */
@Slf4j
@Service
public class CompanySearchService {

    private final RestTemplate restTemplate;
    private final CompanyMasterRepository companyMasterRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta";

    public CompanySearchService(CompanyMasterRepository companyMasterRepository) {
        this.restTemplate = new RestTemplate();
        this.companyMasterRepository = companyMasterRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 住所から企業名候補を検索する（データベース + Gemini API統合）
     */
    public List<CompanySearchResultDto> searchByAddress(String address) {
        List<CompanySearchResultDto> results = new ArrayList<>();

        // 1. データベースで検索
        try {
            List<CompanySearchResultDto> dbResults = searchByDatabase(address);
            results.addAll(dbResults);
            log.info("データベース: {}件の結果を取得", dbResults.size());
        } catch (Exception e) {
            log.warn("データベース検索に失敗しました: {}", e.getMessage());
        }

        // 2. Gemini API で検索
        try {
            List<CompanySearchResultDto> geminiResults = searchByGeminiApi(address);
            results.addAll(geminiResults);
            log.info("Gemini API: {}件の結果を取得", geminiResults.size());
        } catch (Throwable t) {
            // ネイティブライブラリ読込エラー（UnsatisfiedLinkError）も含めて握りつぶし、検索全体は継続
            log.warn("Gemini APIの呼び出しに失敗しました: {}", t.getMessage());
        }

        // 3. 重複を除去（企業名ベース）
        return deduplicateResults(results);
    }

    /**
     * データベース（company_masters テーブル）から住所で企業を検索
     */
    private List<CompanySearchResultDto> searchByDatabase(String address) {
        List<CompanyMaster> masters = companyMasterRepository.findTop20ByAddressContaining(address);

        List<CompanySearchResultDto> results = new ArrayList<>();
        for (CompanyMaster master : masters) {
            if (master.getCompanyName() == null || master.getCompanyName().isBlank()) {
                continue;
            }
            results.add(new CompanySearchResultDto(
                    master.getCompanyName(),
                    master.getCorporateNumber(),
                    master.getAddress(),
                    "データベース"));
        }
        return results;
    }

    /**
     * Gemini API (Google Search Grounding) で住所から企業を検索
     */
    private List<CompanySearchResultDto> searchByGeminiApi(String address) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("Gemini APIキーが設定されていません");
            return Collections.emptyList();
        }

        String url = GEMINI_API_BASE + "/models/gemini-3-flash-preview:generateContent?key=" + geminiApiKey;

        // Google Search Grounding を使ったプロンプト
        String prompt = "以下の住所に存在する建物名・施設名・企業名・会社名・店舗名・事業所名をGoogle検索結果をもとに可能な限り多く列挙してください。\n"
                + "確実でなくても候補として含めてください。\n"
                + "結果は必ず以下のJSON形式のみで出力してください。\n"
                + "余計な説明・マークダウン・コードブロックは不要です。\n\n"
                + "{\"companies\": [\"名称1\", \"名称2\", \"名称3\"]}\n\n"
                + "住所: " + address;

        // Gemini API リクエストボデ
        Map<String, Object> requestBody = new LinkedHashMap<>();

        // contents
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(textPart));
        requestBody.put("contents", List.of(userContent));

        // tools: google_search grounding
        Map<String, Object> googleSearchTool = Map.of(
                "google_search", Map.of());
        requestBody.put("tools", List.of(googleSearchTool));

        log.info("Gemini API リクエスト送信: address={}", address);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            log.info("Gemini API レスポンス: {}", response);
            if (response == null) {
                return Collections.emptyList();
            }

            return parseGeminiResponse(response, address);
        } catch (Throwable t) {
            log.warn("Gemini API呼び出しエラー: {}", t.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gemini APIレスポンスをパースする
     */
    @SuppressWarnings("unchecked")
    private List<CompanySearchResultDto> parseGeminiResponse(Map<String, Object> response, String address) {
        List<CompanySearchResultDto> results = new ArrayList<>();
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return results;
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null)
                return results;

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty())
                return results;

            String text = (String) parts.get(0).get("text");
            if (text == null || text.isBlank())
                return results;

            // マークダウンの不純物（```json ... ```）を除去
            String cleanedText = text.trim();
            if (cleanedText.startsWith("```")) {
                cleanedText = cleanedText.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
            }

            // JSONをパース
            Map<String, Object> jsonMap = objectMapper.readValue(cleanedText, Map.class);
            List<String> companyNames = (List<String>) jsonMap.get("companies");

            if (companyNames != null) {
                for (String companyName : companyNames) {
                    if (companyName == null) {
                        continue;
                    }
                    String trimmed = companyName.trim();
                    if (!trimmed.isBlank() && trimmed.length() >= 2) {
                        results.add(new CompanySearchResultDto(
                                trimmed, null, address, "Gemini"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Geminiレスポンスパースエラー: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * 企業名をキーにして重複を除去する
     */
    private List<CompanySearchResultDto> deduplicateResults(List<CompanySearchResultDto> results) {
        Map<String, CompanySearchResultDto> unique = new LinkedHashMap<>();
        for (CompanySearchResultDto dto : results) {
            if (dto == null || dto.getCompanyName() == null || dto.getCompanyName().isBlank()) {
                continue;
            }

            String key = dto.getCompanyName().trim();
            if (!unique.containsKey(key)) {
                unique.put(key, dto);
            } else {
                // データベースの結果を優先（法人番号が付いているため）
                CompanySearchResultDto existing = unique.get(key);
                if (existing.getCorporateNumber() == null && dto.getCorporateNumber() != null) {
                    unique.put(key, dto);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }
}
