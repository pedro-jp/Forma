//
//  SubscriptionPaywallSheet.swift
//  Forma
//
//  Central de Assinatura Forma Pro: StoreKit 2 local/produção, resgate de cupons de desconto
//  ou períodos grátis e gerenciamento de avisos da régua de atraso (3 dias).
//

import StoreKit
import SwiftUI

struct SubscriptionPaywallSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var authManager = AuthManager.shared
    @ObservedObject private var subscriptionManager = SubscriptionManager.shared

    @State private var selectedPlanId: String = "PRO_MONTHLY"
    @State private var couponCodeInput: String = ""
    @State private var isPromoCodeExpanded: Bool = false
    @State private var showManageSubscriptionsSheet: Bool = false
    @State private var hasCompletedInitialFreshSync: Bool = false

    private func isCurrentActivePlan(_ plan: SubscriptionPlanItem) -> Bool {
        subscriptionManager.isPlanCurrentlyActive(plan.id)
    }

    private var eligibleSelectablePlans: [SubscriptionPlanItem] {
        subscriptionManager.plans.filter { !isCurrentActivePlan($0) }
    }

    private var selectedPlan: SubscriptionPlanItem? {
        if let match = subscriptionManager.plans.first(where: { $0.id == selectedPlanId && !isCurrentActivePlan($0) }) {
            return match
        }
        return eligibleSelectablePlans.first
    }

    private var matchedStoreKitProduct: Product? {
        subscriptionManager.product(for: selectedPlan?.storekitProductId)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    heroStatusCard

                    if subscriptionManager.isPastDue || subscriptionManager.effectiveStatus == "PAUSED" {
                        overdueNoticeCard
                    }

                    feedbackBanner

                    if !hasCompletedInitialFreshSync {
                        freshCatalogLoadingCard
                    } else {
                        plansSelectionCard

                        couponRedemptionCard

                        legalAndAutoRenewCard
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 16)
            }
            .background(FormaTheme.Colors.pageBackground)
            .navigationTitle("Planos e Assinatura")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fechar") {
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .manageSubscriptionsSheet(isPresented: $showManageSubscriptionsSheet)
            .task {
                await performFreshCatalogConsultation()
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                Task { await performFreshCatalogConsultation() }
            }
            .onChange(of: selectedPlanId) { _, newPlanId in
                let trimmed = couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty, subscriptionManager.couponValidation != nil {
                    Task {
                        await subscriptionManager.validateCoupon(code: trimmed, planId: newPlanId)
                    }
                }
            }
            .onChange(of: subscriptionManager.plans) { _, _ in
                syncSelectedPlanToEligible()
                let trimmed = couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty, subscriptionManager.couponValidation != nil {
                    Task {
                        await subscriptionManager.validateCoupon(code: trimmed, planId: selectedPlan?.id)
                    }
                }
            }
            .onChange(of: subscriptionManager.currentSubscription) { _, _ in
                syncSelectedPlanToEligible()
            }
        }
    }

    private func syncSelectedPlanToEligible() {
        if let current = subscriptionManager.plans.first(where: { $0.id == selectedPlanId && !isCurrentActivePlan($0) }) {
            selectedPlanId = current.id
        } else if let firstEligible = eligibleSelectablePlans.first {
            selectedPlanId = firstEligible.id
        } else {
            selectedPlanId = ""
        }
    }

    private func performFreshCatalogConsultation() async {
        subscriptionManager.clearMessages()
        await subscriptionManager.refreshAllBeforeDisplay()
        syncSelectedPlanToEligible()
        let trimmed = couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty, subscriptionManager.couponValidation != nil {
            await subscriptionManager.validateCoupon(code: trimmed, planId: selectedPlan?.id)
        }
        hasCompletedInitialFreshSync = true
    }

    private var freshCatalogLoadingCard: some View {
        FormaCard(padding: 20) {
            VStack(spacing: 12) {
                ProgressView()
                Text("Atualizando planos e condições disponíveis...")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .multilineTextAlignment(.center)
                Text("Consultando os valores vigentes para a sua conta.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
        }
    }

    // MARK: - Hero & Status

    private var heroStatusCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(authManager.isPro ? FormaTheme.Colors.aiGradient : FormaTheme.Colors.brandGradient)
                            .frame(width: 46, height: 46)
                        Image(systemName: authManager.isPro ? "crown.fill" : "sparkles")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.white)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Forma Pro")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        statusPill
                    }
                    Spacer()
                }

                if let sub = subscriptionManager.currentSubscription {
                    VStack(alignment: .leading, spacing: 6) {
                        if let planName = sub.planName {
                            Text("Plano atual: \(planName)")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                        }
                        if let endStr = formatShortDate(sub.currentPeriodEndUtc ?? sub.trialEndsAtUtc) {
                            Text(sub.status == "TRIALING"
                                 ? "Período grátis válido até \(endStr)"
                                 : "Vigência atual até \(endStr)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let appliedCoupon = sub.appliedCouponCode, !appliedCoupon.isEmpty {
                            HStack(spacing: 6) {
                                Image(systemName: "ticket.fill")
                                    .font(.caption2)
                                Text("Código aplicado: \(appliedCoupon)")
                                    .font(.caption.weight(.semibold))
                            }
                            .foregroundStyle(FormaTheme.Colors.brandInk)
                        }
                        if let nextDiscountLabel = sub.nextBillingDiscountLabel, !nextDiscountLabel.isEmpty {
                            HStack(alignment: .top, spacing: 6) {
                                Image(systemName: "sparkles")
                                    .font(.caption2.weight(.bold))
                                    .padding(.top, 2)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Benefício para a próxima cobrança: \(nextDiscountLabel)")
                                        .font(.caption.weight(.semibold))
                                    if let nextPrice = sub.nextBillingPriceCents {
                                        Text("Valor previsto na próxima renovação: \(formatBrlCents(nextPrice))")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .foregroundStyle(FormaTheme.Colors.aiAccent)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.04))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                VStack(alignment: .leading, spacing: 8) {
                    featureRow(icon: "camera.viewfinder", text: "Foto do prato com estimativa de macros via IA")
                    featureRow(icon: "waveform", text: "Registro por voz reativa com preenchimento inteligente")
                    featureRow(icon: "dumbbell.fill", text: "Gerador de fichas de treino personalizadas com IA")
                    featureRow(icon: "chart.line.uptrend.xyaxis", text: "Leituras educacionais de evolução corporal e consistência")
                }
            }
        }
    }

    private var statusPill: some View {
        let status = subscriptionManager.effectiveStatus
        let (label, color): (String, Color) = {
            switch status {
            case "ACTIVE":
                return ("ASSINATURA ATIVA", FormaTheme.Colors.brandInk)
            case "TRIALING":
                return ("PERÍODO GRÁTIS ATIVO", FormaTheme.Colors.aiAccent)
            case "PAST_DUE":
                return ("ATRASO • AVISO DIA \(subscriptionManager.effectiveOverdueDay ?? 1)/3", FormaTheme.Colors.warning)
            case "PAUSED":
                return ("PAUSADA POR ATRASO", FormaTheme.Colors.danger)
            case "CANCELED", "EXPIRED":
                return ("ENCERRADA", FormaTheme.Colors.danger)
            default:
                return authManager.isPro ? ("PRO ATIVO", FormaTheme.Colors.aiAccent) : ("PLANO ESSENCIAL", .secondary)
            }
        }()

        return Text(label)
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.14))
            .foregroundStyle(color)
            .clipShape(Capsule())
    }

    private func featureRow(icon: String, text: String) -> some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(FormaTheme.Colors.aiAccent)
                .frame(width: 20)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Dunning Alert (3 Days)

    private var overdueNoticeCard: some View {
        let dayNum = subscriptionManager.effectiveOverdueDay ?? 1
        let policy = subscriptionManager.currentSubscription?.overdueActionEffective ?? "PAUSE"
        let actionWord = policy == "CANCEL" ? "cancelada" : "pausada"

        return FormaTintedCard(tint: FormaTheme.Colors.warning, padding: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(FormaTheme.Colors.warning)
                    Text(subscriptionManager.effectiveStatus == "PAUSED"
                         ? "Assinatura Pausada Após 3 Dias"
                         : "Aviso de Atraso • Dia \(dayNum) de 3")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }

                Text(subscriptionManager.effectiveWarningMessage
                     ?? "Detectamos pendência na renovação. Você receberá alertas durante 3 dias; após esse prazo a assinatura será \(actionWord) automaticamente.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                if let graceStr = formatShortDate(subscriptionManager.effectiveGraceEndsAtUtc) {
                    Text("Prazo limite de regularização: \(graceStr)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(FormaTheme.Colors.warning)
                }

                if subscriptionManager.isPastDue {
                    Button {
                        Task { await subscriptionManager.acknowledgeOverdueWarning() }
                    } label: {
                        Label("Confirmar Leitura do Aviso", systemImage: "checkmark.circle")
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(FormaTheme.Colors.warning.opacity(0.16))
                            .foregroundStyle(FormaTheme.Colors.warning)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Feedback

    @ViewBuilder
    private var feedbackBanner: some View {
        if let err = subscriptionManager.errorMessage, !err.isEmpty {
            FormaTintedCard(tint: FormaTheme.Colors.danger, padding: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "xmark.octagon.fill")
                        .foregroundStyle(FormaTheme.Colors.danger)
                    Text(err)
                        .font(.caption)
                        .foregroundStyle(FormaTheme.Colors.danger)
                }
            }
        }

        if let ok = subscriptionManager.successMessage, !ok.isEmpty {
            FormaTintedCard(tint: FormaTheme.Colors.brandInk, padding: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(FormaTheme.Colors.brandInk)
                    Text(ok)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(FormaTheme.Colors.brandInk)
                }
            }
        }
    }

    // MARK: - Private Promo Code Entry

    private var couponRedemptionCard: some View {
        FormaCard(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isPromoCodeExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "tag.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.aiAccent)
                        Text("Possui um código promocional?")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Spacer()
                        Image(systemName: isPromoCodeExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if isPromoCodeExpanded {
                    Text("Insira seu código promocional para validar o benefício na sua conta.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    TextField("Digite o código promocional", text: $couponCodeInput)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .font(.system(.subheadline, design: .monospaced).weight(.semibold))
                        .padding(12)
                        .background(Color.primary.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    if let validation = subscriptionManager.couponValidation, validation.valid {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(FormaTheme.Colors.brandInk)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(validation.benefitLabel ?? "Código válido")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(FormaTheme.Colors.brandInk)
                                if let msg = validation.message {
                                    Text(msg)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(FormaTheme.Colors.brandInk.opacity(0.11))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    HStack(spacing: 10) {
                        Button {
                            Task {
                                await subscriptionManager.validateCoupon(
                                    code: couponCodeInput,
                                    planId: selectedPlan?.id
                                )
                            }
                        } label: {
                            HStack {
                                if subscriptionManager.isValidatingCoupon {
                                    ProgressView().controlSize(.small)
                                }
                                Text("Validar")
                                    .font(.subheadline.weight(.semibold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Color.primary.opacity(0.08))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .disabled(couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || subscriptionManager.isValidatingCoupon)

                        Button {
                            Task {
                                let redeemed = await subscriptionManager.redeemCoupon(
                                    code: couponCodeInput,
                                    planId: selectedPlan?.id
                                )
                                if redeemed {
                                    couponCodeInput = ""
                                }
                            }
                        } label: {
                            HStack {
                                if subscriptionManager.isRedeemingCoupon {
                                    ProgressView().tint(.white).controlSize(.small)
                                }
                                Text("Aplicar Código")
                                    .font(.subheadline.weight(.semibold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(FormaTheme.Colors.brandGradient)
                            .foregroundStyle(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .disabled(couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || subscriptionManager.isRedeemingCoupon)
                    }
                }
            }
        }
    }

    // MARK: - StoreKit 2 Plans

    private var plansSelectionCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "creditcard.fill")
                        .foregroundStyle(FormaTheme.Colors.aiAccent)
                    Text("Planos, Preços e Prazos")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()
                    Button {
                        Task { await performFreshCatalogConsultation() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .disabled(subscriptionManager.isRefreshingCatalog)
                }

                if subscriptionManager.isRefreshingCatalog && subscriptionManager.plans.isEmpty {
                    ProgressView("Consultando planos atualizados...")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                } else {
                    ForEach(subscriptionManager.plans) { plan in
                        planRow(plan)
                    }
                }

                if let product = matchedStoreKitProduct {
                    let livePlanName = selectedPlan?.name ?? product.displayName
                    let activeValidation = (subscriptionManager.couponValidation?.valid == true && subscriptionManager.couponValidation?.originalPriceCents == selectedPlan?.priceCents)
                        ? subscriptionManager.couponValidation
                        : nil
                    let displayPriceCents = activeValidation?.finalPriceCents ?? selectedPlan?.priceCents
                    let buttonPriceText = displayPriceCents.map { formatBrlCents($0) } ?? product.displayPrice

                    Button {
                        let appliedCode = subscriptionManager.couponValidation?.valid == true
                            ? subscriptionManager.couponValidation?.code
                            : (couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : couponCodeInput)
                        Task {
                            await subscriptionManager.purchase(product: product, couponCode: appliedCode)
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if subscriptionManager.isPurchasing {
                                ProgressView().tint(.white).controlSize(.small)
                            } else {
                                Image(systemName: "bag.fill")
                            }
                            Text("Assinar \(livePlanName) (\(buttonPriceText))")
                                .font(.subheadline.weight(.bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(FormaTheme.Colors.aiGradient)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(subscriptionManager.isPurchasing)
                } else if let plan = selectedPlan {
                    let activeValidation = (subscriptionManager.couponValidation?.valid == true && subscriptionManager.couponValidation?.originalPriceCents == plan.priceCents)
                        ? subscriptionManager.couponValidation
                        : nil
                    let displayPriceCents = activeValidation?.finalPriceCents ?? plan.priceCents
                    let buttonPriceText = formatBrlCents(displayPriceCents)

                    Button {
                        let appliedCode = subscriptionManager.couponValidation?.valid == true
                            ? subscriptionManager.couponValidation?.code
                            : (couponCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : couponCodeInput)
                        Task {
                            await subscriptionManager.loadStoreKitProducts()
                            if let loadedProduct = subscriptionManager.product(for: plan.storekitProductId) {
                                await subscriptionManager.purchase(product: loadedProduct, couponCode: appliedCode)
                            } else {
                                subscriptionManager.errorMessage = "Não foi possível conectar à App Store neste momento. Verifique sua conta Apple ou utilize um código promocional acima."
                            }
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if subscriptionManager.isPurchasing {
                                ProgressView().tint(.white).controlSize(.small)
                            } else {
                                Image(systemName: "bag.fill")
                            }
                            Text("Assinar \(plan.name) (\(buttonPriceText))")
                                .font(.subheadline.weight(.bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(FormaTheme.Colors.aiGradient)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(subscriptionManager.isPurchasing)
                }

                HStack(spacing: 12) {
                    Button {
                        Task { await subscriptionManager.restorePurchases() }
                    } label: {
                        Label("Restaurar Compras", systemImage: "arrow.clockwise")
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(Color.primary.opacity(0.06))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .disabled(subscriptionManager.isPurchasing)

                    Button {
                        showManageSubscriptionsSheet = true
                    } label: {
                        Label("Gerenciar Assinatura", systemImage: "gearshape")
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(Color.primary.opacity(0.06))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }
        }
    }

    private func planRow(_ plan: SubscriptionPlanItem) -> some View {
        let isCurrentPlan = isCurrentActivePlan(plan)
        let isSelected = (!isCurrentPlan && plan.id == selectedPlan?.id)
        // Sempre usar o preço ao vivo vindo do servidor (Admin), nunca o valor estático do arquivo .storekit local
        let basePriceLabel = formatBrlCents(plan.priceCents)
        let validationForPlan = (isSelected && subscriptionManager.couponValidation?.valid == true && subscriptionManager.couponValidation?.originalPriceCents == plan.priceCents)
            ? subscriptionManager.couponValidation
            : nil
        let intervalSuffix = plan.billingInterval == "YEARLY" ? "/ano" : "/mês"

        return Button {
            guard !isCurrentPlan else { return }
            withAnimation(.easeInOut(duration: 0.18)) {
                selectedPlanId = plan.id
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isCurrentPlan ? "checkmark.seal.fill" : (isSelected ? "checkmark.circle.fill" : "circle"))
                    .foregroundStyle(isCurrentPlan ? FormaTheme.Colors.brandInk : (isSelected ? FormaTheme.Colors.aiAccent : .secondary))
                    .font(.title3)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(plan.name)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        if isCurrentPlan {
                            Text("PLANO ATUAL")
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(FormaTheme.Colors.brandInk.opacity(0.15))
                                .foregroundStyle(FormaTheme.Colors.brandInk)
                                .clipShape(Capsule())
                        } else if plan.defaultTrialDays > 0 {
                            Text("\(plan.defaultTrialDays) DIAS GRÁTIS")
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(FormaTheme.Colors.aiAccent.opacity(0.14))
                                .foregroundStyle(FormaTheme.Colors.aiAccent)
                                .clipShape(Capsule())
                        }
                    }
                    if isCurrentPlan {
                        Text("Este já é o seu plano ativo no momento.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.leading)
                    } else if let desc = plan.description {
                        Text(desc)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    if let discounted = validationForPlan, discounted.finalPriceCents != plan.priceCents {
                        Text(basePriceLabel)
                            .font(.caption2)
                            .strikethrough()
                            .foregroundStyle(.secondary)
                        Text(formatBrlCents(discounted.finalPriceCents))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brandInk)
                    } else {
                        Text(basePriceLabel)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(isCurrentPlan ? FormaTheme.Colors.brandInk : FormaTheme.Colors.primaryText)
                    }
                    Text(intervalSuffix)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(14)
            .background(
                isCurrentPlan
                    ? FormaTheme.Colors.brandInk.opacity(0.08)
                    : (isSelected ? FormaTheme.Colors.aiAccent.opacity(0.10) : Color.primary.opacity(0.035))
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(
                        isCurrentPlan
                            ? FormaTheme.Colors.brandInk.opacity(0.45)
                            : (isSelected ? FormaTheme.Colors.aiAccent : Color.primary.opacity(0.08)),
                        lineWidth: (isCurrentPlan || isSelected) ? 1.5 : 0.7
                    )
            )
        }
        .buttonStyle(.plain)
        .disabled(isCurrentPlan)
    }

    // MARK: - Legal & Auto-Renewal

    private var legalAndAutoRenewCard: some View {
        FormaCard(padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Informações de Renovação & Direito do Consumidor")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                Text("As assinaturas via App Store são renovadas automaticamente ao final de cada ciclo, salvo cancelamento em Ajustes > Conta Apple com pelo menos 24h de antecedência. Política atual em atraso: \(subscriptionManager.catalogGraceDays) dias de avisos antes de \(subscriptionManager.catalogOverduePolicy == "CANCEL" ? "cancelamento" : "pausa") automática. Direito de arrependimento garantido em 7 dias (Art. 49 CDC).")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 16) {
                    if let privacyURL = URL(string: "https://forma-94vp.onrender.com/privacy.html") {
                        Link(destination: privacyURL) {
                            Label("Política de Privacidade", systemImage: "arrow.up.right.square")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brandInk)
                        }
                    }
                    if let termsURL = URL(string: "https://forma-94vp.onrender.com/terms.html") {
                        Link(destination: termsURL) {
                            Label("Termos de Uso (EULA)", systemImage: "doc.text")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brandInk)
                        }
                    }
                }
                .padding(.top, 2)
            }
        }
    }

    // MARK: - Formatters

    private func formatBrlCents(_ cents: Int) -> String {
        let value = Double(cents) / 100.0
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.numberStyle = .currency
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "R$ %.2f", value)
    }

    private func formatShortDate(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let isoWithFrac = ISO8601DateFormatter()
        isoWithFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let isoBasic = ISO8601DateFormatter()
        isoBasic.formatOptions = [.withInternetDateTime]

        guard let date = isoWithFrac.date(from: iso) ?? isoBasic.date(from: iso) else {
            return nil
        }
        let display = DateFormatter()
        display.locale = Locale(identifier: "pt_BR")
        display.dateFormat = "dd/MM/yyyy 'às' HH:mm"
        return display.string(from: date)
    }
}
