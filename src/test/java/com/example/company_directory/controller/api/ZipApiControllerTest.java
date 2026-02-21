package com.example.company_directory.controller.api;

import com.example.company_directory.entity.ZipMaster;
import com.example.company_directory.repository.ZipMasterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZipApiController.class)
class ZipApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ZipMasterRepository zipMasterRepository;

    @Test
    void getAddress_returnsBadRequest_whenZipCodeFormatIsInvalid() throws Exception {
        mockMvc.perform(get("/api/zip/12a-456"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("郵便番号は7桁の数字で入力してください。"));

        verify(zipMasterRepository, never()).findByZipCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getAddress_returnsAddress_whenZipCodeIsValid() throws Exception {
        ZipMaster zipMaster = new ZipMaster();
        zipMaster.setZipCode("1000005");
        zipMaster.setPrefecture("東京都");
        zipMaster.setCity("千代田区");
        zipMaster.setTown("丸の内");

        when(zipMasterRepository.findByZipCode("1000005")).thenReturn(zipMaster);

        mockMvc.perform(get("/api/zip/100-0005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("1000005"))
                .andExpect(jsonPath("$.prefecture").value("東京都"))
                .andExpect(jsonPath("$.city").value("千代田区"))
                .andExpect(jsonPath("$.town").value("丸の内"));
    }
}
