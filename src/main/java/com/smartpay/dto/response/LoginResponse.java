package com.smartpay.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
public class LoginResponse {

    private Long userId;

    private String fullName;

    private String email;

    private String role;

    private String accessToken;

    private String tokenType;

    private long expiresIn;
}
