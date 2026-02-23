package com.example.company_directory.repository;

import com.example.company_directory.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyMasterRepository extends JpaRepository<CompanyMaster, String> {
    /**
     * 住所のキーワードによる部分一致検索（上位20件）
     */
    List<CompanyMaster> findTop20ByAddressContaining(String address);

    /**
     * 企業名のキーワードによる部分一致検索（上位20件）
     */
    List<CompanyMaster> findTop20ByCompanyNameContaining(String companyName);
}
