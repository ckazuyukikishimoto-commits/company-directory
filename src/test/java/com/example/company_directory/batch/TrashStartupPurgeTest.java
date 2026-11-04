package com.example.company_directory.batch;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 起動時のゴミ箱自動パージ（DB を使わない単体テスト）。 */
@ExtendWith(MockitoExtension.class)
class TrashStartupPurgeTest {

    @Mock
    TrashPurgeScheduler scheduler;

    @Test
    void 起動が終わったら裏で1回パージする() {
        new TrashStartupPurge(scheduler, true, Duration.ZERO).onReady();
        verify(scheduler, timeout(5000)).purgeOldTrashData();
    }

    @Test
    void 設定で止めているときはパージしない() throws InterruptedException {
        new TrashStartupPurge(scheduler, false, Duration.ZERO).onReady();
        Thread.sleep(300);
        verify(scheduler, never()).purgeOldTrashData();
    }

    @Test
    void パージに失敗してもアプリは止めない() {
        doThrow(new IllegalStateException("db down")).when(scheduler).purgeOldTrashData();
        // 例外が外に出なければよい
        new TrashStartupPurge(scheduler, true, Duration.ZERO).purgeAfterDelay();
        verify(scheduler).purgeOldTrashData();
    }
}
