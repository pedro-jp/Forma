# Forma — Nutrição, Hidratação & Treinos Inteligentes

Ecossistema multiplataforma (iOS, watchOS, Widgets/Ilha Dinâmica, API Spring Boot, Console Admin React/Vite e Banco PostgreSQL/Supabase) projetado com arquitetura **fail-safe**, **AES-256-GCM em repouso**, **Row-Level Security** e conformidade rigorosa com **LGPD (Lei 13.709/2018)**, **Marco Civil da Internet (Lei 12.965/2014)**, **ANPD**, **CFN/CONFEF/CFM** e **CDC**.

## Links & Rotas Principais
- **Documentação Completa (Markdown):** [docs/DOCUMENTACAO_COMPLETA_SITE.md](docs/DOCUMENTACAO_COMPLETA_SITE.md)
- **Portal Público & Trust Center Web:** `/`
- **Documentação Web Interativa & API:** `/docs` (`/documentacao`)
- **Política de Privacidade (LGPD):** `/privacy` (`/privacidade`)
- **Termos de Uso (EULA 18+ & Avisos de Saúde):** `/terms` (`/termos`)
- **Console Administrativo em Tempo Real:** `/admin/`
- **Health Check Minimalista:** `/api/health`

## Segurança & Conformidade Implementadas
- **Autenticação & Sessão:** JWT HMAC-SHA256 obrigatório via `Authorization: Bearer` (legado `X-User-Id` desativado), revogação imediata via `token_version` no logout/reset/troca de senha, tombstone anti-ressurreição pós-exclusão, lockout OTP em 5 falhas, senha ≥ 8 caracteres (BCrypt) e Apple/Google OAuth verificados em servidor.
- **Criptografia:** `FieldEncryptionService` (AES-256-GCM `ENC:v1:` + HMAC-SHA256) para segredos, tokens OAuth e IPs MCI/auditoria; Keychain `AfterFirstUnlockThisDeviceOnly` e `.completeFileProtectionUnlessOpen` no iOS.
- **IA Opt-In com Minimização:** Nenhuma ativação automática silenciosa; limpeza de EXIF local e backend, omissão do nome civil e e-mail nos prompts e guardrails fisiológicos (`>= 1.200 kcal`, IMC `>= 18,5`, remoção de cargas absolutas em menções a lesões).
- **Direitos LGPD & Marco Civil:** Portabilidade em JSON (`GET /api/privacy/export` e botão no iOS), ledger imutável de consentimentos com hash SHA-256, logs MCI de 180 dias com expurgo diário e trilha de auditoria/incidentes ANPD.

## Configuração & Validação

Copie [`backend/.env.example`](backend/.env.example) para definir as variáveis de produção (`SPRING_DATASOURCE_URL`, `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY`, `APP_ADMIN_*`, `GEMINI_API_KEY`, etc.).

```bash
# 1. Testes integrados do backend Spring Boot
mvn -f backend/pom.xml test

# 2. Build do Console Administrativo React + Vite -> backend static/admin
npm --prefix admin-web run build

# 3. Verificação de compilação iOS (sem simuladores)
xcodebuild -project Forma.xcodeproj -scheme Forma -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```
