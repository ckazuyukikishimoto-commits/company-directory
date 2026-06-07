package com.example.company_directory.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

        private final Client client = Client.builder().build();

        private static final String GEMINI_MODEL = "gemini-3.5-flash";

        /**
         * 画像バイト列から企業情報JSONを抽出して返す
         * 
         * @param imageBytes 画像バイト列
         * @param mimeType   画像バイト列のMIMEタイプ
         * @return 企業情報JSON
         */
        public String extractCompanies(byte[] imageBytes, String mimeType) {

                var imagePart = Part.fromBytes(imageBytes, mimeType);
                var textPart = Part.fromText("""
                                画像から、同じ行に記述されている文字列を1セットとし、
                                企業名・郵便番号・住所の情報を抽出してください。
                                画像に記載されている情報はそのまま使用し、変更しないでください。
                                郵便番号が記載されていない場合は住所から調べて補完してください。
                                住所が記載されていない場合は企業名から調べて補完してください。
                                郵便番号・住所どちらも記載されていない場合は企業名から両方調べて補完してください。
                                郵便番号はハイフンあり形式（例: 123-4567）に整形してください。
                                住所には必ず「高岡市」を含めてください。
                                """);

                // ★ propertiesは1つのMapにまとめて渡す（上書きされるのを防ぐ）
                var companySchema = Schema.builder()
                                .type(new Type(Type.Known.OBJECT))
                                .properties(Map.of(
                                                "companyName",
                                                Schema.builder().type(new Type(Type.Known.STRING)).build(),
                                                "zipCode",
                                                Schema.builder().type(new Type(Type.Known.STRING)).nullable(true)
                                                                .build(),
                                                "address",
                                                Schema.builder().type(new Type(Type.Known.STRING)).nullable(true)
                                                                .build()))
                                .required(List.of("companyName", "zipCode", "address"))
                                .build();

                var rootSchema = Schema.builder()
                                .type(new Type(Type.Known.OBJECT))
                                .properties(Map.of(
                                                "companies", Schema.builder()
                                                                .type(new Type(Type.Known.ARRAY))
                                                                .items(companySchema)
                                                                .build()))
                                .required(List.of("companies"))
                                .build();

                var config = GenerateContentConfig.builder()
                                .responseMimeType("application/json")
                                .responseSchema(rootSchema)
                                .build();

                var content = Content.builder()
                                .parts(List.of(imagePart, textPart))
                                .build();

                var response = client.models.generateContent(
                                GEMINI_MODEL,
                                List.of(content),
                                config);

                return response.text(); // 純粋なJSONが返ってくる
        }

        public String correctCompanies(List<String> companyNames) {

                var textPart = Part.fromText("""
                                以下の企業名から住所と郵便番号を調べ、企業ごとに1件出力してください。
                                郵便番号はハイフン形式（例：123-4567）で出力してください。
                                情報が見つからない場合はnullにしてください。
                                住所には必ず「高岡市」を含めてください。
                                企業名もそのまま含めて出力してください。

                                企業名一覧：
                                """ + companyNames);

                var companySchema = Schema.builder()
                                .type(new Type(Type.Known.OBJECT))
                                .properties(Map.of(
                                                "companyName",
                                                Schema.builder().type(new Type(Type.Known.STRING)).build(),
                                                "zipCode",
                                                Schema.builder().type(new Type(Type.Known.STRING)).nullable(true)
                                                                .build(),
                                                "address",
                                                Schema.builder().type(new Type(Type.Known.STRING)).nullable(true)
                                                                .build()))
                                .required(List.of("companyName", "zipCode", "address"))
                                .build();

                var rootSchema = Schema.builder()
                                .type(new Type(Type.Known.OBJECT))
                                .properties(Map.of(
                                                "companies", Schema.builder()
                                                                .type(new Type(Type.Known.ARRAY))
                                                                .items(companySchema)
                                                                .build()))
                                .required(List.of("companies"))
                                .build();

                var config = GenerateContentConfig.builder()
                                .responseMimeType("application/json")
                                .responseSchema(rootSchema)
                                .build();

                var content = Content.builder()
                                .parts(List.of(textPart))
                                .build();

                var response = client.models.generateContent(
                                GEMINI_MODEL,
                                List.of(content),
                                config);

                return response.text();
        }
}