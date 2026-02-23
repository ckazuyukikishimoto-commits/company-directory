package com.example.company_directory.service;

import com.example.company_directory.dto.CompanySearchResultDto;
import com.example.company_directory.entity.CompanyMaster;
import com.example.company_directory.repository.CompanyMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySearchServiceTest {

    @Mock
    private CompanyMasterRepository companyMasterRepository;

    @InjectMocks
    private CompanySearchService companySearchService;

    @Test
    void searchByAddress_skipsRecordsWithNullCompanyName() {
        CompanyMaster valid = new CompanyMaster();
        valid.setCompanyName("株式会社サンプル");
        valid.setCorporateNumber("1234567890123");
        valid.setAddress("高岡市宮田町8-29");

        CompanyMaster invalid = new CompanyMaster();
        invalid.setCompanyName(null);
        invalid.setCorporateNumber("9876543210987");
        invalid.setAddress("高岡市宮田町8-29");

        when(companyMasterRepository.findTop20ByAddressContaining("高岡市宮田町8-29"))
                .thenReturn(List.of(valid, invalid));

        ReflectionTestUtils.setField(companySearchService, "geminiApiKey", "");

        List<CompanySearchResultDto> results = companySearchService.searchByAddress("高岡市宮田町8-29");

        assertThat(results)
                .extracting(CompanySearchResultDto::getCompanyName)
                .containsExactly("株式会社サンプル");
    }
}
