package com.forma.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.exception.DomainExceptions.InvalidCredentialsException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SocialAuthVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthVerificationService.class);

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String DEFAULT_APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String DEFAULT_APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final String GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String DEFAULT_GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private volatile String appleTokenUrl = DEFAULT_APPLE_TOKEN_URL;
    private volatile String appleRevokeUrl = DEFAULT_APPLE_REVOKE_URL;
    private volatile String googleRevokeUrl = DEFAULT_GOOGLE_REVOKE_URL;

    public void overrideOAuthEndpointsForTesting(String appleTokenUrl, String appleRevokeUrl, String googleRevokeUrl) {
        this.appleTokenUrl = appleTokenUrl != null ? appleTokenUrl : DEFAULT_APPLE_TOKEN_URL;
        this.appleRevokeUrl = appleRevokeUrl != null ? appleRevokeUrl : DEFAULT_APPLE_REVOKE_URL;
        this.googleRevokeUrl = googleRevokeUrl != null ? googleRevokeUrl : DEFAULT_GOOGLE_REVOKE_URL;
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;
    private final boolean allowDevSocialMock;
    private final String appleBundleId;
    private final String googleClientId;

    private final Map<String, PublicKey> appleKeysCache = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> googleKeysCache = new ConcurrentHashMap<>();
    private Instant lastAppleCacheRefresh = Instant.EPOCH;
    private Instant lastGoogleCacheRefresh = Instant.EPOCH;

    public record VerifiedSocialUser(
        String sub,
        String email,
        String name,
        String provider,
        boolean emailVerified
    ) {
        public VerifiedSocialUser(String sub, String email, String name, String provider) {
            this(sub, email, name, provider, true);
        }
    }

    public SocialAuthVerificationService() {
        this(null, false, "jota.Forma", "");
    }

    @Autowired
    public SocialAuthVerificationService(
        AppConfigService appConfigService,
        @Value("${app.auth.allow-dev-social-mock:false}") boolean allowDevSocialMock,
        @Value("${app.auth.apple-bundle-id:jota.Forma}") String appleBundleId,
        @Value("${app.google.client-id:}") String googleClientId
    ) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.appConfigService = appConfigService;
        this.allowDevSocialMock = allowDevSocialMock;
        this.appleBundleId = (appleBundleId != null && !appleBundleId.isBlank()) ? appleBundleId.trim() : "jota.Forma";
        this.googleClientId = googleClientId != null ? googleClientId.trim() : "";
    }

    public VerifiedSocialUser verifyToken(String provider, String idToken, String rawNonce, String fallbackName) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidCredentialsException("Token de autenticação social ausente.");
        }

        String p = provider != null ? provider.toUpperCase().trim() : "APPLE";

        if ("APPLE".equals(p)) {
            return verifyAppleToken(idToken.trim(), rawNonce, fallbackName);
        } else if ("GOOGLE".equals(p)) {
            return verifyGoogleToken(idToken.trim(), fallbackName);
        } else {
            throw new IllegalArgumentException("Provedor social não suportado: " + provider);
        }
    }

    private VerifiedSocialUser verifyAppleToken(String idToken, String rawNonce, String fallbackName) {
        if (!idToken.contains(".") || idToken.startsWith("apple_identity_token")) {
            if (!allowDevSocialMock) {
                throw new InvalidCredentialsException("Identity Token Apple inválido ou não aceito neste ambiente.");
            }
            log.warn("[Apple Auth] Token simulado aceito exclusivamente em perfil de teste.");
            return new VerifiedSocialUser("apple_sim_test", null, fallbackName, "APPLE", true);
        }

        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new InvalidCredentialsException("Formato inválido do Identity Token da Apple.");
        }

        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String kid = headerNode.path("kid").asText();
            if (kid == null || kid.isBlank()) {
                throw new InvalidCredentialsException("Header do token Apple sem Key ID (kid).");
            }

            PublicKey publicKey = getApplePublicKey(kid);
            if (publicKey == null) {
                refreshAppleKeys();
                publicKey = appleKeysCache.get(kid);
                if (publicKey == null) {
                    throw new InvalidCredentialsException("Chave pública da Apple não encontrada para kid: " + kid);
                }
            }

            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signedContent);

            if (!verifier.verify(signatureBytes)) {
                throw new InvalidCredentialsException("Assinatura criptográfica do token da Apple INVÁLIDA.");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            String iss = payload.path("iss").asText();
            if (!APPLE_ISSUER.equals(iss)) {
                throw new InvalidCredentialsException("Emissor inválido no token Apple.");
            }

            if (!audienceMatches(payload.get("aud"), appleBundleId)) {
                throw new InvalidCredentialsException("Audience inválido no token Apple.");
            }

            long exp = payload.path("exp").asLong(0);
            if (exp <= 0 || Instant.now().getEpochSecond() > exp) {
                throw new InvalidCredentialsException("Token da Apple expirado.");
            }

            if (rawNonce != null && !rawNonce.isBlank()) {
                String tokenNonce = payload.path("nonce").asText("");
                String hashedRawNonce = sha256Hex(rawNonce);
                if (tokenNonce.isBlank() || (!tokenNonce.equalsIgnoreCase(hashedRawNonce) && !tokenNonce.equals(rawNonce))) {
                    throw new InvalidCredentialsException("Nonce criptográfico da Apple inválido.");
                }
            }

            String sub = payload.path("sub").asText();
            if (sub == null || sub.isBlank()) {
                throw new InvalidCredentialsException("Claim 'sub' ausente no token da Apple.");
            }

            String email = payload.has("email") ? payload.path("email").asText() : null;
            boolean emailVerified = !payload.has("email_verified")
                || "true".equalsIgnoreCase(payload.path("email_verified").asText())
                || payload.path("email_verified").asBoolean(false);

            return new VerifiedSocialUser(sub, email, fallbackName, "APPLE", emailVerified);
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Apple Auth] Erro ao validar token: {}", e.getMessage());
            throw new InvalidCredentialsException("Falha na validação criptográfica do token da Apple.");
        }
    }

    private VerifiedSocialUser verifyGoogleToken(String idToken, String fallbackName) {
        if (!idToken.contains(".") || idToken.startsWith("google_identity_token")) {
            if (!allowDevSocialMock) {
                throw new InvalidCredentialsException("ID Token Google inválido ou não aceito neste ambiente.");
            }
            log.warn("[Google Auth] Token simulado aceito exclusivamente em perfil de teste.");
            return new VerifiedSocialUser("google_sim_test", null, fallbackName, "GOOGLE", true);
        }

        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new InvalidCredentialsException("Formato inválido do ID Token do Google.");
        }

        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String kid = headerNode.path("kid").asText();

            PublicKey publicKey = getGooglePublicKey(kid);
            if (publicKey == null) {
                refreshGoogleKeys();
                publicKey = googleKeysCache.get(kid);
                if (publicKey == null) {
                    throw new InvalidCredentialsException("Chave pública do Google não encontrada para kid: " + kid);
                }
            }

            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signedContent);

            if (!verifier.verify(signatureBytes)) {
                throw new InvalidCredentialsException("Assinatura criptográfica do ID Token do Google INVÁLIDA.");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            String iss = payload.path("iss").asText();
            if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
                throw new InvalidCredentialsException("Emissor inválido no token Google.");
            }

            if (!googleClientId.isEmpty() && !audienceMatches(payload.get("aud"), googleClientId)) {
                throw new InvalidCredentialsException("Audience (client_id) inválido no token Google.");
            }

            long exp = payload.path("exp").asLong(0);
            if (exp <= 0 || Instant.now().getEpochSecond() > exp) {
                throw new InvalidCredentialsException("ID Token do Google expirado.");
            }

            String sub = payload.path("sub").asText();
            if (sub == null || sub.isBlank()) {
                throw new InvalidCredentialsException("Claim 'sub' ausente no token do Google.");
            }

            String email = payload.has("email") ? payload.path("email").asText() : null;
            boolean emailVerified = payload.path("email_verified").asBoolean(false)
                || "true".equalsIgnoreCase(payload.path("email_verified").asText());
            if (email != null && !email.isBlank() && !emailVerified) {
                throw new InvalidCredentialsException("E-mail da conta Google não verificado.");
            }
            String name = payload.has("name") ? payload.path("name").asText() : fallbackName;

            return new VerifiedSocialUser(sub, email, name, "GOOGLE", emailVerified);
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Google Auth] Erro ao validar token: {}", e.getMessage());
            throw new InvalidCredentialsException("Falha na validação criptográfica do token do Google.");
        }
    }

    public static boolean audienceMatches(JsonNode audNode, String expectedAudiencesCommaSeparated) {
        if (audNode == null || expectedAudiencesCommaSeparated == null || expectedAudiencesCommaSeparated.isBlank()) {
            return false;
        }
        String[] allowed = expectedAudiencesCommaSeparated.split(",");
        if (audNode.isArray()) {
            for (JsonNode item : audNode) {
                String v = item.asText("");
                for (String a : allowed) {
                    if (!a.isBlank() && a.trim().equals(v)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String audText = audNode.asText("");
        for (String a : allowed) {
            if (!a.isBlank() && a.trim().equals(audText)) {
                return true;
            }
        }
        return false;
    }

    private PublicKey getApplePublicKey(String kid) {
        if (appleKeysCache.containsKey(kid) && Duration.between(lastAppleCacheRefresh, Instant.now()).toHours() < 24) {
            return appleKeysCache.get(kid);
        }
        refreshAppleKeys();
        return appleKeysCache.get(kid);
    }

    private PublicKey getGooglePublicKey(String kid) {
        if (googleKeysCache.containsKey(kid) && Duration.between(lastGoogleCacheRefresh, Instant.now()).toHours() < 24) {
            return googleKeysCache.get(kid);
        }
        refreshGoogleKeys();
        return googleKeysCache.get(kid);
    }

    private synchronized void refreshAppleKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(APPLE_KEYS_URL))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                parseJwksIntoCache(response.body(), appleKeysCache);
                lastAppleCacheRefresh = Instant.now();
            }
        } catch (Exception e) {
            log.warn("[Apple Auth] Não foi possível atualizar chaves públicas da Apple: {}", e.getMessage());
        }
    }

    private synchronized void refreshGoogleKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_CERTS_URL))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                parseJwksIntoCache(response.body(), googleKeysCache);
                lastGoogleCacheRefresh = Instant.now();
            }
        } catch (Exception e) {
            log.warn("[Google Auth] Não foi possível atualizar chaves públicas do Google: {}", e.getMessage());
        }
    }

    private void parseJwksIntoCache(String jwksJson, Map<String, PublicKey> cache) {
        try {
            JsonNode root = objectMapper.readTree(jwksJson);
            JsonNode keys = root.path("keys");
            if (keys.isArray()) {
                for (JsonNode k : keys) {
                    String kid = k.path("kid").asText();
                    String nStr = k.path("n").asText();
                    String eStr = k.path("e").asText();

                    if (!kid.isBlank() && !nStr.isBlank() && !eStr.isBlank()) {
                        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(nStr));
                        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(eStr));
                        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        PublicKey publicKey = keyFactory.generatePublic(keySpec);
                        cache.put(kid, publicKey);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[JWKS] Erro ao parsear JWKS: {}", e.getMessage(), e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return input;
        }
    }

    public String exchangeAppleAuthCodeForRefreshToken(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        String clientSecret = generateAppleClientSecret();
        if (clientSecret == null) {
            return null;
        }

        try {
            String form = "client_id=" + urlEncode(appleBundleId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&code=" + urlEncode(authorizationCode.trim())
                + "&grant_type=authorization_code";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(appleTokenUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(resp.body());
                String refreshToken = json.path("refresh_token").asText("");
                if (!refreshToken.isBlank()) {
                    return refreshToken;
                }
                String accessToken = json.path("access_token").asText("");
                if (!accessToken.isBlank()) {
                    return accessToken;
                }
            } else {
                log.warn("[Apple Auth] Resposta HTTP {} ao trocar authorization_code", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("[Apple Auth] Falha ao trocar authorization_code da Apple: {}", e.getMessage());
        }
        return null;
    }

    public void revokeAppleTokensOnAccountDeletion(String storedRefreshToken, String freshAuthorizationCode) {
        java.util.LinkedHashSet<String> tokensToRevoke = new java.util.LinkedHashSet<>();

        if (freshAuthorizationCode != null && !freshAuthorizationCode.isBlank()) {
            String freshToken = exchangeAppleAuthCodeForRefreshToken(freshAuthorizationCode);
            if (freshToken != null && !freshToken.isBlank()) {
                tokensToRevoke.add(freshToken.trim());
            }
        }
        if (storedRefreshToken != null && !storedRefreshToken.isBlank()) {
            tokensToRevoke.add(storedRefreshToken.trim());
        }

        if (tokensToRevoke.isEmpty()) {
            return;
        }

        String clientSecret = generateAppleClientSecret();
        if (clientSecret == null) {
            return;
        }

        for (String tokenToRevoke : tokensToRevoke) {
            try {
                String form = "client_id=" + urlEncode(appleBundleId)
                    + "&client_secret=" + urlEncode(clientSecret)
                    + "&token=" + urlEncode(tokenToRevoke)
                    + "&token_type_hint=refresh_token";

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(appleRevokeUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("[Apple Auth] Token Sign in with Apple revogado com sucesso (HTTP {}).", resp.statusCode());
                } else {
                    log.warn("[Apple Auth] Apple /auth/revoke retornou HTTP {}", resp.statusCode());
                }
            } catch (Exception e) {
                log.warn("[Apple Auth] Falha de rede ao chamar /auth/revoke da Apple: {}", e.getMessage());
            }
        }
    }

    public void revokeGoogleTokensOnAccountDeletion(String storedGoogleToken, String clientProvidedGoogleToken) {
        java.util.LinkedHashSet<String> tokensToRevoke = new java.util.LinkedHashSet<>();
        if (clientProvidedGoogleToken != null && !clientProvidedGoogleToken.isBlank()) {
            tokensToRevoke.add(clientProvidedGoogleToken.trim());
        }
        if (storedGoogleToken != null && !storedGoogleToken.isBlank()) {
            tokensToRevoke.add(storedGoogleToken.trim());
        }

        if (tokensToRevoke.isEmpty()) {
            return;
        }

        for (String token : tokensToRevoke) {
            try {
                String form = "token=" + urlEncode(token);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(googleRevokeUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("[Google Auth] Token Google OAuth revogado com sucesso (HTTP {}).", resp.statusCode());
                    return;
                } else {
                    log.warn("[Google Auth] Google /revoke retornou HTTP {}", resp.statusCode());
                }
            } catch (Exception e) {
                log.warn("[Google Auth] Falha de rede ao chamar oauth2.googleapis.com/revoke: {}", e.getMessage());
            }
        }
    }

    private String generateAppleClientSecret() {
        if (appConfigService == null) {
            return null;
        }
        String teamId = appConfigService.getAppleTeamId();
        String keyId = appConfigService.getAppleKeyId();
        String privateKeyPem = appConfigService.getApplePrivateKeyPem();

        if (teamId.isBlank() || keyId.isBlank() || privateKeyPem.isBlank()) {
            return null;
        }

        try {
            String normalizedPem = privateKeyPem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
            byte[] pkcs8Bytes = Base64.getDecoder().decode(normalizedPem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            PrivateKey ecPrivateKey = KeyFactory.getInstance("EC").generatePrivate(keySpec);

            Instant now = Instant.now();
            return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .audience().add(APPLE_ISSUER).and()
                .subject(appleBundleId)
                .signWith(ecPrivateKey, Jwts.SIG.ES256)
                .compact();
        } catch (Exception e) {
            log.warn("[Apple Auth] Erro ao assinar client_secret ES256 da Apple: {}", e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
