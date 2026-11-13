package com.forma.backend.service;

import com.forma.backend.entity.AppConfigEntity;
import com.forma.backend.repository.AppConfigRepository;
import com.forma.backend.security.FieldEncryptionService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

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
    public static final String DEFAULT_APPLE_TEAM_ID = "Q42LJ4LG32";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";

    private static final Set<String> SECRET_CONFIG_KEYS = Set.of(
        KEY_GEMINI_API_KEY,
        KEY_RESEND_API_KEY,
        KEY_APPLE_PRIVATE_KEY
    );

    private final AppConfigRepository appConfigRepository;
    private final FieldEncryptionService encryptionService;

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

        Optional<AppConfigEntity> modelEntity = appConfigRepository.findById(KEY_GEMINI_MODEL);
        if (modelEntity.isEmpty()) {
            appConfigRepository.save(new AppConfigEntity(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL, "Modelo ativo do Gemini"));
        } else {
            String currentModel = modelEntity.get().getConfigValue();
            if (isDeprecatedModel(currentModel)) {
                modelEntity.get().setConfigValue(DEFAULT_GEMINI_MODEL);
                appConfigRepository.save(modelEntity.get());
            }
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
    }

    public String getGeminiModel() {
        return appConfigRepository.findById(KEY_GEMINI_MODEL)
            .map(AppConfigEntity::getConfigValue)
            .map(String::trim)
            .filter(v -> !v.isBlank() && !isDeprecatedModel(v))
            .orElse(DEFAULT_GEMINI_MODEL);
    }

    public void setGeminiModel(String model) {
        String finalModel = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_GEMINI_MODEL;
        AppConfigEntity entity = appConfigRepository.findById(KEY_GEMINI_MODEL)
            .orElse(new AppConfigEntity(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL, "Modelo ativo do Gemini"));
        entity.setConfigValue(finalModel);
        appConfigRepository.save(entity);
    }

    public boolean isAiBlocked() {
        return appConfigRepository.findById(KEY_AI_BLOCKED)
            .map(AppConfigEntity::getConfigValue)
            .map(v -> "true".equalsIgnoreCase(v.trim()))
            .orElse(false);
    }

    public void setAiBlocked(boolean blocked) {
        AppConfigEntity entity = appConfigRepository.findById(KEY_AI_BLOCKED)
            .orElse(new AppConfigEntity(KEY_AI_BLOCKED, "false", "Bloqueio global de requisições de IA"));
        entity.setConfigValue(blocked ? "true" : "false");
        appConfigRepository.save(entity);
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

    public void setAppleAuthConfig(String teamId, String keyId, String privateKeyPem) {
        if (teamId != null) {
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_TEAM_ID)
                .orElse(new AppConfigEntity(KEY_APPLE_TEAM_ID, DEFAULT_APPLE_TEAM_ID, "Apple Team ID"));
            e.setConfigValue(teamId.trim());
            appConfigRepository.save(e);
        }
        if (keyId != null) {
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_KEY_ID)
                .orElse(new AppConfigEntity(KEY_APPLE_KEY_ID, "", "Apple Sign-In Key ID"));
            e.setConfigValue(keyId.trim());
            appConfigRepository.save(e);
        }
        if (privateKeyPem != null) {
            String clean = privateKeyPem.trim();
            AppConfigEntity e = appConfigRepository.findById(KEY_APPLE_PRIVATE_KEY)
                .orElse(new AppConfigEntity(KEY_APPLE_PRIVATE_KEY, "", "Apple Sign-In .p8 Private Key"));
            e.setConfigValue(clean.isEmpty() ? "" : encryptionService.encrypt(clean));
            appConfigRepository.save(e);
        }
    }

    private String readDecryptedDbValue(String key) {
        String dbValue = appConfigRepository.findById(key)
            .map(AppConfigEntity::getConfigValue)
            .orElse("")
            .trim();
        if (dbValue.isEmpty()) {
            return "";
        }
        if (SECRET_CONFIG_KEYS.contains(key)) {
            return encryptionService.decrypt(dbValue).trim();
        }
        return dbValue;
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
