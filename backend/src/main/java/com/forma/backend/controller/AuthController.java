package com.forma.backend.controller;

import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.User;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AdminUserService;
import com.forma.backend.service.AuthService;
import com.forma.backend.websocket.AdminEventPublisherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AdminUserService adminUserService;
    private final AuthContextResolver authContextResolver;
    private final UserRepository userRepository;
    private final AdminEventPublisherService adminEventPublisherService;
    private final String googleClientId;

    public AuthController(
        AuthService authService,
        AdminUserService adminUserService,
        AuthContextResolver authContextResolver,
        UserRepository userRepository,
        AdminEventPublisherService adminEventPublisherService,
        @Value("${app.google.client-id:}") String googleClientId
    ) {
        this.authService = authService;
        this.adminUserService = adminUserService;
        this.authContextResolver = authContextResolver;
        this.userRepository = userRepository;
        this.adminEventPublisherService = adminEventPublisherService;
        this.googleClientId = googleClientId;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        return ResponseEntity.ok(Map.of(
            "googleClientId", googleClientId != null ? googleClientId : ""
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@RequestBody AuthDto.RegisterRequest request) {
        AuthDto.AuthResponse response = authService.register(request);
        adminEventPublisherService.publishUserActivity(
            "REGISTER",
            response.email(),
            response.name(),
            response.authProvider(),
            userRepository.count()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@RequestBody AuthDto.LoginRequest request) {
        AuthDto.AuthResponse response = authService.login(request);
        adminEventPublisherService.publishUserActivity(
            "LOGIN",
            response.email(),
            response.name(),
            response.authProvider(),
            userRepository.count()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social")
    public ResponseEntity<AuthDto.AuthResponse> socialLogin(@RequestBody AuthDto.SocialLoginRequest request) {
        AuthDto.AuthResponse response = authService.socialLogin(request);
        adminEventPublisherService.publishUserActivity(
            "SOCIAL_LOGIN",
            response.email(),
            response.name(),
            response.authProvider(),
            userRepository.count()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody AuthDto.ForgotPasswordRequest request) {
        authService.requestPasswordReset(request != null ? request.email() : null);
        return ResponseEntity.ok(Map.of(
            "status", "sent",
            "message", "Se o e-mail estiver cadastrado, enviamos o código de 6 dígitos para redefinir sua senha."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.AuthResponse> resetPassword(@RequestBody AuthDto.ResetPasswordRequest request) {
        AuthDto.AuthResponse response = authService.resetPasswordWithCode(request);
        adminEventPublisherService.publishUserActivity(
            "PASSWORD_RESET",
            response.email(),
            response.name(),
            response.authProvider(),
            userRepository.count()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody AuthDto.ChangePasswordRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader, fallbackUserId);
        authService.changePassword(user.getId(), request);
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "message", "Senha atualizada com sucesso!"
        ));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteOwnAccount(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody(required = false) AuthDto.DeleteAccountRequest body
    ) {
        User user = authContextResolver.requireActiveUser(authHeader, fallbackUserId);
        String appleAuthCode = body != null ? body.authorizationCode() : null;
        String googleToken = body != null ? body.googleToken() : null;
        adminUserService.deleteUserPermanently(user.getId(), appleAuthCode, googleToken);
        authContextResolver.markUserDeleted(user.getId());
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "message", "Sua conta e todos os seus dados foram excluídos permanentemente."
        ));
    }
}
