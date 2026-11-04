package com.example.company_directory.desktop;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SplashScreen;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.company_directory.CompanyDirectoryApplication;

/**
 * デスクトップ版の入口（jpackage で作った .exe はここから始まる）。
 *
 * <p>
 * やること:
 * </p>
 * <ol>
 * <li>二重起動を確かめる（もう動いていれば、その画面を開いて終わる）</li>
 * <li>%APPDATA%\CompanyDirectory\.env を読む（無ければ雛形を作って案内する）</li>
 * <li>DB へつながるかの確認と、Spring Boot の起動を<strong>同時に</strong>始める</li>
 * <li>準備ができたら専用ウィンドウ（Edge のアプリモード）で画面を開く</li>
 * <li>ウィンドウを閉じたら Spring Boot を止めて終了する。タスクトレイの「終了」でも止められる</li>
 * </ol>
 *
 * <p>
 * Web 版（Docker・Render）は今までどおり {@link CompanyDirectoryApplication#main} から起動するので、
 * このクラスの影響を受けない。
 * </p>
 */
public final class DesktopLauncher {

    private static final String APP_TITLE = "企業名簿";
    private static final int DEFAULT_PORT = 18080;
    private static final String SUPABASE_DASHBOARD = "https://supabase.com/dashboard/projects";

    /** 専用ウィンドウがこれより早く閉じたら「別の Edge に引き継がれた」と見なして、終了しない。 */
    private static final long HANDOFF_THRESHOLD_MILLIS = 5_000;

    private static volatile ConfigurableApplicationContext context;
    private static volatile boolean exiting;
    /** エラーのダイアログを閉じたあとに開くフォルダ（無ければ null）。 */
    private static volatile Path openFolderAfterDialog;

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        boolean hasScreen = DesktopPaths.isWindows() || System.getenv("DISPLAY") != null
                || System.getenv("WAYLAND_DISPLAY") != null;
        System.setProperty("java.awt.headless", String.valueOf(!hasScreen));
        DesktopPaths paths = DesktopPaths.detect();
        try {
            paths.createDirectories();
        } catch (IOException e) {
            fatal("フォルダを作れませんでした", paths.home() + "\n" + e.getMessage(), false);
            return;
        }

        // 1. 二重起動の確認
        FileLock lock = tryLock(paths.lockFile());
        if (lock == null) {
            openRunningInstance(paths);
            return;
        }

        // 2. 設定ファイル（.env）
        try {
            DotEnvLoader.apply(DotEnvLoader.read(paths.envFile()));
        } catch (IOException e) {
            fatal("設定ファイルを読めませんでした", paths.envFile() + "\n" + e.getMessage(), false);
            return;
        }
        if (!DotEnvLoader.isDefined("SPRING_DATASOURCE_URL")) {
            createEnvTemplateIfMissing(paths.envFile());
            openFolderAfterDialog = paths.home();
            fatal("データベースの接続情報が設定されていません",
                    "次のファイルを開いて、Supabase の接続情報を書いてください。\n"
                            + paths.envFile() + "\n\n"
                            + "（OK を押すとフォルダを開きます。書き終わったら、もう一度起動してください）",
                    false);
            return;
        }

        // 3. DB の確認（並行）と Spring Boot の起動
        // 普通の（デーモンでない）スレッドで動かす。ダイアログを出す前に JVM が終わらないように
        ExecutorService checker = Executors.newSingleThreadExecutor(r -> new Thread(r, "db-check"));
        CompletableFuture<DatabaseCheck.Result> dbCheck = CompletableFuture
                .supplyAsync(() -> DatabaseCheck.run(10), checker);
        checker.shutdown();
        dbCheck.thenAccept(r -> {
            if (!r.ok() && context == null) {
                // Spring の起動を待たずに知らせる（つながらない DB を何十秒も待たせない）
                fatal(r.title(), r.detail(), r.maybePaused());
            }
        });

        int port = choosePort();
        // 127.0.0.1 で待ち受けているので、localhost（IPv6 の ::1 になることがある）ではなく数字で書く
        String url = "http://127.0.0.1:" + port + "/login";
        try {
            context = new SpringApplicationBuilder(CompanyDirectoryApplication.class)
                    .profiles("desktop")
                    .headless(!hasScreen)
                    .properties(
                            "server.port=" + port,
                            "logging.file.name=" + paths.logDir().resolve("app.log").toAbsolutePath())
                    .run(args);
        } catch (Throwable e) {
            if (exiting) {
                return;
            }
            DatabaseCheck.Result r;
            try {
                r = dbCheck.get(15, TimeUnit.SECONDS);
            } catch (Exception waitFailed) {
                r = null;
            }
            if (r != null && !r.ok()) {
                return; // DB 確認のほうでダイアログを出している
            }
            boolean dbProblem = looksLikeDatabaseProblem(e);
            DatabaseCheck.Result c = DatabaseCheck.classify(e);
            fatal(dbProblem ? c.title() : "起動できませんでした",
                    (dbProblem ? c.detail() : String.valueOf(e.getMessage()))
                            + "\n\nログ: " + paths.logDir().resolve("app.log"),
                    dbProblem);
            return;
        }
        if (exiting) {
            return;
        }

        writePort(paths.portFile(), port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(paths.portFile())));

        // 4. 画面を開く
        closeSplash();
        CompletableFuture.runAsync(DesktopLauncher::deleteOldCdsArchives);
        if ("1".equals(System.getenv("DESKTOP_SMOKE_TEST"))) {
            // GitHub Actions の動作確認用: 画面は開かず、少し待ってから正しく終了する
            // （CI のマシンは遅いので、確認スクリプトが画面を取りに来るまで 30 秒待つ）
            Thread t = new Thread(() -> {
                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                shutdown(0);
            }, "smoke-test-exit");
            t.start();
            return;
        }
        AppWindow window = new AppWindow(paths.browserProfileDir());
        installTrayIcon(window, url, paths);
        Optional<Process> windowProcess = window.open(url);

        // 5. 専用ウィンドウを閉じたら終了
        windowProcess.ifPresent(p -> {
            long startedAt = System.currentTimeMillis();
            p.onExit().thenRun(() -> {
                long alive = System.currentTimeMillis() - startedAt;
                if (alive < HANDOFF_THRESHOLD_MILLIS) {
                    // 古い Edge がまだ残っていて、そちらに引き継がれた等。トレイの「終了」で止めてもらう
                    return;
                }
                shutdown(0);
            });
        });
    }

    /** アプリを止めて JVM を終わらせる。 */
    static synchronized void shutdown(int code) {
        if (exiting) {
            return;
        }
        exiting = true;
        ConfigurableApplicationContext ctx = context;
        if (ctx != null) {
            // SpringApplication.exit で正しく止める（CDS のキャッシュもこのとき保存される）
            code = SpringApplication.exit(ctx, () -> 0) == 0 ? code : 1;
        }
        System.exit(code);
    }

    // ---------------------------------------------------------------- 二重起動

    private static FileLock tryLock(Path lockFile) {
        try {
            FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = ch.tryLock();
            if (lock == null) {
                ch.close();
            }
            return lock; // JVM が終わるまで持ち続ける（終われば OS が外す）
        } catch (Exception e) {
            return null;
        }
    }

    private static void openRunningInstance(DesktopPaths paths) {
        // もう 1 つが起動途中かもしれないので、ポート番号が書かれるまで少し待つ
        for (int i = 0; i < 120; i++) {
            try {
                if (Files.exists(paths.portFile())) {
                    String port = Files.readString(paths.portFile(), StandardCharsets.UTF_8).strip();
                    closeSplash();
                    new AppWindow(paths.browserProfileDir()).open("http://127.0.0.1:" + port + "/login");
                    System.exit(0);
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (Exception e) {
                break;
            }
        }
        fatal("すでに起動しています", "タスクトレイの「企業名簿」アイコンから画面を開いてください。", false);
    }

    // ---------------------------------------------------------------- ポート

    /** 18080（環境変数 DESKTOP_PORT で変更可）が空いていれば使い、使われていれば空いている番号を選ぶ。 */
    static int choosePort() {
        int preferred = DEFAULT_PORT;
        String env = System.getenv("DESKTOP_PORT");
        if (env != null && env.matches("\\d{2,5}")) {
            preferred = Integer.parseInt(env);
        }
        if (isFree(preferred)) {
            return preferred;
        }
        try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isFree(int port) {
        try (ServerSocket s = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
            s.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void writePort(Path file, int port) {
        try {
            Files.writeString(file, String.valueOf(port), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 2 回目の起動で使うだけなので、書けなくても動作には影響しない
        }
    }

    // ---------------------------------------------------------------- 画面まわり

    private static void installTrayIcon(AppWindow window, String url, DesktopPaths paths) {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            return;
        }
        try {
            Image image = Toolkit.getDefaultToolkit().getImage(DesktopLauncher.class.getResource("/desktop/icon-16.png"));
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("画面を開く");
            open.addActionListener(e -> window.open(url));
            MenuItem logs = new MenuItem("ログのフォルダを開く");
            logs.addActionListener(e -> openFolder(paths.logDir()));
            MenuItem settings = new MenuItem("設定(.env)のフォルダを開く");
            settings.addActionListener(e -> openFolder(paths.home()));
            MenuItem quit = new MenuItem("終了");
            quit.addActionListener(e -> new Thread(() -> shutdown(0), "desktop-shutdown").start());
            menu.add(open);
            menu.add(logs);
            menu.add(settings);
            menu.addSeparator();
            menu.add(quit);
            TrayIcon icon = new TrayIcon(image, APP_TITLE + "（起動中）", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> window.open(url)); // ダブルクリックで画面を開く
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException | RuntimeException e) {
            // トレイが使えなくても、ウィンドウを閉じれば終了できる
        }
    }

    private static void closeSplash() {
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            if (splash != null && splash.isVisible()) {
                splash.close();
            }
        } catch (Exception ignored) {
            // 起動画面なしで起動したとき
        }
    }

    private static void openFolder(Path dir) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception ignored) {
            // 開けなくても致命的ではない
        }
    }

    /** エラーを日本語のダイアログで知らせて終了する。 */
    private static synchronized void fatal(String title, String detail, boolean offerSupabase) {
        if (exiting) {
            return;
        }
        exiting = true;
        closeSplash();
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(title + ": " + detail);
        } else if (offerSupabase) {
            String[] options = { "Supabase を開く", "閉じる" };
            int choice = JOptionPane.showOptionDialog(null,
                    title + "\n\n" + detail + "\n\n"
                            + "Supabase が一時停止しているときは、Supabase のダッシュボードにログインして\n"
                            + "プロジェクトの「Resume（再開）」を押し、数分待ってからもう一度起動してください。",
                    APP_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                AppWindow.openInDefaultBrowser(SUPABASE_DASHBOARD);
            }
        } else {
            JOptionPane.showMessageDialog(null, title + "\n\n" + detail, APP_TITLE, JOptionPane.ERROR_MESSAGE);
        }
        if (openFolderAfterDialog != null) {
            openFolder(openFolderAfterDialog);
        }
        ConfigurableApplicationContext ctx = context;
        if (ctx != null) {
            ctx.close();
        }
        System.exit(1);
    }

    private static boolean looksLikeDatabaseProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.startsWith("org.postgresql") || name.contains("JDBCConnectionException")
                    || name.contains("CannotCreateTransactionException") || t instanceof java.sql.SQLException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static void createEnvTemplateIfMissing(Path envFile) {
        if (Files.exists(envFile)) {
            return;
        }
        String template = """
                # 企業名簿（デスクトップ版）の設定ファイル
                # 「キー=値」の形で書きます。値に空白があっても " で囲む必要はありません。
                # このファイルには秘密の値が入るので、人に送ったり Git に入れたりしないでください。

                # Supabase の接続情報（Supabase の画面の Connect → JDBC からコピー）
                SPRING_DATASOURCE_URL=
                SPRING_DATASOURCE_USERNAME=
                SPRING_DATASOURCE_PASSWORD=

                # 外部 API のキー（Web 版の .env と同じもの）
                HOJIN_BANGOU_APP_ID=
                GEMINI_API_KEY=
                """;
        try {
            Files.writeString(envFile, template, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException ignored) {
            // 作れなくても、案内のダイアログは出る
        }
    }

    /**
     * 前の版の CDS ファイル（company-directory-*.jsa。1 つ 100MB 近くある）を消す。
     * 今使っているファイルは JVM の起動オプション -XX:SharedArchiveFile から分かる。
     */
    static void deleteOldCdsArchives() {
        String prefix = "-XX:SharedArchiveFile=";
        java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith(prefix))
                .map(a -> Path.of(a.substring(prefix.length())).toAbsolutePath())
                .findFirst()
                .ifPresent(current -> {
                    Path dir = current.getParent();
                    if (dir == null) {
                        return;
                    }
                    try (var files = Files.list(dir)) {
                        files.filter(f -> {
                            String name = f.getFileName().toString();
                            return name.startsWith("company-directory") && name.endsWith(".jsa")
                                    && !f.toAbsolutePath().equals(current);
                        }).forEach(f -> {
                            // JVM は読み取り専用で作る。Windows では読み取り専用のままだと消せない
                            f.toFile().setWritable(true);
                            deleteQuietly(f);
                        });
                    } catch (IOException ignored) {
                        // 消せなくても動作には影響しない
                    }
                });
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 次回起動時に上書きされる
        }
    }
}
