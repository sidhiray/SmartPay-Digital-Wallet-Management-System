package com.smartpay.service.impl;

import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.entity.WalletTransaction;
import com.smartpay.exception.InvalidTransferException;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.repository.WalletTransactionRepository;
import com.smartpay.repository.TransferRequestRecordRepository;
import com.smartpay.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class WalletServiceImplRollbackTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("smartpay_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @MockitoSpyBean
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private TransferRequestRecordRepository transferRequestRecordRepository;

    @Test
    void transferMoney_shouldRollbackWhenTransactionSaveFails() {

        String encodedPassword =
                new BCryptPasswordEncoder().encode("test-password");

        User sender = userRepository.save(
                User.builder()
                        .fullName("Rollback Sender")
                        .email("rollback-sender@gmail.com")
                        .mobile("9000000021")
                        .password(encodedPassword)
                        .build()
        );

        User receiver = userRepository.save(
                User.builder()
                        .fullName("Rollback Receiver")
                        .email("rollback-receiver@gmail.com")
                        .mobile("9000000022")
                        .password(encodedPassword)
                        .build()
        );

        Wallet senderWallet = walletRepository.save(
                Wallet.builder()
                        .user(sender)
                        .balance(new BigDecimal("1000.00"))
                        .currency("INR")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        Wallet receiverWallet = walletRepository.save(
                Wallet.builder()
                        .user(receiver)
                        .balance(new BigDecimal("500.00"))
                        .currency("INR")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        // Simulate database failure while saving a transaction
        doThrow(new RuntimeException("Simulated transaction save failure"))
                .when(walletTransactionRepository)
                .save(any());

        // Transfer must fail
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.transferMoney(
                        sender.getUserId(),
                        receiver.getEmail(),
                        new BigDecimal("300.00"),
                        "rollback-transfer-001"
                )
        );

        assertEquals(
                "Simulated transaction save failure",
                exception.getMessage()
        );

        // Reload wallets from the database after rollback
        Wallet updatedSender = walletRepository
                .findById(senderWallet.getWalletId())
                .orElseThrow();

        Wallet updatedReceiver = walletRepository
                .findById(receiverWallet.getWalletId())
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(updatedSender.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("500.00")
                        .compareTo(updatedReceiver.getBalance())
        );

        assertTrue(
                transferRequestRecordRepository
                        .findBySenderIdAndIdempotencyKey(
                                sender.getUserId(),
                                "rollback-transfer-001"
                        )
                        .isEmpty()
        );
    }
}