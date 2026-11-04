package com.example.company_directory.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * デスクトップ版の {@code .env}（KEY=VALUE 形式）を読み、まだ決まっていない値だけを
 * Java のシステムプロパティに入れる。
 *
 * <p>
 * Web 版は「作業フォルダの .env」を spring-dotenv / {@code spring.config.import} で読んでいるが、
 * デスクトップ版はショートカットから起動するので作業フォルダが決まらない。そこで、
 * 決まった場所（{@link DesktopPaths#envFile()}）の .env をここで読む。
 * 環境変数が設定されていれば、そちらが優先される（上書きしない）。
 * </p>
 *
 * <p>
 * 秘密の値を扱うので、値そのものはログに出さない。
 * </p>
 */
final class DotEnvLoader {

    private DotEnvLoader() {
    }

    /** .env を読んで KEY と VALUE の組を返す（ファイルが無ければ空）。 */
    static Map<String, String> read(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return result;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("﻿")) { // メモ帳で保存したときの BOM
                line = line.substring(1);
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * .env の値をシステムプロパティに入れる。環境変数・既存のシステムプロパティが優先。
     *
     * @return 入れたキーの数
     */
    static int apply(Map<String, String> values) {
        int count = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (System.getenv(key) != null || System.getProperty(key) != null) {
                continue;
            }
            System.setProperty(key, e.getValue());
            count++;
        }
        return count;
    }

    /** 環境変数・システムプロパティのどちらかで値が決まっているか。 */
    static boolean isDefined(String key) {
        String v = System.getenv(key);
        if (v == null) {
            v = System.getProperty(key);
        }
        return v != null && !v.isBlank();
    }
}
