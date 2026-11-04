package com.example.company_directory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * デスクトップ版の設定（desktop プロファイル）でアプリが起動できるかを確かめる。
 * CompanyDirectoryApplicationTests と同じく、DB（SPRING_DATASOURCE_URL など）が必要。
 */
// テストで本番の DB につないでいても、ゴミ箱のデータを消さないよう起動時パージは止める
@SpringBootTest(properties = "app.trash.purge-on-startup=false")
@ActiveProfiles("desktop")
class DesktopProfileContextTests {

    @Autowired
    Environment env;

    @Test
    void contextLoadsWithDesktopProfile() {
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("spring.main.lazy-initialization")).isEqualTo("true");
    }
}
