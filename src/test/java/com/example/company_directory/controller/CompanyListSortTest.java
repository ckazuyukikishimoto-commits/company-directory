package com.example.company_directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.company_directory.config.CustomAuthenticationFailureHandler;
import com.example.company_directory.config.CustomAuthenticationSuccessHandler;
import com.example.company_directory.config.SecurityConfig;
import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanySearchForm;
import com.example.company_directory.service.CompanyService;
import com.example.company_directory.service.ExcelImportService;

/**
 * 企業一覧・ゴミ箱の並び替えをサーバー側（全件）で行うこと、
 * 画面に無い列名が URL に書かれても既定の並びに戻ることを確かめる。
 */
@WebMvcTest(CompanyController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "hanako", roles = "USER")
class CompanyListSortTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;
    @MockitoBean
    private ExcelImportService excelImportService;

    // SecurityConfig が必要とするもの
    @MockitoBean
    private CustomAuthenticationFailureHandler failureHandler;
    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static Page<Company> emptyPage(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }

    private Pageable listPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyService).searchCompanies(any(CompanySearchForm.class), captor.capture());
        return captor.getValue();
    }

    private Pageable trashPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyService).searchTrashCompanies(any(CompanySearchForm.class), captor.capture());
        return captor.getValue();
    }

    // ---- 企業一覧 ----

    @Test
    void list_defaultsToDisplayNumberAscending() throws Exception {
        when(companyService.searchCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        mockMvc.perform(get("/companies").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortParam", "displayNumber,asc"));

        assertThat(listPageable().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("displayNumber"), Sort.Order.asc("companyId")));
    }

    @Test
    void list_sortsAllRowsByRequestedColumn() throws Exception {
        when(companyService.searchCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        mockMvc.perform(get("/companies").param("sort", "companyName,desc").param("page", "2").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortParam", "companyName,desc"));

        Pageable pageable = listPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("companyName"), Sort.Order.asc("companyId")));
    }

    @Test
    void list_companyIdHasNoDuplicateTieBreaker() throws Exception {
        when(companyService.searchCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        mockMvc.perform(get("/companies").param("sort", "companyId,desc"))
                .andExpect(status().isOk());

        assertThat(listPageable().getSort()).isEqualTo(Sort.by(Sort.Order.desc("companyId")));
    }

    @Test
    void list_unknownColumnFallsBackToDefault() throws Exception {
        when(companyService.searchCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        // 画面に無い列（isDeleted）・存在しない列は受け付けない
        mockMvc.perform(get("/companies").param("sort", "isDeleted,desc").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortParam", "displayNumber,asc"));

        assertThat(listPageable().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("displayNumber"), Sort.Order.asc("companyId")));
    }

    @Test
    void list_pagingLinksAndFormsCarrySort() throws Exception {
        when(companyService.searchCompanies(any(), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 500));

        mockMvc.perform(get("/companies").param("sort", "address,asc").param("page", "1").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("sort=address,asc")))
                .andExpect(content().string(Matchers.containsString("name=\"sort\" value=\"address,asc\"")))
                .andExpect(content().string(Matchers.containsString("data-current-sort=\"address,asc\"")));
    }

    // ---- ゴミ箱 ----

    @Test
    void trash_sortsAllRowsByRequestedColumn() throws Exception {
        when(companyService.searchTrashCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        mockMvc.perform(get("/companies/trash").param("sort", "deletedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortParam", "deletedAt,desc"));

        assertThat(trashPageable().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("deletedAt"), Sort.Order.asc("companyId")));
    }

    @Test
    void trash_listOnlyColumnFallsBackToDefault() throws Exception {
        when(companyService.searchTrashCompanies(any(), any())).thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        // 備考はゴミ箱の画面に無い列
        mockMvc.perform(get("/companies/trash").param("sort", "remarks,asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortParam", "displayNumber,asc"));

        assertThat(trashPageable().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("displayNumber"), Sort.Order.asc("companyId")));
    }

    @Test
    void trash_pagingLinksCarrySort() throws Exception {
        when(companyService.searchTrashCompanies(any(), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 50));

        mockMvc.perform(get("/companies/trash").param("sort", "companyName,asc").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("sort=companyName,asc")))
                .andExpect(content().string(Matchers.containsString("data-current-sort=\"companyName,asc\"")));
    }
}
