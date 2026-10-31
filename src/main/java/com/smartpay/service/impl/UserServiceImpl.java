package com.smartpay.service.impl;

import com.smartpay.dto.request.LoginRequest;
import com.smartpay.dto.response.LoginResponse;
import com.smartpay.dto.request.RegisterRequest;
import com.smartpay.dto.response.RegisterResponse;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.security.JwtService;
import com.smartpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${jwt.expiration}")
    private Long expiration;


    @Transactional
    @Override
    public RegisterResponse registerUser(RegisterRequest request){

        if(userRepository.existsByEmail(request.getEmail())){
            throw new IllegalArgumentException("Email already exists");
        }

        if(userRepository.existsByMobile(request.getMobile())){
            throw new RuntimeException("Mobile already exists");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

//        return userRepository.save(user);

        User savedUser = userRepository.save(user);

        Wallet wallet = Wallet.builder()
                .user(savedUser)
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        walletRepository.save(wallet);

        return RegisterResponse.builder()
                .userId(savedUser.getUserId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .mobile(savedUser.getMobile())
                .role(user.getRole())
                .kycStatus(savedUser.getKycStatus())
                .message("User registered successfully")
                .build();

    }

    @Override
    public LoginResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
        ));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()->
                        new RuntimeException("Invalid Email or Password"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw  new RuntimeException("Invalid Email or Password");
        }

//        String token = jwtService.generateToken((UserDetails) authentication.getPrincipal());
        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(expiration)
                .build();
    }
}
