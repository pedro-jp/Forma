# Documentação Completa do Site e Ecossistema Forma

> **Versão Jurídico-Técnica:** `2026.11-LGPD`  
> **Portal Público Web:** `/` · `/docs` (`/documentacao`) · `/privacy` (`/privacidade`) · `/terms` (`/termos`) · `/admin/` · `/api/health`

---

## 1. Visão Geral do Ecossistema

O **Forma** é um ecossistema completo de organização pessoal de nutrição, hidratação e treinos, composto por:

1. **Portal Institucional, Trust Center & Documentação Interativa** (`backend/src/main/resources/static/index.html`, `docs.html`, `privacy.html`, `terms.html`) servidos diretamente pelo backend Spring Boot sob cabeçalhos estritos de segurança (CSP, HSTS, X-Frame-Options, Referrer-Policy).
2. **Backend API & Proxy Seguro de IA** (`backend/`): Spring Boot 3.3 / Java 21 hospedado em nuvem (Render), conectado ao **Supabase PostgreSQL** em modo **fail-closed** (`RENDER=true` ou `APP_ALLOW_H2_FALLBACK=false`).
3. **Console Administrativo em Tempo Real** (`admin-web/`, publicado em `/admin/`): SPA em React 19 + TypeScript + Vite autenticada por cookie `HttpOnly` e WebSocket `/ws/admin` protegido por handshake interceptor.
4. **Aplicativos Nativos Apple** (`Forma/`, `FormaWidgets/`, `FormaWatchApp/`): SwiftUI + SwiftData offline-first, WidgetKit/Dynamic Island e Apple Watch sincronizados via JWT armazenado exclusivamente no Keychain (`AfterFirstUnlockThisDeviceOnly`).

---

## 2. Rotas Web do Site (Públicas & Operacionais)

| Rota | Alias / Forward | Finalidade |
| :--- | :--- | :--- |
| `/` | `index.html` | Portal público, Trust Center de segurança/LGPD e indicador em tempo real (`/api/health`). |
| `/docs` | `/documentacao` → `docs.html` | Documentação técnica, regulatória e operacional pesquisável offline-capable sob CSP estrita. |
| `/privacy` | `/privacidade`, `/privacy.html` | Política de Privacidade v`2026.11-LGPD` (LGPD Arts. 7º, 11, 14, 18, 33 e Marco Civil Art. 15). |
| `/terms` | `/termos`, `/terms.html` | Termos de Uso 18+, conformidade CFN/CONFEF/CFM, Direito do Consumidor (CDC) e EULA Apple. |
| `/admin/` | `/admin` → `/admin/index.html` | Console operacional Vite + React com Kill-Switch, gestão de usuários, chaves, auditoria Art. 37 e incidentes ANPD. |
| `/api/health` | `/api/ping` | Verificação mínima de disponibilidade (`{"status":"ok"}`) sem vazamento de motor de banco. |

---

## 3. Arquitetura de Segurança & Defesa em Profundidade

### 3.1 Autenticação, Sessão e Controle de Acesso
- **Autorização exclusiva por JWT Bearer (`AuthContextResolver`):** Todo endpoint protegido exige `Authorization: Bearer <token>`. O header legado `X-User-Id` não autentica nem é enviado pelo iOS (`FormaAPIClient.swift`).
- **Revogação imediata (`tokenVersion`):** A tabela `users` mantém `token_version`. Eventos de `POST /api/auth/logout`, `POST /api/auth/reset-password` e `POST /api/auth/change-password` incrementam `token_version`, invalidando JWTs emitidos anteriormente na hora.
- **Anti-Ressurreição (`deleted_accounts_tombstone`):** Contas excluídas gravam tombstone imutável; qualquer JWT remanescente recebe HTTP `401` (`accountDeleted: true`).
- **Política de Senha & Bloqueio OTP:** Senhas exigem ≥ 8 caracteres no backend e no iOS (`BCryptPasswordEncoder`). Códigos de recuperação (6 dígitos) possuem `reset_code_attempts` persistido sem rollback transacional: 5 erros consecutivos invalidam o código por segurança.
- **Login Social Seguro (`SocialAuthVerificationService`):** Valida assinatura JWKS da Apple (`https://appleid.apple.com/auth/keys`) e `tokeninfo` do Google + audiência (`APP_APPLE_BUNDLE_ID` / `APP_GOOGLE_CLIENT_ID`). Emulação dev (`allow-dev-social-mock`) permanece `false` por padrão e nunca é aceita em produção.
- **Release HTTPS Strict & WebSockets Seguros:** Em builds iOS `Release` (`#if !DEBUG`), `candidateServerUrls` bloqueia descoberta LAN HTTP cleartext e `AccountRealtimeSocketManager` exige `wss://` enviando o JWT apenas no cabeçalho `Authorization` (sem `?token=` na URL).

### 3.2 Criptografia em Trânsito, em Repouso e no Dispositivo
- **AES-256-GCM em Repouso (`FieldEncryptionService` / `EncryptedStringAttributeConverter`):** Todos os valores sensíveis gravados no PostgreSQL recebem prefixo autenticado `ENC:v1:` com IV aleatório de 12 bytes e tag GCM de 128 bits:
  - `users.apple_refresh_token` e `users.google_refresh_token`
  - `app_config` (`GEMINI_API_KEY`, `RESEND_API_KEY`, `APPLE_PRIVATE_KEY`, `BILLING_WEBHOOK_SECRET`)
  - `mci_access_logs.ip_address_encrypted` e `admin_audit_logs.ip_address_encrypted`
- **Hardening Administrativo:** Sessão por cookie `HttpOnly; SameSite=Strict; Secure`, proteção anti-CSRF (`Sec-Fetch-Site` e validação estrita de `Origin`) em rotas mutáveis, além de bloqueio automático de IP após 5 tentativas falhas (registrando `ADMIN_BRUTE_FORCE_BLOCKED`).
- **Pseudonimização (`HMAC-SHA256`):** Referências de conta no Marco Civil (`account_ref_hash`) e registros de consentimento pós-exclusão (`anon:<48hex>`) utilizam HMAC-SHA256 irreversível sem chave.
- **Proteção no iOS (`KeychainHelper` & Data Protection):** `KeychainHelper` usa `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` sem fallback para `UserDefaults`. Arquivos locais (`AppActivityLogManager`, `WidgetDataManager` e exportação JSON temporária) gravam com `.completeFileProtectionUnlessOpen`, e todo o estado local + App Group widget é limpo no `logout()` e troca de conta.
- **Chave Gemini protegida:** Enviada exclusivamente pelo backend no cabeçalho HTTP `x-goog-api-key` (jamais em query string `?key=` nem no aplicativo iOS).

### 3.3 Cabeçalhos HTTP, CSP e Rate Limiting (`SecurityHeadersAndRateLimitFilter`)
Todas as respostas web aplicam:
- `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()`
- Rotas `/api/**`: `Cache-Control: no-store, no-cache, must-revalidate, private` e `Pragma: no-cache`
- Páginas HTML: `Content-Security-Policy` restritiva (`default-src 'self'`, `frame-ancestors 'none'`) sem CDNs de terceiros.
- Rate Limit deslizante por IP antes da camada MVC (HTTP `429` em picos de `/api/auth/*`, `/api/admin/login` e `/api/ai/*`) e limite de upload multipart/JSON de 4 MB.

---

## 4. Conformidade Legal (LGPD, ANPD, MCI, CFN, CONFEF, CFM e CDC)

1. **Elegibilidade (LGPD Art. 14):** O app e a API exigem `ageConfirmed18Plus == true` no cadastro (menores de 18 anos são bloqueados).
2. **Consentimento Granular e Destacado (LGPD Arts. 8º e 11):**
   - Checkboxes independentes no cadastro (`LoginView.swift`): Maioridade 18+, Termos & Privacidade, Dados Sensíveis de Saúde e *(opcional)* envio à IA do Google Gemini.
   - Zero auto-consentimento silencioso em upgrades para Forma Pro ou aberturas de tela; trilha auditável append-only em `legal_consent_ledger` com versão e hash SHA-256 das políticas.
3. **Minimização & EXIF Stripping antes da IA (LGPD Art. 6º III):**
   - Imagens enviadas a `/api/ai/analyze-meal` passam por recodificação sem metadados EXIF/GPS tanto no iOS (`UIImage.jpegData`) quanto no backend (`ImageIO` / `BufferedImage` no `GeminiAiProxyService`).
   - Payloads de IA não carregam o nome civil real do titular (`"userName": "Usuário"`), e-mail, senha ou tokens.
4. **Portabilidade e Autodeterminação (LGPD Art. 18):**
   - `GET /api/privacy/consents` / `POST /api/privacy/consents`
   - `GET /api/privacy/export` (e botão nativo em *Perfil > Privacidade, Saúde & Termos* no iOS) com JSON completo do titular e lista de suboperadores.
   - `DELETE /api/auth/account` com revogação Apple/Google, eliminação imediata em cascata, tombstone e pseudonimização de ledger.
5. **Marco Civil da Internet (Lei nº 12.965/2014 Art. 15):**
   - `mci_access_logs` registra login, cadastro, logout, troca/reset de senha e exclusão com IP cifrado AES-256-GCM e `retain_until_utc = occurred + 180 dias`. Rotina `@Scheduled(cron = "0 15 3 * * *")` expurga logs vencidos.
6. **Incidentes de Segurança & Auditoria (LGPD Art. 37 & Res. CD/ANPD nº 15/2024):**
   - `admin_audit_logs` registra ações sensíveis do administrador (`GET /api/admin/audit-logs`).
   - `security_incident_logs` cataloga incidentes e prazo legal de notificação à ANPD em 3 dias úteis (`GET /api/admin/incidents`).
7. **Regulação Profissional & Consumidor (CFN, CONFEF, CFM & CDC):**
   - `PhysiologicalSafetyGuard` garante piso ≥ 1.200 kcal/dia, protege IMC alvo ≥ 18,5 kg/m² e remove cargas em kg/lb quando há menção de dor ou lesão, mantendo linguagem exclusivamente educacional/não diagnóstica.
   - Termos garantem direito de arrependimento em 7 dias (Art. 49 CDC) e foro do domicílio do consumidor no Brasil (Arts. 51 e 101 I CDC).

---

## 5. Pagamentos, Assinaturas, Cupons, StoreKit 2 & Régua de Atraso (3 Dias)

1. **Testes Locais StoreKit 2 sem Conta Apple Paga:**
   - O projeto iOS inclui [`Forma/FormaProducts.storekit`](../Forma/FormaProducts.storekit) vinculado ao esquema [`Forma.xcscheme`](../Forma.xcodeproj/xcshareddata/xcschemes/Forma.xcscheme) (`jota.Forma.pro.monthly` e `jota.Forma.pro.yearly` com 7 dias de teste grátis), permitindo testar compras e restaurações localmente com Team pessoal gratuita.
   - Em paralelo, o app e o Console Admin permitem ativação integral via **Cupons de Período Grátis** (`FREE_PERIOD_DAYS`) e descontos percentuais/fixos sem dependência da App Store.
2. **Cupons & Entradas Financeiras:**
   - Endpoints do app: `GET /api/subscriptions/plans`, `GET /api/subscriptions/me`, `POST /api/subscriptions/validate-coupon`, `POST /api/subscriptions/redeem-coupon`, `POST /api/subscriptions/sync-storekit` e `POST /api/subscriptions/acknowledge-warning`.
   - O Console Admin controla entradas manuais (`PIX`, `CARD`, `MANUAL`, `COUPON`, `STOREKIT`, `WEBHOOK`), estornos, KPIs financeiros (MRR, ARR, receita líquida 30d) e exportação CSV.
3. **Webhooks Assinados (`POST /api/webhooks/billing`):**
   - Exige assinatura HMAC-SHA256 válida no header `X-Forma-Webhook-Signature` (`sha256=<hex>`), protege idempotência por `eventId` e audita tentativas válidas e recusadas em `billing_webhook_events`.
4. **Régua Automatizada de Inadimplência (3 Dias):**
   - Ao detectar pagamento em atraso (`PAYMENT_FAILED` via webhook ou expiração em `@Scheduled`), a assinatura entra em `PAST_DUE` e dispara **3 avisos diários consecutivos** (Dia 1, Dia 2 e Dia 3) via WebSocket `/ws/account` (banner nativo no iOS + toast em tempo real).
   - Após completar 3 dias (72 horas) sem regularização, o motor executa automaticamente a política configurada no painel (**Pausar `PAUSED`** ou **Cancelar `CANCELED`**) e rebaixa o usuário para a camada `FREE`.

---

## 6. Modelo de Dados (23 Tabelas PostgreSQL + Row-Level Security)

Consulte os DDLs completos em [`backend/supabase_schema.sql`](../backend/supabase_schema.sql) e [`backend/src/main/resources/schema.sql`](../backend/src/main/resources/schema.sql):

- **Identidade & Segurança:** `users`, `deleted_accounts_tombstone`, `legal_consent_ledger`, `mci_access_logs`, `admin_audit_logs`, `security_incident_logs`, `app_config`
- **Domínio de Saúde & Auditoria de Uso:** `user_profiles`, `food_entries`, `workout_entries`, `workout_routines`, `water_entries`, `weight_entries`, `ai_analyses`, `ai_request_logs`, `user_activity_logs`
- **Faturamento, Assinaturas, Cupons & Webhooks:** `subscription_plans`, `coupons`, `coupon_redemptions`, `user_subscriptions`, `payment_entries`, `billing_webhook_events`, `dunning_notices`

Todas as 23 tabelas possuem `ENABLE ROW LEVEL SECURITY` / `FORCE ROW LEVEL SECURITY`, políticas estritas de isolamento por `user_id` e revogação total das roles públicas `anon` e `authenticated`.

---

## 7. Variáveis de Ambiente de Produção & Comandos de Verificação

Consulte o template completo em [`backend/.env.example`](../backend/.env.example). Em produção (`Render`), configure obrigatoriamente:
- `RENDER=true`, `APP_ALLOW_H2_FALLBACK=false`, `APP_ALLOW_DEV_SOCIAL_MOCK=false`
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY` (mínimo 32 bytes distintos)
- `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` (hash BCrypt recomendado), `APP_ADMIN_KEY`
- `GEMINI_API_KEY`, `RESEND_API_KEY`, `MAIL_FROM`, `GOOGLE_CLIENT_ID`, `APPLE_BUNDLE_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`

### Suítes Locais de Validação
```bash
# Backend Spring Boot (18+ testes JUnit/MockMvc)
mvn -f backend/pom.xml test

# Painel Administrativo React + Vite
npm --prefix admin-web run build

# Compilação iOS (sem bootar simuladores)
xcodebuild -project Forma.xcodeproj -scheme Forma -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```
