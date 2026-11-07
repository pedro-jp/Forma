package com.forma.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.dto.AuthDto;
import com.forma.backend.dto.SyncDto;
import com.forma.backend.entity.User;
import com.forma.backend.entity.UserProfile;
import com.forma.backend.exception.DomainExceptions.AccountAlreadyExistsException;
import com.forma.backend.exception.DomainExceptions.AccountPausedException;
import com.forma.backend.exception.DomainExceptions.InvalidCredentialsException;
import com.forma.backend.repository.UserProfileRepository;
import com.forma.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JwtService jwtService;
    private final SocialAuthVerificationService socialAuthVerificationService;
    private final AppConfigService appConfigService;

    public AuthService(
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        JwtService jwtService,
        SocialAuthVerificationService socialAuthVerificationService,
        AppConfigService appConfigService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.jwtService = jwtService;
        this.socialAuthVerificationService = socialAuthVerificationService;
        this.appConfigService = appConfigService;
    }

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Senha é obrigatória.");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setName(request.name() != null && !request.name().isBlank() ? request.name().trim() : "Usuário");
        user.setPasswordHash(hashPassword(request.password()));
        user.setAuthProvider("EMAIL");
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());

        User savedUser = userRepository.save(user);
        UserProfile profile = userProfileRepository.save(new UserProfile(savedUser.getId()));

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        return toAuthResponse(savedUser, profile, token);
    }

    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new InvalidCredentialsException("Usuário ou senha incorretos."));

        String inputHash = hashPassword(request.password());
        if (!inputHash.equals(user.getPasswordHash())) {
            throw new InvalidCredentialsException("Usuário ou senha incorretos.");
        }
        assertUserNotPaused(user);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(user.getId())
            .orElseGet(() -> userProfileRepository.save(new UserProfile(user.getId())));

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return toAuthResponse(user, profile, token);
    }

    @Transactional
    public AuthDto.AuthResponse socialLogin(AuthDto.SocialLoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados de login social ausentes.");
        }
        String provider = request.provider() != null ? request.provider().toUpperCase() : "EMAIL";
        String resolvedProviderId = request.providerId();
        String resolvedEmail = request.email();
        String resolvedName = request.name();

        if (request.idToken() != null && !request.idToken().isBlank()) {
            try {
                SocialAuthVerificationService.VerifiedSocialUser verified = socialAuthVerificationService.verifyToken(
                    provider,
                    request.idToken(),
                    request.nonce(),
                    request.name()
                );
                resolvedProviderId = verified.sub();
                if (verified.email() != null && !verified.email().isBlank()) {
                    resolvedEmail = verified.email();
                }
                if (verified.name() != null && !verified.name().isBlank()) {
                    resolvedName = verified.name();
                }
            } catch (Exception e) {
                if (request.idToken().contains(".")
                    && !request.idToken().startsWith("apple_identity_token")
                    && !request.idToken().startsWith("google_identity_token")) {
                    throw new IllegalArgumentException("Falha na validação do token social: " + e.getMessage());
                }
            }
        }

        String providerId = (resolvedProviderId != null && !resolvedProviderId.isBlank())
            ? resolvedProviderId
            : (resolvedEmail != null ? resolvedEmail : "social_user");
        String normalizedEmail = (resolvedEmail != null && !resolvedEmail.isBlank())
            ? resolvedEmail.trim().toLowerCase()
            : providerId + "@" + provider.toLowerCase() + ".auth";

        Optional<User> existing = Optional.empty();
        if (!providerId.isBlank()) {
            existing = userRepository.findByAuthProviderAndProviderId(provider, providerId);
        }
        if (existing.isEmpty() && !normalizedEmail.isBlank()) {
            existing = userRepository.findByEmail(normalizedEmail);
        }

        User user;
        if (existing.isPresent()) {
            user = existing.get();
            assertUserNotPaused(user);

            boolean alreadyLinkedThisProvider = provider.equalsIgnoreCase(user.getAuthProvider())
                || (!providerId.isBlank() && providerId.equals(user.getProviderId()));
            if (Boolean.FALSE.equals(request.confirmExisting())) {
                boolean isRegisterAttempt = Boolean.TRUE.equals(request.isRegisterIntent());
                if (isRegisterAttempt || !alreadyLinkedThisProvider) {
                    throw new AccountAlreadyExistsException(
                        "Já existe uma conta cadastrada com o e-mail " + user.getEmail() + ". Deseja entrar nessa conta?",
                        user.getEmail(),
                        user.getName()
                    );
                }
            }

            user.setLastLoginAt(Instant.now());
            if (user.getProviderId() == null || user.getProviderId().isBlank() || Boolean.TRUE.equals(request.confirmExisting())) {
                user.setProviderId(providerId);
            }
            if (resolvedName != null && !resolvedName.isBlank()
                && (user.getName() == null || user.getName().startsWith("Usuário"))) {
                user.setName(resolvedName.trim());
            }
        } else {
            user = new User();
            user.setEmail(normalizedEmail);
            user.setName((resolvedName != null && !resolvedName.isBlank())
                ? resolvedName.trim()
                : (provider.equals("APPLE") ? "Usuário Apple" : "Usuário Google"));
            user.setAuthProvider(provider);
            user.setProviderId(providerId);
            user.setCreatedAt(Instant.now());
            user.setLastLoginAt(Instant.now());
            user = userRepository.save(user);
            userProfileRepository.save(new UserProfile(user.getId()));
        }

        User savedUser = userRepository.save(user);
        UserProfile profile = userProfileRepository.findById(savedUser.getId())
            .orElseGet(() -> userProfileRepository.save(new UserProfile(savedUser.getId())));

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        return toAuthResponse(savedUser, profile, token);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Informe o e-mail cadastrado.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isEmpty()) {
            return;
        }

        User user = existing.get();
        assertUserNotPaused(user);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        user.setResetCodeHash(hashPassword(code));
        user.setResetCodeExpiresAt(Instant.now().plusSeconds(900));
        userRepository.save(user);

        dispatchRecoveryEmailOrLog(normalizedEmail, user.getName(), code);
    }

    @Transactional
    public AuthDto.AuthResponse resetPasswordWithCode(AuthDto.ResetPasswordRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Informe o e-mail da conta.");
        }
        if (request.code() == null || request.code().trim().length() != 6) {
            throw new IllegalArgumentException("Informe o código de 6 dígitos.");
        }
        if (request.newPassword() == null || request.newPassword().trim().length() < 6) {
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 6 caracteres.");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new InvalidCredentialsException("Código inválido ou expirado."));

        assertUserNotPaused(user);

        if (user.getResetCodeHash() == null || user.getResetCodeExpiresAt() == null
            || Instant.now().isAfter(user.getResetCodeExpiresAt())) {
            throw new InvalidCredentialsException("Código expirado. Solicite um novo código.");
        }

        String codeHash = hashPassword(request.code().trim());
        if (!codeHash.equals(user.getResetCodeHash())) {
            throw new InvalidCredentialsException("Código de verificação incorreto.");
        }

        user.setPasswordHash(hashPassword(request.newPassword().trim()));
        user.setResetCodeHash(null);
        user.setResetCodeExpiresAt(null);
        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(savedUser.getId())
            .orElseGet(() -> userProfileRepository.save(new UserProfile(savedUser.getId())));

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        return toAuthResponse(savedUser, profile, token);
    }

    @Transactional
    public void changePassword(UUID userId, AuthDto.ChangePasswordRequest request) {
        if (request == null || request.newPassword() == null || request.newPassword().trim().length() < 6) {
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 6 caracteres.");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidCredentialsException("Sessão inválida."));
        assertUserNotPaused(user);

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            String current = request.currentPassword() != null ? request.currentPassword().trim() : "";
            if (!hashPassword(current).equals(user.getPasswordHash())) {
                throw new InvalidCredentialsException("A senha atual informada está incorreta.");
            }
        }

        user.setPasswordHash(hashPassword(request.newPassword().trim()));
        user.setResetCodeHash(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);
    }

    private void dispatchRecoveryEmailOrLog(String email, String name, String code) {
        String resendKey = appConfigService != null ? appConfigService.getResendApiKey() : "";
        if (resendKey.isBlank()) {
            resendKey = System.getenv("RESEND_API_KEY");
            if (resendKey == null || resendKey.isBlank()) {
                resendKey = System.getProperty("RESEND_API_KEY", "");
            }
        }
        String fromAddress = System.getenv("MAIL_FROM");
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = System.getProperty("MAIL_FROM", "Forma <onboarding@resend.dev>");
        }

        if (!resendKey.isBlank()) {
            try {
                String safeName = (name != null && !name.isBlank()) ? name.trim() : "atleta";
                String htmlEmail = buildStyledRecoveryEmailHtml(safeName, code);
                String plainTextEmail = "Olá, " + safeName + "!\n\n"
                    + "Use o código abaixo no aplicativo Forma para redefinir sua senha:\n\n"
                    + code + "\n\n"
                    + "Este código é válido por 15 minutos.\n"
                    + "Se você não solicitou esta alteração, ignore este e-mail.";
                String jsonBody = new ObjectMapper().writeValueAsString(Map.of(
                    "from", fromAddress,
                    "to", List.of(email.trim()),
                    "subject", "Seu código de acesso Forma: " + code,
                    "text", plainTextEmail,
                    "html", htmlEmail
                ));

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + resendKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("[Auth] E-mail de recuperação enviado via Resend para {} (HTTP {})", email, resp.statusCode());
                    return;
                } else {
                    log.warn("[Auth] Resend retornou HTTP {} para {}: {}", resp.statusCode(), email, resp.body());
                }
            } catch (Exception e) {
                log.warn("[Auth] Falha ao enviar e-mail via Resend ({}), registrando no log local.", e.getMessage());
            }
        }

        log.info("[Auth] Código de recuperação Forma gerado para {}: {} (válido por 15 min)", email, code);
    }

    private String buildStyledRecoveryEmailHtml(String userName, String code) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="dark">
              <meta name="supported-color-schemes" content="dark">
              <style>
                u + .body .gm-screen { background: #000000; mix-blend-mode: screen; }
                u + .body .gm-diff { background: #000000; mix-blend-mode: difference; }
              </style>
            </head>
            <body class="body" style="margin:0;padding:0;background-color:#0B0F12;background-image:linear-gradient(#0B0F12,#0B0F12);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
              <div style="background-color:#0B0F12;background-image:linear-gradient(#0B0F12,#0B0F12);padding:28px 14px;">
                <div style="max-width:440px;margin:0 auto;background-color:#11161A;background-image:linear-gradient(#11161A,#11161A);border-radius:22px;border:1px solid #223038;overflow:hidden;">
                  <div style="height:4px;background-color:#54E1C3;background-image:linear-gradient(90deg,#54E1C3 0%%,#D4F782 100%%);"></div>
                  <div style="padding:28px 26px 26px 26px;text-align:center;">
                    <div style="width:56px;height:56px;margin:0 auto 12px auto;border-radius:16px;background-color:#183C36;background-image:linear-gradient(135deg,#54E1C3 0%%,#D4F782 100%%);line-height:56px;text-align:center;">
                      <span style="font-size:28px;font-weight:900;color:#0B0F12;font-family:-apple-system,BlinkMacSystemFont,sans-serif;">F</span>
                    </div>
                    <div class="gm-screen"><div class="gm-diff" style="color:#FFFFFF;">
                      <div style="font-size:24px;font-weight:800;letter-spacing:-0.4px;margin:0;">Forma</div>
                      <div style="font-size:12px;color:#B0BEC5;margin-top:3px;margin-bottom:22px;">Nutrição &amp; Treinos Inteligentes</div>
                      <div style="text-align:left;font-size:15px;line-height:1.55;margin-bottom:10px;">Olá, <strong>%s</strong>!</div>
                      <div style="text-align:left;font-size:14px;line-height:1.55;color:#CFD8DC;margin-bottom:22px;">Use o código de 6 dígitos abaixo no aplicativo <strong>Forma</strong> para redefinir sua senha:</div>
                    </div></div>
                    <div style="background-color:#183C36;background-image:linear-gradient(135deg,#183C36 0%%,#112A26 100%%);border:1px solid #54E1C3;border-radius:16px;padding:18px 14px;margin-bottom:18px;">
                      <div class="gm-screen"><div class="gm-diff" style="color:#FFFFFF;">
                        <div style="font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1.8px;color:#CFD8DC;margin-bottom:6px;">Código de Verificação</div>
                        <div style="font-size:34px;font-weight:800;letter-spacing:7px;font-family:'SF Mono',Menlo,Consolas,monospace;">%s</div>
                      </div></div>
                    </div>
                    <div class="gm-screen"><div class="gm-diff" style="color:#FFFFFF;">
                      <div style="font-size:12px;line-height:1.5;color:#B0BEC5;margin-bottom:14px;">Válido por <strong>15 minutos</strong>.</div>
                      <div style="font-size:11px;line-height:1.45;color:#90A4AE;">Se você não solicitou esta alteração, basta ignorar esta mensagem.</div>
                    </div></div>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(userName, code);
    }

    public String hashPassword(String password) {
        if (password == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Mantém o salt histórico para compatibilidade integral com contas já registradas
            byte[] hash = digest.digest(("forma_salt_" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao calcular hash de senha", e);
        }
    }

    private void assertUserNotPaused(User user) {
        if (user.isPaused()) {
            String reason = (user.getPausedReason() != null && !user.getPausedReason().isBlank())
                ? ": " + user.getPausedReason()
                : ".";
            throw new AccountPausedException("Sua conta está temporariamente pausada pelo administrador" + reason);
        }
    }

    private AuthDto.AuthResponse toAuthResponse(User user, UserProfile profile, String token) {
        return new AuthDto.AuthResponse(
            token,
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getAuthProvider(),
            SyncDto.ProfileDto.fromEntity(profile)
        );
    }
}
