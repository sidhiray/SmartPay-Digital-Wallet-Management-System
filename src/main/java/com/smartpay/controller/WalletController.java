package com.smartpay.controller;

import com.smartpay.dto.request.AddMoneyRequest;
import com.smartpay.dto.request.TransferRequest;
import com.smartpay.dto.response.TransactionResponse;
import com.smartpay.dto.response.WalletResponse;
import com.smartpay.security.CustomUserDetails;
import com.smartpay.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    public WalletResponse getBalance(Authentication authentication){

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.getWallet(userDetails.getUserId());

    }

    @PostMapping("/add-money")
    public WalletResponse addMoney(@Valid @RequestBody AddMoneyRequest request, Authentication authentication){

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return  walletService.addMoney(userDetails.getUserId(), request.getAmount());
    }

//    @GetMapping("/transactions")
//    public List<TransactionResponse> getTransactions(Authentication authentication){
//        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
//        return walletService.getTransactions(userDetails.getUserId());
//    }

    @GetMapping("/transactions")
    public Page<TransactionResponse> getTransactions(Authentication authentication, @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable){
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.getTransactions(userDetails.getUserId(), pageable);
    }

    @PostMapping("/transfer")
    public WalletResponse transferMoney(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request, Authentication authentication){
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.transferMoney(userDetails.getUserId(), request.getReceiverEmail(), request.getAmount(), idempotencyKey);
    }

}
