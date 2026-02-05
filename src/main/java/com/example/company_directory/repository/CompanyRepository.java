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

public interface CompanyRepository extends JpaRepository<Company, Integer>, JpaSpecificationExecutor<Company> {

    List<Company> findAllByIsDeletedFalse();

    List<Company> findAllByIsDeletedTrueOrderByDeletedAtDesc();

    @Query("SELECT c.companyId FROM Company c")
    Set<String> findAllIds();

    // 企業名で検索（存在チェック用）
    boolean existsByCompanyName(String companyName);

    boolean existsByAddress(String address);

    boolean existsByCompanyNameAndIsDeletedTrue(String companyName);

    boolean existsByAddressAndIsDeletedTrue(String address);

    boolean existsByZipCodeAndIsDeletedTrue(String zipCode);

    @Modifying
    @Query("DELETE FROM Company c WHERE c.isDeleted = true AND c.deletedAt < :threshold")
    int deleteByIsDeletedTrueAndDeletedAtBefore(@Param("threshold") LocalDateTime threshold);

}
