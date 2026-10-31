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
import com.smartpay.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final TransferRequestRecordRepository transferRequestRecordRepository;

    @Override
    public WalletResponse getWallet(Long userId) {

        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .isActive(wallet.getIsActive())
                .build();
    }

    @Override
    @Transactional
    public WalletResponse addMoney(Long userId, BigDecimal amount) {
//        Wallet wallet = walletRepository.findByUserUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        Long walletId = walletRepository
                .findWalletIdByUserUserId(userId)
                        .orElseThrow(()->
                                new WalletNotFoundException("Wallet not found"));

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                        .orElseThrow(()->
                                new WalletNotFoundException("Wallet not found"));

        wallet.setBalance(wallet.getBalance().add(amount));

//        BigDecimal newBalance =  wallet.getBalance().add(amount);

//        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount)
                .transactionType("ADD_MONEY")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        walletTransactionRepository.save(transaction);

        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .isActive(wallet.getIsActive())
                .build();
    }

    @Override
    public Page<TransactionResponse> getTransactions(Long userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserUserId(userId).orElseThrow(
                ()-> new RuntimeException("Wallet not found")
        );

        return walletTransactionRepository.findByWalletWalletId(wallet.getWalletId(), pageable)
                .map(transaction -> TransactionResponse.builder()
                        .transactionId(transaction.getTransactionId())
                        .amount(transaction.getAmount())
                        .transactionType(transaction.getTransactionType())
                        .status(transaction.getStatus())
                        .createdAt(transaction.getCreatedAt())
                        .build());
    }

    @Override
    @Transactional
    public WalletResponse transferMoney(
            Long senderId,
            String receiverEmail,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException(
                    "Transfer amount must be greater than zero"
            );
        }

        // 2. Validate idempotency key
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidTransferException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > 50) {
            throw new InvalidTransferException(
                    "Idempotency-Key must not exceed 50 characters"
            );
        }

        // 3. Find receiver
        User receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() ->
                        new UserNotFoundException("Receiver not found"));

        // 4. Prevent self-transfer
        if (senderId.equals(receiver.getUserId())) {
            throw new InvalidTransferException(
                    "Cannot transfer money to yourself"
            );
        }

        // 5. Find wallet IDs
        Long senderWalletId = walletRepository
                .findWalletIdByUserUserId(senderId)
                .orElseThrow(() ->
                        new WalletNotFoundException("Sender wallet not found"));

        Long receiverWalletId = walletRepository
                .findWalletIdByUserUserId(receiver.getUserId())
                .orElseThrow(() ->
                        new WalletNotFoundException("Receiver wallet not found"));

//        if(idempotencyKey == null || idempotencyKey.isBlank()){
//            throw new InvalidTransferException("Idempotency-Key header is required");
//        }
//
//        if (idempotencyKey.length() > 50){
//            throw new InvalidTransferException("Idempotency-Key must not exceed 50 characters");
//        }

        Optional<TransferRequestRecord> existingRequest = transferRequestRecordRepository.findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        if(existingRequest.isPresent()){
            TransferRequestRecord previousRequest = existingRequest.get();

            if(!previousRequest.getReceiverEmail().equalsIgnoreCase(receiverEmail) || previousRequest.getAmount().compareTo(amount) !=0){
                throw new InvalidTransferException("Idempotency-Key was already used with different transfer details");
            }

            return WalletResponse.builder()
                    .walletId(previousRequest.getResponseWalletId())
                    .balance(previousRequest.getResponseBalance())
                    .currency(previousRequest.getResponseCurrency())
                    .isActive(previousRequest.getResponseActive())
                    .build();
        }

//        if(senderId.equals(receiver.getUserId())){
//            throw new InvalidTransferException("Cannot transfer money to yourself");
//        }

        Wallet firstWallet;
        Wallet secondWallet;

        if(senderWalletId < receiverWalletId){
            firstWallet = walletRepository
                    .findByIdForUpdate(senderWalletId)
                    .orElseThrow(() ->
                            new WalletNotFoundException("Sender wallet not found"));

            secondWallet = walletRepository
                    .findByIdForUpdate(receiverWalletId)
                    .orElseThrow(()->
                            new WalletNotFoundException("Receiver wallet not found"));
        }else{
            firstWallet = walletRepository
                    .findByIdForUpdate(receiverWalletId)
                    .orElseThrow(()->
                            new WalletNotFoundException("Receiver wallet not found"));

            secondWallet = walletRepository
                    .findByIdForUpdate(senderWalletId)
                    .orElseThrow(()->
                            new WalletNotFoundException("Sender wallet not found"));
        }

        Wallet senderWallet = senderWalletId.equals(firstWallet.getWalletId()) ? firstWallet : secondWallet;

        Wallet receiverWallet = receiverWalletId.equals(firstWallet.getWalletId()) ? firstWallet : secondWallet;

        // Recheck idempotency after acquiring wallet locks
        Optional<TransferRequestRecord> lockedExistingRequest =
                transferRequestRecordRepository
                        .findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);

        if (lockedExistingRequest.isPresent()) {
            TransferRequestRecord previousRequest = lockedExistingRequest.get();

            if (!previousRequest.getReceiverEmail().equalsIgnoreCase(receiverEmail)
                    || previousRequest.getAmount().compareTo(amount) != 0) {
                throw new InvalidTransferException(
                        "Idempotency-Key was already used with different transfer details"
                );
            }

            return WalletResponse.builder()
                    .walletId(previousRequest.getResponseWalletId())
                    .balance(previousRequest.getResponseBalance())
                    .currency(previousRequest.getResponseCurrency())
                    .isActive(previousRequest.getResponseActive())
                    .build();
        }

        if(senderWallet.getBalance().compareTo(amount)<0){
            throw new InsufficientBalanceException("Insufficient wallet balance");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));

        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        String referenceId = UUID.randomUUID().toString();

        TransferRequestRecord requestRecord =
                TransferRequestRecord.builder()
                        .senderId(senderId)
                        .idempotencyKey(idempotencyKey)
                        .receiverEmail(receiverEmail)
                        .amount(amount)
                        .referenceId(referenceId)
                        .responseWalletId(senderWallet.getWalletId())
                        .responseBalance(senderWallet.getBalance())
                        .responseCurrency(senderWallet.getCurrency())
                        .responseActive(senderWallet.getIsActive())
                        .createdAt(LocalDateTime.now())
                        .build();
        transferRequestRecordRepository.save(requestRecord);

        WalletTransaction debitTransaction =
                WalletTransaction.builder()
                        .wallet(senderWallet)
                        .amount(amount)
                        .transactionType("TRANSFER_DEBIT")
                        .status("SUCCESS")
                        .referenceId(referenceId)
                        .createdAt(LocalDateTime.now())
                        .build();

        WalletTransaction creditTransaction =
                WalletTransaction.builder()
                        .wallet(receiverWallet)
                        .amount(amount)
                        .transactionType("TRANSFER_CREDIT")
                        .status("SUCCESS")
                        .referenceId(referenceId)
                        .createdAt(LocalDateTime.now())
                        .build();

        walletTransactionRepository.save(debitTransaction);
        walletTransactionRepository.save(creditTransaction);

        return WalletResponse.builder()
                .walletId(senderWallet.getWalletId())
                .balance(senderWallet.getBalance())
                .currency(senderWallet.getCurrency())
                .isActive(senderWallet.getIsActive())
                .build();
    }
}
