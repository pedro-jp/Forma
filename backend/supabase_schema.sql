-- ==============================================================================
-- Schema do Supabase / PostgreSQL para o Forma (Hardened Security + LGPD / MCI)
-- Execute este script no SQL Editor do seu projeto Supabase para aplicar todas
-- as tabelas, índices de auditoria e Row Level Security (RLS).
-- ==============================================================================

-- 1. Tabela de Configurações do Sistema (Chaves sensíveis criptografadas via AES-256-GCM pelo backend)
CREATE TABLE IF NOT EXISTS app_config (
    id VARCHAR(50) PRIMARY KEY,
    config_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_config (id, config_value, description)
VALUES ('GEMINI_API_KEY', '', 'Chave de API do Google Gemini utilizada pelo Backend Proxy')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_config (id, config_value, description)
VALUES ('GEMINI_MODEL', 'gemini-3.1-flash-lite', 'Modelo ativo para análise de refeições e treinos')
ON CONFLICT (id) DO NOTHING;

-- 2. Tabela de Usuários
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    name VARCHAR(255),
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'EMAIL',
    account_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    provider_id VARCHAR(255),
    apple_refresh_token TEXT,
    google_refresh_token TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    paused_reason VARCHAR(255),
    ai_blocked BOOLEAN DEFAULT FALSE,
    daily_ai_limit INT,
    force_reset_at TIMESTAMP WITH TIME ZONE,
    reset_code_hash VARCHAR(255),
    reset_code_expires_at TIMESTAMP WITH TIME ZONE,
    reset_code_attempts INT NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 1,
    ai_consent_granted BOOLEAN NOT NULL DEFAULT FALSE,
    ai_consent_updated_at TIMESTAMP WITH TIME ZONE,
    terms_accepted_version VARCHAR(40),
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    health_consent_accepted_at TIMESTAMP WITH TIME ZONE,
    age_confirmed_18_plus BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS account_tier VARCHAR(20) DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS paused_reason VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_blocked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_ai_limit INT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS force_reset_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_code_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_code_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_code_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_consent_granted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_consent_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_version VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS health_consent_accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS age_confirmed_18_plus BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN apple_refresh_token TYPE TEXT;
ALTER TABLE users ALTER COLUMN google_refresh_token TYPE TEXT;

-- 3. Perfil e Metas do Usuário
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    goal VARCHAR(50) DEFAULT 'gain',
    target_calorie_intake DOUBLE PRECISION DEFAULT 2800.0,
    target_calorie_burn DOUBLE PRECISION DEFAULT 600.0,
    target_steps DOUBLE PRECISION DEFAULT 8000.0,
    target_weight DOUBLE PRECISION DEFAULT 80.0,
    target_protein DOUBLE PRECISION DEFAULT 160.0,
    target_carbohydrates DOUBLE PRECISION DEFAULT 380.0,
    target_fat DOUBLE PRECISION DEFAULT 70.0,
    target_water_milliliters INT DEFAULT 2500,
    height_meters DOUBLE PRECISION DEFAULT 1.78,
    current_weight DOUBLE PRECISION DEFAULT 72.0,
    onboarding_completed BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN DEFAULT FALSE;

-- 4. Alimentos Registrados
CREATE TABLE IF NOT EXISTS food_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    meal_type VARCHAR(50) NOT NULL,
    portion_grams DOUBLE PRECISION DEFAULT 100.0,
    calories DOUBLE PRECISION NOT NULL,
    protein DOUBLE PRECISION DEFAULT 0.0,
    carbohydrates DOUBLE PRECISION DEFAULT 0.0,
    fat DOUBLE PRECISION DEFAULT 0.0,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_food_entries_user_date ON food_entries(user_id, consumed_at);

-- 5. Treinos Registrados
CREATE TABLE IF NOT EXISTS workout_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    workout_type VARCHAR(50) NOT NULL,
    duration_minutes INT DEFAULT 45,
    calories_burned DOUBLE PRECISION DEFAULT 300.0,
    notes TEXT,
    workout_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workout_entries_user_date ON workout_entries(user_id, workout_date);

-- 6. Registros de Água
CREATE TABLE IF NOT EXISTS water_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    milliliters INT NOT NULL,
    entry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_water_entries_user_date ON water_entries(user_id, entry_date);

-- 7. Histórico de Peso
CREATE TABLE IF NOT EXISTS weight_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL,
    entry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_weight_entries_user_date ON weight_entries(user_id, entry_date);

-- 8. Logs de Requisições de IA
CREATE TABLE IF NOT EXISTS ai_request_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    endpoint VARCHAR(255) NOT NULL,
    prompt_summary VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_request_logs_user_ts ON ai_request_logs(user_id, created_at DESC);

-- 9. Fichas e Rotinas de Treino Salvas
CREATE TABLE IF NOT EXISTS workout_routines (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    workout_type VARCHAR(50) NOT NULL,
    target_muscles VARCHAR(255),
    hypertrophy_focus TEXT,
    estimated_duration_minutes INT DEFAULT 50,
    estimated_calories DOUBLE PRECISION DEFAULT 350.0,
    recommended_rest_seconds INT DEFAULT 90,
    is_favorite BOOLEAN DEFAULT FALSE,
    exercises_json TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_trained_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_workout_routines_user ON workout_routines(user_id);

-- 10. Histórico de Análises Educacionais da IA
CREATE TABLE IF NOT EXISTS ai_analyses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status_summary TEXT,
    calorie_diagnostic TEXT,
    macro_diagnostic TEXT,
    hydration_diagnostic TEXT,
    workout_suggestions_json TEXT,
    nutrition_suggestions_json TEXT,
    weekly_action_plan TEXT,
    suggested_targets_json TEXT,
    recommended_routine_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_analyses_user_date ON ai_analyses(user_id, created_at);

-- 11. Log Completo de Atividades do Usuário
CREATE TABLE IF NOT EXISTS user_activity_logs (
    id VARCHAR(100) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    is_snack BOOLEAN DEFAULT FALSE,
    calories_change DOUBLE PRECISION DEFAULT 0.0,
    protein_grams DOUBLE PRECISION,
    carbs_grams DOUBLE PRECISION,
    fat_grams DOUBLE PRECISION,
    water_milliliters INT,
    weight_kg DOUBLE PRECISION,
    details TEXT,
    activity_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_activity_logs_user_time ON user_activity_logs(user_id, activity_timestamp);

-- 12. Tombstone Persistente de Contas Excluídas (evita ressurreição por JWT legado após reinício)
CREATE TABLE IF NOT EXISTS deleted_accounts_tombstone (
    user_id VARCHAR(64) PRIMARY KEY,
    deleted_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(120)
);

-- 13. Ledger Imutável de Consentimentos (LGPD Art. 8 §2 e Art. 11 I)
CREATE TABLE IF NOT EXISTS legal_consent_ledger (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    consent_type VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL,
    document_version VARCHAR(40) NOT NULL,
    document_sha256 VARCHAR(128) NOT NULL,
    source_platform VARCHAR(40),
    ip_hash VARCHAR(128),
    recorded_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_legal_consent_ledger_user_ts ON legal_consent_ledger(user_id, recorded_at_utc DESC);

-- 14. Registros de Acesso de Aplicação (Marco Civil da Internet - Lei 12.965/2014 Art. 15 - Retenção 6 meses)
CREATE TABLE IF NOT EXISTS mci_access_logs (
    id VARCHAR(64) PRIMARY KEY,
    account_ref_hash VARCHAR(128) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    ip_address_encrypted TEXT NOT NULL,
    source_port INTEGER,
    accessed_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until_utc TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mci_access_logs_retain ON mci_access_logs(retain_until_utc);

-- 15. Trilha de Auditoria Administrativa (LGPD Art. 37 e Art. 46)
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    admin_actor VARCHAR(80) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    target_ref VARCHAR(128),
    detail_summary VARCHAR(500),
    ip_address_encrypted TEXT,
    created_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_ts ON admin_audit_logs(created_at_utc DESC);

-- 16. Registro de Incidentes de Segurança (Resolução CD/ANPD nº 15/2024 - Retenção 5 anos)
CREATE TABLE IF NOT EXISTS security_incident_logs (
    id VARCHAR(64) PRIMARY KEY,
    severity VARCHAR(32) NOT NULL,
    incident_type VARCHAR(80) NOT NULL,
    affected_categories VARCHAR(255),
    affected_count INTEGER DEFAULT 0,
    description TEXT NOT NULL,
    mitigation_actions TEXT,
    anpd_notified BOOLEAN NOT NULL DEFAULT FALSE,
    detected_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until_utc TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_security_incident_logs_ts ON security_incident_logs(detected_at_utc DESC);

-- ==============================================================================
-- BLINDAGEM ROW LEVEL SECURITY (RLS) & BLOQUEIO DE POSTGREST (anon/authenticated)
-- O backend Spring Boot conecta com role proprietária (postgres) via TLS/JDBC.
-- Nenhuma chave pública anon/authenticated do Supabase pode ler/escrever dados.
-- ==============================================================================
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated;

ALTER TABLE app_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_config FORCE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE food_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE food_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE workout_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE water_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE water_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE weight_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE weight_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE workout_routines ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout_routines FORCE ROW LEVEL SECURITY;
ALTER TABLE ai_analyses ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_analyses FORCE ROW LEVEL SECURITY;
ALTER TABLE user_activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_activity_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE ai_request_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_request_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE deleted_accounts_tombstone ENABLE ROW LEVEL SECURITY;
ALTER TABLE deleted_accounts_tombstone FORCE ROW LEVEL SECURITY;
ALTER TABLE legal_consent_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_consent_ledger FORCE ROW LEVEL SECURITY;
ALTER TABLE mci_access_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE mci_access_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE admin_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_audit_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE security_incident_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_incident_logs FORCE ROW LEVEL SECURITY;
