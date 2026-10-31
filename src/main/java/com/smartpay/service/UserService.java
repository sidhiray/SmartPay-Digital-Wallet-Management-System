package com.smartpay.service;

import com.smartpay.dto.request.LoginRequest;
import com.smartpay.dto.response.LoginResponse;
import com.smartpay.dto.request.RegisterRequest;
import com.smartpay.dto.response.RegisterResponse;

public interface UserService {

    RegisterResponse registerUser(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
