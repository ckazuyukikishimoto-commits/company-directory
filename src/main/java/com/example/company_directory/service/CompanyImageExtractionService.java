package com.example.company_directory.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.company_directory.dto.CompanyImageExtractionDto;
import com.example.company_directory.entity.ZipMaster;
import com.example.company_directory.repository.ZipMasterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 画像から企業情報を抽出するサービス。
 * Google Cloud Vision APIとGoogle Gemini APIを使用して、画像から企業情報を抽出します。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CompanyImageExtractionService {

    private static final int MAX_COMPANIES = 25;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final ObjectMapper objectMapper;
    private final GeminiService geminiService;
    private final ZipMasterRepository zipMasterRepository;

    /**
     * 画像から企業情報を抽出する。
     * 
     * @param imageFile
     * @return 抽出された企業情報
     */
    public List<CompanyImageExtractionDto> extractCompaniesFromImage(MultipartFile imageFile) throws IOException {
        validateImageFile(imageFile);

        String jsonText = geminiService.extractCompanies(
                imageFile.getBytes(),
                imageFile.getContentType());

        return parseCompaniesFromText(jsonText);
    }

    /**
     * 画像ファイルを検証する。
     * 
     * @param imageFile
     */
    private void validateImageFile(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("画像ファイルを選択してください。");
        }
        if (imageFile.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("画像サイズは5MB以下にしてください。");
        }

        // 画像ファイルのContent-Typeと拡張子を取得
        String contentType = imageFile.getContentType() == null ? ""
                : imageFile.getContentType().toLowerCase(Locale.ROOT);
        String extension = extractExtension(imageFile.getOriginalFilename());

        // 画像ファイルのContent-Typeと拡張子が許可されているかを検証
        if (!ALLOWED_CONTENT_TYPES.contains(contentType) && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("JPGまたはPNG形式の画像を指定してください。");
        }
    }

    /**
     * Gemini APIのレスポンスから企業情報を抽出する。
     * 
     * @param geminiText
     * @return 抽出された企業情報
     */
    private List<CompanyImageExtractionDto> parseCompaniesFromText(String geminiText) {
        String jsonText = extractJsonPayload(geminiText);
        if (jsonText.isBlank()) {
            return Collections.emptyList();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(jsonText, Map.class);
            List<Map<String, Object>> companies = castMapList(jsonMap.get("companies"));
            if (companies.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> uniqueKeys = new LinkedHashSet<>();
            Map<Integer, CompanyImageExtractionDto> resultMap = new LinkedHashMap<>();
            List<Map.Entry<Integer, CompanyImageExtractionDto>> toBeCorrected = new ArrayList<>();

            for (Map<String, Object> company : companies) {
                String companyName = trimToEmpty(company.get("companyName"));
                String zipCode = normalizeZipCode(trimToEmpty(company.get("zipCode")));
                String address = trimToEmpty(company.get("address"));

                if (companyName.isBlank())
                    continue; // 企業名なしは破棄

                String uniqueKey = companyName + "|" + zipCode + "|" + address;
                if (!uniqueKeys.add(uniqueKey))
                    continue;

                int index = resultMap.size();
                CompanyImageExtractionDto dto = new CompanyImageExtractionDto(companyName, zipCode, address);
                resultMap.put(index, dto);

                // 欠損があれば補正リストに追加
                if (zipCode.isBlank() || address.isBlank()) {
                    toBeCorrected.add(Map.entry(index, dto));
                }

                if (resultMap.size() >= MAX_COMPANIES)
                    break;
            }

            // 補正処理（別メソッドに切り出す）
            correctCompanies(toBeCorrected, resultMap);

            return new ArrayList<>(resultMap.values());
        } catch (Exception e) {
            log.warn("Gemini response parse failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * テキストからJSONペイロードを抽出する。
     * 
     * @param text
     * @return 抽出されたJSONペイロード
     */
    private String extractJsonPayload(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceAll("\\s*```$", "");
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    /**
     * オブジェクトをMapリストに変換する。
     * 
     * @param target
     * @return Mapリスト
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object target) {
        if (!(target instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> mapItem) {
                result.add((Map<String, Object>) mapItem);
            }
        }
        return result;
    }

    /**
     * 郵便番号を正規化する。
     * 
     * @param zipCode
     * @return 正規化された郵便番号
     */
    private String normalizeZipCode(String zipCode) {
        if (zipCode == null || zipCode.isBlank()) {
            return "";
        }
        String digits = zipCode.replaceAll("[^0-9]", "");
        if (digits.length() == 7) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        return zipCode.trim();
    }

    /**
     * ファイル名から拡張子を抽出する。
     * 
     * @param fileName
     * @return 抽出された拡張子
     */
    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void correctCompanies(
        List<Map.Entry<Integer, CompanyImageExtractionDto>> toBeCorrected,
        Map<Integer, CompanyImageExtractionDto> resultMap) {

        // Geminiで補完が必要なものだけ集める
        List<Map.Entry<Integer, CompanyImageExtractionDto>> needGemini = new ArrayList<>();

        for (Map.Entry<Integer, CompanyImageExtractionDto> entry : toBeCorrected) {
            int index = entry.getKey();
            CompanyImageExtractionDto dto = entry.getValue();

            // 住所あり・郵便番号なし → DBで逆引き
            if (!dto.getAddress().isBlank() && dto.getZipCode().isBlank()) {
                List<ZipMaster> results = zipMasterRepository.searchByAddress(dto.getAddress());
                if (!results.isEmpty()) {
                    dto.setZipCode(normalizeZipCode(results.get(0).getZipCode()));
                    resultMap.put(index, dto);
                    continue;
                }
            }

            // 郵便番号あり・住所なし → DBで住所取得
            if (!dto.getZipCode().isBlank() && dto.getAddress().isBlank()) {
                String cleanZip = dto.getZipCode().replace("-", "");
                ZipMaster master = zipMasterRepository.findByZipCode(cleanZip);
                if (master != null) {
                    String address = master.getPrefecture() + master.getCity() + master.getTown();
                    dto.setAddress(address);
                    resultMap.put(index, dto);
                    continue;
                }
            }

            // DBで解決できなかったものをGemini用リストに追加
            needGemini.add(entry);
        }

        // まとめてGeminiに投げる
        if (!needGemini.isEmpty()) {
            // 企業名だけ抽出してGeminiに渡す
            List<String> companyNames = needGemini.stream()
                    .map(e -> e.getValue().getCompanyName())
                    .toList();

            String jsonText = geminiService.correctCompanies(companyNames);

            List<CompanyImageExtractionDto> corrected = parseCompaniesFromTextSimple(jsonText);

            // 結果をresultMapに反映
            for (int i = 0; i < corrected.size(); i++) {
                int index = needGemini.get(i).getKey();
                resultMap.put(index, corrected.get(i));
            }
        }
    }

    private List<CompanyImageExtractionDto> parseCompaniesFromTextSimple(String geminiText) {
        String jsonText = extractJsonPayload(geminiText);
        if (jsonText.isBlank()) {
            return Collections.emptyList();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(jsonText, Map.class);
            List<Map<String, Object>> companies = castMapList(jsonMap.get("companies"));
            if (companies.isEmpty()) {
                return Collections.emptyList();
            }
    
            List<CompanyImageExtractionDto> results = new ArrayList<>();
            for (Map<String, Object> company : companies) {
                String companyName = trimToEmpty(company.get("companyName"));
                String zipCode = normalizeZipCode(trimToEmpty(company.get("zipCode")));
                String address = trimToEmpty(company.get("address"));
                results.add(new CompanyImageExtractionDto(companyName, zipCode, address));
            }
            return results;
        } catch (Exception e) {
            log.warn("parse failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
