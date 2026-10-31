package com.smartpay.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {

        jwtService = new JwtService();

        // Test-only secret, production secret use nahi karenge.
        String secret = Base64.getEncoder().encodeToString(
                "smartpay-test-secret-key-for-hs256-2026"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
    }

    @Test
    void generateToken_shouldExtractCorrectUsername() {

        String email = "sender@gmail.com";

        String token = jwtService.createToken(
                Map.of("userId", 1L),
                email
        );

        String extractedEmail = jwtService.extractUsername(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    void extractUsername_shouldRejectTamperedToken() {

        String token = jwtService.createToken(
                Map.of("userId", 1L),
                "sender@gmail.com"
        );

        // Change JWT payload without updating its signature.
        String[] parts = token.split("\\.");

        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);

        String payload = new String(
                payloadBytes,
                java.nio.charset.StandardCharsets.UTF_8
        );

        String modifiedPayload = payload.replace(
                "sender@gmail.com",
                "hacker@gmail.com"
        );

        assertNotEquals(payload, modifiedPayload);

        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        modifiedPayload.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                );

        String tamperedToken = String.join(".", parts);

        assertThrows(
                SignatureException.class,
                () -> jwtService.extractUsername(tamperedToken)
        );
    }

    @Test
    void extractUsername_shouldRejectExpiredToken() {

        // Token already expired 1 second ago
        ReflectionTestUtils.setField(
                jwtService,
                "jwtExpiration",
                -1000L
        );

        String expiredToken = jwtService.createToken(
                Map.of("userId", 1L),
                "sender@gmail.com"
        );

        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.extractUsername(expiredToken)
        );
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {

        String email = "sender@gmail.com";

        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn(email);

        String token = jwtService.createToken(
                Map.of("userId", 1L),
                email
        );

        boolean result = jwtService.isTokenValid(token, userDetails);

        assertTrue(result);
    }

    @Test
    void isTokenValid_shouldReturnFalseWhenUsernameDoesNotMatch() {

        String tokenEmail = "sender@gmail.com";
        String userDetailsEmail = "hacker@gmail.com";

        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn(userDetailsEmail);

        String token = jwtService.createToken(
                Map.of("userId", 1L),
                tokenEmail
        );

        boolean result = jwtService.isTokenValid(token, userDetails);

        assertFalse(result);
    }

    @Test
    void isTokenValid_shouldThrowExceptionForExpiredToken() {

        String email = "sender@gmail.com";

        UserDetails userDetails = mock(UserDetails.class);

        // Token already expired 1 second ago
        ReflectionTestUtils.setField(
                jwtService,
                "jwtExpiration",
                -1000L
        );

        String expiredToken = jwtService.createToken(
                Map.of("userId", 1L),
                email
        );

        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.isTokenValid(expiredToken, userDetails)
        );
    }
}