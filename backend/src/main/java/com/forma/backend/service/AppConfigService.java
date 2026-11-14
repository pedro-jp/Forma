package com.forma.backend.service;

import com.forma.backend.entity.AppConfigEntity;
import com.forma.backend.repository.AppConfigRepository;
import com.forma.backend.security.FieldEncryptionService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppConfigService {

    public static final String KEY_GEMINI_API_KEY = "GEMINI_API_KEY";
    public static final String KEY_GEMINI_MODEL = "GEMINI_MODEL";
    public static final String KEY_AI_BLOCKED = "AI_REQUESTS_BLOCKED";
    public static final String KEY_RESEND_API_KEY = "RESEND_API_KEY";
    public static final String KEY_MAIL_FROM = "MAIL_FROM";
    public static final String KEY_APPLE_TEAM_ID = "APPLE_TEAM_ID";
    public static final String KEY_APPLE_KEY_ID = "APPLE_KEY_ID";
    public static final String KEY_APPLE_PRIVATE_KEY = "APPLE_PRIVATE_KEY";
    public static final String KEY_WEBHOOK_SECRET = "WEBHOOK_SECRET";
    public static final String KEY_BILLING_OVERDUE_ACTION = "BILLING_OVERDUE_ACTION";
    public static final String DEFAULT_APPLE_TEAM_ID = "Q42LJ4LG32";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Set<String> SECRET_CONFIG_KEYS = Set.of(
        KEY_GEMINI_API_KEY,
        KEY_RESEND_API_KEY,
        KEY_APPLE_PRIVATE_KEY,
        KEY_WEBHOOK_SECRET
    );

    private final AppConfigRepository appConfigRepository;
    private final FieldEncryptionService encryptionService;
    private final ConcurrentHashMap<String, String> decryptedCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    public AppConfigService(
        AppConfigRepository appConfigRepository,
        FieldEncryptionService encryptionService
    ) {
        this.appConfigRepository = appConfigRepository;
        this.encryptionService = encryptionService;
    }

    @PostConstruct
    public void initDefaults() {
        if (!appConfigRepository.existsById(KEY_GEMINI_API_KEY)) {
            appConfigRepository.save(new AppConfigEntity(KEY_GEMINI_API_KEY, "", "Chave de API do Google Gemini"));
        } else {
            migrateSecretIfPlaintext(KEY_GEMINI_API_KEY);
        }
        if (!appConfigRepository.existsById(KEY_AI_BLOCKED)) {
            appConfigRepository.save(new AppConfigEntity(KEY_AI_BLOCKED, "false", "Bloqueio global de requisições de IA"));
        }
        migrateSecretIfPlaintext(KEY_RESEND_API_KEY);
        migrateSecretIfPlaintext(KEY_APPLE_PRIVATE_KEY);
        migrateSecretIfPlaintext(KEY_WEBHOOK_SECRET);

        reloadCacheFromDb();

        if (getWebhookSecret().isBlank()) {
            rotateWebhookSecret();
        }
        if (!appConfigRepository.existsById(KEY_BILLING_OVERDUE_ACTION)) {
            appConfigRepository.save(new AppConfigEntity(KEY_BILLING_OVERDUE_ACTION, "PAUSE", "Ação automática após 3 dias de atraso (PAUSE ou CANCEL)"));
            cachePutTransactionalSafe(KEY_BILLING_OVERDUE_ACTION, "PAUSE");
        }

        Optional<AppConfigEntity> modelEntity = appConfigRepository.findById(KEY_GEMINI_MODEL);
        if (modelEntity.isEmpty()) {
            appConfigRepository.save(new AppConfigEntity(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL, "Modelo ativo do Gemini"));
            cachePutTransactionalSafe(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
        } else {
            String currentModel = modelEntity.get().getConfigValue();
            if (isDeprecatedModel(currentModel)) {
                modelEntity.get().setConfigValue(DEFAULT_GEMINI_MODEL);
                appConfigRepository.save(modelEntity.get());
                cachePutTransactionalSafe(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
            } else {
                cachePutTransactionalSafe(KEY_GEMINI_MODEL, currentModel != null ? currentModel.trim() : DEFAULT_GEMINI_MODEL);
            }
        }
    }

    public synchronized void reloadCacheFromDb() {
        try {
            List<AppConfigEntity> all = appConfigRepository.findAll();
            if (all != null) {
                for (AppConfigEntity entity : all) {
                    if (entity == null || entity.getId() == null) continue;
                    String key = entity.getId();
                    decryptedCache.put(key, decryptIfSecret(key, entity.getConfigValue()));
                }
            }
            cacheLoaded = true;
        } catch (Exception ignored) {
            cacheLoaded = false;
        }
    }

    private void migrateSecretIfPlaintext(String key) {
        appConfigRepository.findById(key).ifPresent(entity -> {
            String val = entity.getConfigValue();
            if (val != null && !val.isBlank() && !val.startsWith("ENC:v1:")) {
                entity.setConfigValue(encryptionService.encrypt(val.trim()));
                appConfigRepository.save(entity);
            }
        });
    }

    private boolean isDeprecatedModel(String model) {
        if (model == null || model.isBlank()) return true;
        String m = model.trim().toLowerCase();
        return m.startsWith("gemini-1.5") || m.startsWith("gemini-2.0") || m.startsWith("gemini-1.0")
            || m.startsWith("gemini-2.5") || m.equals("gemini-flash-lite-latest") || m.equals("gemini-3.5-flash-lite");
    }

    public String getGeminiApiKey() {
        String dbValue = readDecryptedDbValue(KEY_GEMINI_API_KEY);
        if (!dbValue.isEmpty()) {
            return dbValue;
        }
        String envValue = System.getenv(KEY_GEMINI_API_KEY);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String propValue = System.getProperty(KEY_GEMINI_API_KEY);
        return propValue != null ? propValue.trim() : "";
    }

    public void setGeminiApiKey(String apiKey) {
        String clean = apiKey != null ? apiKey.trim() : "";
        AppConfigEntity entity = appConfigRepository.findById(KEY_GEMINI_API_KEY)
            .orElse(new AppConfigEntity(KEY_GEMINI_API_KEY, "", "Chave de API do Google Gemini"));
        entity.setConfigValue(clean.isEmpty() ? "" : encryptionService.encrypt(clean));
        appConfigRepository.saveAndFlush(entity);
        cachePutTransactionalSafe(KEY_GEMINI_API_KEY, clean);
    }

    public String getGeminiModel() {
        String cached = readDecryptedDbValue(KEY_GEMINI_MODEL);
        if (!cached.isBlank() && !isDeprecatedModel(cached)) {
            return cached;
        }
        return DEFAULT_GEMINI_MODEL;
    }

    public void setGeminiModel(String model) {
        String finalModel = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_GEMINI_MODEL;
        AppConfigEntity entity = appConfigRepository.findById(KEY_GEMINI_MODEL)
            .orElse(new AppConfigEntity(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL, "Modelo ativo do Gemini"));
        entity.setConfigValue(finalModel);
        appConfigRepository.save(entity);
        cachePutTransactionalSafe(KEY_GEMINI_MODEL, finalModel);
    }

    public boolean isAiBlocked() {
        String cached = readDecryptedDbValue(KEY_AI_BLOCKED);
        return "true".equalsIgnoreCase(cached);
    }

    public void setAiBlocked(boolean blocked) {
        AppConfigEntity entity = appConfigRepository.findById(KEY_AI_BLOCKED)
            .orElse(new AppConfigEntity(KEY_AI_BLOCKED, "false", "Bloqueio global de requisições de IA"));
        String val = blocked ? "true" : "false";
        entity.setConfigValue(val);
        appConfigRepository.save(entity);
        cachePutTransactionalSafe(KEY_AI_BLOCKED, val);
    }

    public String getMaskedApiKey() {
        String key = getGeminiApiKey();
        if (key.isBlank()) return "Não configurada";
        if (key.length() <= 8) return "********";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    public String getResendApiKey() {
        return getConfigOrEnv(KEY_RESEND_API_KEY, "");
    }

    public String getMailFrom() {
        return getConfigOrEnv(KEY_MAIL_FROM, "");
    }

    public String getAppleTeamId() {
        return getConfigOrEnv(KEY_APPLE_TEAM_ID, DEFAULT_APPLE_TEAM_ID);
    }

    public String getAppleKeyId() {
        return getConfigOrEnv(KEY_APPLE_KEY_ID, "");
    }

    public String getApplePrivateKeyPem() {
        return getConfigOrEnv(KEY_APPLE_PRIVATE_KEY, "");
    }

    public String getWebhookSecret() {
        String env = System.getenv("APP_WEBHOOK_SECRET");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return readDecryptedDbValue(KEY_WEBHOOK_SECRET);
    }

    public String getMaskedWebhookSecret() {
        String sec = getWebhookSecret();
        if (sec.isBlank()) return "Não configurado";
        if (sec.length() <= 12) return "whsec_********";
        return sec.substring(0, 10) + "..." + sec.substring(sec.length() - 4);
    }

    public String rotateWebhookSecret() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        String nextSecret = "whsec_" + HexFormat.of().formatHex(bytes);
        AppConfigEntity e = appConfigRepository.findById(KEY_WEBHOOK_SECRET)
            .orElse(new AppConfigEntity(KEY_WEBHOOK_SECRET, "", "Segredo HMAC-SHA256 dos Webhooks de Cobrança"));
        e.setConfigValue(encryptionService.encrypt(nextSecret));
        appConfigRepository.saveAndFlush(e);
        cachePutTransactionalSafe(KEY_WEBHOOK_SECRET, nextSecret);
        return nextSecret;
    }

    public String getBillingOverdueAction() {
        String val = readDecryptedDbValue(KEY_BILLING_OVERDUE_ACTION)
            .trim()
            .toUpperCase();
        return "CANCEL".equals(val) ? "CANCEL" : "PAUSE";
    }

    public void setBillingOverdueAction(String action) {
        String normalized = (action != null && "CANCEL".equalsIgnoreCase(action.trim())) ? "CANCEL" : "PAUSE";
        AppConfigEntity e = appConfigRepository.findById(KEY_BILLING_OVERDUE_ACTION)
            .orElse(new AppConfigEntity(KEY_BILLING_OVERDUE_ACTION, "PAUSE", "Ação automática após 3 dias de atraso (PAUSE ou CANCEL)"));
        e.setConfigValue(normalized);
        appConfigRepository.saveAndFlush(e);
        cachePutTransactionalSafe(KEY_BILLING_OVERDUE_ACTION, normalized);
    }

    public void setAppleAuthConfig(String teamId, String keyId, String privateKeyPem) {
        if (teamId != null) {
            String cleanTeam = teamId.trim();
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_TEAM_ID)
                .orElse(new AppConfigEntity(KEY_APPLE_TEAM_ID, DEFAULT_APPLE_TEAM_ID, "Apple Team ID"));
            e.setConfigValue(cleanTeam);
            appConfigRepository.save(e);
            cachePutTransactionalSafe(KEY_APPLE_TEAM_ID, cleanTeam);
        }
        if (keyId != null) {
            String cleanKey = keyId.trim();
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_KEY_ID)
                .orElse(new AppConfigEntity(KEY_APPLE_KEY_ID, "", "Apple Sign-In Key ID"));
            e.setConfigValue(cleanKey);
            appConfigRepository.save(e);
            cachePutTransactionalSafe(KEY_APPLE_KEY_ID, cleanKey);
        }
        if (privateKeyPem != null) {
            String clean = privateKeyPem.trim();
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_PRIVATE_KEY)
                .orElse(new AppConfigEntity(KEY_APPLE_PRIVATE_KEY, "", "Apple Sign-In .p8 Private Key"));
            e.setConfigValue(clean.isEmpty() ? "" : encryptionService.encrypt(clean));
            appConfigRepository.save(e);
            cachePutTransactionalSafe(KEY_APPLE_PRIVATE_KEY, clean);
        }
    }

    private String readDecryptedDbValue(String key) {
        if (!cacheLoaded) {
            reloadCacheFromDb();
        }
        String cached = decryptedCache.get(key);
        if (cached != null) {
            return cached;
        }
        String plain = appConfigRepository.findById(key)
            .map(e -> decryptIfSecret(key, e.getConfigValue()))
            .orElse("");
        decryptedCache.put(key, plain);
        return plain;
    }

    private String decryptIfSecret(String key, String rawValue) {
        String dbValue = rawValue != null ? rawValue.trim() : "";
        if (dbValue.isEmpty()) {
            return "";
        }
        if (SECRET_CONFIG_KEYS.contains(key)) {
            return encryptionService.decrypt(dbValue).trim();
        }
        return dbValue;
    }

    private void cachePutTransactionalSafe(String key, String plainValue) {
        String nextVal = plainValue != null ? plainValue : "";
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            boolean hadPrev = decryptedCache.containsKey(key);
            String prev = decryptedCache.get(key);
            decryptedCache.put(key, nextVal);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        if (hadPrev && prev != null) {
                            decryptedCache.put(key, prev);
                        } else {
                            decryptedCache.remove(key);
                        }
                    }
                }
            });
        } else {
            decryptedCache.put(key, nextVal);
        }
    }

    private String getConfigOrEnv(String key, String fallback) {
        String dbValue = readDecryptedDbValue(key);
        if (!dbValue.isEmpty()) {
            return dbValue;
        }
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String propValue = System.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.trim();
        }
        return fallback;
    }
}
