package com.example.company_directory.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.example.company_directory.dto.CompanyImageExtractionDto;
import com.example.company_directory.service.CompanyImageExtractionService;
import com.example.company_directory.service.ExcelImportService;

@WebMvcTest(BatchApiController.class)
@WithMockUser
class BatchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExcelImportService excelImportService;

    @MockitoBean
    private CompanyImageExtractionService companyImageExtractionService;

    @Test
    void extractCompaniesFromImage_returnsBadRequest_whenFileIsInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "invalid".getBytes());

        when(companyImageExtractionService.extractCompaniesFromImage(any(MultipartFile.class)))
                .thenThrow(new IllegalArgumentException("invalid image format"));

        mockMvc.perform(multipart("/api/batch/extract-companies-from-image")
                .file(file)
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("invalid image format"));
    }

    @Test
    void extractCompaniesFromImage_returnsCompanies_whenSucceeded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "companies.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy".getBytes());

        when(companyImageExtractionService.extractCompaniesFromImage(any(MultipartFile.class)))
                .thenReturn(List.of(
                        new CompanyImageExtractionDto("Sample Co., Ltd.", "100-0001", "Chiyoda-ku, Tokyo"),
                        new CompanyImageExtractionDto("Test LLC", "150-0001", "Shibuya-ku, Tokyo")));

        mockMvc.perform(multipart("/api/batch/extract-companies-from-image")
                .file(file)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companies[0].companyName").value("Sample Co., Ltd."))
                .andExpect(jsonPath("$.companies[0].zipCode").value("100-0001"))
                .andExpect(jsonPath("$.companies[0].address").value("Chiyoda-ku, Tokyo"))
                .andExpect(jsonPath("$.companies[1].companyName").value("Test LLC"));
    }
}
