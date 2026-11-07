package com.forma.backend.security;

import com.forma.backend.entity.User;
import com.forma.backend.entity.UserProfile;
import com.forma.backend.exception.DomainExceptions.*;
import com.forma.backend.repository.AiRequestLogRepository;
import com.forma.backend.repository.UserProfileRepository;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthContextResolver {

    public static final String ADMIN_COOKIE_NAME = "forma_admin_token";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AppConfigService appConfigService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final String configuredAdminPassword;
    private final String configuredAdminKey;
    private final Set<UUID> deletedUserIds = ConcurrentHashMap.newKeySet();

    public AuthContextResolver(
        JwtService jwtService,
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        AppConfigService appConfigService,
        AiRequestLogRepository aiRequestLogRepository,
        @Value("${app.admin.password:admin123}") String configuredAdminPassword,
        @Value("${app.admin.key:}") String configuredAdminKey
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.appConfigService = appConfigService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.configuredAdminPassword = configuredAdminPassword;
        this.configuredAdminKey = configuredAdminKey;
    }

    public void markUserDeleted(UUID userId) {
        if (userId != null) {
            deletedUserIds.add(userId);
        }
    }

    public UUID resolveOptionalUserId(String authHeader, String fallbackUserId) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty() && jwtService.validateToken(token)) {
                try {
                    return jwtService.getUserIdFromToken(token);
                } catch (Exception ignored) {
                }
            }
        }
        if (fallbackUserId != null && !fallbackUserId.isBlank()) {
            try {
                return UUID.fromString(fallbackUserId.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public User requireActiveUser(String authHeader, String fallbackUserId) {
        UUID userId = resolveOptionalUserId(authHeader, fallbackUserId);
        if (userId == null) {
            throw new UnauthorizedAccessException("Sessão inválida ou não autenticada.");
        }
        String tokenEmail = extractBearerEmail(authHeader);
        return verifyUserAccountActive(userId, tokenEmail);
    }

    public UUID validateAiAccess(String authHeader, String fallbackUserId) {
        if (appConfigService.isAiBlocked()) {
            throw new AiBlockedException(
                "As análises de Inteligência Artificial foram temporariamente pausadas no painel administrativo.",
                true
            );
        }

        UUID userId = resolveOptionalUserId(authHeader, fallbackUserId);
        if (userId != null) {
            String tokenEmail = extractBearerEmail(authHeader);
            User user = verifyUserAccountActive(userId, tokenEmail);
            if (user.isAiBlocked()) {
                throw new AiBlockedException(
                    "O acesso à Inteligência Artificial está desativado para esta conta.",
                    false
                );
            }
            if (user.getDailyAiLimit() != null && user.getDailyAiLimit() > 0) {
                Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
                long usedToday = aiRequestLogRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay);
                if (usedToday >= user.getDailyAiLimit()) {
                    throw new AiQuotaExceededException(
                        "Limite diário de IA atingido (" + usedToday + "/" + user.getDailyAiLimit() + " análises hoje)."
                    );
                }
            }
        }
        return userId;
    }

    public boolean isAdminAuthenticated(HttpServletRequest request, String adminKey, String cookieToken) {
        String effectiveCookie = cookieToken;
        if ((effectiveCookie == null || effectiveCookie.isBlank()) && request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (ADMIN_COOKIE_NAME.equals(c.getName())) {
                    effectiveCookie = c.getValue();
                    break;
                }
            }
        }
        if (effectiveCookie != null && jwtService.validateAdminToken(effectiveCookie)) {
            return true;
        }

        if (request != null) {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (jwtService.validateAdminToken(token)) {
                    return true;
                }
            }
        }

        String headerKey = adminKey;
        if ((headerKey == null || headerKey.isBlank()) && request != null) {
            headerKey = request.getHeader("X-Admin-Key");
        }
        if (headerKey != null && !headerKey.isBlank()) {
            String trimmed = headerKey.trim();
            boolean matchesKey = configuredAdminKey != null
                && !configuredAdminKey.trim().isEmpty()
                && trimmed.equals(configuredAdminKey.trim());
            boolean matchesPassword = configuredAdminPassword != null
                && !configuredAdminPassword.trim().isEmpty()
                && trimmed.equals(configuredAdminPassword.trim());
            return matchesKey || matchesPassword;
        }

        return false;
    }

    public void requireAdmin(HttpServletRequest request, String adminKey, String cookieToken) {
        if (!isAdminAuthenticated(request, adminKey, cookieToken)) {
            throw new UnauthorizedAccessException("Acesso administrativo não autorizado. Faça login novamente.");
        }
    }

    private String extractBearerEmail(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty() && jwtService.validateToken(token)) {
                return jwtService.getEmailFromToken(token);
            }
        }
        return null;
    }

    private User verifyUserAccountActive(UUID userId, String tokenEmail) {
        if (deletedUserIds.contains(userId)) {
            throw new AccountDeletedException("Conta não encontrada ou removida pelo administrador.");
        }

        User user = userRepository.findById(userId).orElseGet(() -> {
            if (tokenEmail != null && !tokenEmail.isBlank()) {
                var byEmail = userRepository.findByEmail(tokenEmail.trim().toLowerCase());
                if (byEmail.isPresent()) {
                    return byEmail.get();
                }
                // Auto-provisiona a conta do token JWT válido quando o banco do backend foi recém-criado/migrado
                User restored = new User(
                    tokenEmail.trim().toLowerCase(),
                    "Atleta Forma",
                    "EMAIL",
                    null
                );
                restored.setId(userId);
                User saved = userRepository.save(restored);
                if (!userProfileRepository.existsById(saved.getId())) {
                    userProfileRepository.save(new UserProfile(saved.getId()));
                }
                return saved;
            }
            throw new AccountDeletedException("Conta não encontrada ou removida pelo administrador.");
        });

        if (user.isPaused()) {
            String suffix = (user.getPausedReason() != null && !user.getPausedReason().isBlank())
                ? " Motivo: " + user.getPausedReason().trim()
                : "";
            throw new AccountPausedException("Sua conta está temporariamente pausada pelo administrador." + suffix);
        }
        return user;
    }
}
