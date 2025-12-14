package com.example.company_directory.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanyForm;
import com.example.company_directory.repository.CompanyRepository;
import com.example.company_directory.util.ExcelHelper;

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

    public void delete(Integer id){
        Company company = this.findById(id);

        company.setIsDeleted(true);
        company.setDeletedAt(LocalDateTime.now());
        // company.setDeletedBy(null);

        companyRepository.save(company);

    }

    public List<Company> findAllTrash() {
        return companyRepository.findAllByIsDeletedTrueOrderByDeletedAtDesc();
    }

    public void restore(Integer id) {
        Company company = this.findById(id);

        company.setIsDeleted(false);
        company.setDeletedAt(null);

        companyRepository.save(company);
        
    }

    public ByteArrayInputStream exportExcel(){
        //後で条件指定された企業のみをExcel変換する機能を追加
        return ExcelHelper.companiesToExcel(getAllCompanies());
    }
}
