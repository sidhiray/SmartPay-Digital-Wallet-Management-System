package com.smartpay.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterResponse {

    private Long userId;

    private String fullName;

    private String email;

    private String mobile;

    private String role;

    private String kycStatus;

    private String message;

}
