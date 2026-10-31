package com.smartpay.controller;

import com.smartpay.dto.response.WalletResponse;
import com.smartpay.security.CustomUserDetails;
import com.smartpay.security.JwtAuthenticationFilter;
import com.smartpay.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = WalletController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    void transferMoney_shouldReturn200WhenRequestIsValid() throws Exception {

        Long senderId = 1L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(userDetails.getUserId()).thenReturn(senderId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        Collections.emptyList()
                );

        WalletResponse response = WalletResponse.builder()
                .walletId(10L)
                .balance(new BigDecimal("700.00"))
                .currency("INR")
                .isActive(true)
                .build();

        when(walletService.transferMoney(
                eq(senderId),
                eq("receiver@gmail.com"),
                eq(new BigDecimal("300.00")),
                eq("transfer-api-001")
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .principal(auth)
                                .header("Idempotency-Key", "transfer-api-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "receiverEmail": "receiver@gmail.com",
                                    "amount": 300.00
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(10))
                .andExpect(jsonPath("$.balance").value(700.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(walletService).transferMoney(
                eq(senderId),
                eq("receiver@gmail.com"),
                eq(new BigDecimal("300.00")),
                eq("transfer-api-001")
        );
    }


    @Test
    void transferMoney_shouldReturn400WhenIdempotencyKeyIsMissing() throws Exception {

        Long senderId = 1L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(senderId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        Collections.emptyList()
                );

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .principal(auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "receiverEmail": "receiver@gmail.com",
                                "amount": 300.00
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }

    @Test
    void transferMoney_shouldReturn400WhenAmountIsZero() throws Exception {

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .header("Idempotency-Key", "transfer-api-002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "receiverEmail": "receiver@gmail.com",
                                "amount": 0
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }

    @Test
    void transferMoney_shouldReturn400WhenAmountIsNegative() throws Exception {

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .header("Idempotency-Key", "transfer-api-003")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "receiverEmail": "receiver@gmail.com",
                                "amount": -100.00
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }

    @Test
    void transferMoney_shouldReturn400WhenReceiverEmailIsInvalid() throws Exception {

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .header("Idempotency-Key", "transfer-api-004")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "receiverEmail": "invalid-email",
                                "amount": 100.00
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }

    @Test
    void transferMoney_shouldReturn400WhenReceiverEmailIsMissing() throws Exception {

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .header("Idempotency-Key", "transfer-api-005")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "amount": 100.00
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }

    @Test
    void transferMoney_shouldReturn400WhenAmountIsMissing() throws Exception {

        mockMvc.perform(
                        post("/api/v1/wallet/transfer")
                                .header("Idempotency-Key", "transfer-api-006")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "receiverEmail": "receiver@gmail.com"
                            }
                            """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(walletService);
    }
}
