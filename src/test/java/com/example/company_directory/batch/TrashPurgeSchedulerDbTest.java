package com.example.company_directory.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.company_directory.entity.Company;
import com.example.company_directory.repository.CompanyRepository;

/**
 * ゴミ箱の自動パージが「30 日を過ぎた、削除済みのものだけ」を消すかを確かめる。
 * CompanyDirectoryApplicationTests と同じく、DB（SPRING_DATASOURCE_URL など）が必要。
 * テストの最後に元に戻す（@Transactional）ので、DB にデータは残らない。
 */
@SpringBootTest(properties = "app.trash.purge-on-startup=false")
@Transactional
class TrashPurgeSchedulerDbTest {

    @Autowired
    TrashPurgeScheduler scheduler;

    @Autowired
    CompanyRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 三十日を過ぎた削除済みだけを消す() {
        Company old = save("テスト古い削除済み", true, LocalDateTime.now().minusDays(31));
        Company recent = save("テスト最近の削除済み", true, LocalDateTime.now().minusDays(29));
        Company active = save("テスト有効", false, null);

        scheduler.purgeOldTrashData();
        // 2 回目（Web 版で定期実行と重なった場合など）も問題なく終わる
        scheduler.purgeOldTrashData();
        // まとめて消す DELETE 文は JPA の手元の控え（キャッシュ）を更新しないので、控えを捨てて DB から読み直す
        entityManager.clear();

        assertThat(repository.findById(old.getCompanyId())).isEmpty();
        assertThat(repository.findById(recent.getCompanyId())).isPresent();
        assertThat(repository.findById(active.getCompanyId())).isPresent();
    }

    private Company save(String name, boolean deleted, LocalDateTime deletedAt) {
        Company c = new Company();
        c.setCompanyName(name);
        c.setAddress("テスト住所");
        c.setZipCode("000-0000");
        c.setRegistrationDate(LocalDate.now());
        c.setIsDeleted(deleted);
        c.setDeletedAt(deletedAt);
        return repository.saveAndFlush(c);
    }
}
