package com.example.company_directory.controller;

import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanyForm;
import com.example.company_directory.service.CompanyService;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("")
    public String list(Model model) {
        List<Company> companies = companyService.getAllCompanies();
        model.addAttribute("companies", companies);
        return "companies/list";
    }

    @GetMapping("/add")
    public String form(Model model) {
        model.addAttribute("companyForm", new CompanyForm());
        return "companies/form";
    }

    @PostMapping("/add")
    public String create(@Validated CompanyForm form, BindingResult result, RedirectAttributes redirectAttributes, Model model) {
        if (result.hasErrors()){
            model.addAttribute("companyForm", form);
            return "companies/form";
        }
        try {
            companyService.save(form);
            redirectAttributes.addFlashAttribute("successMessage", "企業を登録しました。");
            return "redirect:/companies";
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("companyForm", form);
            result.rejectValue("companyName", "duplicate", "既に登録されている企業です。");
            return "companies/form";
        } catch (Exception e) {
            model.addAttribute("companyForm", form);
            model.addAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return "companies/form";
        }
    }

    @GetMapping("/edit/{id}")
    public String form(@PathVariable Integer id, Model model) {
        Company company = companyService.findById(id);
        CompanyForm form = new CompanyForm();

        form.setId(id);
        form.setCompanyName(company.getCompanyName());
        form.setAddress(company.getAddress());
        form.setZipCode(company.getZipCode());
        form.setRemarks(company.getRemarks());

        model.addAttribute("companyForm", form);

        return "companies/form";
    }

    @PostMapping("/edit/{id}")
    public String update(@Validated CompanyForm form, BindingResult result, RedirectAttributes redirectAttributes, Model model) {
        if (result.hasErrors()){
            model.addAttribute("companyForm", form);
            return "companies/form";
        }
        try {
            companyService.update(form);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を更新しました。");
            return "redirect:/companies";
    
        } catch (DataIntegrityViolationException e) {
            // 例えば企業名の重複など
            model.addAttribute("companyForm", form);
            result.rejectValue("companyName", "duplicate", "既に登録されている企業です。");
            return "companies/form";
    
        } catch (Exception e) {
            // 想定外のエラー
            model.addAttribute("companyForm", form);
            model.addAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return "companies/form";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, RedirectAttributes redirectAttributes, Model model) {
        try {
            companyService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を削除しました。");
            return "redirect:/companies";
    
        } catch (DataIntegrityViolationException e) {
            // 参照関係（他テーブルで使われていて削除できないケース）
            redirectAttributes.addFlashAttribute("errorMessage", "関連データが存在するため削除できません。");
            return "redirect:/companies";
    
        } catch (Exception e) {
            // 想定外のエラー
            redirectAttributes.addFlashAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return "redirect:/companies";
        }
    }

    @GetMapping("/trash")
    public String trash(Model model) {
        model.addAttribute("trashCompanies", companyService.findAllTrash());
        return "companies/trash";
    }

    @PostMapping("/trash/restore/{id}")
    public String restore(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            companyService.restore(id);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を復元しました。");
            return "redirect:/companies";
            
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "関連データが存在するため復元できません。");
            return "redirect:/companies";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return "redirect:/companies";
        }
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> export(){
        try {
            InputStream inputStream = companyService.exportExcel();

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=companies.xlsx");

            return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
        

    }
}
