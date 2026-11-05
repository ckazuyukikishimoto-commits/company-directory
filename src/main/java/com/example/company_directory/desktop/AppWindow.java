package com.example.company_directory.desktop;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 画面を開く役。
 *
 * <p>
 * Windows には Microsoft Edge が必ず入っているので、Edge の「アプリモード」（{@code --app=URL}）で
 * アドレスバーの無い専用ウィンドウとして開く。専用のプロファイルフォルダ（{@code --user-data-dir}）を
 * 使うので、普段使いの Edge とは別のプロセスになり、「ウィンドウを閉じた＝アプリを終了」が分かる。
 * Edge（または Chrome）が見つからないときは、既定のブラウザで開く。
 * </p>
 *
 * <p>
 * 環境変数 {@code DESKTOP_UI=browser} にすると、いつも既定のブラウザで開く。
 * </p>
 */
final class AppWindow {

    private static final Logger log = LoggerFactory.getLogger(AppWindow.class);

    private final Path profileDir;

    AppWindow(Path profileDir) {
        this.profileDir = profileDir;
    }

    /**
     * 画面を開く。
     *
     * @return 専用ウィンドウで開けたときはそのプロセス。既定のブラウザで開いたときは空。
     */
    Optional<Process> open(String url) {
        boolean forceBrowser = "browser".equalsIgnoreCase(System.getenv("DESKTOP_UI"));
        if (!forceBrowser) {
            Optional<Path> exe = findChromiumBrowser();
            if (exe.isPresent()) {
                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(exe.get().toString());
                    cmd.add("--app=" + url);
                    cmd.add("--user-data-dir=" + profileDir.toAbsolutePath());
                    cmd.add("--no-first-run");
                    cmd.add("--no-default-browser-check");
                    cmd.add("--window-size=1280,860");
                    Process p = new ProcessBuilder(cmd)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
                    log.info("専用ウィンドウで開きました: {}", exe.get().getFileName());
                    return Optional.of(p);
                } catch (Exception e) {
                    log.warn("専用ウィンドウで開けなかったので、既定のブラウザで開きます", e);
                }
            }
        }
        openInDefaultBrowser(url);
        return Optional.empty();
    }

    static void openInDefaultBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            if (DesktopPaths.isWindows()) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            log.error("ブラウザを開けませんでした。手で {} を開いてください", url, e);
        }
    }

    /** Edge → Chrome の順に探す。見つからなければ空。 */
    static Optional<Path> findChromiumBrowser() {
        List<Path> candidates = new ArrayList<>();
        String custom = System.getenv("DESKTOP_BROWSER");
        if (custom != null && !custom.isBlank()) {
            candidates.add(Paths.get(custom));
        }
        if (DesktopPaths.isWindows()) {
            for (String env : new String[] { "ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA" }) {
                String base = System.getenv(env);
                if (base != null) {
                    candidates.add(Paths.get(base, "Microsoft", "Edge", "Application", "msedge.exe"));
                }
            }
            for (String env : new String[] { "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA" }) {
                String base = System.getenv(env);
                if (base != null) {
                    candidates.add(Paths.get(base, "Google", "Chrome", "Application", "chrome.exe"));
                }
            }
        } else {
            String path = System.getenv("PATH");
            if (path != null) {
                for (String dir : path.split(File.pathSeparator)) {
                    for (String name : new String[] { "microsoft-edge", "google-chrome", "chromium", "chromium-browser" }) {
                        candidates.add(Paths.get(dir, name));
                    }
                }
            }
        }
        return candidates.stream().filter(Files::isExecutable).findFirst();
    }
}
