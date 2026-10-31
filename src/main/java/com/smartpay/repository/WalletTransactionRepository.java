package com.smartpay.repository;

import com.smartpay.dto.response.TransactionResponse;
import com.smartpay.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

//    List<WalletTransaction> findByWalletWalletIdOrderByCreatedAtDesc(Long walletId);

    Page<WalletTransaction> findByWalletWalletId(Long walletId, Pageable pageable);
}
