package com.example.company_directory.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.company_directory.dto.CompanyImageExtractionDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CompanyImageExtractionService {

    private static final String VISION_API_URL = "https://vision.googleapis.com/v1/images:annotate";
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String GEMINI_MODEL = "gemini-3-flash-preview";
    private static final int MAX_COMPANIES = 25;
    private static final int MAX_OCR_TOKENS = 500;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    public CompanyImageExtractionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public List<CompanyImageExtractionDto> extractCompaniesFromImage(MultipartFile imageFile) {
        validateApiKey();
        validateImageFile(imageFile);

        String base64Image = encodeImageToBase64(imageFile);
        List<OcrTextToken> ocrTokens = executeDocumentTextDetection(base64Image);
        if (ocrTokens.isEmpty()) {
            return Collections.emptyList();
        }

        return executeGeminiExtraction(ocrTokens);
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("APIキーが設定されていません。");
        }
    }

    private void validateImageFile(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("画像ファイルを選択してください。");
        }
        if (imageFile.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("画像サイズは5MB以下にしてください。");
        }

        String contentType = imageFile.getContentType() == null ? "" : imageFile.getContentType().toLowerCase(Locale.ROOT);
        String extension = extractExtension(imageFile.getOriginalFilename());

        if (!ALLOWED_CONTENT_TYPES.contains(contentType) && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("JPGまたはPNG形式の画像を指定してください。");
        }
    }

    private String encodeImageToBase64(MultipartFile imageFile) {
        try {
            return Base64.getEncoder().encodeToString(imageFile.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("画像の読み込みに失敗しました。", e);
        }
    }

    private List<OcrTextToken> executeDocumentTextDetection(String base64Image) {
        String url = VISION_API_URL + "?key=" + apiKey;

        Map<String, Object> image = Map.of("content", base64Image);
        Map<String, Object> feature = Map.of("type", "DOCUMENT_TEXT_DETECTION");
        Map<String, Object> request = Map.of(
                "image", image,
                "features", List.of(feature));
        Map<String, Object> requestBody = Map.of("requests", List.of(request));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        return parseVisionResponse(response);
    }

    @SuppressWarnings("unchecked")
    private List<OcrTextToken> parseVisionResponse(Map<String, Object> response) {
        if (response == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> responses = castMapList(response.get("responses"));
        if (responses.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> firstResponse = responses.get(0);
        if (firstResponse == null || firstResponse.get("error") != null) {
            log.warn("Vision API error response: {}", firstResponse);
            return Collections.emptyList();
        }

        List<Map<String, Object>> textAnnotations = castMapList(firstResponse.get("textAnnotations"));
        if (textAnnotations.isEmpty()) {
            return Collections.emptyList();
        }

        int startIndex = textAnnotations.size() > 1 ? 1 : 0;
        List<OcrTextToken> tokens = new ArrayList<>();
        for (int i = startIndex; i < textAnnotations.size() && tokens.size() < MAX_OCR_TOKENS; i++) {
            Map<String, Object> annotation = textAnnotations.get(i);
            if (annotation == null) {
                continue;
            }

            String text = trimToEmpty(annotation.get("description"));
            if (text.isBlank()) {
                continue;
            }

            List<Map<String, Integer>> vertices = parseVertices(annotation.get("boundingPoly"));
            if (vertices.isEmpty()) {
                continue;
            }
            tokens.add(new OcrTextToken(text, vertices));
        }
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Integer>> parseVertices(Object boundingPolyObject) {
        if (!(boundingPolyObject instanceof Map<?, ?> boundingPoly)) {
            return Collections.emptyList();
        }

        Object verticesObject = boundingPoly.get("vertices");
        if (!(verticesObject instanceof List<?> verticesList)) {
            return Collections.emptyList();
        }

        List<Map<String, Integer>> vertices = new ArrayList<>();
        for (Object vertexObject : verticesList) {
            if (!(vertexObject instanceof Map<?, ?> vertexMap)) {
                continue;
            }

            int x = toInt(vertexMap.get("x"));
            int y = toInt(vertexMap.get("y"));
            Map<String, Integer> point = new LinkedHashMap<>();
            point.put("x", x);
            point.put("y", y);
            vertices.add(point);
        }
        return vertices;
    }

    private List<CompanyImageExtractionDto> executeGeminiExtraction(List<OcrTextToken> ocrTokens) {
        String prompt = buildPrompt(ocrTokens);
        String url = GEMINI_API_BASE + "/models/" + GEMINI_MODEL + ":generateContent?key=" + apiKey;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(textPart));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        return parseGeminiResponse(response);
    }

    private String buildPrompt(List<OcrTextToken> ocrTokens) {
        List<Map<String, Object>> tokenPayload = new ArrayList<>();
        for (OcrTextToken token : ocrTokens) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("text", token.text());
            row.put("boundingPoly", token.vertices());
            tokenPayload.add(row);
        }

        try {
            String tokenJson = objectMapper.writeValueAsString(tokenPayload);
            return """
                    以下は画像から取得したテキストと座標の一覧です。
                    Y座標が近いテキスト同士を同じ行として判定し、
                    企業名・郵便番号・住所のセットにまとめてください。
                    最大25件まで対応してください。
                    結果は以下のJSON形式のみで出力してください。余計な説明・マークダウン・コードブロックは不要です。
                    
                    {"companies": [{"companyName": "企業名", "zipCode": "郵便番号", "address": "住所"}]}
                    
                    テキストと座標の一覧:
                    %s
                    """.formatted(tokenJson);
        } catch (Exception e) {
            throw new IllegalStateException("OCR結果の組み立てに失敗しました。", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<CompanyImageExtractionDto> parseGeminiResponse(Map<String, Object> response) {
        if (response == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> candidates = castMapList(response.get("candidates"));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> firstCandidate = candidates.get(0);
        if (firstCandidate == null || firstCandidate.get("content") == null) {
            return Collections.emptyList();
        }

        if (firstCandidate.get("content") instanceof Map<?, ?> contentMap) {
            String text = extractTextFromGeminiContent(contentMap);
            if (text.isBlank()) {
                return Collections.emptyList();
            }
            return parseCompaniesFromText(text);
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiContent(Map<?, ?> contentMap) {
        Object partsObject = contentMap.get("parts");
        if (!(partsObject instanceof List<?> parts) || parts.isEmpty()) {
            return "";
        }

        Object firstPart = parts.get(0);
        if (!(firstPart instanceof Map<?, ?> partMap)) {
            return "";
        }

        return trimToEmpty(partMap.get("text"));
    }

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
            List<CompanyImageExtractionDto> results = new ArrayList<>();
            for (Map<String, Object> company : companies) {
                String companyName = trimToEmpty(company.get("companyName"));
                String zipCode = normalizeZipCode(trimToEmpty(company.get("zipCode")));
                String address = trimToEmpty(company.get("address"));

                if (companyName.isBlank() && zipCode.isBlank() && address.isBlank()) {
                    continue;
                }

                String uniqueKey = companyName + "|" + zipCode + "|" + address;
                if (!uniqueKeys.add(uniqueKey)) {
                    continue;
                }

                results.add(new CompanyImageExtractionDto(companyName, zipCode, address));
                if (results.size() >= MAX_COMPANIES) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Gemini response parse failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

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

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

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

    private record OcrTextToken(String text, List<Map<String, Integer>> vertices) {
    }
}
