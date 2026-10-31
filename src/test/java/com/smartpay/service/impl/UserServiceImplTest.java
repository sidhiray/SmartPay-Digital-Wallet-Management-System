
package com.smartpay.service.impl;

import com.smartpay.dto.request.RegisterRequest;
import com.smartpay.dto.response.RegisterResponse;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smartpay.dto.request.LoginRequest;
import com.smartpay.dto.response.LoginResponse;

import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setFullName("Sajid Shaikh");
        request.setEmail("sajid@gmail.com");
        request.setMobile("9876543210");
        request.setPassword("Password@123");
    }

    @Test
    void registerUser_shouldCreateUserAndWalletSuccessfully() {

        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);

        when(userRepository.existsByMobile(request.getMobile()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(1L);
                    return user;
                });

        RegisterResponse response = userService.registerUser(request);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("Sajid Shaikh", response.getFullName());
        assertEquals("sajid@gmail.com", response.getEmail());
        assertEquals("User registered successfully", response.getMessage());

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("encodedPassword", savedUser.getPassword());

        ArgumentCaptor<Wallet> walletCaptor =
                ArgumentCaptor.forClass(Wallet.class);

        verify(walletRepository).save(walletCaptor.capture());

        Wallet savedWallet = walletCaptor.getValue();

        assertEquals(1L, savedWallet.getUser().getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(savedWallet.getBalance()));
        assertEquals("INR", savedWallet.getCurrency());
        assertTrue(savedWallet.getIsActive());
        assertNotNull(savedWallet.getCreatedAt());

        verify(passwordEncoder).encode("Password@123");
        verify(userRepository).existsByEmail("sajid@gmail.com");
        verify(userRepository).existsByMobile("9876543210");
    }


    @Test
    void registerUser_shouldRejectDuplicateEmail() {

        // Arrange: Email already registered hai
        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(true);

        // Act + Assert: Registration reject hona chahiye
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(request)
        );

        assertEquals("Email already exists", exception.getMessage());

        // Email check execute hona chahiye
        verify(userRepository).existsByEmail("sajid@gmail.com");

        // Registration yahin stop ho jana chahiye
        verify(userRepository, never()).existsByMobile(anyString());

        // No user or wallet should be created
        verify(userRepository, never()).save(any(User.class));
        verify(walletRepository, never()).save(any(Wallet.class));

        // Password encoding bhi nahi hona chahiye
        verifyNoInteractions(passwordEncoder);
    }


    @Test
    void registerUser_shouldRejectDuplicateMobile() {

        // Arrange: Email available hai, lekin mobile already registered hai
        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);

        when(userRepository.existsByMobile(request.getMobile()))
                .thenReturn(true);

        // Act + Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.registerUser(request)
        );

        assertEquals("Mobile already exists", exception.getMessage());

        // Verify email and mobile checks
        verify(userRepository).existsByEmail("sajid@gmail.com");
        verify(userRepository).existsByMobile("9876543210");

        // User save nahi hona chahiye
        verify(userRepository, never()).save(any(User.class));

        // Wallet create nahi hona chahiye
        verify(walletRepository, never()).save(any(Wallet.class));

        // Password encode nahi hona chahiye
        verifyNoInteractions(passwordEncoder);
    }


    @Test
    void login_shouldReturnJwtForValidCredentials() {

        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sajid@gmail.com");
        loginRequest.setPassword("Password@123");

        User user = User.builder()
                .fullName("Sajid Shaikh")
                .email("sajid@gmail.com")
                .password("encodedPassword")
                .build();

        user.setUserId(1L);

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenReturn(mock(Authentication.class));

        when(userRepository.findByEmail("sajid@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "Password@123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("mock.jwt.token");

        ReflectionTestUtils.setField(
                userService,
                "expiration",
                3600000L
        );

        // Act
        LoginResponse response = userService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("Sajid Shaikh", response.getFullName());
        assertEquals("sajid@gmail.com", response.getEmail());
        assertEquals("mock.jwt.token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600000L, response.getExpiresIn());

        // Verify authentication
        ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
                ArgumentCaptor.forClass(
                        UsernamePasswordAuthenticationToken.class
                );

        verify(authenticationManager).authenticate(authCaptor.capture());

        assertEquals(
                "sajid@gmail.com",
                authCaptor.getValue().getPrincipal()
        );

        assertEquals(
                "Password@123",
                authCaptor.getValue().getCredentials()
        );

        verify(userRepository).findByEmail("sajid@gmail.com");

        verify(passwordEncoder).matches(
                "Password@123",
                "encodedPassword"
        );

        verify(jwtService).generateToken(user);
    }


    @Test
    void login_shouldRejectInvalidPassword() {

        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sajid@gmail.com");
        loginRequest.setPassword("WrongPassword@123");

        User user = User.builder()
                .email("sajid@gmail.com")
                .password("encodedCorrectPassword")
                .build();

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenReturn(mock(Authentication.class));

        when(userRepository.findByEmail("sajid@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongPassword@123",
                "encodedCorrectPassword"
        )).thenReturn(false);

        // Act + Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.login(loginRequest)
        );

        assertEquals(
                "Invalid Email or Password",
                exception.getMessage()
        );

        // Verify authentication was attempted
        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );

        // Verify password was checked
        verify(passwordEncoder).matches(
                "WrongPassword@123",
                "encodedCorrectPassword"
        );

        // JWT must not be generated
        verifyNoInteractions(jwtService);
    }


    @Test
    void login_shouldRejectInvalidEmail() {

        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("unknown@gmail.com");
        loginRequest.setPassword("Password@123");

        // Authentication mocked successful to test repository branch
        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenReturn(mock(Authentication.class));

        // Email does not exist in database
        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        // Act + Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.login(loginRequest)
        );

        assertEquals(
                "Invalid Email or Password",
                exception.getMessage()
        );

        // Authentication should be attempted
        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );

        // User lookup should happen
        verify(userRepository).findByEmail("unknown@gmail.com");

        // Password should not be checked again
        verifyNoInteractions(passwordEncoder);

        // JWT must not be generated
        verifyNoInteractions(jwtService);
    }


    @Test
    void login_shouldRejectWhenAuthenticationFails() {

        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sajid@gmail.com");
        loginRequest.setPassword("WrongPassword@123");

        // Simulate Spring Security authentication failure
        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        // Act + Assert
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> userService.login(loginRequest)
        );

        assertEquals("Bad credentials", exception.getMessage());

        // Verify authentication was attempted
        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );

        // Database lookup must not happen
        verifyNoInteractions(userRepository);

        // Password should not be checked again
        verifyNoInteractions(passwordEncoder);

        // JWT must not be generated
        verifyNoInteractions(jwtService);
    }


}
