package com.forma.backend.dto;

import java.util.UUID;

public class AuthDto {

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(
        String email,
        String password,
        String name,
        Boolean ageConfirmed18Plus,
        Boolean sensitiveHealthConsentGranted,
        Boolean aiConsentGranted,
        String termsVersion
    ) {
        public RegisterRequest(String email, String password, String name) {
            this(email, password, name, null, null, null, null);
        }
    }

    public record ForgotPasswordRequest(String email) {}

    public record ResetPasswordRequest(String email, String code, String newPassword) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record SocialLoginRequest(
        String provider,
        String idToken,
        String email,
        String name,
        String providerId,
        String nonce,
        Boolean isRegisterIntent,
        Boolean confirmExisting,
        String authorizationCode,
        String oauthRefreshToken,
        Boolean ageConfirmed18Plus,
        Boolean sensitiveHealthConsentGranted,
        Boolean aiConsentGranted,
        String termsVersion
    ) {
        public SocialLoginRequest(
            String provider,
            String idToken,
            String email,
            String name,
            String providerId,
            String nonce,
            Boolean isRegisterIntent,
            Boolean confirmExisting,
            String authorizationCode,
            String oauthRefreshToken
        ) {
            this(provider, idToken, email, name, providerId, nonce, isRegisterIntent, confirmExisting, authorizationCode, oauthRefreshToken, null, null, null, null);
        }

        public SocialLoginRequest(
            String provider,
            String idToken,
            String email,
            String name,
            String providerId,
            String nonce,
            Boolean isRegisterIntent,
            Boolean confirmExisting,
            String authorizationCode
        ) {
            this(provider, idToken, email, name, providerId, nonce, isRegisterIntent, confirmExisting, authorizationCode, null, null, null, null, null);
        }

        public SocialLoginRequest(
            String provider,
            String idToken,
            String email,
            String name,
            String providerId,
            String nonce,
            Boolean isRegisterIntent,
            Boolean confirmExisting
        ) {
            this(provider, idToken, email, name, providerId, nonce, isRegisterIntent, confirmExisting, null, null, null, null, null, null);
        }

        public SocialLoginRequest(
            String provider,
            String idToken,
            String email,
            String name,
            String providerId,
            String nonce
        ) {
            this(provider, idToken, email, name, providerId, nonce, false, true, null, null, null, null, null, null);
        }

        public SocialLoginRequest(String provider, String idToken, String email, String name, String providerId) {
            this(provider, idToken, email, name, providerId, null, false, true, null, null, null, null, null, null);
        }
    }

    public record DeleteAccountRequest(String authorizationCode, String googleToken) {
        public DeleteAccountRequest(String authorizationCode) {
            this(authorizationCode, null);
        }
    }

    public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String name,
        String authProvider,
        String accountTier,
        boolean aiConsentGranted,
        boolean healthConsentGranted,
        boolean ageConfirmed18Plus,
        SyncDto.ProfileDto profile
    ) {
        public AuthResponse(
            String token,
            UUID userId,
            String email,
            String name,
            String authProvider,
            String accountTier,
            SyncDto.ProfileDto profile
        ) {
            this(token, userId, email, name, authProvider, accountTier, false, false, false, profile);
        }

        public AuthResponse(
            String token,
            UUID userId,
            String email,
            String name,
            String authProvider,
            SyncDto.ProfileDto profile
        ) {
            this(token, userId, email, name, authProvider, "FREE", false, false, false, profile);
        }
    }
}
