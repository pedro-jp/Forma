//
//  LegalPrivacySheet.swift
//  Forma
//
//  Política de Privacidade, aviso de saúde/nutrição e fluxos de redefinição e troca de senha.
//

import SwiftUI
import SwiftData

private struct PrivacyLogPreviewItem: Identifiable {
    let id: String
    let badge: String
    let title: String
    let detail: String
    let icon: String
    let sortDate: Date
}

struct LegalPrivacySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @ObservedObject private var aiConsent = AIDataConsentManager.shared
    @ObservedObject private var authManager = AuthManager.shared

    @State private var isExportingData: Bool = false
    @State private var exportErrorMessage: String? = nil
    @State private var exportedFileURL: URL? = nil
    @State private var showExportShareSheet: Bool = false
    @State private var isLoadingLogs: Bool = false
    @State private var logPreviewItems: [PrivacyLogPreviewItem] = []
    @State private var logCounts: (consents: Int, accesses: Int, aiCalls: Int, activities: Int) = (0, 0, 0, 0)
    @State private var fallbackNoticeMessage: String? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "hand.raised.shield.fill")
                                    .foregroundStyle(FormaTheme.Colors.brand)
                                Text("Privacidade & LGPD (Lei 13.709/2018)")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                            Text("O Forma coleta apenas os dados necessários para calcular suas metas diárias e sincronizar seu progresso: nome, e-mail, peso, altura, objetivo corporal, refeições (incluindo fotos ou áudio enviados por você), ingestão de água, treinos e fichas.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Controlador: Forma App • Encarregado pelo Tratamento de Dados (DPO): privacidade@forma.app. Dados em trânsito cifrados via TLS 1.3 e senhas protegidas por BCrypt. Suboperadores (LGPD Art. 33): Supabase Inc. (PostgreSQL com RLS e AES-256-GCM em repouso), Render Services Inc. (API), Google LLC (Gemini API opt-in sem nome/EXIF e sem treino de modelos), Resend Inc. (OTP de senha) e Apple Inc. (Sign in with Apple / HealthKit local). Registros de acesso do Marco Civil da Internet (Art. 15) são mantidos com IP cifrado por 180 dias e expurgados automaticamente.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("Seus dados nunca são vendidos a terceiros, não alimentam modelos públicos de IA e não são usados para publicidade ou rastreamento entre aplicativos. Você pode peticionar perante a ANPD (LGPD Art. 18 §1º) a qualquer tempo.")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)

                            HStack(spacing: 14) {
                                if let privacyURL = URL(string: "https://forma-94vp.onrender.com/privacy.html") {
                                    Link(destination: privacyURL) {
                                        Label("Política de Privacidade", systemImage: "arrow.up.right.square")
                                            .font(.caption.weight(.semibold))
                                            .foregroundStyle(FormaTheme.Colors.brand)
                                    }
                                }
                                if let termsURL = URL(string: "https://forma-94vp.onrender.com/terms.html") {
                                    Link(destination: termsURL) {
                                        Label("Termos de Uso (EULA)", systemImage: "doc.text")
                                            .font(.caption.weight(.semibold))
                                            .foregroundStyle(FormaTheme.Colors.brand)
                                    }
                                }
                            }
                            .padding(.top, 2)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 8) {
                                Image(systemName: "sparkles")
                                    .foregroundStyle(FormaTheme.Colors.aiAccent)
                                Text("IA do Google Gemini (Google LLC)")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                            Text("O Forma utiliza a inteligência artificial do Google Gemini (Google LLC). Quando você autoriza o uso da IA na nuvem, fotos ou descrições de refeições, descrições de treinos, seu nome de exibição, medidas corporais (peso, altura e objetivo) e histórico recente de progresso são enviados sob demanda à Google Gemini API por conexão criptografada exclusivamente para gerar suas estimativas e análises no app.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Seu e-mail, senha e credenciais Apple/Google nunca são compartilhados com a IA do Google Gemini.")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.primaryText)

                            Divider()

                            Toggle(
                                isOn: Binding(
                                    get: { aiConsent.hasGrantedConsent },
                                    set: { aiConsent.setConsent($0) }
                                )
                            ) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Autorizar IA do Google Gemini")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(FormaTheme.Colors.primaryText)
                                    Text(aiConsent.hasGrantedConsent
                                         ? "Ativo • Você pode revogar a qualquer momento."
                                         : "Desativado • Apenas cálculos locais e base TACO estão em uso.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .tint(FormaTheme.Colors.brand)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "heart.text.square.fill")
                                    .foregroundStyle(FormaTheme.Colors.energyGreen)
                                Text("Integração com o Apple Saúde")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                            Text("Com sua autorização explícita no iOS, o Forma lê seus passos, calorias em movimento e treinos registrados no Apple Saúde, além de salvar os treinos concluídos no app. Você pode gerenciar ou revogar essas permissões a qualquer momento nos Ajustes do iPhone > Saúde.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "stethoscope")
                                    .foregroundStyle(FormaTheme.Colors.warning)
                                Text("Aviso de Saúde & Fontes Científicas")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                            Text("As estimativas calóricas, cálculos de IMC/TMB, metas diárias e orientações geradas com a IA do Google Gemini têm caráter exclusivamente informativo e educacional. O Forma não realiza diagnóstico médico ou clínico e não substitui médico, nutricionista ou profissional de educação física.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Referências Científicas Oficiais:")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)

                                if let mifflinURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/2305711/") {
                                    Link("• Taxa Metabólica Basal — Equação de Mifflin-St Jeor (PubMed)", destination: mifflinURL)
                                        .font(.caption)
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                }
                                if let tacoURL = URL(string: "https://www.cfn.org.br/wp-content/uploads/2017/03/taco_4_edicao_ampliada_e_revisada.pdf") {
                                    Link("• Composição de Alimentos — Tabela TACO 4ª ed. (NEPA/UNICAMP)", destination: tacoURL)
                                        .font(.caption)
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                }
                                if let whoURL = URL(string: "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight") {
                                    Link("• Classificação de IMC & Peso Saudável — OMS (WHO)", destination: whoURL)
                                        .font(.caption)
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                }
                                if let metURL = URL(string: "https://pacompendium.com/") {
                                    Link("• Gasto Calórico em Exercícios (METs) — Compendium of Physical Activities", destination: metURL)
                                        .font(.caption)
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                }
                                if let issnURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/28642676/") {
                                    Link("• Proteínas e Exercício Físico — ISSN Position Stand (PubMed)", destination: issnURL)
                                        .font(.caption)
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                }
                            }
                            .padding(.top, 2)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.down.doc.fill")
                                    .foregroundStyle(FormaTheme.Colors.brand)
                                Text("Portabilidade de Dados (LGPD Art. 18 V)")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Spacer(minLength: 4)
                                if authManager.isAuthenticated {
                                    Button {
                                        Task { await loadAccountLogs() }
                                    } label: {
                                        if isLoadingLogs {
                                            ProgressView()
                                                .controlSize(.small)
                                        } else {
                                            Image(systemName: "arrow.clockwise")
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(FormaTheme.Colors.brand)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(isLoadingLogs)
                                    .accessibilityLabel("Atualizar registros de auditoria e atividades")
                                }
                            }
                            Text("Exporte a qualquer momento uma cópia completa dos seus dados cadastrais, perfil, refeições, treinos, hidratação, histórico de consentimentos e registros de auditoria de IA em formato JSON.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

                            if authManager.isAuthenticated {
                                HStack(spacing: 8) {
                                    logMetricChip(title: "Consentimentos", count: logCounts.consents)
                                    logMetricChip(title: "Acessos", count: logCounts.accesses)
                                    logMetricChip(title: "IA", count: logCounts.aiCalls)
                                    logMetricChip(title: "Atividades", count: logCounts.activities)
                                }

                                if let fallbackNoticeMessage {
                                    FormaBanner(message: fallbackNoticeMessage, style: .info)
                                }

                                if logPreviewItems.isEmpty && !isLoadingLogs {
                                    Text("Nenhum registro recente encontrado.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                } else if !logPreviewItems.isEmpty {
                                    VStack(alignment: .leading, spacing: 8) {
                                        ForEach(logPreviewItems.prefix(6)) { item in
                                            HStack(alignment: .top, spacing: 10) {
                                                Image(systemName: item.icon)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundStyle(FormaTheme.Colors.brand)
                                                    .frame(width: 18, height: 18)
                                                    .padding(.top, 1)

                                                VStack(alignment: .leading, spacing: 2) {
                                                    HStack(spacing: 6) {
                                                        Text(item.badge)
                                                            .font(.caption2.weight(.bold))
                                                            .foregroundStyle(FormaTheme.Colors.brand)
                                                            .padding(.horizontal, 6)
                                                            .padding(.vertical, 2)
                                                            .background(FormaTheme.Colors.brand.opacity(0.12))
                                                            .clipShape(Capsule())
                                                        Text(item.title)
                                                            .font(.caption.weight(.semibold))
                                                            .foregroundStyle(FormaTheme.Colors.primaryText)
                                                            .lineLimit(1)
                                                    }
                                                    Text(item.detail)
                                                        .font(.caption2)
                                                        .foregroundStyle(.secondary)
                                                        .lineLimit(2)
                                                }
                                                Spacer(minLength: 0)
                                            }
                                        }
                                    }
                                    .padding(10)
                                    .background(Color.primary.opacity(0.04))
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                }
                            }

                            if let exportErrorMessage {
                                FormaBanner(message: exportErrorMessage, style: .error)
                            }

                            Button {
                                exportLgpdData()
                            } label: {
                                HStack(spacing: 8) {
                                    if isExportingData {
                                        ProgressView().tint(.white)
                                    } else {
                                        Image(systemName: "square.and.arrow.up")
                                    }
                                    Text(isExportingData ? "Gerando arquivo JSON..." : "Exportar Meus Dados (JSON — LGPD Art. 18)")
                                        .font(.subheadline.weight(.semibold))
                                }
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(authManager.isAuthenticated ? AnyShapeStyle(FormaTheme.Colors.brandGradient) : AnyShapeStyle(Color.gray.opacity(0.45)))
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(!authManager.isAuthenticated || isExportingData)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "trash.slash.fill")
                                    .foregroundStyle(FormaTheme.Colors.danger)
                                Text("Direitos LGPD & Exclusão de Conta")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                            Text("Você pode excluir permanentemente sua conta, todos os dados armazenados nos nossos servidores e revogar imediatamente os tokens de acesso do Sign in with Apple e do Google a qualquer momento acessando Perfil > Conta > Excluir Minha Conta.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 16)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Privacidade & Saúde")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") {
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $showExportShareSheet) {
                if let url = exportedFileURL {
                    LgpdShareActivitySheet(activityItems: [url])
                }
            }
            .task(id: authManager.currentUserId) {
                await loadAccountLogs()
            }
        }
    }

    @ViewBuilder
    private func logMetricChip(title: String, count: Int) -> some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.caption.weight(.bold))
                .foregroundStyle(FormaTheme.Colors.primaryText)
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .padding(.horizontal, 4)
        .background(Color.primary.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func isLegacyOrOfflineFallbackError(_ error: Error) -> Bool {
        if let apiError = error as? FormaAPIError {
            switch apiError {
            case .unreachableServer:
                return true
            case .serverError(let statusCode, let message):
                return statusCode == 404 || message.localizedCaseInsensitiveContains("No static resource")
            default:
                break
            }
        }
        return error.localizedDescription.localizedCaseInsensitiveContains("No static resource")
    }

    private func loadAccountLogs() async {
        guard authManager.isAuthenticated else {
            logCounts = (0, 0, 0, 0)
            logPreviewItems = []
            fallbackNoticeMessage = nil
            return
        }
        isLoadingLogs = true
        exportErrorMessage = nil
        defer { isLoadingLogs = false }

        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/privacy/export",
                method: "GET",
                authenticated: true,
                timeout: 18.0
            )
            if parseServerExportForPreview(data) {
                fallbackNoticeMessage = nil
            }
        } catch {
            if isLegacyOrOfflineFallbackError(error) {
                _ = await pullAndBuildLocalFallbackPayload()
                fallbackNoticeMessage = "Exibindo registros sincronizados no dispositivo (atualização da nuvem em andamento)."
            } else {
                exportErrorMessage = error.localizedDescription
            }
        }
    }

    @discardableResult
    private func parseServerExportForPreview(_ data: Data) -> Bool {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return false
        }
        let consentsObj = root["consents"] as? [String: Any]
        let consentHistory = consentsObj?["history"] as? [[String: Any]] ?? []
        let mciLogs = root["mciAccessLogs"] as? [[String: Any]] ?? []
        let aiLogs = root["recentAiRequestAudit"] as? [[String: Any]] ?? []
        let activityLogs = root["activityLogs"] as? [[String: Any]] ?? []

        logCounts = (
            consents: consentHistory.count,
            accesses: mciLogs.count,
            aiCalls: aiLogs.count,
            activities: activityLogs.count
        )

        var items: [PrivacyLogPreviewItem] = []

        for entry in consentHistory {
            let id = (entry["id"] as? String) ?? UUID().uuidString
            let type = (entry["consentType"] as? String) ?? "CONSENT"
            let granted = (entry["granted"] as? Bool) ?? false
            let version = (entry["documentVersion"] as? String) ?? ""
            let date = parseIsoDate(entry["recordedAtUtc"]) ?? Date.distantPast
            items.append(PrivacyLogPreviewItem(
                id: "consent-\(id)",
                badge: "LGPD",
                title: "\(type) (\(granted ? "Autorizado" : "Revogado"))",
                detail: "\(formatShortDate(date)) • Versão \(version)",
                icon: "checkmark.shield.fill",
                sortDate: date
            ))
        }

        for entry in mciLogs {
            let id = (entry["id"] as? String) ?? UUID().uuidString
            let eventType = (entry["eventType"] as? String) ?? "ACCESS"
            let date = parseIsoDate(entry["accessedAtUtc"]) ?? Date.distantPast
            items.append(PrivacyLogPreviewItem(
                id: "mci-\(id)",
                badge: "MCI",
                title: eventType,
                detail: "\(formatShortDate(date)) • Retenção 180 dias (IP cifrado)",
                icon: "network.badge.shield.half.filled",
                sortDate: date
            ))
        }

        for entry in aiLogs {
            let id = (entry["id"] as? String) ?? UUID().uuidString
            let endpoint = (entry["endpoint"] as? String) ?? "/api/ai"
            let status = (entry["status"] as? String) ?? "OK"
            let durationMs = (entry["durationMs"] as? Int) ?? 0
            let date = parseIsoDate(entry["createdAt"]) ?? Date.distantPast
            items.append(PrivacyLogPreviewItem(
                id: "ai-\(id)",
                badge: "IA",
                title: "\(endpoint) • \(status)",
                detail: "\(formatShortDate(date)) • \(durationMs) ms",
                icon: "sparkles",
                sortDate: date
            ))
        }

        for entry in activityLogs {
            let id = (entry["id"] as? String) ?? UUID().uuidString
            let title = (entry["title"] as? String) ?? "Atividade"
            let details = (entry["details"] as? String) ?? ""
            let date = parseIsoDate(entry["timestamp"]) ?? Date.distantPast
            let detailText = details.isEmpty ? formatShortDate(date) : "\(formatShortDate(date)) • \(details)"
            items.append(PrivacyLogPreviewItem(
                id: "act-\(id)",
                badge: "ATIVIDADE",
                title: title,
                detail: detailText,
                icon: "list.bullet.rectangle",
                sortDate: date
            ))
        }

        items.sort { $0.sortDate > $1.sortDate }
        logPreviewItems = Array(items.prefix(6))
        return true
    }

    private func pullAndBuildLocalFallbackPayload() async -> [String: Any] {
        var remoteConsentCount = aiConsent.hasGrantedConsent ? 1 : 0
        if let userId = authManager.currentUserId, !userId.isEmpty {
            let encodedUserId = userId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? userId
            if let pullData = try? await FormaAPIClient.shared.requestData(
                path: "/api/sync/pull?userId=\(encodedUserId)",
                method: "GET",
                authenticated: true,
                timeout: 15.0,
                interceptAccountStatus: false
            ),
               let pullJson = try? JSONSerialization.jsonObject(with: pullData) as? [String: Any],
               let remoteLogs = pullJson["activityLogs"] as? [[String: Any]] {
                var records: [ActivityLogRecord] = []
                for l in remoteLogs {
                    guard let id = l["id"] as? String else { continue }
                    let timestamp = parseIsoDate(l["timestamp"]) ?? Date()
                    records.append(ActivityLogRecord(
                        id: id,
                        timestamp: timestamp,
                        category: (l["category"] as? String) ?? "activity",
                        title: (l["title"] as? String) ?? "",
                        isSnack: l["isSnack"] as? Bool,
                        caloriesChange: (l["caloriesChange"] as? Double) ?? 0.0,
                        proteinGrams: l["proteinGrams"] as? Double,
                        carbsGrams: l["carbsGrams"] as? Double,
                        fatGrams: l["fatGrams"] as? Double,
                        waterMilliliters: l["waterMilliliters"] as? Int,
                        weightKg: l["weightKg"] as? Double,
                        details: l["details"] as? String
                    ))
                }
                if !records.isEmpty {
                    AppActivityLogManager.shared.mergeRemoteRecords(records)
                }
            }

            if let consentData = try? await FormaAPIClient.shared.requestData(
                path: "/api/privacy/consents",
                method: "GET",
                authenticated: true,
                timeout: 10.0,
                interceptAccountStatus: false
            ),
               let consentJson = try? JSONSerialization.jsonObject(with: consentData) as? [String: Any],
               let history = consentJson["history"] as? [[String: Any]] {
                remoteConsentCount = max(remoteConsentCount, history.count)
            }
        }

        let profiles = (try? modelContext.fetch(FetchDescriptor<Profile>())) ?? []
        let foods = (try? modelContext.fetch(FetchDescriptor<FoodEntry>(sortBy: [SortDescriptor(\.date, order: .reverse)]))) ?? []
        let workouts = (try? modelContext.fetch(FetchDescriptor<WorkoutEntry>(sortBy: [SortDescriptor(\.date, order: .reverse)]))) ?? []
        let routines = (try? modelContext.fetch(FetchDescriptor<WorkoutRoutine>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))) ?? []
        let waters = (try? modelContext.fetch(FetchDescriptor<WaterEntry>(sortBy: [SortDescriptor(\.date, order: .reverse)]))) ?? []
        let weights = (try? modelContext.fetch(FetchDescriptor<WeightEntry>(sortBy: [SortDescriptor(\.date, order: .reverse)]))) ?? []
        let activityRecords = AppActivityLogManager.shared.getAllRecords().sorted { $0.timestamp > $1.timestamp }

        logCounts = (
            consents: remoteConsentCount,
            accesses: 0,
            aiCalls: 0,
            activities: activityRecords.count
        )

        var previews: [PrivacyLogPreviewItem] = activityRecords.prefix(6).map { rec in
            let date = parseIsoDate(rec.timestamp) ?? Date.distantPast
            let detail = (rec.details ?? "").isEmpty ? formatShortDate(date) : "\(formatShortDate(date)) • \(rec.details ?? "")"
            return PrivacyLogPreviewItem(
                id: "local-act-\(rec.id)",
                badge: rec.category.uppercased(),
                title: rec.title,
                detail: detail,
                icon: "list.bullet.rectangle",
                sortDate: date
            )
        }
        previews.sort { $0.sortDate > $1.sortDate }
        logPreviewItems = Array(previews.prefix(6))

        let isoFormatter = ISO8601DateFormatter()
        let profileDict: Any = profiles.first.map { p in
            [
                "nome": p.nome,
                "email": p.email,
                "goal": p.goalRaw,
                "currentWeightKg": p.currentWeight,
                "targetWeightKg": p.targetWeight,
                "heightMeters": p.height,
                "targetCalorieIntake": p.targetCalorieIntake,
                "targetCalorieBurn": p.targetCalorieBurn,
                "targetProtein": p.targetProtein,
                "targetCarbohydrates": p.targetCarbohydrates,
                "targetFat": p.targetFat,
                "targetWaterMilliliters": p.targetWaterMilliliters,
                "biologicalSex": p.biologicalSex.rawValue
            ] as [String: Any]
        } ?? NSNull()

        let activityDictionaries: [[String: Any]] = activityRecords.map { rec in
            var dict: [String: Any] = [
                "id": rec.id,
                "timestamp": rec.timestamp,
                "category": rec.category,
                "title": rec.title,
                "caloriesChange": rec.caloriesChange
            ]
            if let isSnack = rec.isSnack { dict["isSnack"] = isSnack }
            if let prot = rec.proteinGrams { dict["proteinGrams"] = prot }
            if let carbs = rec.carbsGrams { dict["carbsGrams"] = carbs }
            if let fat = rec.fatGrams { dict["fatGrams"] = fat }
            if let water = rec.waterMilliliters { dict["waterMilliliters"] = water }
            if let weight = rec.weightKg { dict["weightKg"] = weight }
            if let details = rec.details { dict["details"] = details }
            return dict
        }

        return [
            "exportSchema": "forma-lgpd-art18-portability-v1",
            "exportSource": "ios-failsafe-sync-fallback",
            "metadata": [
                "application": "Forma — Nutrição & Treinos Inteligentes",
                "exportedAtUtc": isoFormatter.string(from: Date()),
                "policyVersion": "2026.11-LGPD",
                "dpoContactEmail": "privacidade@forma.app"
            ],
            "account": [
                "id": authManager.currentUserId ?? "",
                "email": authManager.currentUserEmail ?? "",
                "name": authManager.currentUserName ?? "",
                "authProvider": authManager.currentAuthProvider ?? "",
                "accountTier": authManager.accountTier.rawValue
            ],
            "consents": [
                "aiConsentGranted": aiConsent.hasGrantedConsent,
                "policyVersion": "2026.11-LGPD"
            ],
            "profile": profileDict,
            "foods": foods.map { f in
                [
                    "id": f.id.uuidString,
                    "name": f.name,
                    "brand": f.brand,
                    "mealType": f.mealTypeRaw,
                    "portionGrams": f.portionGrams,
                    "calories": f.calories,
                    "protein": f.protein,
                    "carbohydrates": f.carbohydrates,
                    "fat": f.fat,
                    "date": isoFormatter.string(from: f.date)
                ]
            },
            "workouts": workouts.map { w in
                [
                    "id": w.id.uuidString,
                    "title": w.title,
                    "workoutType": w.workoutTypeRaw,
                    "durationMinutes": w.durationMinutes,
                    "caloriesBurned": w.caloriesBurned,
                    "notes": w.notes,
                    "date": isoFormatter.string(from: w.date)
                ]
            },
            "routines": routines.map { r in
                [
                    "id": r.id.uuidString,
                    "title": r.title,
                    "workoutType": r.workoutTypeRaw,
                    "targetMuscles": r.targetMuscles,
                    "estimatedDurationMinutes": r.estimatedDurationMinutes,
                    "estimatedCalories": r.estimatedCalories,
                    "isFavorite": r.isFavorite,
                    "createdAt": isoFormatter.string(from: r.createdAt)
                ]
            },
            "waters": waters.map { wt in
                [
                    "id": wt.id.uuidString,
                    "milliliters": wt.milliliters,
                    "date": isoFormatter.string(from: wt.date)
                ]
            },
            "weights": weights.map { wg in
                [
                    "id": wg.id.uuidString,
                    "weight": wg.weight,
                    "notes": wg.notes,
                    "date": isoFormatter.string(from: wg.date)
                ]
            },
            "activityLogs": activityDictionaries
        ]
    }

    private func parseIsoDate(_ raw: Any?) -> Date? {
        guard let str = raw as? String, !str.isEmpty else { return nil }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = fractional.date(from: str) {
            return parsed
        }
        let standard = ISO8601DateFormatter()
        standard.formatOptions = [.withInternetDateTime]
        return standard.date(from: str)
    }

    private func formatShortDate(_ date: Date) -> String {
        guard date != Date.distantPast else { return "—" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.dateFormat = "dd/MM HH:mm"
        return formatter.string(from: date)
    }

    private func exportLgpdData() {
        guard authManager.isAuthenticated else {
            exportErrorMessage = "Entre na sua conta para exportar os dados armazenados na nuvem."
            return
        }
        exportErrorMessage = nil
        isExportingData = true
        Task {
            do {
                let data = try await FormaAPIClient.shared.requestData(
                    path: "/api/privacy/export",
                    method: "GET",
                    authenticated: true,
                    timeout: 20.0
                )
                _ = parseServerExportForPreview(data)
                fallbackNoticeMessage = nil
                let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("forma-dados-lgpd.json")
                try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
                exportedFileURL = fileURL
                isExportingData = false
                showExportShareSheet = true
            } catch {
                if isLegacyOrOfflineFallbackError(error) {
                    let fallbackPayload = await pullAndBuildLocalFallbackPayload()
                    do {
                        let fallbackData = try JSONSerialization.data(withJSONObject: fallbackPayload, options: [.prettyPrinted, .sortedKeys])
                        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("forma-dados-lgpd.json")
                        try fallbackData.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
                        exportedFileURL = fileURL
                        fallbackNoticeMessage = "Arquivo gerado com os registros sincronizados no dispositivo."
                        exportErrorMessage = nil
                        isExportingData = false
                        showExportShareSheet = true
                        return
                    } catch {
                        isExportingData = false
                        exportErrorMessage = error.localizedDescription
                        return
                    }
                }
                isExportingData = false
                exportErrorMessage = error.localizedDescription
            }
        }
    }
}

private struct LgpdShareActivitySheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct PasswordRecoverySheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var authManager = AuthManager.shared

    private enum RecoveryFocusField {
        case email
        case code
        case newPassword
    }

    @State private var emailText: String
    @State private var codeText: String = ""
    @State private var newPasswordText: String = ""
    @State private var showNewPassword: Bool = false
    @State private var stepSentCode: Bool = false
    @State private var statusMessage: String? = nil
    @State private var errorMessage: String? = nil
    @State private var isSubmitting: Bool = false
    @FocusState private var focusedField: RecoveryFocusField?

    var onRecoverySuccess: () -> Void

    init(initialEmail: String = "", onRecoverySuccess: @escaping () -> Void) {
        _emailText = State(initialValue: initialEmail)
        self.onRecoverySuccess = onRecoverySuccess
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(stepSentCode
                         ? "Digite o código de 6 dígitos enviado para seu e-mail e escolha uma nova senha."
                         : "Informe o e-mail da sua conta para receber um código de 6 dígitos e criar uma nova senha.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    VStack(spacing: 12) {
                        HStack(spacing: 12) {
                            Image(systemName: "envelope.fill")
                                .foregroundStyle(focusedField == .email ? FormaTheme.Colors.brand : .secondary)
                                .frame(width: 20)
                            TextField("Seu e-mail cadastrado", text: $emailText)
                                .keyboardType(.emailAddress)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .focused($focusedField, equals: .email)
                        }
                        .padding(14)
                        .background(FormaTheme.Colors.cardSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(
                                    focusedField == .email ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                    lineWidth: focusedField == .email ? 1.4 : 0.8
                                )
                        )

                        if stepSentCode {
                            HStack(spacing: 12) {
                                Image(systemName: "number")
                                    .foregroundStyle(focusedField == .code ? FormaTheme.Colors.brand : .secondary)
                                    .frame(width: 20)
                                TextField("Código de 6 dígitos", text: $codeText)
                                    .keyboardType(.numberPad)
                                    .focused($focusedField, equals: .code)
                            }
                            .padding(14)
                            .background(FormaTheme.Colors.cardSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(
                                        focusedField == .code ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                        lineWidth: focusedField == .code ? 1.4 : 0.8
                                    )
                            )

                            HStack(spacing: 12) {
                                Image(systemName: "lock.fill")
                                    .foregroundStyle(focusedField == .newPassword ? FormaTheme.Colors.brand : .secondary)
                                    .frame(width: 20)
                                Group {
                                    if showNewPassword {
                                        TextField("Nova senha (mín. 8 caracteres)", text: $newPasswordText)
                                            .textInputAutocapitalization(.never)
                                            .autocorrectionDisabled()
                                    } else {
                                        SecureField("Nova senha (mín. 8 caracteres)", text: $newPasswordText)
                                    }
                                }
                                .textContentType(.newPassword)
                                .focused($focusedField, equals: .newPassword)

                                Button {
                                    showNewPassword.toggle()
                                } label: {
                                    Image(systemName: showNewPassword ? "eye.slash.fill" : "eye.fill")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(14)
                            .background(FormaTheme.Colors.cardSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(
                                        focusedField == .newPassword ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                        lineWidth: focusedField == .newPassword ? 1.4 : 0.8
                                    )
                            )
                        }
                    }

                    if let statusMessage {
                        FormaBanner(message: statusMessage, style: .info)
                    }
                    if let errorMessage {
                        FormaBanner(message: errorMessage, style: .error)
                    }

                    Button {
                        if stepSentCode {
                            confirmReset()
                        } else {
                            sendCode()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if isSubmitting {
                                ProgressView().tint(.white)
                            }
                            Text(stepSentCode ? "Redefinir Senha e Entrar" : "Enviar Código de Recuperação")
                                .font(.headline)
                                .foregroundStyle(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(FormaTheme.Colors.brandGradient)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(isSubmitting)

                    if !stepSentCode {
                        Button {
                            stepSentCode = true
                        } label: {
                            Text("Já tenho um código de 6 dígitos")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Recuperar Senha")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }

    private func sendCode() {
        errorMessage = nil
        statusMessage = nil
        isSubmitting = true
        Task {
            do {
                let message = try await authManager.requestPasswordReset(email: emailText)
                statusMessage = message
                stepSentCode = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isSubmitting = false
        }
    }

    private func confirmReset() {
        errorMessage = nil
        isSubmitting = true
        Task {
            do {
                try await authManager.resetPassword(
                    email: emailText,
                    code: codeText,
                    newPassword: newPasswordText
                )
                isSubmitting = false
                dismiss()
                onRecoverySuccess()
            } catch {
                errorMessage = error.localizedDescription
                isSubmitting = false
            }
        }
    }
}

struct ChangePasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var authManager = AuthManager.shared

    private enum ChangeFocusField {
        case current
        case newPassword
    }

    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var showCurrentPassword: Bool = false
    @State private var showNewPassword: Bool = false
    @State private var errorMessage: String? = nil
    @State private var isSaving: Bool = false
    @FocusState private var focusedField: ChangeFocusField?

    var onSaved: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Atualize a senha de acesso da sua conta Forma.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    HStack(spacing: 12) {
                        Image(systemName: "lock.fill")
                            .foregroundStyle(focusedField == .current ? FormaTheme.Colors.brand : .secondary)
                            .frame(width: 20)
                        Group {
                            if showCurrentPassword {
                                TextField("Senha atual", text: $currentPassword)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                            } else {
                                SecureField("Senha atual", text: $currentPassword)
                            }
                        }
                        .textContentType(.password)
                        .focused($focusedField, equals: .current)

                        Button {
                            showCurrentPassword.toggle()
                        } label: {
                            Image(systemName: showCurrentPassword ? "eye.slash.fill" : "eye.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(14)
                    .background(FormaTheme.Colors.cardSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(
                                focusedField == .current ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                lineWidth: focusedField == .current ? 1.4 : 0.8
                            )
                    )

                    HStack(spacing: 12) {
                        Image(systemName: "key.fill")
                            .foregroundStyle(focusedField == .newPassword ? FormaTheme.Colors.brand : .secondary)
                            .frame(width: 20)
                        Group {
                            if showNewPassword {
                                TextField("Nova senha (mín. 8 caracteres)", text: $newPassword)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                            } else {
                                SecureField("Nova senha (mín. 8 caracteres)", text: $newPassword)
                            }
                        }
                        .textContentType(.newPassword)
                        .focused($focusedField, equals: .newPassword)

                        Button {
                            showNewPassword.toggle()
                        } label: {
                            Image(systemName: showNewPassword ? "eye.slash.fill" : "eye.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(14)
                    .background(FormaTheme.Colors.cardSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(
                                focusedField == .newPassword ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                lineWidth: focusedField == .newPassword ? 1.4 : 0.8
                            )
                    )

                    if let errorMessage {
                        FormaBanner(message: errorMessage, style: .error)
                    }

                    Button {
                        saveNewPassword()
                    } label: {
                        HStack(spacing: 8) {
                            if isSaving {
                                ProgressView().tint(.white)
                            }
                            Text("Salvar Nova Senha")
                                .font(.headline)
                                .foregroundStyle(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(FormaTheme.Colors.brandGradient)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(isSaving)
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Alterar Senha")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }

    private func saveNewPassword() {
        errorMessage = nil
        isSaving = true
        Task {
            do {
                try await authManager.changePassword(
                    currentPassword: currentPassword,
                    newPassword: newPassword
                )
                isSaving = false
                dismiss()
                onSaved()
            } catch {
                errorMessage = error.localizedDescription
                isSaving = false
            }
        }
    }
}
