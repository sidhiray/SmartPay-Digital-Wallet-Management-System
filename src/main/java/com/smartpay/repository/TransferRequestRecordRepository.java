package com.smartpay.repository;

import com.smartpay.dto.request.TransferRequest;
import com.smartpay.entity.TransferRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRequestRecordRepository extends JpaRepository<TransferRequestRecord, Long> {

    Optional<TransferRequestRecord> findBySenderIdAndIdempotencyKey(Long senderId, String idempotencyKey);

}
