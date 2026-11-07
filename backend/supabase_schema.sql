-- ==============================================================================
-- SCHEMA DO FORMA BACKEND PARA SUPABASE (POSTGRESQL)
-- Execute este script no SQL Editor do Supabase se quiser criar todas as tabelas
-- manualmente, caso o Hibernate ddl-auto ainda não tenha conectado.
-- ==============================================================================

-- 1. TABELA DE CONFIGURAÇÕES GERAIS
CREATE TABLE IF NOT EXISTS app_config (
    id VARCHAR(50) PRIMARY KEY,
    config_value TEXT,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. TABELA DE USUÁRIOS
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    name VARCHAR(255),
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'EMAIL',
    provider_id VARCHAR(255),
    apple_refresh_token VARCHAR(1024),
    google_refresh_token VARCHAR(1024),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    paused_reason VARCHAR(255),
    ai_blocked BOOLEAN DEFAULT FALSE,
    daily_ai_limit INTEGER,
    force_reset_at TIMESTAMPTZ,
    reset_code_hash VARCHAR(255),
    reset_code_expires_at TIMESTAMPTZ
);

-- 3. TABELA DE PERFIL E METAS DO USUÁRIO
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
    target_water_milliliters INTEGER DEFAULT 2500,
    height_meters DOUBLE PRECISION DEFAULT 1.78,
    current_weight DOUBLE PRECISION DEFAULT 72.0,
    onboarding_completed BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 4. REFEIÇÕES / ALIMENTOS
CREATE TABLE IF NOT EXISTS food_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    meal_type VARCHAR(50) NOT NULL,
    portion_grams DOUBLE PRECISION,
    calories DOUBLE PRECISION NOT NULL,
    protein DOUBLE PRECISION DEFAULT 0.0,
    carbohydrates DOUBLE PRECISION DEFAULT 0.0,
    fat DOUBLE PRECISION DEFAULT 0.0,
    consumed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_food_entries_user_date ON food_entries(user_id, consumed_at DESC);

-- 5. TREINOS
CREATE TABLE IF NOT EXISTS workout_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    workout_type VARCHAR(50) NOT NULL,
    duration_minutes INTEGER DEFAULT 45,
    calories_burned DOUBLE PRECISION DEFAULT 300.0,
    notes TEXT,
    workout_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workout_entries_user_date ON workout_entries(user_id, workout_date DESC);

-- 6. ÁGUA
CREATE TABLE IF NOT EXISTS water_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    milliliters INTEGER NOT NULL,
    entry_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_water_entries_user_date ON water_entries(user_id, entry_date DESC);

-- 7. PESO
CREATE TABLE IF NOT EXISTS weight_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL,
    entry_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_weight_entries_user_date ON weight_entries(user_id, entry_date DESC);

-- 8. FICHAS E ROTINAS DE TREINO
CREATE TABLE IF NOT EXISTS workout_routines (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    workout_type VARCHAR(50) NOT NULL,
    target_muscles VARCHAR(255),
    hypertrophy_focus TEXT,
    estimated_duration_minutes INTEGER DEFAULT 50,
    estimated_calories DOUBLE PRECISION DEFAULT 350.0,
    recommended_rest_seconds INTEGER DEFAULT 90,
    is_favorite BOOLEAN DEFAULT FALSE,
    exercises_json TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_trained_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_workout_routines_user ON workout_routines(user_id, created_at DESC);

-- 9. DIAGNÓSTICOS E ANÁLISES DA IA
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
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_analyses_user ON ai_analyses(user_id, created_at DESC);

-- 10. HISTÓRICO COMPLETO DE ATIVIDADES
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
    water_milliliters INTEGER,
    weight_kg DOUBLE PRECISION,
    details TEXT,
    activity_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_activity_logs_user_ts ON user_activity_logs(user_id, activity_timestamp DESC);

-- 11. LOGS DE REQUISIÇÕES DE IA (AUDITORIA E LIMITES DIÁRIOS)
CREATE TABLE IF NOT EXISTS ai_request_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    endpoint VARCHAR(255) NOT NULL,
    prompt_summary VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_request_logs_user_ts ON ai_request_logs(user_id, created_at DESC);
