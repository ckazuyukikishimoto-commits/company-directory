package com.example.company_directory.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.company_directory.service.CompanyService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
public class CompanyApiController {

    private final CompanyService companyService;

    public CompanyApiController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id) {
        try {
            companyService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Company soft-deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/restore/{id}")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Integer id) {
        try {
            companyService.restore(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Company restored successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
