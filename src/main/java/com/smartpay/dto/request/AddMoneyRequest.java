package com.smartpay.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddMoneyRequest {

    @NotNull
    @DecimalMin(value = "1.00")
    private BigDecimal amount;
}
