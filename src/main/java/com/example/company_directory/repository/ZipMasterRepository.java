package com.example.company_directory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.company_directory.entity.ZipMaster;

public interface ZipMasterRepository extends JpaRepository<ZipMaster, String> {
    // 郵便番号で検索
    ZipMaster findByZipCode(String zipCode);
}