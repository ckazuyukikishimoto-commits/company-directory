package com.example.company_directory.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanyForm;
import com.example.company_directory.form.CompanySearchForm;
import com.example.company_directory.form.ExportForm;
import com.example.company_directory.repository.CompanyRepository;
import com.example.company_directory.repository.CompanySpecification;
import com.example.company_directory.util.ExcelHelper;
import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.data.domain.Sort;

/**
 * 企業情報を管理するサービス。
 * 企業情報のCRUD、検索、エクスポートを行います。
 */
@Service
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * すべての企業情報を取得する。
     * @return すべての企業情報
     */
    public List<Company> getAllCompanies() {
        return companyRepository.findAllByIsDeletedFalse();
    }

    /**
     * 表示番号を初期化する。
     */
    @PostConstruct
    @Transactional
    public void initDisplayNumbers() {
        List<Company> companiesToInit = companyRepository
                .findAllByIsDeletedFalseAndDisplayNumberIsNullOrderByCompanyIdAsc();
        if (!companiesToInit.isEmpty()) {
            int currentMax = companyRepository.findMaxDisplayNumberByIsDeletedFalse();
            for (Company company : companiesToInit) {
                currentMax++;
                company.setDisplayNumber(currentMax);
                companyRepository.save(company);
            }
        }
    }

    /**
     * 企業情報を保存する。
     * @param form
     * @return 保存された企業情報
     */
    public Company save(CompanyForm form) {
        Company company = new Company();

        company.setCompanyName(form.getCompanyName());
        company.setAddress(form.getAddress());
        company.setZipCode(form.getZipCode());
        company.setRemarks(form.getRemarks());
        company.setRegistrationDate(LocalDate.now());

        int nextNumber = companyRepository.findMaxDisplayNumberByIsDeletedFalse() + 1;
        company.setDisplayNumber(nextNumber);

        return companyRepository.save(company);
    }

    /**
     * 削除された重複企業情報を検索する。
     * @param form
     * @return 削除された重複企業情報が存在するかどうか
     */
    public boolean hasDeletedDuplicate(CompanyForm form) {
        if (form == null) {
            return false;
        }
        boolean hasDuplicate = false;
        if (form.getCompanyName() != null && !form.getCompanyName().isBlank()) {
            hasDuplicate = companyRepository.existsByCompanyNameAndIsDeletedTrue(form.getCompanyName());
        }
        if (!hasDuplicate && form.getAddress() != null && !form.getAddress().isBlank()) {
            hasDuplicate = companyRepository.existsByAddressAndIsDeletedTrue(form.getAddress());
        }
        if (!hasDuplicate && form.getZipCode() != null && !form.getZipCode().isBlank()) {
            hasDuplicate = companyRepository.existsByZipCodeAndIsDeletedTrue(form.getZipCode());
        }
        return hasDuplicate;
    }

    /**
     * 企業情報をIDで取得する。
     * @param id
     * @return 企業情報
     */
    public Company findById(Integer id) {
        return companyRepository.findById(id).orElseThrow(() -> new RuntimeException("Company not found"));
    }

    public void update(CompanyForm form) {
        Company company = this.findById(form.getId());

        company.setCompanyName(form.getCompanyName());
        company.setAddress(form.getAddress());
        company.setZipCode(form.getZipCode());
        company.setRemarks(form.getRemarks());

        companyRepository.save(company);

    }

    /**
     * 企業情報を削除する。
     * @param id
     * @param userId
     */
    @Transactional
    public void delete(Integer id, String userId) {
        Company company = this.findById(id);
        Integer deletedDisplayNumber = company.getDisplayNumber();

        company.setIsDeleted(true);
        company.setDeletedAt(LocalDateTime.now());
        company.setDeletedBy(userId);

        companyRepository.save(company);

        if (deletedDisplayNumber != null) {
            companyRepository.shiftDisplayNumbersDown(deletedDisplayNumber);
        }
    }

    /**
     * 削除された企業情報を取得する。
     * @return 削除された企業情報
     */
    public List<Company> findAllTrash() {
        return companyRepository.findAllByIsDeletedTrueOrderByDeletedAtDesc();
    }

    /**
     * 削除された企業情報を検索する。
     * @param form
     * @param pageable
     * @return 削除された企業情報
     */
    public Page<Company> searchTrashCompanies(CompanySearchForm form, Pageable pageable) {
        // Specificationを使って検索 (isDeleted = true)
        Specification<Company> spec = CompanySpecification.search(form, true);
        return companyRepository.findAll(spec, pageable);
    }

    /**
     * 削除された企業情報を復元する。
     * @param id
     */
    @Transactional
    public void restore(Integer id) {
        Company company = this.findById(id);

        company.setIsDeleted(false);
        company.setDeletedAt(null);
        company.setDeletedBy(null);

        int nextNumber = companyRepository.findMaxDisplayNumberByIsDeletedFalse() + 1;
        company.setDisplayNumber(nextNumber);

        companyRepository.save(company);

    }

    /**
     * 企業情報をエクスポートする。
     * @param form
     * @return エクスポートされた企業情報
     */
    public ByteArrayInputStream exportExcel(ExportForm form) {
        List<Company> companies;

        String scope = form.getScope() != null ? form.getScope() : "ALL";

        // Sorting logic
        String sortBy = form.getSortBy() != null ? form.getSortBy() : "companyId";
        String sortOrder = form.getSortOrder() != null ? form.getSortOrder() : "ASC";

        // Validate sort field to prevent injection/errors (simple allow-list)
        List<String> allowedSorts = Arrays.asList("companyId", "companyName", "address", "zipCode", "registrationDate");
        if (!allowedSorts.contains(sortBy)) {
            sortBy = "companyId";
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder.toUpperCase()), sortBy);

        switch (scope) {
            case "SELECTION":
                if (form.getSelectedIds() != null && !form.getSelectedIds().isEmpty()) {
                    // Filter by IDs AND apply sort
                    Specification<Company> idSpec = (root, query, cb) -> root.get("companyId")
                            .in(form.getSelectedIds());
                    companies = companyRepository.findAll(idSpec, sort);
                } else {
                    companies = new ArrayList<>();
                }
                break;
            case "SEARCH":
                // Use search specification (unpaged) with sort
                Specification<Company> spec = CompanySpecification.search(form);
                companies = companyRepository.findAll(spec, sort);
                break;
            case "ALL":
            default:
                // Active companies only with sort
                // Using specification to combine isDeleted=false with sort
                Specification<Company> activeSpec = (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
                companies = companyRepository.findAll(activeSpec, sort);
                break;
        }

        return ExcelHelper.companiesToExcel(companies, form.resolveOrderedColumns());
    }

    /**
     * 企業情報を検索する。
     * @param form
     * @param pageable
     * @return 検索された企業情報
     */
    public Page<Company> searchCompanies(CompanySearchForm form, Pageable pageable) {
        // Specificationを使って検索
        Specification<Company> spec = CompanySpecification.search(form);
        return companyRepository.findAll(spec, pageable);
    }
}
