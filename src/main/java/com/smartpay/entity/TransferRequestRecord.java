package com.smartpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_request_records", uniqueConstraints = {@UniqueConstraint(name = "uk_transfer_idempotency", columnNames = {"sender_id", "idempotency_key"})})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "response_wallet_id")
    private Long responseWalletId;

    @Column(name = "response_balance", precision = 19, scale = 2)
    private BigDecimal responseBalance;

    @Column(name = "response_currency", length = 3)
    private String responseCurrency;

    @Column(name = "response_active")
    private Boolean responseActive;

    @Column(name="idempotency_key", nullable = false, length = 50)
    private String idempotencyKey;

    @Column(name = "receiver_email", nullable = false)
    private String receiverEmail;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_id", nullable = false, unique = true)
    private String referenceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
