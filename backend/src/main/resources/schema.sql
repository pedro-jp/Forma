-- ==============================================================================
-- Schema do Supabase / PostgreSQL para o Forma
-- Execute este script no SQL Editor do seu projeto Supabase se desejar criar manualmente
-- ==============================================================================

-- 1. Tabela de Configurações do Sistema (Chaves de API dinâmicas)
CREATE TABLE IF NOT EXISTS app_config (
    id VARCHAR(50) PRIMARY KEY,
    config_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Inserção padrão para a chave do Gemini
INSERT INTO app_config (id, config_value, description)
VALUES ('GEMINI_API_KEY', '', 'Chave de API do Google Gemini utilizada pelo Backend Proxy')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_config (id, config_value, description)
VALUES ('GEMINI_MODEL', 'gemini-1.5-flash', 'Modelo ativo para análise de refeições e treinos')
ON CONFLICT (id) DO NOTHING;

-- 2. Tabela de Usuários
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    name VARCHAR(255),
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'EMAIL', -- EMAIL, GOOGLE, APPLE
    provider_id VARCHAR(255),
    apple_refresh_token VARCHAR(1024),
    google_refresh_token VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_synced_at TIMESTAMP WITH TIME ZONE
);

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

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

-- 8. Logs de Requisições de IA (Para acompanhamento no Painel Admin)
CREATE TABLE IF NOT EXISTS ai_request_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    endpoint VARCHAR(100) NOT NULL,
    prompt_summary VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    duration_ms INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

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

-- 10. Histórico de Análises e Diagnósticos da IA
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

-- 11. Log Completo de Atividades do Usuário (Entradas/Saídas de calorias, horários, água e treinos)
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
