package com.shopflow.user.service;

import com.shopflow.user.dto.AuthResponse;
import com.shopflow.user.dto.LoginRequest;
import com.shopflow.user.dto.RegisterRequest;
import com.shopflow.user.entity.User;
import com.shopflow.user.mapper.UserMapper;
import com.shopflow.user.repository.UserRepository;
import com.shopflow.user.security.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserService userService;

    private User buildUser() {
        User u = new User();
        u.setUsername("john");
        u.setEmail("john@test.com");
        u.setPassword("encoded_password");
        return u;
    }

    @Test
    void register_savesUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("john", "john@test.com", "password123");
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        when(userRepository.save(any())).thenReturn(buildUser());
        when(jwtUtil.generateToken(any())).thenReturn("jwt_token");

        AuthResponse response = userService.register(request);

        assertThat(response.token()).isEqualTo("jwt_token");
        assertThat(response.username()).isEqualTo("john");
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsException_whenUsernameExists() {
        RegisterRequest request = new RegisterRequest("john", "john@test.com", "password123");
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsException_whenEmailExists() {
        RegisterRequest request = new RegisterRequest("john", "john@test.com", "password123");
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_returnsToken_whenCredentialsValid() {
        LoginRequest request = new LoginRequest("john", "password123");
        User user = buildUser();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("jwt_token");

        AuthResponse response = userService.login(request);

        assertThat(response.token()).isEqualTo("jwt_token");
        assertThat(response.username()).isEqualTo("john");
    }

    @Test
    void login_throwsException_whenUserNotFound() {
        LoginRequest request = new LoginRequest("unknown", "password123");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_throwsException_whenPasswordWrong() {
        LoginRequest request = new LoginRequest("john", "wrong_password");
        User user = buildUser();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_password", "encoded_password")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void getUserById_throwsException_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}