package com.forma.backend.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografia simétrica autenticada AES-256-GCM e HMAC-SHA256 para proteção em repouso
 * (tokens OAuth, endereços IP do Marco Civil e segredos operacionais).
 */
@Service
public class FieldEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptionService.class);
    private static final String PREFIX = "ENC:v1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static volatile FieldEncryptionService activeInstance;

    private final SecretKey aesKey;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptionService(
        @Value("${app.security.encryption-key:}") String encryptionKey,
        @Value("${app.jwt.secret:}") String jwtSecret
    ) {
        String source = (encryptionKey != null && !encryptionKey.isBlank()) ? encryptionKey.trim() : "";
        if (source.isEmpty()) {
            String isRender = System.getenv("RENDER");
            if ("true".equalsIgnoreCase(isRender)) {
                log.error("[Security] APP_ENCRYPTION_KEY ausente em produção. Configure APP_ENCRYPTION_KEY dedicado no ambiente.");
            }
            if (jwtSecret != null && !jwtSecret.isBlank()) {
                source = "derived:" + jwtSecret.trim();
            } else {
                byte[] randomSeed = new byte[32];
                new SecureRandom().nextBytes(randomSeed);
                source = Base64.getEncoder().encodeToString(randomSeed);
                log.warn("[Security] Chave efêmera de criptografia em repouso gerada para esta execução.");
            }
        }
        byte[] derived = sha256(source.getBytes(StandardCharsets.UTF_8));
        this.aesKey = new SecretKeySpec(derived, "AES");
        this.hmacKeyBytes = sha256(("hmac-salt:" + source).getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    void registerStaticHolder() {
        activeInstance = this;
    }

    public static FieldEncryptionService current() {
        return activeInstance;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar dado sensível em repouso.", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        if (!ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Payload criptografado inválido.");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar dado sensível em repouso.", e);
        }
    }

    public String hmacSha256Hex(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular HMAC-SHA256.", e);
        }
    }

    public static String sha256Hex(String input) {
        byte[] hash = sha256((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
