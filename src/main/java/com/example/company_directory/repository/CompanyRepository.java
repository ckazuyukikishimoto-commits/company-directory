package com.example.company_directory.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.company_directory.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Integer>{

    List<Company> findAllByIsDeletedFalse();

    
    List<Company> findAllByIsDeletedTrueOrderByDeletedAtDesc();

    @Query("SELECT c.companyId FROM Company c")
    Set<String> findAllIds();

    // 企業名で検索（存在チェック用）
    boolean existsByCompanyName(String companyName);

    boolean existsByAddress(String address);
    
}
