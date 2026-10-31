package com.smartpay.controller;

import com.smartpay.dto.request.LoginRequest;
import com.smartpay.dto.response.LoginResponse;
import com.smartpay.dto.request.RegisterRequest;
import com.smartpay.dto.response.RegisterResponse;
import com.smartpay.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserServiceImpl userService;

    @PostMapping("/register")
    public RegisterResponse getUser(@RequestBody RegisterRequest request){
        return userService.registerUser(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){
        return userService.login(request);
    }
}
