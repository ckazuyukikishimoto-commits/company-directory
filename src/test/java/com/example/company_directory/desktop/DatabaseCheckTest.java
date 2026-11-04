package com.example.company_directory.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class DatabaseCheckTest {

    @Test
    void unknownHost_isReportedAsMaybePaused() {
        var r = DatabaseCheck.classify(new SQLException("x", new UnknownHostException("db.example.supabase.co")));
        assertThat(r.ok()).isFalse();
        assertThat(r.maybePaused()).isTrue();
        assertThat(r.title()).contains("見つかりません");
    }

    @Test
    void tenantNotFound_isReportedAsMaybePaused() {
        var r = DatabaseCheck.classify(new SQLException("FATAL: Tenant or user not found"));
        assertThat(r.maybePaused()).isTrue();
        assertThat(r.title()).contains("一時停止");
    }

    @Test
    void wrongPassword_isNotReportedAsPaused() {
        var r = DatabaseCheck.classify(new SQLException("FATAL: password authentication failed for user \"postgres\""));
        assertThat(r.maybePaused()).isFalse();
        assertThat(r.title()).contains("パスワード");
    }

    @Test
    void connectionRefused_isReportedAsNetworkProblem() {
        var r = DatabaseCheck.classify(new SQLException("refused", new ConnectException("Connection refused")));
        assertThat(r.ok()).isFalse();
        assertThat(r.title()).contains("つながりませんでした");
    }

    @Test
    void run_reportsFailureForUnreachableDatabase() {
        // 誰も待ち受けていないポート
        var r = DatabaseCheck.run("jdbc:postgresql://127.0.0.1:1/none", "u", "p", 2);
        assertThat(r.ok()).isFalse();
    }
}
