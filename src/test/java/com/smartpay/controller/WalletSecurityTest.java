package com.smartpay.controller;

import com.smartpay.config.SecurityConfig;
import com.smartpay.security.CustomUserDetailsService;
import com.smartpay.security.JwtAuthenticationFilter;
import com.smartpay.security.JwtService;
import com.smartpay.service.WalletService;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartpay.security.CustomUserDetails;
import com.smartpay.dto.response.WalletResponse;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(WalletController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
class WalletSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void getBalance_shouldRejectRequestWithoutJwt() throws Exception {

        mockMvc.perform(
                        get("/api/v1/wallet/balance")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(
                walletService,
                jwtService,
                userDetailsService
        );
    }

    @Test
    void getBalance_shouldRejectInvalidJwt() throws Exception {

        String invalidToken = "invalid.jwt.token";

        when(jwtService.extractUsername(invalidToken))
                .thenThrow(new IllegalArgumentException("Invalid JWT token"));

        mockMvc.perform(
                        get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + invalidToken)
                )
                .andExpect(status().isUnauthorized());

        verify(jwtService).extractUsername(invalidToken);

        verifyNoInteractions(
                walletService,
                userDetailsService
        );
    }

    @Test
    void getBalance_shouldReturn200WhenJwtIsValid() throws Exception {

        Long userId = 1L;
        String email = "sender@gmail.com";
        String validToken = "valid.jwt.token";

        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(userDetails.getUserId()).thenReturn(userId);
        when(userDetails.getUsername()).thenReturn(email);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        when(jwtService.extractUsername(validToken))
                .thenReturn(email);

        when(userDetailsService.loadUserByUsername(email))
                .thenReturn(userDetails);

        when(jwtService.isTokenValid(validToken, userDetails))
                .thenReturn(true);

        WalletResponse response = WalletResponse.builder()
                .walletId(10L)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();

        when(walletService.getWallet(userId))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + validToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(10))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(jwtService).extractUsername(validToken);
        verify(jwtService).isTokenValid(validToken, userDetails);
        verify(userDetailsService).loadUserByUsername(email);
        verify(walletService).getWallet(userId);
    }

    @Test
    void getBalance_shouldRejectExpiredJwt() throws Exception {

        String expiredToken = "expired.jwt.token";

        when(jwtService.extractUsername(expiredToken))
                .thenThrow(new ExpiredJwtException(
                        null,
                        null,
                        "JWT token has expired"
                ));

        mockMvc.perform(
                        get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + expiredToken)
                )
                .andExpect(status().isUnauthorized());

        verify(jwtService).extractUsername(expiredToken);

        verifyNoInteractions(
                walletService,
                userDetailsService
        );
    }

    @Test
    void getBalance_shouldRejectTamperedJwt() throws Exception {

        String tamperedToken = "tampered.jwt.token";

        when(jwtService.extractUsername(tamperedToken))
                .thenThrow(new SignatureException(
                        "JWT signature does not match"
                ));

        mockMvc.perform(
                        get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + tamperedToken)
                )
                .andExpect(status().isUnauthorized());

        verify(jwtService).extractUsername(tamperedToken);

        verifyNoInteractions(
                walletService,
                userDetailsService
        );
    }
}