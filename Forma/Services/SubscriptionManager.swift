//
//  SubscriptionManager.swift
//  Forma
//
//  Gerenciamento completo de assinaturas StoreKit 2 (sandbox/local .storekit e produção),
//  resgate de cupons de desconto / períodos grátis no backend e avisos de atraso em tempo real.
//

import Combine
import Foundation
import StoreKit
import SwiftUI

public struct SubscriptionPlanItem: Codable, Identifiable, Equatable {
    public let id: String
    public let name: String
    public let description: String?
    public let storekitProductId: String?
    public let billingInterval: String
    public let priceCents: Int
    public let currency: String
    public let defaultTrialDays: Int
    public let active: Bool
}

public struct CouponValidationInfo: Codable, Equatable {
    public let valid: Bool
    public let code: String?
    public let couponType: String?
    public let freePeriodDays: Int?
    public let discountPercent: Int?
    public let discountCents: Int?
    public let originalPriceCents: Int
    public let finalPriceCents: Int
    public let benefitLabel: String?
    public let message: String?
}

public struct DunningNoticeItem: Codable, Identifiable, Equatable {
    public let id: String
    public let dayNumber: Int
    public let noticeType: String
    public let title: String
    public let message: String
    public let acknowledgedAtUtc: String?
    public let createdAtUtc: String?
}

public struct UserSubscriptionStatusInfo: Codable, Equatable {
    public let id: String?
    public let userId: String?
    public let userEmail: String?
    public let accountTier: String
    public let planId: String?
    public let planName: String?
    public let source: String?
    public let status: String
    public let priceCents: Int
    public let currency: String
    public let appliedCouponCode: String?
    public let autoRenew: Bool
    public let externalTransactionId: String?
    public let currentPeriodStartUtc: String?
    public let currentPeriodEndUtc: String?
    public let trialEndsAtUtc: String?
    public let overdueSinceUtc: String?
    public let graceEndsAtUtc: String?
    public let dunningWarningsSent: Int
    public let lastWarningDaySent: Int
    public let overdueActionEffective: String?
    public let statusReason: String?
    public let pastDue: Bool
    public let overdueDay: Int?
    public let activeWarningMessage: String?
    public let updatedAtUtc: String?
    public let recentNotices: [DunningNoticeItem]?
}

public struct RedeemCouponResult: Codable {
    public let redeemed: Bool
    public let accountTier: String
    public let subscriptionStatus: String
    public let message: String
    public let subscription: UserSubscriptionStatusInfo?
}

@MainActor
public final class SubscriptionManager: ObservableObject {
    public static let shared = SubscriptionManager()

    public static let storeKitProductIds: [String] = [
        "jota.Forma.pro.monthly",
        "jota.Forma.pro.yearly"
    ]

    @Published public private(set) var plans: [SubscriptionPlanItem] = []
    @Published public private(set) var storeKitProducts: [Product] = []
    @Published public private(set) var currentSubscription: UserSubscriptionStatusInfo? = nil
    @Published public private(set) var couponValidation: CouponValidationInfo? = nil

    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isPurchasing: Bool = false
    @Published public private(set) var isValidatingCoupon: Bool = false
    @Published public private(set) var isRedeemingCoupon: Bool = false

    @Published public var errorMessage: String? = nil
    @Published public var successMessage: String? = nil

    @Published public private(set) var realtimeSubscriptionStatus: String? = nil
    @Published public private(set) var realtimeOverdueDay: Int? = nil
    @Published public private(set) var realtimeGraceEndsAtUtc: String? = nil
    @Published public private(set) var realtimeWarningMessage: String? = nil

    private var updatesTask: Task<Void, Never>?
    private let decoder = JSONDecoder()

    public var isPastDue: Bool {
        if currentSubscription?.pastDue == true {
            return true
        }
        return effectiveStatus == "PAST_DUE"
    }

    public var effectiveStatus: String {
        let raw = realtimeSubscriptionStatus
            ?? currentSubscription?.status
            ?? (AuthManager.shared.isPro ? "ACTIVE" : "NONE")
        return raw.uppercased()
    }

    public var effectiveOverdueDay: Int? {
        realtimeOverdueDay ?? currentSubscription?.overdueDay
    }

    public var effectiveGraceEndsAtUtc: String? {
        realtimeGraceEndsAtUtc ?? currentSubscription?.graceEndsAtUtc
    }

    public var effectiveWarningMessage: String? {
        realtimeWarningMessage ?? currentSubscription?.activeWarningMessage
    }

    private init() {
        self.updatesTask = Task.detached(priority: .background) {
            for await result in StoreKit.Transaction.updates {
                guard case .verified(let transaction) = result else { continue }
                await SubscriptionManager.shared.handleVerifiedTransaction(transaction, couponCode: nil)
            }
        }
    }

    // MARK: - StoreKit 2

    public func loadStoreKitProducts() async {
        do {
            let loaded = try await Product.products(for: Self.storeKitProductIds)
            self.storeKitProducts = loaded.sorted { $0.price < $1.price }
        } catch {
            // Ambiente sem Apple ID ativo ou sem esquema .storekit vinculado continua operando via cupons/backend
        }
    }

    public func product(for storekitId: String?) -> Product? {
        guard let storekitId, !storekitId.isEmpty else { return nil }
        return storeKitProducts.first { $0.id == storekitId }
    }

    public func purchase(product: Product, couponCode: String? = nil) async {
        guard !isPurchasing else { return }
        isPurchasing = true
        errorMessage = nil
        successMessage = nil
        defer { isPurchasing = false }

        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    await handleVerifiedTransaction(transaction, couponCode: couponCode)
                    if errorMessage == nil {
                        successMessage = "Assinatura Forma Pro ativada via StoreKit!"
                    }
                case .unverified(_, let verificationError):
                    errorMessage = "Transação StoreKit não pôde ser verificada: \(verificationError.localizedDescription)"
                }
            case .userCancelled:
                break
            case .pending:
                successMessage = "Compra pendente de confirmação."
            @unknown default:
                break
            }
        } catch {
            errorMessage = "Não foi possível concluir a compra StoreKit: \(error.localizedDescription)"
        }
    }

    public func restorePurchases() async {
        guard !isPurchasing else { return }
        isPurchasing = true
        errorMessage = nil
        successMessage = nil
        defer { isPurchasing = false }

        do {
            try await AppStore.sync()
        } catch {
            // Segue para leitura de entitlements locais (.storekit) e sincronização com backend
        }

        var syncedAny = false
        for await result in StoreKit.Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               Self.storeKitProductIds.contains(transaction.productID) {
                await syncStoreKitTransaction(transaction, couponCode: nil)
                syncedAny = true
            }
        }

        await refreshMySubscription()

        if syncedAny {
            successMessage = "Compras restauradas e sincronizadas com sucesso!"
        } else if AuthManager.shared.isPro {
            successMessage = "Sua assinatura Forma Pro está sincronizada."
        } else if errorMessage == nil {
            errorMessage = "Nenhuma assinatura Apple ativa encontrada para restaurar."
        }
    }

    public func handleVerifiedTransaction(_ transaction: StoreKit.Transaction, couponCode: String?) async {
        if AuthManager.shared.isAuthenticated {
            await syncStoreKitTransaction(transaction, couponCode: couponCode)
        }
        await transaction.finish()
    }

    // MARK: - Backend API

    public func fetchPlans() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/plans",
                method: "GET",
                authenticated: false,
                timeout: 12.0,
                interceptAccountStatus: false
            )
            let fetched = try decoder.decode([SubscriptionPlanItem].self, from: data)
            self.plans = fetched.filter { $0.active }
        } catch {
            if plans.isEmpty {
                errorMessage = (error as? FormaAPIError)?.errorDescription ?? error.localizedDescription
            }
        }
    }

    public func refreshMySubscription() async {
        guard AuthManager.shared.isAuthenticated else { return }
        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/me",
                method: "GET",
                authenticated: true,
                timeout: 12.0
            )
            let status = try decoder.decode(UserSubscriptionStatusInfo.self, from: data)
            self.currentSubscription = status
            self.realtimeSubscriptionStatus = status.status
            self.realtimeOverdueDay = status.overdueDay
            self.realtimeGraceEndsAtUtc = status.graceEndsAtUtc
            self.realtimeWarningMessage = status.activeWarningMessage

            if !status.accountTier.isEmpty {
                AuthManager.shared.updateAccountTier(status.accountTier, notifyUser: false)
            }
        } catch {
            // Falha silenciosa em refresh de fundo para não poluir UI offline
        }
    }

    public func validateCoupon(code: String, planId: String? = nil) async {
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !cleanCode.isEmpty else {
            couponValidation = nil
            errorMessage = "Informe o código do cupom."
            return
        }
        isValidatingCoupon = true
        errorMessage = nil
        successMessage = nil
        defer { isValidatingCoupon = false }

        var payload: [String: Any] = ["code": cleanCode]
        if let planId, !planId.isEmpty {
            payload["planId"] = planId
        }

        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/validate-coupon",
                method: "POST",
                jsonBody: payload,
                authenticated: true,
                timeout: 12.0
            )
            let validation = try decoder.decode(CouponValidationInfo.self, from: data)
            self.couponValidation = validation
            if !validation.valid {
                errorMessage = validation.message ?? "Cupom inválido ou expirado."
            }
        } catch {
            errorMessage = (error as? FormaAPIError)?.errorDescription ?? error.localizedDescription
        }
    }

    @discardableResult
    public func redeemCoupon(code: String, planId: String? = nil) async -> Bool {
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !cleanCode.isEmpty else {
            errorMessage = "Digite um código de cupom válido."
            return false
        }

        isRedeemingCoupon = true
        errorMessage = nil
        successMessage = nil
        defer { isRedeemingCoupon = false }

        var payload: [String: Any] = ["code": cleanCode]
        if let planId, !planId.isEmpty {
            payload["planId"] = planId
        }

        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/redeem-coupon",
                method: "POST",
                jsonBody: payload,
                authenticated: true,
                timeout: 14.0
            )
            let result = try decoder.decode(RedeemCouponResult.self, from: data)
            if let sub = result.subscription {
                self.currentSubscription = sub
                self.realtimeSubscriptionStatus = sub.status
                self.realtimeOverdueDay = sub.overdueDay
                self.realtimeGraceEndsAtUtc = sub.graceEndsAtUtc
                self.realtimeWarningMessage = sub.activeWarningMessage
            }
            if !result.accountTier.isEmpty {
                AuthManager.shared.updateAccountTier(result.accountTier, notifyUser: true)
            }
            self.couponValidation = nil
            self.successMessage = result.message
            return result.redeemed
        } catch {
            errorMessage = (error as? FormaAPIError)?.errorDescription ?? error.localizedDescription
            return false
        }
    }

    public func syncStoreKitTransaction(_ transaction: StoreKit.Transaction, couponCode: String? = nil) async {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        var body: [String: Any] = [
            "productId": transaction.productID,
            "transactionId": String(transaction.id),
            "originalTransactionId": String(transaction.originalID),
            "purchaseDateUtc": isoFormatter.string(from: transaction.purchaseDate),
            "isTrialPeriod": (transaction.offer?.type == .introductory),
            "environment": transaction.environment.rawValue
        ]
        if let expires = transaction.expirationDate {
            body["expiresDateUtc"] = isoFormatter.string(from: expires)
        }
        if let cleanCoupon = couponCode?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanCoupon.isEmpty {
            body["couponCode"] = cleanCoupon.uppercased()
        }
        if let matchedPlan = plans.first(where: { $0.storekitProductId == transaction.productID }) {
            body["paidAmountCents"] = matchedPlan.priceCents
        }

        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/sync-storekit",
                method: "POST",
                jsonBody: body,
                authenticated: true,
                timeout: 14.0
            )
            let updated = try decoder.decode(UserSubscriptionStatusInfo.self, from: data)
            self.currentSubscription = updated
            self.realtimeSubscriptionStatus = updated.status
            self.realtimeOverdueDay = updated.overdueDay
            self.realtimeGraceEndsAtUtc = updated.graceEndsAtUtc
            self.realtimeWarningMessage = updated.activeWarningMessage
            AuthManager.shared.updateAccountTier(updated.accountTier, notifyUser: true)
        } catch {
            errorMessage = (error as? FormaAPIError)?.errorDescription ?? error.localizedDescription
        }
    }

    public func acknowledgeOverdueWarning() async {
        guard AuthManager.shared.isAuthenticated else { return }
        do {
            _ = try await FormaAPIClient.shared.requestData(
                path: "/api/subscriptions/acknowledge-warning",
                method: "POST",
                jsonBody: [:],
                authenticated: true,
                timeout: 10.0
            )
            self.realtimeWarningMessage = nil
            await refreshMySubscription()
        } catch {
            errorMessage = (error as? FormaAPIError)?.errorDescription ?? error.localizedDescription
        }
    }

    public func applyRealtimeEvent(
        subscriptionStatus: String?,
        overdueDay: Int?,
        graceEndsAtUtc: String?,
        warningMessage: String?
    ) {
        if let subscriptionStatus, !subscriptionStatus.isEmpty {
            self.realtimeSubscriptionStatus = subscriptionStatus.uppercased()
        }
        if let overdueDay {
            self.realtimeOverdueDay = overdueDay
        }
        if let graceEndsAtUtc {
            self.realtimeGraceEndsAtUtc = graceEndsAtUtc
        }
        if let warningMessage, !warningMessage.isEmpty {
            self.realtimeWarningMessage = warningMessage
        } else if let status = subscriptionStatus?.uppercased(),
                  status == "ACTIVE" || status == "TRIALING" {
            self.realtimeOverdueDay = nil
            self.realtimeGraceEndsAtUtc = nil
            self.realtimeWarningMessage = nil
        }

        Task { @MainActor in
            await self.refreshMySubscription()
        }
    }

    public func resetOnLogout() {
        self.currentSubscription = nil
        self.couponValidation = nil
        self.errorMessage = nil
        self.successMessage = nil
        self.realtimeSubscriptionStatus = nil
        self.realtimeOverdueDay = nil
        self.realtimeGraceEndsAtUtc = nil
        self.realtimeWarningMessage = nil
    }

    public func clearMessages() {
        self.errorMessage = nil
        self.successMessage = nil
    }
}
