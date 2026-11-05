package com.example.company_directory.batch;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 起動したときに 1 回だけ、30 日を過ぎたゴミ箱のデータを削除する。
 *
 * <p>毎日 3:00 の定期実行（{@link TrashPurgeScheduler}）は、その時間にアプリが動いていないと実行されない。
 * デスクトップ版は使うときだけ起動するので、起動時にも 1 回消して取りこぼしを防ぐ。
 *
 * <ul>
 *   <li>起動を遅くしないよう、起動が終わってから（画面が開いたあと）別のスレッドで動かす</li>
 *   <li>消す条件は定期実行と同じ（同じメソッドを呼ぶ）。Web 版で定期実行と重なっても、
 *       もう消えた行は消えないだけなので困らない</li>
 *   <li>DB につながらないなどで失敗しても、ログに残すだけでアプリは止めない（次の起動か 3:00 にまた試す）</li>
 *   <li>止めたいときは {@code app.trash.purge-on-startup=false} にする（このクラスを消しても元に戻る）</li>
 * </ul>
 */
@Lazy(false)
@Component
public class TrashStartupPurge {
    private static final Logger log = LoggerFactory.getLogger(TrashStartupPurge.class);

    private final TrashPurgeScheduler scheduler;
    private final boolean enabled;
    private final Duration delay;

    public TrashStartupPurge(TrashPurgeScheduler scheduler,
            @Value("${app.trash.purge-on-startup:true}") boolean enabled,
            @Value("${app.trash.purge-on-startup-delay:10s}") Duration delay) {
        // scheduler は Spring が作った @Transactional 付きの入れ物（プロキシ）なので、
        // ここから呼べばトランザクションの中で削除される
        this.scheduler = scheduler;
        this.enabled = enabled;
        this.delay = delay;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        Thread t = new Thread(this::purgeAfterDelay, "trash-startup-purge");
        // アプリを終了するときに、この処理の終わりを待たない
        t.setDaemon(true);
        t.start();
    }

    /** 少し待ってから削除する。待つのは、最初の画面表示と DB の取り合いをしないため。 */
    void purgeAfterDelay() {
        try {
            Thread.sleep(delay.toMillis());
            scheduler.purgeOldTrashData();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // 例外の中身（接続先など）はログに出さず、種類だけ書く
            log.warn("起動時のゴミ箱自動パージに失敗しました（次の起動か定期実行でまた試します）: {}",
                    e.getClass().getSimpleName());
        }
    }
}
