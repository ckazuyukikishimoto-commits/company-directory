package com.example.company_directory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.company_directory.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUserId(String userId);

    /** アカウント管理画面の一覧（ユーザーIDの昇順）。 */
    List<User> findAllByOrderByUserIdAsc();
}
