package com.forma.backend.controller;

import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.User;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AdminUserService;
import com.forma.backend.service.AuthService;
import com.forma.backend.service.MciAccessLogService;
import com.forma.backend.websocket.AdminEventPublisherService;
import com.forma.backend.websocket.UserAccountWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserAccountWebSocketHandler userAccountWebSocketHandler;
    private final MciAccessLogService mciAccessLogService;
    private final String googleClientId;

    public AuthController(
        AuthService authService,
        AdminUserService adminUserService,
        AuthContextResolver authContextResolver,
        UserRepository userRepository,
        AdminEventPublisherService adminEventPublisherService,
        UserAccountWebSocketHandler userAccountWebSocketHandler,
        MciAccessLogService mciAccessLogService,
        @Value("${app.google.client-id:}") String googleClientId
    ) {
        this.authService = authService;
        this.adminUserService = adminUserService;
        this.authContextResolver = authContextResolver;
        this.userRepository = userRepository;
        this.adminEventPublisherService = adminEventPublisherService;
        this.userAccountWebSocketHandler = userAccountWebSocketHandler;
        this.mciAccessLogService = mciAccessLogService;
        this.googleClientId = googleClientId;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        return ResponseEntity.ok(Map.of(
            "googleClientId", googleClientId != null ? googleClientId : ""
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(
        @RequestBody AuthDto.RegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthDto.AuthResponse response = authService.register(request, httpRequest);
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
    public ResponseEntity<AuthDto.AuthResponse> login(
        @RequestBody AuthDto.LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthDto.AuthResponse response = authService.login(request, httpRequest);
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
    public ResponseEntity<AuthDto.AuthResponse> socialLogin(
        @RequestBody AuthDto.SocialLoginRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthDto.AuthResponse response = authService.socialLogin(request, httpRequest);
        adminEventPublisherService.publishUserActivity(
            "SOCIAL_LOGIN",
            response.email(),
            response.name(),
            response.authProvider(),
            userRepository.count()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentSession(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(Map.of(
            "userId", user.getId().toString(),
            "email", user.getEmail(),
            "name", user.getName(),
            "authProvider", user.getAuthProvider(),
            "accountTier", user.getAccountTier() != null ? user.getAccountTier() : "free",
            "aiConsentGranted", user.isAiConsentGranted(),
            "sensitiveHealthConsentGranted", user.getHealthConsentAcceptedAt() != null,
            "termsVersion", user.getTermsAcceptedVersion() != null ? user.getTermsAcceptedVersion() : "",
            "ageConfirmed18Plus", user.isAgeConfirmed18Plus()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        HttpServletRequest httpRequest
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        authService.logoutSession(user, httpRequest);
        userAccountWebSocketHandler.disconnectUser(user.getId());
        return ResponseEntity.ok(Map.of(
            "status", "logged_out",
            "message", "Sessão revogada com segurança."
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
        @RequestBody AuthDto.ForgotPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        authService.requestPasswordReset(request != null ? request.email() : null, httpRequest);
        return ResponseEntity.ok(Map.of(
            "status", "sent",
            "message", "Se o e-mail estiver cadastrado, enviamos o código de 6 dígitos para redefinir sua senha."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.AuthResponse> resetPassword(
        @RequestBody AuthDto.ResetPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthDto.AuthResponse response = authService.resetPasswordWithCode(request, httpRequest);
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
        @RequestBody AuthDto.ChangePasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        authService.changePassword(user.getId(), request, httpRequest);
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "message", "Senha atualizada com sucesso!"
        ));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteOwnAccount(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody(required = false) AuthDto.DeleteAccountRequest body,
        HttpServletRequest httpRequest
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        String appleAuthCode = body != null ? body.authorizationCode() : null;
        String googleToken = body != null ? body.googleToken() : null;
        mciAccessLogService.recordAccessEvent(user.getId().toString(), "ACCOUNT_DELETE", httpRequest);
        adminUserService.deleteUserPermanently(user.getId(), appleAuthCode, googleToken);
        authContextResolver.markUserDeleted(user.getId());
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "message", "Sua conta e todos os seus dados foram excluídos permanentemente."
        ));
    }
}
