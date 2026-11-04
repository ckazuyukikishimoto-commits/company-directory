package com.example.company_directory.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesktopLauncherTest {

    @Test
    void 最初は企業一覧を開く_未ログインならログイン画面に回される() {
        assertThat(DesktopLauncher.startUrl(18080)).isEqualTo("http://127.0.0.1:18080/companies");
    }

    @Test
    void 待ち受けのアドレスとポートはコマンドライン引数で渡す() {
        String[] all = DesktopLauncher.withServerArgs(new String[] { "--debug" }, 18081);
        assertThat(all).containsExactly("--server.address=127.0.0.1", "--server.port=18081", "--debug");
    }
}
