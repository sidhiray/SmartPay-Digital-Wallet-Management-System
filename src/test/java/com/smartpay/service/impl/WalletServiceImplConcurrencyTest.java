package com.smartpay.service.impl;

import com.smartpay.dto.response.WalletResponse;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.InsufficientBalanceException;
import com.smartpay.repository.TransferRequestRecordRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.repository.WalletTransactionRepository;
import com.smartpay.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class WalletServiceImplConcurrencyTest {

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
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private TransferRequestRecordRepository transferRequestRecordRepository;

    @Test
    void concurrentDuplicateRequests_shouldTransferMoneyOnlyOnce()
            throws Exception {

        // Arrange: create two users
        String encodedPassword =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode("test-password");

        User sender = userRepository.save(
                User.builder()
                        .fullName("Sender Test")
                        .email("sender-concurrent@gmail.com")
                        .mobile("9000000001")
                        .password(encodedPassword)
                        .build()
        );

        User receiver = userRepository.save(
                User.builder()
                        .fullName("Receiver Test")
                        .email("receiver-concurrent@gmail.com")
                        .mobile("9000000002")
                        .password(encodedPassword)
                        .build()
        );
        // Add required fields here if your User entity
        // has other non-null database columns.

        Wallet senderWallet = walletRepository.save(
                Wallet.builder()
                        .user(sender)
                        .balance(new BigDecimal("1000.00"))
                        .currency("INR")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        walletRepository.save(
                Wallet.builder()
                        .user(receiver)
                        .balance(new BigDecimal("500.00"))
                        .currency("INR")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        Long senderId = sender.getUserId();
        String receiverEmail = receiver.getEmail();
        String idempotencyKey = "concurrent-transfer-001";
        BigDecimal amount = new BigDecimal("300.00");

        int requestCount = 8;

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<WalletResponse>> futures = new ArrayList<>();

        try {
            // Act: launch eight requests at approximately the same time
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to start request"
                        );
                    }

                    return walletService.transferMoney(
                            senderId,
                            receiverEmail,
                            amount,
                            idempotencyKey
                    );
                }));
            }

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "All requests should be ready before starting"
            );

            start.countDown();

            // Every retry should receive the same successful response
            for (Future<WalletResponse> future : futures) {
                WalletResponse response =
                        future.get(30, TimeUnit.SECONDS);

                assertEquals(
                        senderWallet.getWalletId(),
                        response.getWalletId()
                );

                assertEquals(
                        0,
                        new BigDecimal("700.00")
                                .compareTo(response.getBalance())
                );
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        // Assert: verify committed database state
        Long senderWalletId = walletRepository
                .findWalletIdByUserUserId(senderId)
                .orElseThrow();

        Wallet updatedSenderWallet = walletRepository
                .findById(senderWalletId)
                .orElseThrow();

        Long receiverWalletId = walletRepository
                .findWalletIdByUserUserId(receiver.getUserId())
                .orElseThrow();

        Wallet updatedReceiverWallet = walletRepository
                .findById(receiverWalletId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("700.00")
                        .compareTo(updatedSenderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(updatedReceiverWallet.getBalance())
        );

        assertEquals(2, walletTransactionRepository.count());
        assertEquals(1, transferRequestRecordRepository.count());
    }

    @Test
    void concurrentTransfers_shouldPreventOverspending() throws Exception {

        String encodedPassword =
                new BCryptPasswordEncoder().encode("test-password");

        User sender = userRepository.save(
                User.builder()
                        .fullName("Sender Overspend")
                        .email("sender-overspend@gmail.com")
                        .mobile("9000000011")
                        .password(encodedPassword)
                        .build()
        );

        User receiver = userRepository.save(
                User.builder()
                        .fullName("Receiver Overspend")
                        .email("receiver-overspend@gmail.com")
                        .mobile("9000000012")
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

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();

                try {
                    walletService.transferMoney(
                            sender.getUserId(),
                            receiver.getEmail(),
                            new BigDecimal("700.00"),
                            "overspend-key-1"
                    );
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                start.await();

                try {
                    walletService.transferMoney(
                            sender.getUserId(),
                            receiver.getEmail(),
                            new BigDecimal("700.00"),
                            "overspend-key-2"
                    );
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });

            start.countDown();

            boolean firstResult = first.get(30, TimeUnit.SECONDS);
            boolean secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    (firstResult ? 1 : 0) + (secondResult ? 1 : 0),
                    "Exactly one transfer should succeed"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Wallet updatedSender = walletRepository
                .findById(senderWallet.getWalletId())
                .orElseThrow();

        Wallet updatedReceiver = walletRepository
                .findById(receiverWallet.getWalletId())
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("300.00")
                        .compareTo(updatedSender.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("1200.00")
                        .compareTo(updatedReceiver.getBalance())
        );
    }
}
