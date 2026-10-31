package com.smartpay.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionResponse {

    private Long transactionId;
    private BigDecimal amount;
    private String transactionType;
    private String status;
    private LocalDateTime createdAt;

}
