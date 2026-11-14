//
//  ProfileView.swift
//  Forma
//
//  Aba Perfil & Ajustes Fisiológicos do Forma com validação flexível, recálculo contextual e gestão de conta.
//

import SwiftUI
import SwiftData

struct ProfileView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var profiles: [Profile]
    @Query(sort: \WeightEntry.date, order: .reverse) private var weightEntries: [WeightEntry]

    @FocusState private var isHeightFocused: Bool
    @FocusState private var isWeightFocused: Bool
    @State private var weightInputText: String = ""
    @State private var heightInputText: String = ""
    @State private var isLoggingOut: Bool = false
    @State private var isDeletingAccount: Bool = false
    @State private var showDeleteAccountAlert: Bool = false
    @State private var deleteErrorMessage: String? = nil
    @State private var showLegalPrivacySheet: Bool = false
    @State private var showChangePasswordSheet: Bool = false
    @State private var showSubscriptionSheet: Bool = false

    @ObservedObject private var authManager = AuthManager.shared
    @ObservedObject private var syncManager = BackendSyncManager.shared
    @ObservedObject private var subscriptionManager = SubscriptionManager.shared

    var body: some View {
        NavigationStack {
            ScrollView {
                if let profile = profiles.first {
                    @Bindable var bindableProfile = profile
                    let fullCtx = FormaDataStore.shared.buildRecalculationContext(in: modelContext)
                    let contextualMetrics = bindableProfile.contextualMetrics(context: fullCtx)

                    VStack(alignment: .leading, spacing: FormaTheme.Spacing.sectionGap) {
                        FormaScreenHeader(
                            title: "Perfil & Ajustes",
                            subtitle: "Medidas, metas diárias e sua conta"
                        )

                        profileHeaderCard(profile: bindableProfile)
                        overdueSubscriptionBanner
                        accountTierCard
                        fitnessGoalCard(profile: bindableProfile)

                            ProfileTargetWeightCard(
                                profile: bindableProfile,
                                onAdjustCurrentWeight: { val, syncText, record in
                                    applyWeightChange(val, profile: bindableProfile, syncText: syncText, recordEntry: record)
                                },
                                onFinishWeightSlider: {
                                    syncTodayWeightEntry(weight: bindableProfile.currentWeight)
                                    try? modelContext.save()
                                    FormaDataStore.shared.syncTodayData()
                                },
                                onFinishTargetSlider: {
                                    recalculateIfNeededAndSave(profile: bindableProfile)
                                },
                                onFocusWeightField: {
                                    isWeightFocused = true
                                }
                            )

                            ProfileBiometricsCard(
                                profile: bindableProfile,
                                heightInputText: $heightInputText,
                                weightInputText: $weightInputText,
                                isHeightFocused: $isHeightFocused,
                                isWeightFocused: $isWeightFocused,
                                contextualTdee: contextualMetrics.contextualTdee,
                                onApplyHeight: { parsed, syncText in
                                    applyHeightChange(parsed, profile: bindableProfile, syncText: syncText)
                                },
                                onApplyWeight: { parsed, syncText, record in
                                    applyWeightChange(parsed, profile: bindableProfile, syncText: syncText, recordEntry: record)
                                },
                                onRecalculateTap: {
                                    FormaDataStore.shared.recalculateProfileWithFullContext(profile: bindableProfile, in: modelContext)
                                    try? modelContext.save()
                                    FormaDataStore.shared.syncTodayData()
                                    showToast(authManager.isPro ? "Metas recalculadas pela Inteligência Forma!" : "Metas recalculadas com base nas suas medidas!")
                                }
                            )

                            ProfileDailyGoalsCard(
                                profile: bindableProfile,
                                onContextSliderEnded: {
                                    recalculateIfNeededAndSave(profile: bindableProfile)
                                }
                            )

                            ProfileMacroGoalsCard(
                                profile: bindableProfile,
                                onSuggestTap: {
                                    suggestOptimalMacros(profile: bindableProfile)
                                    showToast("Proporção ideal de macronutrientes aplicada!")
                                }
                            )

                            accountCard
                            saveSettingsButton(profile: bindableProfile)
                        }
                        .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                        .padding(.top, 10)
                        .onAppear { syncInputStrings(from: profile) }
                        .onChange(of: profile.currentWeight) { _, newWeight in
                            if !isWeightFocused {
                                weightInputText = String(format: "%.1f", newWeight)
                            }
                        }
                        .onChange(of: profile.height) { _, newHeight in
                            if !isHeightFocused {
                                heightInputText = String(format: "%.2f", newHeight)
                            }
                        }
                    } else {
                        ProgressView()
                            .padding(.top, 60)
                    }
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .dismissKeyboardOnTap()
                .withDoneKeyboardToolbar()
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showLegalPrivacySheet) {
                LegalPrivacySheet()
            }
            .sheet(isPresented: $showSubscriptionSheet) {
                SubscriptionPaywallSheet()
            }
            .sheet(isPresented: $showChangePasswordSheet) {
                ChangePasswordSheet {
                    showToast("Senha alterada com sucesso!")
                }
            }
            .alert("Excluir conta permanentemente?", isPresented: $showDeleteAccountAlert) {
                Button("Cancelar", role: .cancel) {}
                Button("Excluir Minha Conta", role: .destructive) {
                    performPermanentAccountDeletion()
                }
            } message: {
                Text("Esta ação não pode ser desfeita. Seu cadastro, refeições, treinos, fichas, pesagens e registros de água serão apagados definitivamente do Forma e da nuvem, e os tokens de acesso do Sign in with Apple e Google serão revogados.")
            }
            .task {
                let active = FormaDataStore.shared.ensureSingleProfile(in: modelContext)
                syncInputStrings(from: active)
                await subscriptionManager.refreshAllBeforeDisplay()
            }
        }
    }

    private func profileHeaderCard(@Bindable profile: Profile) -> some View {
        FormaCard(padding: 18) {
            HStack(spacing: 16) {
                FormaUserAvatar(
                    name: profile.nome.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? (authManager.currentUserName ?? "Forma")
                        : profile.nome,
                    isPro: authManager.isPro,
                    size: 56,
                    badgeBorderColor: FormaTheme.Colors.cardSurface
                )

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        TextField("Seu Nome", text: $profile.nome)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        Image(systemName: "pencil")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.tertiary)
                    }

                    Text(profile.email.isEmpty ? (authManager.currentUserEmail ?? "Conta Forma") : profile.email)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var accountTierCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(authManager.isPro ? AnyShapeStyle(FormaTheme.Colors.aiGradient) : AnyShapeStyle(FormaTheme.Colors.brandSoft))
                            .frame(width: 42, height: 42)
                        Image(systemName: authManager.isPro ? "sparkles" : "leaf.fill")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(authManager.isPro ? .white : FormaTheme.Colors.brand)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(authManager.isPro ? "Forma Pro" : "Plano Essencial")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)

                            Text(authManager.isPro ? "PRO" : "ESSENCIAL")
                                .font(.system(size: 10, weight: .heavy, design: .rounded))
                                .foregroundStyle(authManager.isPro ? .white : FormaTheme.Colors.brand)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(
                                    authManager.isPro
                                        ? AnyShapeStyle(FormaTheme.Colors.aiGradient)
                                        : AnyShapeStyle(FormaTheme.Colors.brandSoft)
                                )
                                .clipShape(Capsule())
                        }

                        Text(authManager.isPro ? "Todas as funções de Inteligência Forma ativas" : "Visual simplificado e direto para o dia a dia")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer(minLength: 4)

                    Button {
                        Task { @MainActor in
                            await syncManager.sync(context: modelContext, forcePull: true)
                            await subscriptionManager.refreshAllBeforeDisplay()
                            showToast("Dados da assinatura atualizados.")
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.secondary)
                            .frame(width: 32, height: 32)
                            .background(Color.primary.opacity(0.05))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }

                Text(
                    authManager.isPro
                        ? "Inclui reconhecimento por foto do prato, registro por voz com ondas reativas, criador de fichas com IA e análise educacional de evolução com inteligência artificial (sem caráter diagnóstico)."
                        : "Acesso completo à busca na Tabela TACO, hidratação, fichas manuais com catálogo 3D, Ilha Dinâmica e gráficos. Assine o Forma Pro para liberar todos os recursos avançados de IA."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

                if let sub = subscriptionManager.currentSubscription {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Image(systemName: sub.appliedCouponCode != nil ? "ticket.fill" : "calendar.badge.clock")
                                .font(.caption)
                                .foregroundStyle(FormaTheme.Colors.brandInk)
                            Text(subscriptionSummaryLine(sub))
                                .font(.caption.weight(.medium))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                        }
                        if let nextDiscount = sub.nextBillingDiscountLabel, !nextDiscount.isEmpty {
                            HStack(spacing: 6) {
                                Image(systemName: "sparkles")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(FormaTheme.Colors.aiAccent)
                                Text(
                                    sub.nextBillingPriceCents.map {
                                        "Próxima cobrança: \(nextDiscount) (\(formatBrlPrice($0)))"
                                    } ?? "Próxima cobrança: \(nextDiscount)"
                                )
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(FormaTheme.Colors.aiAccent)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.045))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                } else if let monthlyPlan = subscriptionManager.plans.first {
                    HStack(spacing: 8) {
                        Image(systemName: "tag.fill")
                            .font(.caption)
                            .foregroundStyle(FormaTheme.Colors.brandInk)
                        Text(
                            monthlyPlan.defaultTrialDays > 0
                                ? "\(monthlyPlan.name): \(formatBrlPrice(monthlyPlan.priceCents))/mês • \(monthlyPlan.defaultTrialDays) dias grátis"
                                : "\(monthlyPlan.name): \(formatBrlPrice(monthlyPlan.priceCents))/mês"
                        )
                            .font(.caption.weight(.medium))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.045))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                Button {
                    Task { @MainActor in
                        await subscriptionManager.refreshAllBeforeDisplay()
                    }
                    showSubscriptionSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: authManager.isPro ? "slider.horizontal.3" : "crown.fill")
                        Text(authManager.isPro ? "Gerenciar Assinatura" : "Conhecer o Forma Pro")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(authManager.isPro ? AnyShapeStyle(Color.primary.opacity(0.08)) : AnyShapeStyle(FormaTheme.Colors.aiGradient))
                    .foregroundStyle(authManager.isPro ? FormaTheme.Colors.primaryText : .white)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var overdueSubscriptionBanner: some View {
        if subscriptionManager.isPastDue || (subscriptionManager.effectiveStatus == "PAUSED" && subscriptionManager.effectiveWarningMessage != nil) {
            let isPaused = (subscriptionManager.effectiveStatus == "PAUSED")
            FormaTintedCard(tint: isPaused ? FormaTheme.Colors.danger : FormaTheme.Colors.warning, padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: isPaused ? "pause.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundStyle(isPaused ? FormaTheme.Colors.danger : FormaTheme.Colors.warning)
                        Text(isPaused
                             ? "Assinatura Pausada Após 3 Dias"
                             : "Aviso de Atraso • Dia \(subscriptionManager.effectiveOverdueDay ?? 1) de 3")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }

                    if let msg = subscriptionManager.effectiveWarningMessage, !msg.isEmpty {
                        Text(msg)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    HStack(spacing: 10) {
                        Button {
                            showSubscriptionSheet = true
                        } label: {
                            Text("Regularizar Assinatura")
                                .font(.caption.weight(.bold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(FormaTheme.Colors.warning)
                                .foregroundStyle(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        .buttonStyle(.plain)

                        if subscriptionManager.isPastDue {
                            Button {
                                Task { await subscriptionManager.acknowledgeOverdueWarning() }
                            } label: {
                                Text("Ciente")
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(Color.primary.opacity(0.08))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private func subscriptionSummaryLine(_ sub: UserSubscriptionStatusInfo) -> String {
        var parts: [String] = [sub.planName ?? "Forma Pro"]
        parts.append(SubscriptionManager.localizedStatusLabel(sub.status))
        if sub.priceCents > 0 {
            parts.append(formatBrlPrice(sub.priceCents))
        }
        if let coupon = sub.appliedCouponCode, !coupon.isEmpty {
            parts.append("Cupom: \(coupon)")
        }
        return parts.joined(separator: " • ")
    }

    private func formatBrlPrice(_ cents: Int) -> String {
        let value = Double(cents) / 100.0
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.numberStyle = .currency
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "R$ %.2f", value)
    }

    private func fitnessGoalCard(@Bindable profile: Profile) -> some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "target")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Objetivo Corporal")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }

                Picker("Objetivo", selection: $profile.goal) {
                    ForEach(FitnessGoal.allCases) { goal in
                        Text(goal.rawValue).tag(goal)
                    }
                }
                .pickerStyle(.segmented)
                .onChange(of: profile.goal) { _, _ in
                    recalculateIfNeededAndSave(profile: profile)
                }

                Text(profile.goal.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Divider().background(Color.primary.opacity(0.06))

                VStack(alignment: .leading, spacing: 8) {
                    Text("Foco na Tela Inicial")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)

                    Picker("Foco", selection: Binding(
                        get: { profile.effectiveFocus },
                        set: { profile.preferredFocus = $0 }
                    )) {
                        ForEach(FocusMetric.allCases) { focus in
                            Text(focus.rawValue).tag(focus)
                        }
                    }
                    .pickerStyle(.segmented)

                    if profile.goal == .gain {
                        Text("Recomendamos manter o foco em **Consumidas** para bater sua meta diária de ganho.")
                            .font(.caption)
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                }
            }
        }
    }

    private var accountCard: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "person.crop.circle.fill")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Conta")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()
                    if authManager.isAuthenticated {
                        FormaBadge(text: "Ativa", tint: FormaTheme.Colors.brandInk)
                    }
                }

                HStack(spacing: 14) {
                    Circle()
                        .fill(FormaTheme.Colors.brandSoft)
                        .frame(width: 48, height: 48)
                        .overlay(
                            Image(systemName: authManager.currentAuthProvider == "APPLE" ? "applelogo" : (authManager.currentAuthProvider == "GOOGLE" ? "g.circle.fill" : "person.fill"))
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(authManager.currentUserName ?? "Usuário Forma")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        if let email = authManager.currentUserEmail, !email.isEmpty {
                            Text(email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                }

                Divider().padding(.vertical, 2)

                Button {
                    showLegalPrivacySheet = true
                } label: {
                    HStack {
                        Image(systemName: "hand.raised.shield.fill")
                            .foregroundStyle(FormaTheme.Colors.brand)
                        Text("Privacidade, Saúde & Termos de Uso")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }
                .buttonStyle(.plain)

                if (authManager.currentAuthProvider ?? "EMAIL") == "EMAIL" {
                    Button {
                        showChangePasswordSheet = true
                    } label: {
                        HStack {
                            Image(systemName: "key.fill")
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("Alterar Senha")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }

                if let deleteErrorMessage {
                    FormaBanner(message: deleteErrorMessage, style: .error)
                }

                Divider().padding(.vertical, 2)

                Button(role: .destructive) {
                    guard !isLoggingOut && !isDeletingAccount else { return }
                    isLoggingOut = true
                    Task { @MainActor in
                        if authManager.isAuthenticated {
                            syncManager.cancelPendingSyncs()
                            await syncManager.sync(context: modelContext)
                        }
                        isLoggingOut = false
                        authManager.logout()
                    }
                } label: {
                    HStack {
                        if isLoggingOut {
                            ProgressView().tint(FormaTheme.Colors.danger)
                            Text("Sincronizando e saindo...")
                        } else {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                            Text("Sair da Conta")
                        }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(FormaTheme.Colors.danger)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(FormaTheme.Colors.danger.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .disabled(isLoggingOut || isDeletingAccount)

                Button(role: .destructive) {
                    showDeleteAccountAlert = true
                } label: {
                    HStack(spacing: 6) {
                        if isDeletingAccount {
                            ProgressView().tint(FormaTheme.Colors.danger)
                            Text("Excluindo conta...")
                        } else {
                            Image(systemName: "trash")
                            Text("Excluir Minha Conta")
                        }
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(FormaTheme.Colors.danger.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
                .disabled(isLoggingOut || isDeletingAccount)
            }
        }
    }

    private func performPermanentAccountDeletion() {
        guard !isDeletingAccount else { return }
        deleteErrorMessage = nil
        isDeletingAccount = true
        Task { @MainActor in
            do {
                try await authManager.deleteAccount()
                isDeletingAccount = false
            } catch is CancellationError {
                isDeletingAccount = false
            } catch {
                deleteErrorMessage = error.localizedDescription
                isDeletingAccount = false
            }
        }
    }

    private func saveSettingsButton(profile: Profile) -> some View {
        Button {
            if let parsedH = ProfileInputValidator.parseFlexibleHeight(heightInputText) {
                applyHeightChange(parsedH, profile: profile, syncText: true)
            }
            let weightChangedTo: Double?
            if let parsedW = ProfileInputValidator.parseFlexibleWeight(weightInputText) {
                let clamped = max(30.0, min(300.0, (parsedW * 10.0).rounded() / 10.0))
                let hadDiff = abs(profile.currentWeight - clamped) >= 0.05
                profile.currentWeight = clamped
                profile.autoRecalculateGoals = true
                weightInputText = String(format: "%.1f", clamped)
                FormaDataStore.shared.recalculateProfileWithFullContext(for: clamped, profile: profile, in: modelContext)
                weightChangedTo = hadDiff ? clamped : nil
            } else {
                weightChangedTo = nil
            }

            isHeightFocused = false
            isWeightFocused = false
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)

            Task { @MainActor in
                do {
                    if let newW = weightChangedTo {
                        try await FormaDataStore.shared.createWeightEntryRemoteFirst(
                            weight: newW,
                            date: Date(),
                            notes: "Atualizado nos ajustes"
                        )
                    }
                    try await FormaDataStore.shared.saveProfileRemoteFirst(profile)
                    showToast("Configurações salvas no banco e sincronizadas!")
                } catch {
                    showToast("Erro ao sincronizar: \(FormaAPIClient.humanizeErrorMessage(error.localizedDescription))")
                }
            }
        } label: {
            HStack {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .bold))
                Text("Salvar Configurações")
                    .font(.headline)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(FormaTheme.Colors.brand)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.top, 4)
        .padding(.bottom, 32)
    }

    // MARK: - Helpers

    private func showToast(_ text: String) {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
        AuthManager.shared.showRealtimeToast(text)
    }

    private func syncInputStrings(from profile: Profile) {
        if !isWeightFocused {
            weightInputText = String(format: "%.1f", profile.currentWeight)
        }
        if !isHeightFocused {
            heightInputText = String(format: "%.2f", profile.height)
        }
    }

    private func recalculateIfNeededAndSave(profile: Profile) {
        profile.autoRecalculateGoals = true
        FormaDataStore.shared.recalculateProfileWithFullContext(profile: profile, in: modelContext)
        try? modelContext.save()
        FormaDataStore.shared.syncTodayData()
    }

    private func applyWeightChange(_ newWeight: Double, profile: Profile, syncText: Bool, recordEntry: Bool) {
        let clamped = max(30.0, min(300.0, (newWeight * 10.0).rounded() / 10.0))
        profile.currentWeight = clamped
        profile.autoRecalculateGoals = true
        if syncText {
            weightInputText = String(format: "%.1f", clamped)
        }
        if recordEntry {
            syncTodayWeightEntry(weight: clamped)
        }
        FormaDataStore.shared.recalculateProfileWithFullContext(for: clamped, profile: profile, in: modelContext)
        try? modelContext.save()
        FormaDataStore.shared.syncTodayData()
    }

    private func applyHeightChange(_ newHeight: Double, profile: Profile, syncText: Bool) {
        let clamped = max(1.0, min(2.5, (newHeight * 100.0).rounded() / 100.0))
        profile.height = clamped
        profile.autoRecalculateGoals = true
        if syncText {
            heightInputText = String(format: "%.2f", clamped)
        }
        FormaDataStore.shared.recalculateProfileWithFullContext(profile: profile, in: modelContext)
        try? modelContext.save()
        FormaDataStore.shared.syncTodayData()
    }

    private func syncTodayWeightEntry(weight: Double) {
        let latestWeight = weightEntries.first?.weight
        guard latestWeight == nil || abs((latestWeight ?? 0) - weight) >= 0.05 else { return }
        Task { @MainActor in
            try? await FormaDataStore.shared.createWeightEntryRemoteFirst(
                weight: weight,
                date: Date(),
                notes: "Atualizado nos ajustes"
            )
        }
    }

    private func suggestOptimalMacros(profile: Profile) {
        let ctx = FormaDataStore.shared.buildRecalculationContext(in: modelContext)
        let metrics = profile.contextualMetrics(context: ctx)
        let activeCalories = profile.targetCalorieIntake > 0 ? profile.targetCalorieIntake : metrics.suggestedCalories
        let protein = metrics.proteinGrams
        let fat = metrics.fatGrams
        let minCarbs: Double
        switch profile.goal {
        case .gain: minCarbs = 120.0
        case .lose: minCarbs = 60.0
        case .maintain: minCarbs = 80.0
        }
        let remainingCalories = max(0.0, activeCalories - (protein * 4.0) - (fat * 9.0))
        let carbs = max(minCarbs, (remainingCalories / 4.0).rounded())
        profile.macros = Macros(protein: protein, fat: fat, carbohydrates: carbs)

        try? modelContext.save()
        FormaDataStore.shared.syncTodayData()
    }
}

#Preview {
    ProfileView()
        .modelContainer(PreviewContainer.shared)
}
