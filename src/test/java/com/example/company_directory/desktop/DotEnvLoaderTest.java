package com.example.company_directory.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvLoaderTest {

    @TempDir
    Path dir;

    @Test
    void read_parsesCommentsQuotesExportAndBom() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                ﻿# コメント
                SPRING_DATASOURCE_URL=jdbc:postgresql://example:5432/db?sslmode=require
                export SPRING_DATASOURCE_USERNAME = "postgres.abc"
                SPRING_DATASOURCE_PASSWORD='p@ss=word'

                not a pair
                EMPTY=
                """, StandardCharsets.UTF_8);

        Map<String, String> values = DotEnvLoader.read(env);

        assertThat(values)
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://example:5432/db?sslmode=require")
                .containsEntry("SPRING_DATASOURCE_USERNAME", "postgres.abc")
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "p@ss=word")
                .containsEntry("EMPTY", "")
                .hasSize(4);
    }

    @Test
    void read_returnsEmptyWhenFileIsMissing() throws Exception {
        assertThat(DotEnvLoader.read(dir.resolve("nothing.env"))).isEmpty();
    }

    @Test
    void apply_doesNotOverwriteExistingSystemProperty() {
        String key = "DOTENV_LOADER_TEST_" + System.nanoTime();
        System.setProperty(key, "from-system");
        try {
            int applied = DotEnvLoader.apply(Map.of(key, "from-file"));
            assertThat(applied).isZero();
            assertThat(System.getProperty(key)).isEqualTo("from-system");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void apply_setsMissingValues() {
        String key = "DOTENV_LOADER_TEST_" + System.nanoTime();
        try {
            assertThat(DotEnvLoader.apply(Map.of(key, "v"))).isEqualTo(1);
            assertThat(DotEnvLoader.isDefined(key)).isTrue();
        } finally {
            System.clearProperty(key);
        }
    }
}
