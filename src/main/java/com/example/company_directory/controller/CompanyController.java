package com.example.company_directory.controller;

import java.io.InputStream;

import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanyForm;
import com.example.company_directory.form.CompanySearchForm;
import com.example.company_directory.form.ExportForm;
import com.example.company_directory.service.CompanyService;
import com.example.company_directory.service.ExcelImportService;
import com.example.company_directory.dto.ImportResultDto;
import com.example.company_directory.dto.ImportRowDto;
import java.util.List;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final ExcelImportService excelImportService;

    public CompanyController(CompanyService companyService, ExcelImportService excelImportService) {
        this.companyService = companyService;
        this.excelImportService = excelImportService;
    }

    @GetMapping
    public String list(@ModelAttribute CompanySearchForm searchForm, @RequestParam(defaultValue = "5") int size,
            @PageableDefault(size = 5) Pageable pageable,
            Model model) {

        pageable = PageRequest.of(pageable.getPageNumber(), size);
        // 検索＆ページ取得
        Page<Company> page = companyService.searchCompanies(searchForm, pageable);

        model.addAttribute("page", page); // ページ情報 (現在のページ、総ページ数など)
        model.addAttribute("companies", page.getContent()); // 現在のページのデータリスト
        model.addAttribute("searchForm", searchForm); // 検索条件を画面に残すために返す

        return "companies/list";
    }

    @GetMapping("/add")
    public String form(Model model) {
        model.addAttribute("companyForm", new CompanyForm());
        return "companies/form";
    }

    /**
     * まとめて登録画面を表示
     */
    @GetMapping("/batch")
    public String batchForm() {
        return "companies/batch";
    }

    @PostMapping("/add")
    public String create(@Validated CompanyForm form, BindingResult result, RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String returnUrl, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("companyForm", form);
            return "companies/form";
        }
        try {
            boolean hasDeletedDuplicate = companyService.hasDeletedDuplicate(form);
            companyService.save(form);
            redirectAttributes.addFlashAttribute("successMessage", "企業を登録しました。");
            if (hasDeletedDuplicate) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "【重複注意】削除履歴に同一データがあります。登録前に履歴画面を確認してください。");
            }
            return resolveReturnUrl(returnUrl, "/companies");
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
    public String update(@Validated CompanyForm form, BindingResult result, RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String returnUrl, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("companyForm", form);
            return "companies/form";
        }
        try {
            companyService.update(form);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を更新しました。");
            return resolveReturnUrl(returnUrl, "/companies");

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
    public String delete(@PathVariable Integer id, RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String returnUrl, Model model) {
        try {
            companyService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を削除しました。");
            return resolveReturnUrl(returnUrl, "/companies");

        } catch (DataIntegrityViolationException e) {
            // 参照関係（他テーブルで使われていて削除できないケース）
            redirectAttributes.addFlashAttribute("errorMessage", "関連データが存在するため削除できません。");
            return resolveReturnUrl(returnUrl, "/companies");

        } catch (Exception e) {
            // 想定外のエラー
            redirectAttributes.addFlashAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return resolveReturnUrl(returnUrl, "/companies");
        }
    }

    @GetMapping("/trash")
    public String trash(@ModelAttribute CompanySearchForm searchForm, @RequestParam(defaultValue = "5") int size,
            @PageableDefault(size = 5) Pageable pageable, Model model) {

        pageable = PageRequest.of(pageable.getPageNumber(), size);
        Page<Company> page = companyService.searchTrashCompanies(searchForm, pageable);

        model.addAttribute("page", page);
        model.addAttribute("trashCompanies", page.getContent());
        model.addAttribute("searchForm", searchForm);

        return "companies/trash";
    }

    @PostMapping("/trash/restore/{id}")
    public String restore(@PathVariable Integer id, RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String returnUrl) {
        try {
            companyService.restore(id);
            redirectAttributes.addFlashAttribute("successMessage", "企業情報を復元しました。");
            return resolveReturnUrl(returnUrl, "/companies/trash");

        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "関連データが存在するため復元できません。");
            return resolveReturnUrl(returnUrl, "/companies/trash");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "予期せぬエラーが発生しました。");
            return resolveReturnUrl(returnUrl, "/companies/trash");
        }
    }

    private String resolveReturnUrl(String returnUrl, String fallback) {
        if (returnUrl != null && !returnUrl.isBlank() && returnUrl.startsWith("/companies")) {
            return "redirect:" + returnUrl;
        }
        return "redirect:" + fallback;
    }

    @PostMapping("/export")
    public ResponseEntity<InputStreamResource> export(@ModelAttribute ExportForm form) {
        try {
            InputStream inputStream = companyService.exportExcel(form);

            HttpHeaders headers = new HttpHeaders();
            String filename = (form.getFileName() != null && !form.getFileName().isEmpty())
                    ? form.getFileName()
                    : "companies";
            if (!filename.endsWith(".xlsx")) {
                filename += ".xlsx";
            }

            // Encode filename for browser compatibility if needed, but for now simple
            headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            e.printStackTrace(); // Log error
            return ResponseEntity.internalServerError().build();
        }

    }

    /**
     * まとめて登録を実行
     */
    @PostMapping("/batch/execute")
    public String executeBatch(@RequestParam(name = "mode", defaultValue = "all") String mode,
            CompanyForm form,
            RedirectAttributes redirectAttributes) {

        List<ImportRowDto> rows = form.getRows();
        if (rows == null || rows.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "登録対象のデータがありません。");
            return "redirect:/companies/batch";
        }

        // 1. 再バリデーションと登録対象の選定
        ImportResultDto result = excelImportService.validateRows(rows);

        List<ImportRowDto> targetList;
        if ("strict".equals(mode)) {
            targetList = result.getSuccessList();
        } else {
            targetList = result.getTotalList().stream()
                    .filter(row -> !row.isHasError())
                    .toList();
        }

        if (targetList.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "登録対象のデータがありません。");
            return "redirect:/companies/batch";
        }

        // 2. 保存
        excelImportService.saveValidData(targetList);

        redirectAttributes.addFlashAttribute("successMessage", targetList.size() + "件の企業を登録しました。");
        return "redirect:/companies";
    }
}
