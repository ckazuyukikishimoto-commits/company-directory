package com.example.company_directory.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * デスクトップ版で使うフォルダの場所をまとめたクラス。
 *
 * <ul>
 * <li>Windows: {@code %APPDATA%\CompanyDirectory}（例: C:\Users\あなた\AppData\Roaming\CompanyDirectory）</li>
 * <li>それ以外: {@code ~/.company-directory}</li>
 * </ul>
 * 環境変数 {@code COMPANY_DIRECTORY_HOME} があれば、そちらを優先します。
 */
final class DesktopPaths {

    private final Path home;

    private DesktopPaths(Path home) {
        this.home = home;
    }

    static DesktopPaths detect() {
        String override = System.getenv("COMPANY_DIRECTORY_HOME");
        if (override != null && !override.isBlank()) {
            return new DesktopPaths(Paths.get(override));
        }
        String appData = System.getenv("APPDATA");
        if (isWindows() && appData != null && !appData.isBlank()) {
            return new DesktopPaths(Paths.get(appData, "CompanyDirectory"));
        }
        return new DesktopPaths(Paths.get(System.getProperty("user.home"), ".company-directory"));
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    Path home() {
        return home;
    }

    /** DB の接続情報などを書く設定ファイル。 */
    Path envFile() {
        return home.resolve(".env");
    }

    Path logDir() {
        return home.resolve("logs");
    }

    /** 二重起動を防ぐためのロックファイル。 */
    Path lockFile() {
        return home.resolve("app.lock");
    }

    /** 起動中のアプリが使っているポート番号を書いておくファイル（2 回目の起動で使う）。 */
    Path portFile() {
        return home.resolve("app.port");
    }

    /** 専用ウィンドウ（Edge のアプリモード）用のブラウザのデータ置き場。 */
    Path browserProfileDir() {
        return home.resolve("browser-profile");
    }

    void createDirectories() throws java.io.IOException {
        Files.createDirectories(home);
        Files.createDirectories(logDir());
    }
}
