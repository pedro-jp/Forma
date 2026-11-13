package com.forma.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@SpringBootApplication
@EnableScheduling
public class FormaBackendApplication {

    private static final Logger log = LoggerFactory.getLogger(FormaBackendApplication.class);

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        loadDotEnv();
        Map<String, Object> resolvedDbProps = resolveSupabaseJdbcConnection();

        SpringApplication app = new SpringApplication(FormaBackendApplication.class);
        app.addInitializers(context -> {
            ConfigurableEnvironment env = context.getEnvironment();
            if (Arrays.asList(env.getActiveProfiles()).contains("test")) {
                return;
            }
            // Prioridade máxima sobre System.getenv(SUPABASE_DB_URL) e application.yml
            env.getPropertySources().addFirst(new MapPropertySource("formaResolvedDatabase", resolvedDbProps));
        });
        app.run(args);
    }

    private static void loadDotEnv() {
        File[] candidates = {
            new File(".env"),
            new File("../.env"),
            new File("backend/.env"),
            new File("/etc/secrets/.env")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(f.toPath());
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        int idx = trimmed.indexOf('=');
                        if (idx > 0) {
                            String key = trimmed.substring(0, idx).trim();
                            String value = trimmed.substring(idx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    log.info("[Config] Variáveis de ambiente carregadas de: {}", f.getAbsolutePath());
                    return;
                } catch (Exception e) {
                    log.warn("[Config] Falha ao ler .env: {}", e.getMessage());
                }
            }
        }
    }

    private static String getEnvOrProperty(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env.trim();
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop.trim();
        return null;
    }

    private static String extractProjectRef(String supabaseUrl, String configuredDbUrl) {
        if (supabaseUrl != null && !supabaseUrl.isBlank()) {
            try {
                URI uri = URI.create(supabaseUrl.trim());
                String host = uri.getHost();
                if (host != null && host.endsWith(".supabase.co")) {
                    String prefix = host.substring(0, host.length() - ".supabase.co".length());
                    if (prefix.startsWith("db.")) {
                        prefix = prefix.substring("db.".length());
                    }
                    if (!prefix.isBlank()) return prefix;
                }
            } catch (Exception ignored) {
            }
        }
        if (configuredDbUrl != null && !configuredDbUrl.isBlank()) {
            int dbIdx = configuredDbUrl.indexOf("//db.");
            if (dbIdx < 0) {
                int atDbIdx = configuredDbUrl.indexOf("@db.");
                if (atDbIdx >= 0) dbIdx = atDbIdx - 1;
            }
            if (dbIdx >= 0) {
                int start = configuredDbUrl.indexOf("db.", dbIdx) + 3;
                int endIdx = configuredDbUrl.indexOf(".supabase.co", start);
                if (endIdx > start) {
                    return configuredDbUrl.substring(start, endIdx);
                }
            }
            int userIdx = configuredDbUrl.indexOf("postgres.");
            if (userIdx >= 0) {
                int start = userIdx + "postgres.".length();
                int end = start;
                while (end < configuredDbUrl.length() && Character.isLetterOrDigit(configuredDbUrl.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    return configuredDbUrl.substring(start, end);
                }
            }
        }
        return null;
    }

    private record JdbcCandidate(String url, String user, String label, int timeoutSeconds) {}

    /**
     * Valida e resolve automaticamente a conexão PostgreSQL do Supabase a partir de
     * SUPABASE_DB_URL ou SUPABASE_URL + SUPABASE_DB_PASSWORD. Prioriza o Pooler IPv4
     * (compatível com Render/Docker) e injeta o resultado com precedência máxima no Spring.
     */
    private static Map<String, Object> resolveSupabaseJdbcConnection() {
        String dbPassword = getEnvOrProperty("SUPABASE_DB_PASSWORD");
        String configuredUrl = getEnvOrProperty("SUPABASE_DB_URL");
        String configuredUser = getEnvOrProperty("SUPABASE_DB_USER");
        String supabaseUrl = getEnvOrProperty("SUPABASE_URL");

        if (configuredUrl != null && configuredUrl.startsWith("postgresql://")) {
            configuredUrl = "jdbc:" + configuredUrl;
        }

        if (configuredUrl != null && configuredUrl.contains("?")) {
            for (String param : configuredUrl.substring(configuredUrl.indexOf('?') + 1).split("&")) {
                if (param.startsWith("password=") && (dbPassword == null || dbPassword.isBlank())) {
                    String rawPwd = param.substring("password=".length());
                    dbPassword = rawPwd.contains("%")
                        ? URLDecoder.decode(rawPwd, StandardCharsets.UTF_8)
                        : rawPwd;
                } else if (param.startsWith("user=") && (configuredUser == null || configuredUser.isBlank())) {
                    String rawUsr = param.substring("user=".length());
                    configuredUser = rawUsr.contains("%")
                        ? URLDecoder.decode(rawUsr, StandardCharsets.UTF_8)
                        : rawUsr;
                }
            }
        }

        String projectRef = extractProjectRef(supabaseUrl, configuredUrl);

        if (dbPassword == null || dbPassword.isBlank()) {
            log.info("[Database] SUPABASE_DB_PASSWORD não configurado. Usando banco local H2 (./forma_local_db).");
            return activateH2Fallback();
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            log.warn("[Database] Driver PostgreSQL não encontrado: {}", e.getMessage());
            return activateH2Fallback();
        }

        List<JdbcCandidate> candidates = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        boolean configuredIsDirectIpv6 = configuredUrl != null
            && configuredUrl.contains("db.")
            && configuredUrl.contains(".supabase.co");

        if (configuredUrl != null && configuredUrl.startsWith("jdbc:postgresql:") && !configuredIsDirectIpv6) {
            String normalizedUrl = normalizeJdbcUrl(configuredUrl);
            String user = (configuredUser != null && !configuredUser.isBlank())
                ? configuredUser
                : (projectRef != null ? "postgres." + projectRef : "postgres");
            candidates.add(new JdbcCandidate(normalizedUrl, user, "configurado no ambiente", 8));
            seenUrls.add(normalizedUrl + "|" + user);
        }

        if (projectRef != null && !projectRef.isBlank()) {
            String poolerUser = "postgres." + projectRef;
            // Região principal verificada (aws-1-ca-central-1) em Session Mode (5432) e Transaction Mode (6543)
            String[] primaryUrls = {
                "jdbc:postgresql://aws-1-ca-central-1.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0",
                "jdbc:postgresql://aws-1-ca-central-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0"
            };
            for (String pUrl : primaryUrls) {
                if (seenUrls.add(pUrl + "|" + poolerUser)) {
                    candidates.add(new JdbcCandidate(pUrl, poolerUser, "Pooler IPv4 aws-1-ca-central-1", 8));
                }
            }

            String[] fallbackRegions = {
                "aws-0-ca-central-1",
                "aws-0-sa-east-1",
                "aws-1-sa-east-1",
                "aws-0-us-east-1",
                "aws-1-us-east-1",
                "aws-0-us-east-2",
                "aws-1-us-east-2",
                "aws-0-us-west-1",
                "aws-1-us-west-1",
                "aws-0-us-west-2",
                "aws-1-us-west-2",
                "aws-0-eu-central-1",
                "aws-1-eu-central-1",
                "aws-0-eu-west-1",
                "aws-1-eu-west-1"
            };
            for (String region : fallbackRegions) {
                String url = "jdbc:postgresql://" + region + ".pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0";
                if (seenUrls.add(url + "|" + poolerUser)) {
                    candidates.add(new JdbcCandidate(url, poolerUser, "Pooler IPv4 " + region, 4));
                }
            }
        } else if (configuredIsDirectIpv6) {
            String normalizedUrl = normalizeJdbcUrl(configuredUrl);
            String user = (configuredUser != null && !configuredUser.isBlank()) ? configuredUser : "postgres";
            candidates.add(new JdbcCandidate(normalizedUrl, user, "configurado no ambiente", 6));
        }

        String lastError = null;
        for (JdbcCandidate candidate : candidates) {
            Properties props = new Properties();
            props.setProperty("user", candidate.user());
            props.setProperty("password", dbPassword);
            props.setProperty("connectTimeout", String.valueOf(candidate.timeoutSeconds()));
            props.setProperty("loginTimeout", String.valueOf(candidate.timeoutSeconds()));
            props.setProperty("socketTimeout", "15");

            try (Connection conn = DriverManager.getConnection(candidate.url(), props)) {
                System.setProperty("SUPABASE_DB_DRIVER", "org.postgresql.Driver");
                System.setProperty("SUPABASE_DB_URL", candidate.url());
                System.setProperty("SUPABASE_DB_USER", candidate.user());
                System.setProperty("SUPABASE_DB_PASSWORD", dbPassword);

                Map<String, Object> dsProps = new HashMap<>();
                dsProps.put("SUPABASE_DB_DRIVER", "org.postgresql.Driver");
                dsProps.put("SUPABASE_DB_URL", candidate.url());
                dsProps.put("SUPABASE_DB_USER", candidate.user());
                dsProps.put("SUPABASE_DB_PASSWORD", dbPassword);
                dsProps.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
                dsProps.put("spring.datasource.url", candidate.url());
                dsProps.put("spring.datasource.username", candidate.user());
                dsProps.put("spring.datasource.password", dbPassword);
                log.info("[Supabase] Conectado com sucesso ao PostgreSQL do Supabase ({})!", candidate.label());
                return dsProps;
            } catch (SQLException ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                lastError = msg;
                if ("28P01".equals(ex.getSQLState())
                    || msg.toLowerCase().contains("password authentication failed")
                    || msg.toLowerCase().contains("circuit breaker")) {
                    if (shouldFailClosedOnPostgresError()) {
                        throw new IllegalStateException(
                            "Falha crítica de autenticação/conectividade PostgreSQL em modo fail-closed: " + msg,
                            ex
                        );
                    }
                    log.error(
                        "[Supabase] Região encontrada ({}), mas SUPABASE_DB_PASSWORD foi recusada pelo PostgreSQL ({}). " +
                        "Confira a senha do banco em Supabase -> Project Settings -> Database. Usando H2 local temporariamente.",
                        candidate.label(),
                        msg
                    );
                    return activateH2Fallback();
                }
            }
        }

        if (shouldFailClosedOnPostgresError()) {
            throw new IllegalStateException(
                "Banco PostgreSQL configurado indisponível no startup (fail-closed ativo): " + lastError
            );
        }

        log.error(
            "[Supabase] Não foi possível conectar ao PostgreSQL do Supabase (último erro: {}). " +
            "Usando H2 local temporariamente para manter o servidor e o /admin ativos.",
            lastError
        );
        return activateH2Fallback();
    }

    private static boolean shouldFailClosedOnPostgresError() {
        String envVal = System.getenv("APP_ALLOW_H2_FALLBACK");
        String propVal = System.getProperty("app.database.allow-h2-fallback");
        String allowH2Fallback = (envVal != null && !envVal.isBlank()) ? envVal.trim()
            : (propVal != null && !propVal.isBlank() ? propVal.trim() : null);
        if ("false".equalsIgnoreCase(allowH2Fallback)) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("RENDER"))
            && !"true".equalsIgnoreCase(allowH2Fallback);
    }

    private static String normalizeJdbcUrl(String url) {
        String clean = url;
        int qIdx = url.indexOf('?');
        if (qIdx >= 0) {
            String base = url.substring(0, qIdx);
            List<String> kept = new ArrayList<>();
            for (String param : url.substring(qIdx + 1).split("&")) {
                if (!param.startsWith("user=") && !param.startsWith("password=") && !param.isBlank()) {
                    kept.add(param);
                }
            }
            clean = kept.isEmpty() ? base : base + "?" + String.join("&", kept);
        }
        if (!clean.contains("sslmode=")) {
            clean += (clean.contains("?") ? "&" : "?") + "sslmode=require";
        }
        if (!clean.contains("prepareThreshold=")) {
            clean += (clean.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }
        return clean;
    }

    private static Map<String, Object> activateH2Fallback() {
        String h2Url = "jdbc:h2:file:./forma_local_db;DB_CLOSE_DELAY=-1";
        System.setProperty("SUPABASE_DB_DRIVER", "org.h2.Driver");
        System.setProperty("SUPABASE_DB_URL", h2Url);
        System.setProperty("SUPABASE_DB_USER", "sa");
        System.setProperty("SUPABASE_DB_PASSWORD", "");

        Map<String, Object> dsProps = new HashMap<>();
        dsProps.put("SUPABASE_DB_DRIVER", "org.h2.Driver");
        dsProps.put("SUPABASE_DB_URL", h2Url);
        dsProps.put("SUPABASE_DB_USER", "sa");
        dsProps.put("SUPABASE_DB_PASSWORD", "");
        dsProps.put("spring.datasource.driver-class-name", "org.h2.Driver");
        dsProps.put("spring.datasource.url", h2Url);
        dsProps.put("spring.datasource.username", "sa");
        dsProps.put("spring.datasource.password", "");
        return dsProps;
    }
}
