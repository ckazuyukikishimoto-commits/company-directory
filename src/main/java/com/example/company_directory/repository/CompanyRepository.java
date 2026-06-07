package com.example.company_directory.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.company_directory.entity.Company;

/**
 * 企業リポジトリ。
 * 企業情報のCRUD、検索を行います。
 */
public interface CompanyRepository extends JpaRepository<Company, Integer>, JpaSpecificationExecutor<Company> {

    //削除されていない企業情報を取得する。
    List<Company> findAllByIsDeletedFalse();

    //削除された企業情報を取得する。
    List<Company> findAllByIsDeletedTrueOrderByDeletedAtDesc();

    // 企業IDを取得する。
    @Query("SELECT c.companyId FROM Company c")
    Set<String> findAllIds();

    // 企業名で検索（存在チェック用）
    boolean existsByCompanyName(String companyName);

    // 住所で検索（存在チェック用）
    boolean existsByAddress(String address);

    // 企業名と削除フラグがtrueの企業情報を検索する。
    boolean existsByCompanyNameAndIsDeletedTrue(String companyName);

    // 住所と削除フラグがtrueの企業情報を検索する。
    boolean existsByAddressAndIsDeletedTrue(String address);

    // 郵便番号と削除フラグがtrueの企業情報を検索する。
    boolean existsByZipCodeAndIsDeletedTrue(String zipCode);

    // 削除された企業情報を削除する。
    @Modifying
    @Query("DELETE FROM Company c WHERE c.isDeleted = true AND c.deletedAt < :threshold")
    int deleteByIsDeletedTrueAndDeletedAtBefore(@Param("threshold") LocalDateTime threshold);

    // 削除されていない企業情報の最大表示番号を取得する。
    @Query("SELECT COALESCE(MAX(c.displayNumber), 0) FROM Company c WHERE c.isDeleted = false")
    Integer findMaxDisplayNumberByIsDeletedFalse();

    // 削除されていない企業情報の表示番号を1つ下げる。
    @Modifying
    @Query("UPDATE Company c SET c.displayNumber = c.displayNumber - 1 WHERE c.isDeleted = false AND c.displayNumber > :deletedDisplayNumber")
    void shiftDisplayNumbersDown(@Param("deletedDisplayNumber") Integer deletedDisplayNumber);

    // 削除されていない企業情報の表示番号がnullの企業情報を取得する。
    List<Company> findAllByIsDeletedFalseAndDisplayNumberIsNullOrderByCompanyIdAsc();
}
