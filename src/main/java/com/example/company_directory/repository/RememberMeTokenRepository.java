package com.example.company_directory.repository;

import com.example.company_directory.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

    void deleteByUsername(String username);

    /** 本人のパスワード変更のとき、今使っている「ログイン保持」以外を消す。 */
    void deleteByUsernameAndSeriesNot(String username, String series);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<RememberMeToken> findBySeries(String series);
}
