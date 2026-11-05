package com.example.company_directory.batch;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.company_directory.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

// デスクトップ版は遅延初期化（spring.main.lazy-initialization）を使うが、
// 定期実行のクラスは作られないと動かないので、いつも起動時に作る
@Lazy(false)
@Component
@RequiredArgsConstructor
public class TrashPurgeScheduler {
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeScheduler.class);
    private static final int PURGE_DAYS = 30; // 保持日数
    private final CompanyRepository companyRepository;

    /**
     * 毎日午前3時に実行（サーバー時間）
     * cron = "秒 分 時 日 月 曜日"
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeOldTrashData() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(PURGE_DAYS);

        log.info("ゴミ箱自動パージ開始: 基準日時 = {}", threshold);

        int deletedCount = companyRepository.deleteByIsDeletedTrueAndDeletedAtBefore(threshold);

        log.info("ゴミ箱自動パージ完了: 削除件数 = {}", deletedCount);
    }
}