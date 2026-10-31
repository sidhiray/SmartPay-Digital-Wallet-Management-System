package com.smartpay.service;

import com.smartpay.dto.response.TransactionResponse;
import com.smartpay.dto.response.WalletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {

    WalletResponse getWallet(Long userId);
    WalletResponse addMoney(Long userId, BigDecimal amount);
//    List<TransactionResponse> getTransactions(Long userId);
    Page<TransactionResponse> getTransactions(Long userId, Pageable pageable);
    WalletResponse transferMoney(Long senderId, String receiverEmail, BigDecimal amount, String idempotencyKey);
}
