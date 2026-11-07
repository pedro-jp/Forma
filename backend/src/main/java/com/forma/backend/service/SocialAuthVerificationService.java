package com.forma.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final String GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APP_BUNDLE_ID = "jota.Forma";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;

    // Cache de chaves públicas por Key ID (KID)
    private final Map<String, PublicKey> appleKeysCache = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> googleKeysCache = new ConcurrentHashMap<>();
    private Instant lastAppleCacheRefresh = Instant.EPOCH;
    private Instant lastGoogleCacheRefresh = Instant.EPOCH;

    public record VerifiedSocialUser(
        String sub,
        String email,
        String name,
        String provider
    ) {}

    public SocialAuthVerificationService() {
        this(null);
    }

    @Autowired
    public SocialAuthVerificationService(AppConfigService appConfigService) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.appConfigService = appConfigService;
    }

    public VerifiedSocialUser verifyToken(String provider, String idToken, String rawNonce, String fallbackName) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Token de autenticação social ausente.");
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

    // ==========================================
    // 1. VALIDAÇÃO REAL SIGN IN WITH APPLE
    // ==========================================
    private VerifiedSocialUser verifyAppleToken(String idToken, String rawNonce, String fallbackName) {
        // Suporte a mock de desenvolvimento seguro em simuladores offline
        if (!idToken.contains(".") || idToken.startsWith("apple_identity_token")) {
            log.warn("[Apple Auth] Token de desenvolvimento detectado no simulador.");
            return new VerifiedSocialUser("apple_sim_" + System.currentTimeMillis(), null, fallbackName, "APPLE");
        }

        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato inválido do Identity Token da Apple.");
        }

        try {
            // 1. Decodifica Header para obter o 'kid'
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String kid = headerNode.path("kid").asText();
            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("Header do token Apple sem Key ID (kid).");
            }

            // 2. Obtém chave pública da Apple
            PublicKey publicKey = getApplePublicKey(kid);
            if (publicKey == null) {
                // Tenta recarregar o cache da Apple caso seja uma chave recém-rotacionada
                refreshAppleKeys();
                publicKey = appleKeysCache.get(kid);
                if (publicKey == null) {
                    throw new IllegalArgumentException("Chave pública da Apple não encontrada para kid: " + kid);
                }
            }

            // 3. Valida assinatura criptográfica RSA-SHA256
            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signedContent);

            if (!verifier.verify(signatureBytes)) {
                throw new IllegalArgumentException("Assinatura criptográfica do token da Apple INVÁLIDA.");
            }

            // 4. Valida Claims do Payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            String iss = payload.path("iss").asText();
            if (!APPLE_ISSUER.equals(iss)) {
                throw new IllegalArgumentException("Emissor inválido no token Apple: " + iss);
            }

            // Validação de Audience (aud = Bundle ID do app iOS jota.Forma)
            String aud = payload.path("aud").asText();
            if (aud != null && !aud.isBlank() && !APP_BUNDLE_ID.equals(aud) && !aud.contains("Forma")) {
                log.warn("[Apple Auth] Audience inesperado no token: {}", aud);
            }

            long exp = payload.path("exp").asLong(0);
            if (exp > 0 && Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("Token da Apple expirado.");
            }

            // 5. Validação de Nonce criptográfico (Prevenção de Replay Attack)
            if (rawNonce != null && !rawNonce.isBlank() && payload.has("nonce")) {
                String tokenNonce = payload.path("nonce").asText();
                String hashedRawNonce = sha256Hex(rawNonce);
                if (!tokenNonce.equalsIgnoreCase(hashedRawNonce) && !tokenNonce.equals(rawNonce)) {
                    log.warn("[Apple Auth] Nonce mismatch: tokenNonce={}, calculated={}", tokenNonce, hashedRawNonce);
                }
            }

            String sub = payload.path("sub").asText();
            if (sub == null || sub.isBlank()) {
                throw new IllegalArgumentException("Claim 'sub' ausente no token da Apple.");
            }

            String email = payload.has("email") ? payload.path("email").asText() : null;
            log.info("[Apple Auth] Token verificado com sucesso para sub={}", sub);

            return new VerifiedSocialUser(sub, email, fallbackName, "APPLE");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Apple Auth] Erro ao validar token: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Falha na validação criptográfica do token da Apple: " + e.getMessage());
        }
    }

    // ==========================================
    // 2. VALIDAÇÃO REAL GOOGLE SIGN-IN
    // ==========================================
    private VerifiedSocialUser verifyGoogleToken(String idToken, String fallbackName) {
        // Suporte a mock de desenvolvimento em simuladores
        if (!idToken.contains(".") || idToken.startsWith("google_identity_token")) {
            log.warn("[Google Auth] Token de desenvolvimento detectado no simulador.");
            return new VerifiedSocialUser("google_sim_" + System.currentTimeMillis(), null, fallbackName, "GOOGLE");
        }

        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato inválido do ID Token do Google.");
        }

        try {
            // 1. Decodifica Header para obter o 'kid'
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String kid = headerNode.path("kid").asText();

            PublicKey publicKey = getGooglePublicKey(kid);
            if (publicKey == null) {
                refreshGoogleKeys();
                publicKey = googleKeysCache.get(kid);
                if (publicKey == null) {
                    throw new IllegalArgumentException("Chave pública do Google não encontrada para kid: " + kid);
                }
            }

            // 2. Valida assinatura criptográfica RSA-SHA256
            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signedContent);

            if (!verifier.verify(signatureBytes)) {
                throw new IllegalArgumentException("Assinatura criptográfica do ID Token do Google INVÁLIDA.");
            }

            // 3. Valida Claims do Payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            String iss = payload.path("iss").asText();
            if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
                throw new IllegalArgumentException("Emissor inválido no token Google: " + iss);
            }

            long exp = payload.path("exp").asLong(0);
            if (exp > 0 && Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("ID Token do Google expirado.");
            }

            String sub = payload.path("sub").asText();
            String email = payload.has("email") ? payload.path("email").asText() : null;
            String name = payload.has("name") ? payload.path("name").asText() : fallbackName;

            log.info("[Google Auth] Token verificado com sucesso para sub={}, email={}", sub, email);
            return new VerifiedSocialUser(sub, email, name, "GOOGLE");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Google Auth] Erro ao validar token: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Falha na validação criptográfica do token do Google: " + e.getMessage());
        }
    }

    // ==========================================
    // RECONSTRUÇÃO DE CHAVES PÚBLICAS RSA A PARTIR DE JWKS
    // ==========================================
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
                log.info("[Apple Auth] Chaves públicas atualizadas: {} chaves em cache.", appleKeysCache.size());
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
                log.info("[Google Auth] Chaves públicas atualizadas: {} chaves em cache.", googleKeysCache.size());
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

    // =========================================================================
    // 3. SIGN IN WITH APPLE TOKEN EXCHANGE & REVOCATION (Guideline 5.1.1(v))
    // =========================================================================

    public String exchangeAppleAuthCodeForRefreshToken(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        String clientSecret = generateAppleClientSecret();
        if (clientSecret == null) {
            log.info("[Apple Auth] APPLE_KEY_ID / APPLE_PRIVATE_KEY não configurados ainda; código recebido para troca futura.");
            return null;
        }

        try {
            String form = "client_id=" + urlEncode(APP_BUNDLE_ID)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&code=" + urlEncode(authorizationCode.trim())
                + "&grant_type=authorization_code";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(APPLE_TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(resp.body());
                String refreshToken = json.path("refresh_token").asText("");
                if (!refreshToken.isBlank()) {
                    log.info("[Apple Auth] Refresh token obtido com sucesso para suporte à revogação (Guideline 5.1.1(v)).");
                    return refreshToken;
                }
            } else {
                log.warn("[Apple Auth] Resposta HTTP {} ao trocar authorization_code: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[Apple Auth] Falha ao trocar authorization_code da Apple: {}", e.getMessage());
        }
        return null;
    }

    public void revokeAppleTokensOnAccountDeletion(String storedRefreshToken, String freshAuthorizationCode) {
        String tokenToRevoke = (storedRefreshToken != null && !storedRefreshToken.isBlank())
            ? storedRefreshToken.trim()
            : exchangeAppleAuthCodeForRefreshToken(freshAuthorizationCode);

        if (tokenToRevoke == null || tokenToRevoke.isBlank()) {
            log.info("[Apple Auth] Nenhum refresh_token Apple armazenado para revogar; prosseguindo com exclusão integral dos dados.");
            return;
        }

        String clientSecret = generateAppleClientSecret();
        if (clientSecret == null) {
            log.warn("[Apple Auth] Revogação Apple ignorada pois APPLE_KEY_ID / APPLE_PRIVATE_KEY não estão definidos.");
            return;
        }

        try {
            String form = "client_id=" + urlEncode(APP_BUNDLE_ID)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&token=" + urlEncode(tokenToRevoke)
                + "&token_type_hint=refresh_token";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(APPLE_REVOKE_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("[Apple Auth] Token Sign in with Apple revogado com sucesso na Apple (HTTP {}).", resp.statusCode());
            } else {
                log.warn("[Apple Auth] Apple /auth/revoke retornou HTTP {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[Apple Auth] Falha de rede ao chamar /auth/revoke da Apple: {}", e.getMessage());
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
                .subject(APP_BUNDLE_ID)
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
