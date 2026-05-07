package com.shopflow.user;

import com.shopflow.user.dto.AuthResponse;
import com.shopflow.user.dto.LoginRequest;
import com.shopflow.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registerAndLogin_shouldReturnToken() {
        RegisterRequest registerReq = new RegisterRequest("testuser", "test@shop.com", "password123");
        ResponseEntity<AuthResponse> registerResp =
                restTemplate.postForEntity("/api/users/register", registerReq, AuthResponse.class);

        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResp.getBody()).isNotNull();
        assertThat(registerResp.getBody().token()).isNotBlank();

        LoginRequest loginReq = new LoginRequest("testuser", "password123");
        ResponseEntity<AuthResponse> loginResp =
                restTemplate.postForEntity("/api/users/login", loginReq, AuthResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody().role()).isEqualTo("USER");
    }

    @Test
    void register_withDuplicateUsername_shouldReturn400() {
        RegisterRequest req = new RegisterRequest("duplicate", "dup@shop.com", "password123");
        restTemplate.postForEntity("/api/users/register", req, AuthResponse.class);

        RegisterRequest dup = new RegisterRequest("duplicate", "other@shop.com", "password123");
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/users/register", dup, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
