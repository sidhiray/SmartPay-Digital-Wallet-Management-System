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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get wallet balance", description = "Retrieves the wallet balance of the authenticated user.")
    @GetMapping("/balance")
    public WalletResponse getBalance(Authentication authentication){

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.getWallet(userDetails.getUserId());

    }

    @Operation(summary = "Add money to wallet", description = "Adds the specified amount to the authenticated user's wallet.")
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

    @Operation(summary = "Get transaction history", description = "Retrieves paginated wallet transactions. Default page size is 10, sorted by createdAt in descending order.")
    @GetMapping("/transactions")
    public Page<TransactionResponse> getTransactions(Authentication authentication, @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable){
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.getTransactions(userDetails.getUserId(), pageable);
    }

    @Operation(summary = "Transfer money", description = "Transfers money to another user's wallet. Requires a unique Idempotency-Key header to prevent duplicate processing during retries.")
    @PostMapping("/transfer")
    public WalletResponse transferMoney(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request, Authentication authentication){
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return walletService.transferMoney(userDetails.getUserId(), request.getReceiverEmail(), request.getAmount(), idempotencyKey);
    }

}
