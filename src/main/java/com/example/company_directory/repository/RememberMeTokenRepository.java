package com.example.company_directory.repository;

import com.example.company_directory.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

    void deleteByUsername(String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<RememberMeToken> findBySeries(String series);
}
