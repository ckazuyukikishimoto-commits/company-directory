package com.example.company_directory.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAllByIsDeletedFalse();
    }

    public Company save(CompanyForm form) {
        Company company = new Company();

        company.setCompanyName(form.getCompanyName());
        company.setAddress(form.getAddress());
        company.setZipCode(form.getZipCode());
        company.setRemarks(form.getRemarks());
        company.setRegistrationDate(LocalDate.now());

        return companyRepository.save(company);
    }

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

    public void delete(Integer id) {
        Company company = this.findById(id);

        company.setIsDeleted(true);
        company.setDeletedAt(LocalDateTime.now());
        // company.setDeletedBy(null);

        companyRepository.save(company);

    }

    public List<Company> findAllTrash() {
        return companyRepository.findAllByIsDeletedTrueOrderByDeletedAtDesc();
    }

    public Page<Company> searchTrashCompanies(CompanySearchForm form, Pageable pageable) {
        // Specificationを使って検索 (isDeleted = true)
        Specification<Company> spec = CompanySpecification.search(form, true);
        return companyRepository.findAll(spec, pageable);
    }

    public void restore(Integer id) {
        Company company = this.findById(id);

        company.setIsDeleted(false);
        company.setDeletedAt(null);

        companyRepository.save(company);

    }

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

        return ExcelHelper.companiesToExcel(companies, form.getColumns());
    }

    public Page<Company> searchCompanies(CompanySearchForm form, Pageable pageable) {
        // Specificationを使って検索
        Specification<Company> spec = CompanySpecification.search(form);
        return companyRepository.findAll(spec, pageable);
    }
}
