package com.example.company_directory.desktop;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * 起動と同時に（並行して）DB へ 1 回だけつないでみて、つながらないときに
 * 「なぜつながらないか」を分かりやすい日本語にする。
 *
 * <p>
 * Supabase の無料プランは、しばらく使わないとプロジェクトが一時停止する。そのときは
 * Spring の英語のエラーで終わってしまうので、ここで先に見つけて知らせる。
 * パスワードなどの秘密の値はメッセージに入れない。
 * </p>
 */
final class DatabaseCheck {

    /** 結果。ok が true なら問題なし。 */
    record Result(boolean ok, String title, String detail, boolean maybePaused) {
        static Result success() {
            return new Result(true, "", "", false);
        }
    }

    private DatabaseCheck() {
    }

    static Result run(int timeoutSeconds) {
        String url = value("SPRING_DATASOURCE_URL");
        Properties props = new Properties();
        props.setProperty("user", nullToEmpty(value("SPRING_DATASOURCE_USERNAME")));
        props.setProperty("password", nullToEmpty(value("SPRING_DATASOURCE_PASSWORD")));
        props.setProperty("connectTimeout", String.valueOf(timeoutSeconds));
        props.setProperty("loginTimeout", String.valueOf(timeoutSeconds));
        props.setProperty("socketTimeout", String.valueOf(timeoutSeconds));
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection c = DriverManager.getConnection(url, props); Statement s = c.createStatement()) {
                s.execute("select 1");
            }
            return Result.success();
        } catch (Exception e) {
            return classify(e);
        }
    }

    static Result classify(Throwable e) {
        String all = messages(e).toLowerCase();
        if (has(e, UnknownHostException.class)) {
            return new Result(false, "データベースのサーバーが見つかりません",
                    "インターネットにつながっていないか、Supabase のプロジェクトが一時停止している可能性があります。", true);
        }
        if (all.contains("tenant or user not found")) {
            return new Result(false, "データベースにつながりません（Supabase が一時停止中かもしれません）",
                    "Supabase のプロジェクトが一時停止しているか、.env のユーザー名（postgres.プロジェクトID）が違う可能性があります。", true);
        }
        if (all.contains("password authentication failed")) {
            return new Result(false, "データベースのパスワードが違います",
                    ".env の SPRING_DATASOURCE_PASSWORD を確かめてください。", false);
        }
        if (has(e, SocketTimeoutException.class) || has(e, ConnectException.class)
                || has(e, NoRouteToHostException.class) || all.contains("timeout") || all.contains("timed out")) {
            return new Result(false, "データベースにつながりませんでした（時間切れ・接続拒否）",
                    "インターネット接続を確かめてください。Supabase のプロジェクトが一時停止している可能性もあります。", true);
        }
        String first = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new Result(false, "データベースにつながりませんでした", "詳しい理由: " + first, true);
    }

    private static String value(String key) {
        String v = System.getenv(key);
        return v != null ? v : System.getProperty(key);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean has(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
            if (t.getCause() == t) {
                break;
            }
        }
        return sb.toString();
    }
}
