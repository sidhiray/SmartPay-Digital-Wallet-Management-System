package com.smartpay.service.impl;

import com.smartpay.dto.response.TransactionResponse;
import com.smartpay.dto.response.WalletResponse;
import com.smartpay.entity.TransferRequestRecord;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.entity.WalletTransaction;
import com.smartpay.exception.InsufficientBalanceException;
import com.smartpay.exception.InvalidTransferException;
import com.smartpay.exception.UserNotFoundException;
import com.smartpay.exception.WalletNotFoundException;
import com.smartpay.repository.TransferRequestRecordRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private TransferRequestRecordRepository transferRequestRecordRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void transferMoney_shouldTransferAmountSuccessfully(){

        Long senderId = 1L;
        Long receiverId = 2L;

        User receiver = User.builder()
                .userId(receiverId)
                .email("os@gmail.com")
                .build();

        Wallet senderWallet = Wallet.builder()
                .walletId(10L)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();

        Wallet receiverWallet = Wallet.builder()
                .walletId(20L)
                .balance(new BigDecimal("500.00"))
                .currency("INR")
                .isActive(true)
                .build();

        when(userRepository.findByEmail("os@gmail.com"))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(10L));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(20L));

        when(walletRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(receiverWallet));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, "transfer-001"))
                .thenReturn(Optional.empty());

        WalletResponse response = walletService.transferMoney(
                senderId,
                "os@gmail.com",
                new BigDecimal("300.00"),
                "transfer-001"
        );

        assertEquals(0, new BigDecimal("700.00")
                .compareTo(senderWallet.getBalance()));

        assertEquals(0, new BigDecimal("800.00")
                .compareTo(receiverWallet.getBalance()));

        assertEquals(senderWallet.getWalletId(), response.getWalletId());

        assertEquals(0, new BigDecimal("700.00")
                .compareTo(response.getBalance()));

        verify(walletTransactionRepository, times(2))
                .save(any(WalletTransaction.class));

        verify(transferRequestRecordRepository)
                .save(any(TransferRequestRecord.class));
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenBalanceIsInsufficient() {
        Long senderId = 1L;
        Long receiverId = 2L;

        User receiver = User.builder()
                .userId(receiverId)
                .email("os@gmail.com")
                .build();

        Wallet senderWallet = Wallet.builder()
                .walletId(10L)
                .balance(new BigDecimal("500.00"))
                .currency("INR")
                .isActive(true)
                .build();

        Wallet receiverWallet = Wallet.builder()
                .walletId(20L)
                .balance(new BigDecimal("300.00"))
                .currency("INR")
                .isActive(true)
                .build();

        when(userRepository.findByEmail("os@gmail.com"))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(10L));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(20L));

        when(walletRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(receiverWallet));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, "transfer-002"))
                .thenReturn(Optional.empty());

        assertThrows(
                InsufficientBalanceException.class,
                () -> walletService.transferMoney(
                        senderId,
                        "os@gmail.com",
                        new BigDecimal("700.00"),
                        "transfer-002"
                )
        );

        assertEquals(
                0,
                new BigDecimal("500.00")
                        .compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("300.00")
                        .compareTo(receiverWallet.getBalance())
        );

        verify(walletTransactionRepository, never())
                .save(any(WalletTransaction.class));

        verify(transferRequestRecordRepository, never())
                .save(any(TransferRequestRecord.class));
    }

    @Test
    void transferMoney_shouldReturnSavedResponseForDuplicateRequest() {
        Long senderId = 1L;
        Long receiverId = 2L;
        String idempotencyKey = "transfer-duplicate";

        User receiver = User.builder()
                .userId(receiverId)
                .email("os@gmail.com")
                .build();

        Wallet senderWallet = Wallet.builder()
                .walletId(10L)
                .balance(new BigDecimal("700.00"))
                .currency("INR")
                .isActive(true)
                .build();

        Wallet receiverWallet = Wallet.builder()
                .walletId(20L)
                .balance(new BigDecimal("800.00"))
                .currency("INR")
                .isActive(true)
                .build();

        TransferRequestRecord existingRecord =
                TransferRequestRecord.builder()
                        .senderId(senderId)
                        .idempotencyKey(idempotencyKey)
                        .receiverEmail("os@gmail.com")
                        .amount(new BigDecimal("300.00"))
                        .referenceId("REF-123")
                        .responseWalletId(10L)
                        .responseBalance(new BigDecimal("700.00"))
                        .responseCurrency("INR")
                        .responseActive(true)
                        .build();

        when(userRepository.findByEmail("os@gmail.com"))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(10L));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(20L));

//        when(walletRepository.findByIdForUpdate(10L))
//                .thenReturn(Optional.of(senderWallet));
//
//        when(walletRepository.findByIdForUpdate(20L))
//                .thenReturn(Optional.of(receiverWallet));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(
                        senderId, idempotencyKey))
                .thenReturn(Optional.of(existingRecord));

        WalletResponse response = walletService.transferMoney(
                senderId,
                "os@gmail.com",
                new BigDecimal("300.00"),
                idempotencyKey
        );

        assertEquals(10L, response.getWalletId());

        assertEquals(
                0,
                new BigDecimal("700.00")
                        .compareTo(response.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("700.00")
                        .compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(receiverWallet.getBalance())
        );

        verify(walletTransactionRepository, never())
                .save(any(WalletTransaction.class));

        verify(transferRequestRecordRepository, never())
                .save(any(TransferRequestRecord.class));
    }

    @Test
    void transferMoney_shouldThrowExceptionWhenIdempotencyKeyIsReusedWithDifferentAmount() {
        Long senderId = 1L;
        Long receiverId = 2L;
        String idempotencyKey = "transfer-reused";

        User receiver = User.builder()
                .userId(receiverId)
                .email("receiver@gmail.com")
                .build();

        Wallet senderWallet = Wallet.builder()
                .walletId(10L)
                .balance(new BigDecimal("700.00"))
                .currency("INR")
                .isActive(true)
                .build();

        Wallet receiverWallet = Wallet.builder()
                .walletId(20L)
                .balance(new BigDecimal("800.00"))
                .currency("INR")
                .isActive(true)
                .build();

        TransferRequestRecord existingRecord =
                TransferRequestRecord.builder()
                        .senderId(senderId)
                        .idempotencyKey(idempotencyKey)
                        .receiverEmail("receiver@gmail.com")
                        .amount(new BigDecimal("300.00"))
                        .referenceId("REF-123")
                        .responseWalletId(10L)
                        .responseBalance(new BigDecimal("700.00"))
                        .responseCurrency("INR")
                        .responseActive(true)
                        .build();

        when(userRepository.findByEmail("receiver@gmail.com"))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(10L));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(20L));

//        when(walletRepository.findByIdForUpdate(10L))
//                .thenReturn(Optional.of(senderWallet));
//
//        when(walletRepository.findByIdForUpdate(20L))
//                .thenReturn(Optional.of(receiverWallet));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(Optional.of(existingRecord));

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        "receiver@gmail.com",
                        new BigDecimal("400.00"),
                        idempotencyKey
                )
        );

        assertEquals(
                0,
                new BigDecimal("700.00")
                        .compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(receiverWallet.getBalance())
        );

        verify(walletTransactionRepository, never())
                .save(any(WalletTransaction.class));

        verify(transferRequestRecordRepository, never())
                .save(any(TransferRequestRecord.class));
    }

    @Test
    void transferMoney_shouldThrowExceptionWhenIdempotencyKeyIsReusedWithDifferentReceiver() {
        Long senderId = 1L;
        Long originalReceiverId = 2L;
        Long newReceiverId = 3L;

        String idempotencyKey = "transfer-receiver-test";

        User newReceiver = User.builder()
                .userId(newReceiverId)
                .email("another@gmail.com")
                .build();

        TransferRequestRecord existingRecord =
                TransferRequestRecord.builder()
                        .senderId(senderId)
                        .idempotencyKey(idempotencyKey)
                        .receiverEmail("receiver@gmail.com")
                        .amount(new BigDecimal("300.00"))
                        .referenceId("REF-123")
                        .responseWalletId(10L)
                        .responseBalance(new BigDecimal("700.00"))
                        .responseCurrency("INR")
                        .responseActive(true)
                        .build();

        when(userRepository.findByEmail("another@gmail.com"))
                .thenReturn(Optional.of(newReceiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(10L));

        when(walletRepository.findWalletIdByUserUserId(newReceiverId))
                .thenReturn(Optional.of(30L));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(
                        senderId, idempotencyKey))
                .thenReturn(Optional.of(existingRecord));

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        "another@gmail.com",
                        new BigDecimal("300.00"),
                        idempotencyKey
                )
        );

        verify(walletRepository, never())
                .save(any(Wallet.class));

        verify(walletTransactionRepository, never())
                .save(any(WalletTransaction.class));

        verify(transferRequestRecordRepository, never())
                .save(any(TransferRequestRecord.class));
    }


    @Test
    void transferMoney_shouldRejectNegativeAmount() {

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        1L,
                        "receiver@gmail.com",
                        new BigDecimal("-100.00"),
                        "negative-transfer-001"
                )
        );

        verifyNoInteractions(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }

    @Test
    void transferMoney_shouldRejectZeroAmount() {

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        1L,
                        "receiver@gmail.com",
                        BigDecimal.ZERO,
                        "zero-transfer-001"
                )
        );

        verifyNoInteractions(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }

    @Test
    void transferMoney_shouldRejectNullAmount() {

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        1L,
                        "receiver@gmail.com",
                        null,
                        "null-transfer-001"
                )
        );

        verifyNoInteractions(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }

    @Test
    void transferMoney_shouldRejectSelfTransfer() {

        Long senderId = 1L;
        String email = "sender@gmail.com";

        User sender = User.builder()
                .userId(senderId)
                .email(email)
                .build();

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(sender));

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        email,
                        new BigDecimal("100.00"),
                        "self-transfer-001"
                )
        );

        verifyNoInteractions(
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }

    @Test
    void transferMoney_shouldRejectMissingIdempotencyKey() {

        Long senderId = 1L;
        String email = "receiver@gmail.com";

        assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        email,
                        new BigDecimal("100.00"),
                        null
                )
        );

        verifyNoInteractions(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }


    @Test
    void getWallet_shouldReturnWalletForValidUser() {

        // Arrange
        Long userId = 1L;

        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("1500.00"))
                .currency("INR")
                .isActive(true)
                .build();

        wallet.setWalletId(10L);

        when(walletRepository.findByUserUserId(userId))
                .thenReturn(Optional.of(wallet));

        // Act
        WalletResponse response = walletService.getWallet(userId);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getWalletId());
        assertEquals(0, new BigDecimal("1500.00")
                .compareTo(response.getBalance()));
        assertEquals("INR", response.getCurrency());
        assertTrue(response.getIsActive());

        // Verify repository interaction
        verify(walletRepository).findByUserUserId(userId);
    }


    @Test
    void getWallet_shouldThrowExceptionWhenWalletNotFound() {

        // Arrange
        Long userId = 999L;

        when(walletRepository.findByUserUserId(userId))
                .thenReturn(Optional.empty());

        // Act + Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.getWallet(userId)
        );

        assertEquals(
                "Wallet not found",
                exception.getMessage()
        );

        // Verify repository lookup
        verify(walletRepository).findByUserUserId(userId);
    }


    @Test
    void addMoney_shouldIncreaseBalanceAndSaveTransaction() {

        // Arrange
        Long userId = 1L;
        Long walletId = 10L;

        BigDecimal initialBalance = new BigDecimal("1000.00");
        BigDecimal amount = new BigDecimal("500.00");

        Wallet wallet = Wallet.builder()
                .balance(initialBalance)
                .currency("INR")
                .isActive(true)
                .build();

        wallet.setWalletId(walletId);

        when(walletRepository.findWalletIdByUserUserId(userId))
                .thenReturn(Optional.of(walletId));

        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        // Act
        WalletResponse response = walletService.addMoney(userId, amount);

        // Assert: Updated balance
        assertNotNull(response);
        assertEquals(walletId, response.getWalletId());

        assertEquals(
                0,
                new BigDecimal("1500.00").compareTo(response.getBalance())
        );

        assertEquals("INR", response.getCurrency());
        assertTrue(response.getIsActive());

        // Verify wallet balance updated
        assertEquals(
                0,
                new BigDecimal("1500.00").compareTo(wallet.getBalance())
        );

        verify(walletRepository).save(wallet);

        // Verify transaction history
        ArgumentCaptor<WalletTransaction> transactionCaptor =
                ArgumentCaptor.forClass(WalletTransaction.class);

        verify(walletTransactionRepository)
                .save(transactionCaptor.capture());

        WalletTransaction savedTransaction =
                transactionCaptor.getValue();

        assertSame(wallet, savedTransaction.getWallet());

        assertEquals(
                0,
                amount.compareTo(savedTransaction.getAmount())
        );

        assertEquals(
                "ADD_MONEY",
                savedTransaction.getTransactionType()
        );

        assertEquals(
                "SUCCESS",
                savedTransaction.getStatus()
        );

        assertNotNull(savedTransaction.getCreatedAt());

        // Verify repository calls
        verify(walletRepository)
                .findWalletIdByUserUserId(userId);

        verify(walletRepository)
                .findByIdForUpdate(walletId);
    }


    @Test
    void addMoney_shouldThrowExceptionWhenWalletIdNotFound() {

        // Arrange
        Long userId = 999L;
        BigDecimal amount = new BigDecimal("500.00");

        when(walletRepository.findWalletIdByUserUserId(userId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.addMoney(userId, amount)
        );

        assertEquals("Wallet not found", exception.getMessage());

        // Verify wallet ID lookup
        verify(walletRepository)
                .findWalletIdByUserUserId(userId);

        // Wallet locking should never happen
        verify(walletRepository, never())
                .findByIdForUpdate(anyLong());

        // No wallet or transaction should be saved
        verify(walletRepository, never())
                .save(any(Wallet.class));

        verifyNoInteractions(walletTransactionRepository);
    }


    @Test
    void addMoney_shouldThrowExceptionWhenLockedWalletNotFound() {

        // Arrange
        Long userId = 1L;
        Long walletId = 10L;

        BigDecimal amount = new BigDecimal("500.00");

        // Wallet ID exists
        when(walletRepository.findWalletIdByUserUserId(userId))
                .thenReturn(Optional.of(walletId));

        // But wallet cannot be retrieved
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.addMoney(userId, amount)
        );

        assertEquals("Wallet not found", exception.getMessage());

        // Verify wallet lookup
        verify(walletRepository)
                .findWalletIdByUserUserId(userId);

        // Verify locked wallet lookup
        verify(walletRepository)
                .findByIdForUpdate(walletId);

        // Wallet must not be saved
        verify(walletRepository, never())
                .save(any(Wallet.class));

        // No transaction history should be created
        verifyNoInteractions(walletTransactionRepository);
    }



    @Test
    void addMoney_shouldRejectNegativeAmount() {

        // Arrange
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("-500.00");

        // Act + Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.addMoney(userId, amount)
        );

        assertEquals(
                "Amount must be greater than zero",
                exception.getMessage()
        );

        // Invalid amount par database access nahi hona chahiye
        verifyNoInteractions(
                walletRepository,
                walletTransactionRepository
        );
    }


    @Test
    void addMoney_shouldRejectZeroAmount() {

        // Arrange
        Long userId = 1L;
        BigDecimal amount = BigDecimal.ZERO;

        // Act + Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.addMoney(userId, amount)
        );

        assertEquals(
                "Amount must be greater than zero",
                exception.getMessage()
        );

        // Invalid amount par repository calls nahi honi chahiye
        verifyNoInteractions(
                walletRepository,
                walletTransactionRepository
        );
    }


    @Test
    void addMoney_shouldRejectNullAmount() {

        // Arrange
        Long userId = 1L;
        BigDecimal amount = null;

        // Act + Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.addMoney(userId, amount)
        );

        assertEquals(
                "Amount must be greater than zero",
                exception.getMessage()
        );

        // Invalid amount par database access nahi hona chahiye
        verifyNoInteractions(
                walletRepository,
                walletTransactionRepository
        );
    }


    @Test
    void getTransactions_shouldReturnTransactionsForValidUser() {

        // Arrange
        Long userId = 1L;
        Long walletId = 10L;

        Pageable pageable = PageRequest.of(0, 10);

        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("1500.00"))
                .currency("INR")
                .isActive(true)
                .build();

        wallet.setWalletId(walletId);

        LocalDateTime createdAt = LocalDateTime.now();

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .amount(new BigDecimal("500.00"))
                .transactionType("ADD_MONEY")
                .status("SUCCESS")
                .createdAt(createdAt)
                .build();

        // WalletTransaction entity ka ID setter
        transaction.setTransactionId(100L);

        Page<WalletTransaction> transactionPage =
                new PageImpl<>(List.of(transaction), pageable, 1);

        when(walletRepository.findByUserUserId(userId))
                .thenReturn(Optional.of(wallet));

        when(walletTransactionRepository.findByWalletWalletId(
                walletId, pageable
        )).thenReturn(transactionPage);

        // Act
        Page<TransactionResponse> response =
                walletService.getTransactions(userId, pageable);

        // Assert: Pagination
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        // Assert: DTO mapping
        TransactionResponse result = response.getContent().get(0);

        assertEquals(100L, result.getTransactionId());

        assertEquals(
                0,
                new BigDecimal("500.00").compareTo(result.getAmount())
        );

        assertEquals("ADD_MONEY", result.getTransactionType());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(createdAt, result.getCreatedAt());

        // Verify repository calls
        verify(walletRepository).findByUserUserId(userId);

        verify(walletTransactionRepository)
                .findByWalletWalletId(walletId, pageable);
    }


    @Test
    void getTransactions_shouldThrowExceptionWhenWalletNotFound() {

        // Arrange
        Long userId = 999L;
        Pageable pageable = PageRequest.of(0, 10);

        when(walletRepository.findByUserUserId(userId))
                .thenReturn(Optional.empty());

        // Act + Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.getTransactions(userId, pageable)
        );

        assertEquals(
                "Wallet not found",
                exception.getMessage()
        );

        // Verify wallet lookup
        verify(walletRepository)
                .findByUserUserId(userId);

        // Transactions should not be fetched
        verifyNoInteractions(walletTransactionRepository);
    }


    @Test
    void getTransactions_shouldReturnEmptyPageWhenNoTransactionsExist() {

        // Arrange
        Long userId = 1L;
        Long walletId = 10L;

        Pageable pageable = PageRequest.of(0, 10);

        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();

        wallet.setWalletId(walletId);

        Page<WalletTransaction> emptyPage =
                Page.empty(pageable);

        when(walletRepository.findByUserUserId(userId))
                .thenReturn(Optional.of(wallet));

        when(walletTransactionRepository.findByWalletWalletId(
                walletId, pageable
        )).thenReturn(emptyPage);

        // Act
        Page<TransactionResponse> response =
                walletService.getTransactions(userId, pageable);

        // Assert
        assertNotNull(response);

        assertTrue(response.isEmpty());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getContent().size());

        assertEquals(0, response.getNumber());
        assertEquals(10, response.getSize());

        // Verify repository calls
        verify(walletRepository)
                .findByUserUserId(userId);

        verify(walletTransactionRepository)
                .findByWalletWalletId(walletId, pageable);
    }


    @Test
    void transferMoney_shouldRejectIdempotencyKeyLongerThan50Characters() {

        // Arrange
        Long senderId = 1L;
        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");

        // 51 characters
        String idempotencyKey = "A".repeat(51);

        // Act + Assert
        InvalidTransferException exception = assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Idempotency-Key must not exceed 50 characters",
                exception.getMessage()
        );

        // Validation should happen before database access
        verifyNoInteractions(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenReceiverNotFound() {

        // Arrange
        Long senderId = 1L;
        String receiverEmail = "unknown@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "transfer-key-001";

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.empty());

        // Act + Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Receiver not found",
                exception.getMessage()
        );

        // Verify receiver lookup
        verify(userRepository).findByEmail(receiverEmail);

        // No wallet operations should happen
        verifyNoInteractions(
                walletRepository,
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenSenderWalletNotFound() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "transfer-key-002";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        // Receiver exists
        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        // Sender wallet does not exist
        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Sender wallet not found",
                exception.getMessage()
        );

        // Verify receiver lookup
        verify(userRepository).findByEmail(receiverEmail);

        // Verify sender wallet lookup
        verify(walletRepository)
                .findWalletIdByUserUserId(senderId);

        // Receiver wallet lookup should not happen
        verify(walletRepository, never())
                .findWalletIdByUserUserId(receiverId);

        // No transaction should be saved
        verifyNoInteractions(
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenReceiverWalletNotFound() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;
        Long senderWalletId = 10L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "transfer-key-003";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        // Receiver user exists
        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        // Sender wallet exists
        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        // Receiver wallet does not exist
        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Receiver wallet not found",
                exception.getMessage()
        );

        // Verify receiver lookup
        verify(userRepository).findByEmail(receiverEmail);

        // Verify both wallet ID lookups
        verify(walletRepository)
                .findWalletIdByUserUserId(senderId);

        verify(walletRepository)
                .findWalletIdByUserUserId(receiverId);

        // No wallet locking should happen
        verify(walletRepository, never())
                .findByIdForUpdate(anyLong());

        // No transaction or idempotency record should be saved
        verifyNoInteractions(
                walletTransactionRepository,
                transferRequestRecordRepository
        );
    }


    @Test
    void transferMoney_shouldLockReceiverWalletFirstWhenReceiverWalletIdIsSmaller() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 20L;
        Long receiverWalletId = 10L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "reverse-lock-001";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();
        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();
        senderWallet.setWalletId(senderWalletId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();
        receiverWallet.setWalletId(receiverWalletId);

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(Optional.empty());

        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        // Act
        WalletResponse response = walletService.transferMoney(
                senderId,
                receiverEmail,
                amount,
                idempotencyKey
        );

        // Assert: Correct balances
        assertEquals(
                0,
                new BigDecimal("500.00").compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("700.00").compareTo(receiverWallet.getBalance())
        );

        assertNotNull(response);
        assertEquals(senderWalletId, response.getWalletId());

        // Verify locking order
        InOrder inOrder = inOrder(walletRepository);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(receiverWalletId);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(senderWalletId);
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenSenderWalletLockFails() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "sender-lock-fail-001";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        // Sender wallet cannot be locked/retrieved
        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Sender wallet not found",
                exception.getMessage()
        );

        // Sender wallet lookup attempted
        verify(walletRepository)
                .findByIdForUpdate(senderWalletId);

        // Receiver wallet should not be locked
        verify(walletRepository, never())
                .findByIdForUpdate(receiverWalletId);

        // No transaction should be saved
        verifyNoInteractions(walletTransactionRepository);

        verify(transferRequestRecordRepository, never())
                .save(any());
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenReceiverWalletLockFails() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "receiver-lock-fail-001";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();

        senderWallet.setWalletId(senderWalletId);

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        // Sender wallet retrieved successfully
        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        // Receiver wallet missing during locking
        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Receiver wallet not found",
                exception.getMessage()
        );

        // Verify wallet locking order
        InOrder inOrder = inOrder(walletRepository);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(senderWalletId);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(receiverWalletId);

        // Sender balance must remain unchanged
        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(senderWallet.getBalance())
        );

        // No transaction should be saved
        verifyNoInteractions(walletTransactionRepository);

        // No idempotency record should be saved
        verify(transferRequestRecordRepository, never())
                .save(any());
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenReceiverWalletMissingDuringReverseLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 20L;
        Long receiverWalletId = 10L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "reverse-lock-fail-001";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        // Receiver wallet (ID 10) is missing
        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Receiver wallet not found",
                exception.getMessage()
        );

        // Receiver wallet should be locked first
        verify(walletRepository)
                .findByIdForUpdate(receiverWalletId);

        // Sender wallet should never be locked
        verify(walletRepository, never())
                .findByIdForUpdate(senderWalletId);

        // No transaction should be created
        verifyNoInteractions(walletTransactionRepository);

        // No idempotency record should be saved
        verify(transferRequestRecordRepository, never())
                .save(any());
    }


    @Test
    void transferMoney_shouldThrowExceptionWhenSenderWalletMissingDuringReverseLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 20L;
        Long receiverWalletId = 10L;

        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");
        String idempotencyKey = "reverse-sender-lock-fail-001";

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();

        receiverWallet.setWalletId(receiverWalletId);

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        // Receiver wallet successfully retrieved first
        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        // Sender wallet missing
        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.empty());

        // Act + Assert
        WalletNotFoundException exception = assertThrows(
                WalletNotFoundException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Sender wallet not found",
                exception.getMessage()
        );

        // Verify correct locking order
        InOrder inOrder = inOrder(walletRepository);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(receiverWalletId);

        inOrder.verify(walletRepository)
                .findByIdForUpdate(senderWalletId);

        // Receiver balance must remain unchanged
        assertEquals(
                0,
                new BigDecimal("200.00")
                        .compareTo(receiverWallet.getBalance())
        );

        // No transaction should be saved
        verifyNoInteractions(walletTransactionRepository);

        // No idempotency record should be saved
        verify(transferRequestRecordRepository, never())
                .save(any());
    }


    @Test
    void transferMoney_shouldRejectDifferentTransferDetailsAfterLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        String idempotencyKey = "transfer-123";

        BigDecimal amount = new BigDecimal("500.00");

        User receiver = User.builder()
                .email(receiverEmail)
                .build();
        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();
        senderWallet.setWalletId(senderWalletId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();
        receiverWallet.setWalletId(receiverWalletId);

        TransferRequestRecord previousRequest =
                TransferRequestRecord.builder()
                        .receiverEmail(receiverEmail)
                        .amount(new BigDecimal("700.00"))
                        .build();

        // Receiver exists
        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        // Wallet IDs exist
        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        // Wallet locking succeeds
        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        // First lookup: no record
        // Second lookup: existing record with different amount
        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(previousRequest)
                );

        // Act + Assert
        InvalidTransferException exception = assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Idempotency-Key was already used with different transfer details",
                exception.getMessage()
        );

        // Verify both idempotency lookups
        verify(transferRequestRecordRepository, times(2))
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        // Verify wallet locks
        verify(walletRepository).findByIdForUpdate(senderWalletId);
        verify(walletRepository).findByIdForUpdate(receiverWalletId);

        // Balances must remain unchanged
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("200.00").compareTo(receiverWallet.getBalance())
        );

        // No new transaction or idempotency record
        verifyNoInteractions(walletTransactionRepository);

        verify(transferRequestRecordRepository, never()).save(any());
    }


    @Test
    void transferMoney_shouldRejectDifferentReceiverAfterLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        String idempotencyKey = "transfer-different-receiver-001";

        BigDecimal amount = new BigDecimal("500.00");

        User receiver = User.builder()
                .email(receiverEmail)
                .build();

        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();

        senderWallet.setWalletId(senderWalletId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();

        receiverWallet.setWalletId(receiverWalletId);

        // Previous request has a different receiver
        TransferRequestRecord previousRequest =
                TransferRequestRecord.builder()
                        .receiverEmail("another@gmail.com")
                        .amount(new BigDecimal("500.00"))
                        .build();

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        // First check: no record
        // Second check: same key with different receiver
        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(previousRequest)
                );

        // Act + Assert
        InvalidTransferException exception = assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        assertEquals(
                "Idempotency-Key was already used with different transfer details",
                exception.getMessage()
        );

        // Verify both idempotency checks
        verify(transferRequestRecordRepository, times(2))
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        // Verify wallet locks
        verify(walletRepository)
                .findByIdForUpdate(senderWalletId);

        verify(walletRepository)
                .findByIdForUpdate(receiverWalletId);

        // Balances must remain unchanged
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("200.00").compareTo(receiverWallet.getBalance())
        );

        // No new transaction should be saved
        verifyNoInteractions(walletTransactionRepository);

        // No duplicate idempotency record
        verify(transferRequestRecordRepository, never())
                .save(any());
    }


    @Test
    void transferMoney_shouldReturnExistingResponseAfterLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        String idempotencyKey = "transfer-duplicate-001";

        BigDecimal amount = new BigDecimal("500.00");

        User receiver = User.builder()
                .email(receiverEmail)
                .build();
        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();
        senderWallet.setWalletId(senderWalletId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();
        receiverWallet.setWalletId(receiverWalletId);

        // Previously completed transfer's saved response
        TransferRequestRecord previousRequest =
                TransferRequestRecord.builder()
                        .receiverEmail(receiverEmail)
                        .amount(new BigDecimal("500.00"))
                        .responseWalletId(senderWalletId)
                        .responseBalance(new BigDecimal("500.00"))
                        .responseCurrency("INR")
                        .responseActive(true)
                        .build();

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        // First lookup: empty
        // Second lookup: existing completed request
        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(previousRequest)
                );

        // Act
        WalletResponse response = walletService.transferMoney(
                senderId,
                receiverEmail,
                amount,
                idempotencyKey
        );

        // Assert: response comes from previous request
        assertEquals(senderWalletId, response.getWalletId());

        assertEquals(
                0,
                new BigDecimal("500.00").compareTo(response.getBalance())
        );

        assertEquals("INR", response.getCurrency());
        assertEquals(true, response.getIsActive());

        // Both idempotency checks executed
        verify(transferRequestRecordRepository, times(2))
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        // Wallet locks acquired
        verify(walletRepository).findByIdForUpdate(senderWalletId);
        verify(walletRepository).findByIdForUpdate(receiverWalletId);

        // No second debit or credit
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("200.00").compareTo(receiverWallet.getBalance())
        );

        // No new wallet balance persisted
        verify(walletRepository, never()).save(any());

        // No new transaction persisted
        verifyNoInteractions(walletTransactionRepository);

        // No duplicate idempotency record
        verify(transferRequestRecordRepository, never()).save(any());
    }


    @Test
    void transferMoney_shouldReturnExistingResponseWhenReceiverEmailCaseDiffersAfterLocking() {

        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;

        Long senderWalletId = 10L;
        Long receiverWalletId = 20L;

        String receiverEmail = "receiver@gmail.com";
        String idempotencyKey = "transfer-case-001";

        BigDecimal amount = new BigDecimal("500.00");

        User receiver = User.builder()
                .email(receiverEmail)
                .build();
        receiver.setUserId(receiverId);

        Wallet senderWallet = Wallet.builder()
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .isActive(true)
                .build();
        senderWallet.setWalletId(senderWalletId);

        Wallet receiverWallet = Wallet.builder()
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .isActive(true)
                .build();
        receiverWallet.setWalletId(receiverWalletId);

        TransferRequestRecord previousRequest =
                TransferRequestRecord.builder()
                        .receiverEmail("RECEIVER@GMAIL.COM")
                        .amount(new BigDecimal("500.00"))
                        .responseWalletId(senderWalletId)
                        .responseBalance(new BigDecimal("500.00"))
                        .responseCurrency("INR")
                        .responseActive(true)
                        .build();

        when(userRepository.findByEmail(receiverEmail))
                .thenReturn(Optional.of(receiver));

        when(walletRepository.findWalletIdByUserUserId(senderId))
                .thenReturn(Optional.of(senderWalletId));

        when(walletRepository.findWalletIdByUserUserId(receiverId))
                .thenReturn(Optional.of(receiverWalletId));

        when(walletRepository.findByIdForUpdate(senderWalletId))
                .thenReturn(Optional.of(senderWallet));

        when(walletRepository.findByIdForUpdate(receiverWalletId))
                .thenReturn(Optional.of(receiverWallet));

        // First lookup: no record
        // Second lookup: same request, email case differs
        when(transferRequestRecordRepository
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(previousRequest)
                );

        // Act
        WalletResponse response = walletService.transferMoney(
                senderId,
                receiverEmail,
                amount,
                idempotencyKey
        );

        // Assert: existing response returned
        assertEquals(senderWalletId, response.getWalletId());

        assertEquals(
                0,
                new BigDecimal("500.00").compareTo(response.getBalance())
        );

        assertEquals("INR", response.getCurrency());
        assertEquals(true, response.getIsActive());

        verify(transferRequestRecordRepository, times(2))
                .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        // No balance changes
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(senderWallet.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("200.00").compareTo(receiverWallet.getBalance())
        );

        // No duplicate transfer
        verify(walletRepository, never()).save(any());
        verifyNoInteractions(walletTransactionRepository);

        verify(transferRequestRecordRepository, never()).save(any());
    }


    @Test
    void transferMoney_shouldRejectBlankIdempotencyKey() {

        // Arrange
        Long senderId = 1L;
        String receiverEmail = "receiver@gmail.com";
        BigDecimal amount = new BigDecimal("500.00");

        String idempotencyKey = "   ";

        // Act
        InvalidTransferException exception = assertThrows(
                InvalidTransferException.class,
                () -> walletService.transferMoney(
                        senderId,
                        receiverEmail,
                        amount,
                        idempotencyKey
                )
        );

        // Assert
        assertEquals(
                "Idempotency-Key header is required",
                exception.getMessage()
        );

        // Validation should fail before database access
        verifyNoInteractions(
                userRepository,
                walletRepository,
                transferRequestRecordRepository,
                walletTransactionRepository
        );
    }


}
