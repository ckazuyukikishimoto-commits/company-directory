package com.example.company_directory.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;

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

    @Test
    void 空きの確認は待ち受けと同じ_127_0_0_1_で行う() {
        assertThat(DesktopLauncher.LISTEN_ADDRESS.getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void 先に127_0_0_1のポートが使われていたら別のポートを選ぶ() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            int used = other.getLocalPort();
            assertThat(DesktopLauncher.isFree(used)).isFalse();
            int chosen = DesktopLauncher.choosePort(used);
            assertThat(chosen).isNotEqualTo(used);
            // 選ばれたポートは 127.0.0.1 で本当に使える
            try (ServerSocket s = new ServerSocket(chosen, 0, InetAddress.getByName("127.0.0.1"))) {
                assertThat(s.getLocalPort()).isEqualTo(chosen);
            }
        }
    }

    @Test
    void 空いていれば希望のポートをそのまま使う() throws Exception {
        int free;
        try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            free = s.getLocalPort();
        }
        assertThat(DesktopLauncher.choosePort(free)).isEqualTo(free);
    }
}
