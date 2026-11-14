package com.forma.backend.security;

import com.forma.backend.entity.DeletedAccountTombstoneEntity;
import com.forma.backend.entity.User;
import com.forma.backend.exception.DomainExceptions.*;
import com.forma.backend.repository.AiRequestLogRepository;
import com.forma.backend.repository.DeletedAccountTombstoneRepository;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Component
public class AuthContextResolver {

    public static final String ADMIN_COOKIE_NAME = "forma_admin_token";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DeletedAccountTombstoneRepository tombstoneRepository;
    private final AppConfigService appConfigService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final String configuredAdminPassword;
    private final String configuredAdminKey;

    public AuthContextResolver(
        JwtService jwtService,
        UserRepository userRepository,
        DeletedAccountTombstoneRepository tombstoneRepository,
        AppConfigService appConfigService,
        AiRequestLogRepository aiRequestLogRepository,
        @Value("${app.admin.password:}") String configuredAdminPassword,
        @Value("${app.admin.key:}") String configuredAdminKey
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tombstoneRepository = tombstoneRepository;
        this.appConfigService = appConfigService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.configuredAdminPassword = configuredAdminPassword;
        this.configuredAdminKey = configuredAdminKey;
    }

    public void markUserDeleted(UUID userId) {
        if (userId != null) {
            tombstoneRepository.save(new DeletedAccountTombstoneEntity(userId.toString(), "ACCOUNT_DELETED"));
        }
    }

    public User requireActiveUser(String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null || !jwtService.validateToken(token)) {
            throw new UnauthorizedAccessException("Sessão inválida ou não autenticada.");
        }
        UUID userId;
        try {
            userId = jwtService.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new UnauthorizedAccessException("Sessão inválida ou não autenticada.");
        }
        int tokenVersion = jwtService.extractTokenVersion(token);
        return verifyUserAccountActive(userId, tokenVersion);
    }

    public UUID validateAiAccess(String authHeader) {
        if (appConfigService.isAiBlocked()) {
            throw new AiBlockedException(
                "As análises de Inteligência Artificial foram temporariamente pausadas no painel administrativo.",
                true
            );
        }

        User user = requireActiveUser(authHeader);

        if (!user.isPro()) {
            throw new AiBlockedException(
                "Esta conta está na camada Free (Essencial). Os recursos de Inteligência Artificial são exclusivos da camada Forma Pro.",
                false
            );
        }
        if (user.isAiBlocked()) {
            throw new AiBlockedException(
                "O acesso à Inteligência Artificial está desativado para esta conta.",
                false
            );
        }
        if (!user.isAiConsentGranted()) {
            throw new AiConsentRequiredException(
                "Consentimento específico pendente para envio de dados ao Google Gemini (LGPD Art. 11). Autorize nas opções de Privacidade."
            );
        }
        if (user.getDailyAiLimit() != null && user.getDailyAiLimit() > 0) {
            Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            long usedToday = aiRequestLogRepository.countByUserIdAndCreatedAtAfter(user.getId(), startOfDay);
            if (usedToday >= user.getDailyAiLimit()) {
                throw new AiQuotaExceededException(
                    "Limite diário de IA atingido (" + usedToday + "/" + user.getDailyAiLimit() + " análises hoje)."
                );
            }
        }
        return user.getId();
    }

    public boolean isAdminAuthenticated(HttpServletRequest request, String adminKey, String cookieToken) {
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
            boolean matchesKey = constantTimeSecretMatch(trimmed, configuredAdminKey);
            boolean matchesPassword = constantTimeSecretMatch(trimmed, configuredAdminPassword);
            if (matchesKey || matchesPassword) {
                return true;
            }
        }

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
            if (isMutatingMethod(request) && !passesCookieCsrfCheck(request)) {
                return false;
            }
            return true;
        }

        return false;
    }

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private boolean isMutatingMethod(HttpServletRequest request) {
        if (request == null || request.getMethod() == null) return false;
        return !SAFE_METHODS.contains(request.getMethod().toUpperCase());
    }

    private boolean passesCookieCsrfCheck(HttpServletRequest request) {
        String secFetchSite = request.getHeader("Sec-Fetch-Site");
        if (secFetchSite != null && "cross-site".equalsIgnoreCase(secFetchSite.trim())) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !"null".equalsIgnoreCase(origin.trim())) {
            try {
                URI uri = URI.create(origin.trim());
                String originHost = uri.getHost();
                String serverName = request.getServerName();
                if (originHost == null) return false;
                if (originHost.equalsIgnoreCase(serverName)) return true;
                boolean localDev = ("localhost".equalsIgnoreCase(originHost) || "127.0.0.1".equals(originHost))
                    && ("localhost".equalsIgnoreCase(serverName) || "127.0.0.1".equals(serverName));
                return localDev;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public boolean constantTimeSecretMatch(String provided, String configured) {
        if (provided == null || configured == null) return false;
        String c = configured.trim();
        String p = provided.trim();
        if (c.isEmpty() || p.isEmpty()) return false;
        if (c.startsWith("$2a$") || c.startsWith("$2b$") || c.startsWith("$2y$")) {
            return BCRYPT.matches(p, c);
        }
        return MessageDigest.isEqual(p.getBytes(StandardCharsets.UTF_8), c.getBytes(StandardCharsets.UTF_8));
    }

    public void requireAdmin(HttpServletRequest request, String adminKey, String cookieToken) {
        if (!isAdminAuthenticated(request, adminKey, cookieToken)) {
            throw new UnauthorizedAccessException("Acesso administrativo não autorizado. Faça login novamente.");
        }
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private User verifyUserAccountActive(UUID userId, int tokenVersion) {
        if (tombstoneRepository.existsById(userId.toString())) {
            throw new AccountDeletedException("Conta não encontrada ou removida pelo administrador.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AccountDeletedException("Conta não encontrada ou removida pelo administrador."));

        if (user.getTokenVersion() != tokenVersion) {
            throw new UnauthorizedAccessException("Sessão revogada por segurança. Faça login novamente.");
        }

        if (user.isPaused()) {
            String suffix = (user.getPausedReason() != null && !user.getPausedReason().isBlank())
                ? " Motivo: " + user.getPausedReason().trim()
                : "";
            throw new AccountPausedException("Sua conta está temporariamente pausada pelo administrador." + suffix);
        }
        return user;
    }
}
