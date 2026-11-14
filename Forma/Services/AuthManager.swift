//
//  AuthManager.swift
//  Forma
//
//  Created by João Pedro on 11/02/26.
//

import Foundation
import Combine
import SwiftUI
import SwiftData
import AuthenticationServices
import UIKit

public enum AccountTier: String, Codable, CaseIterable, Sendable {
    case free = "FREE"
    case pro = "PRO"

    public static func from(_ raw: String?) -> AccountTier {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return .free
        }
        return trimmed == "PRO" ? .pro : .free
    }

    public var displayName: String {
        switch self {
        case .free: return "Camada Essencial"
        case .pro: return "Forma Pro"
        }
    }

    public var shortBadgeTitle: String {
        switch self {
        case .free: return "Essencial"
        case .pro: return "PRO"
        }
    }
}

@MainActor
private final class AppleDeletionAuthorizationCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<String?, Error>?

    func requestAuthorizationCode() async throws -> String? {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let provider = ASAuthorizationAppleIDProvider()
            let request = provider.createRequest()
            request.requestedScopes = []

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes
            let windowScene = scenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
                ?? scenes.compactMap { $0 as? UIWindowScene }.first
            if let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first {
                return window
            }
            guard let scene = windowScene else {
                preconditionFailure("Nenhuma UIWindowScene ativa disponível para confirmação Apple.")
            }
            return UIWindow(windowScene: scene)
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let code = (authorization.credential as? ASAuthorizationAppleIDCredential)?
            .authorizationCode
            .flatMap { String(data: $0, encoding: .utf8) }
        Task { @MainActor in
            self.continuation?.resume(returning: code)
            self.continuation = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let nsError = error as NSError
        Task { @MainActor in
            if nsError.domain == ASAuthorizationError.errorDomain,
               nsError.code == ASAuthorizationError.canceled.rawValue {
                self.continuation?.resume(throwing: CancellationError())
            } else {
                // Em builds locais Debug sem capability paga ativa, segue com o refresh_token salvo no backend
                self.continuation?.resume(returning: nil)
            }
            self.continuation = nil
        }
    }
}

@MainActor
public final class AuthManager: ObservableObject {
    public static let shared = AuthManager()

    // MARK: - Chaves de Armazenamento Seguro (mantidas para compatibilidade de sessão)
    private let tokenKey = "forma_jwt_token"
    private let userIdKey = "forma_user_id"
    private let userEmailKey = "forma_user_email"
    private let userNameKey = "forma_user_name"
    private let authProviderKey = "forma_auth_provider"
    private let socialProviderIdKey = "forma_social_provider_id"
    private let accountTierKey = "forma_account_tier"
    private var appleDeletionCoordinator: AppleDeletionAuthorizationCoordinator?

    // MARK: - Endereços de Rede Local / Bonjour / Simulador (Lê BACKEND_BASE_URL do Secrets.plist primeiro)
    public static var configuredPlistUrl: String? {
        if let path = Bundle.main.path(forResource: "Secrets", ofType: "plist"),
           let dict = NSDictionary(contentsOfFile: path) as? [String: Any],
           let url = dict["BACKEND_BASE_URL"] as? String {
            let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if !trimmed.isEmpty { return trimmed }
        }
        return nil
    }

    private static let lastWorkingServerUrlKey = "forma_last_working_server_url"
    private static let boundServerOriginKey = "forma_bound_server_origin"

    public static let productionRenderUrl = "https://forma-94vp.onrender.com"

    public static var isConfiguredRemoteHttps: Bool {
        let active = (configuredPlistUrl ?? productionRenderUrl).lowercased()
        return active.hasPrefix("https://")
    }

    public static var defaultServerUrl: String {
        if let plistUrl = configuredPlistUrl, !plistUrl.isEmpty {
            return plistUrl
        }
        return productionRenderUrl
    }
    public static let fallbackBonjourUrl = "http://MacBook-Pro-de-Joao-2.local:8085"
    public static let fallbackCurrentLanUrl = "http://192.168.1.31:8085"
    public static let fallbackSecondaryLanUrl = "http://192.168.1.23:8085"
    public static let fallbackLocalUrl = "http://localhost:8085"
    public static let fallbackLoopbackUrl = "http://127.0.0.1:8085"
    public static let fallbackLanUrl = "http://192.168.10.15:8085"

    private static let deprecatedSubnetHosts: Set<String> = [
        "http://10.0.0.105:8085",
        "http://10.0.0.103:8085",
        "http://192.0.0.2:8085",
        "http://192.168.1.13:8085"
    ]

    public func markWorkingServerUrl(_ url: String) {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let changed = (serverBaseUrl != trimmed)
        if changed {
            serverBaseUrl = trimmed
        }
        UserDefaults.standard.set(trimmed, forKey: Self.lastWorkingServerUrlKey)
        if changed && isAuthenticated {
            Task { @MainActor in
                if !AccountRealtimeSocketManager.shared.isConnected {
                    AccountRealtimeSocketManager.shared.connectIfNeeded(forceReconnect: true)
                }
            }
        }
    }

    public var candidateServerUrls: [String] {
        // Quando apontado para servidor HTTPS em nuvem (ex: Render), usa exclusivamente a URL de produção
        // sem tentar IPs locais nem reduzir o timeout em cold-starts.
        if Self.isConfiguredRemoteHttps {
            return [Self.defaultServerUrl]
        }

        #if !DEBUG
        // Em builds Release, exige obrigatoriamente HTTPS configurado (bloqueia cleartext HTTP LAN/loopback)
        return [Self.defaultServerUrl]
        #else
        var list: [String] = []
        if let plistUrl = Self.configuredPlistUrl, !plistUrl.isEmpty {
            list.append(plistUrl)
        }
        if let saved = UserDefaults.standard.string(forKey: Self.lastWorkingServerUrlKey),
           !saved.isEmpty,
           !saved.lowercased().hasPrefix("https://"),
           !Self.deprecatedSubnetHosts.contains(saved) {
            list.append(saved)
        }
        let current = serverBaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        #if targetEnvironment(simulator)
        if !current.isEmpty { list.append(current) }
        list.append(Self.fallbackLocalUrl)
        list.append(Self.fallbackLoopbackUrl)
        list.append(Self.fallbackBonjourUrl)
        list.append(Self.fallbackSecondaryLanUrl)
        list.append(Self.fallbackCurrentLanUrl)
        #else
        if !current.isEmpty && !Self.deprecatedSubnetHosts.contains(current) {
            list.append(current)
        }
        list.append(Self.fallbackBonjourUrl)
        list.append(Self.fallbackSecondaryLanUrl)
        list.append(Self.fallbackCurrentLanUrl)
        list.append(Self.fallbackLocalUrl)
        list.append(Self.fallbackLoopbackUrl)
        #endif
        list.append(Self.fallbackLanUrl)

        var seen = Set<String>()
        return list.filter { seen.insert($0).inserted }
        #endif
    }

    // MARK: - Estados Publicados
    @Published public var isAuthenticated: Bool = false
    @Published public var isGuest: Bool = false
    @Published public var currentUserId: String? = nil
    @Published public var currentUserEmail: String? = nil
    @Published public var currentUserName: String? = nil
    @Published public var currentAuthProvider: String? = nil
    @Published public var accountTier: AccountTier = .free
    @Published public var isAccountPaused: Bool = false
    @Published public var accountPausedReason: String? = nil
    @Published public var isAiAccessBlocked: Bool = false
    @Published public var isGlobalAiBlocked: Bool = false
    @Published public var realtimeToastMessage: String? = nil
    @Published public var jwtToken: String? = nil
    @Published public var isAuthenticating: Bool = false
    @Published public var errorMessage: String? = nil
    @Published public var serverBaseUrl: String
    private var toastDismissTask: Task<Void, Never>? = nil

    public static let accountTierDidChangeNotification = Notification.Name("FormaAccountTierDidChange")

    public var isPro: Bool {
        accountTier == .pro
    }

    public func updateAccountTier(_ rawTier: String?, notifyUser: Bool = false) {
        let resolved = AccountTier.from(rawTier)
        let didChange = (self.accountTier != resolved)

        if didChange {
            withAnimation(.spring(response: 0.36, dampingFraction: 0.82)) {
                self.accountTier = resolved
            }
            if resolved == .pro {
                GeminiAIService.shared.activateAllForProTier()
            } else {
                GeminiAIService.shared.cancelAndCutAllForFreeTier()
                VoiceRecognitionService.shared.cutAllAIFlowsForFreeTier()
                AppNavigationRouter.shared.cutAllAIFlowsForFreeTier()
            }
            NotificationCenter.default.post(name: Self.accountTierDidChangeNotification, object: resolved)

            if notifyUser && isAuthenticated {
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                showRealtimeToast(
                    resolved == .pro
                        ? "Forma Pro ativado! 100% da IA liberada"
                        : "Plano Essencial ativo • Funções de IA encerradas"
                )
            }
        }
        KeychainHelper.shared.save(key: accountTierKey, string: resolved.rawValue)
        UserDefaults.standard.set(resolved.rawValue, forKey: accountTierKey)
    }

    public func showRealtimeToast(_ message: String) {
        toastDismissTask?.cancel()
        withAnimation(.spring(response: 0.34, dampingFraction: 0.84)) {
            self.realtimeToastMessage = message
        }
        toastDismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.25)) {
                self?.realtimeToastMessage = nil
            }
        }
    }

    private init() {
        let resolvedUrl = Self.defaultServerUrl
        self.serverBaseUrl = resolvedUrl

        let previousOrigin = UserDefaults.standard.string(forKey: Self.boundServerOriginKey)
        let previousWasHttps = previousOrigin?.lowercased().hasPrefix("https://") ?? false
        let currentIsHttps = resolvedUrl.lowercased().hasPrefix("https://")

        UserDefaults.standard.set(resolvedUrl, forKey: Self.lastWorkingServerUrlKey)

        if let previousOrigin, previousOrigin != resolvedUrl, (Self.isConfiguredRemoteHttps || previousWasHttps != currentIsHttps) {
            // Trocou entre ambiente remoto (Render HTTPS) e servidor local (HTTP), ou mudou de origem cloud:
            // invalida apenas o JWT antigo para evitar erro de assinatura sem apagar os dados locais do iPhone.
            UserDefaults.standard.set(resolvedUrl, forKey: Self.boundServerOriginKey)
            KeychainHelper.shared.delete(key: tokenKey)
            UserDefaults.standard.removeObject(forKey: tokenKey)
        } else {
            UserDefaults.standard.set(resolvedUrl, forKey: Self.boundServerOriginKey)
        }

        loadPersistedSessionSynchronously()
        DispatchQueue.main.async {
            AuthManager.shared.completePostInitStartup()
        }
    }

    private func loadPersistedSessionSynchronously() {
        let keychainToken = KeychainHelper.shared.loadString(key: tokenKey)
        let legacyDefaultsToken = UserDefaults.standard.string(forKey: tokenKey)
        let token = keychainToken ?? legacyDefaultsToken
        if keychainToken == nil, let legacy = legacyDefaultsToken, !legacy.isEmpty {
            KeychainHelper.shared.save(key: tokenKey, string: legacy)
        }
        UserDefaults.standard.removeObject(forKey: tokenKey)

        let keychainUserId = KeychainHelper.shared.loadString(key: userIdKey)
        let legacyDefaultsUserId = UserDefaults.standard.string(forKey: userIdKey)
        let userId = keychainUserId ?? legacyDefaultsUserId
        if keychainUserId == nil, let legacyId = legacyDefaultsUserId, !legacyId.isEmpty {
            KeychainHelper.shared.save(key: userIdKey, string: legacyId)
        }
        UserDefaults.standard.removeObject(forKey: userIdKey)

        if let token, !token.isEmpty, let userId, !userId.isEmpty {
            self.jwtToken = token
            self.currentUserId = userId
            self.currentUserEmail = KeychainHelper.shared.loadString(key: userEmailKey) ?? UserDefaults.standard.string(forKey: userEmailKey)
            self.currentUserName = KeychainHelper.shared.loadString(key: userNameKey) ?? UserDefaults.standard.string(forKey: userNameKey)
            self.currentAuthProvider = KeychainHelper.shared.loadString(key: authProviderKey) ?? UserDefaults.standard.string(forKey: authProviderKey)
            let storedTier = KeychainHelper.shared.loadString(key: accountTierKey) ?? UserDefaults.standard.string(forKey: accountTierKey)
            self.accountTier = AccountTier.from(storedTier)
            self.isAuthenticated = true
            self.isGuest = false
        } else {
            self.jwtToken = nil
            self.currentUserId = nil
            self.currentUserEmail = nil
            self.currentUserName = nil
            self.currentAuthProvider = nil
            self.accountTier = .free
            self.isAuthenticated = false
            self.isGuest = false
        }
    }

    public func completePostInitStartup() {
        guard isAuthenticated, let userId = currentUserId, !userId.isEmpty else {
            AIDataConsentManager.shared.refreshForCurrentUser()
            return
        }

        let activeLocalId = UserDefaults.standard.string(forKey: BackendSyncManager.activeLocalUserIdKey)
        if activeLocalId != userId {
            BackendSyncManager.shared.markRequiresInitialPull(true)
            BackendSyncManager.shared.hasCompletedInitialSessionPull = false
        } else if let localProfile = try? FormaDataStore.shared.mainContext.fetch(FetchDescriptor<Profile>()).first,
                  localProfile.onboardingCompleted {
            BackendSyncManager.shared.hasCompletedInitialSessionPull = true
        }

        AIDataConsentManager.shared.refreshForCurrentUser()
        AccountRealtimeSocketManager.shared.connectIfNeeded()
        Task { @MainActor in
            await AIDataConsentManager.shared.refreshFromBackendIfAuthenticated()
            await SubscriptionManager.shared.refreshAllBeforeDisplay()
        }
    }

    // MARK: - Restauração de Sessão Segura via Keychain
    public func restoreSession() {
        loadPersistedSessionSynchronously()
        DispatchQueue.main.async {
            AuthManager.shared.completePostInitStartup()
        }
    }

    // MARK: - Login com E-mail e Senha
    public func login(email: String, password: String) async throws {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanEmail.isEmpty, !cleanPassword.isEmpty else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Preencha e-mail e senha."])
        }

        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/login",
            method: "POST",
            jsonBody: ["email": cleanEmail, "password": cleanPassword],
            authenticated: false,
            timeout: 12.0,
            interceptAccountStatus: false
        )
        try await handleAuthSuccess(data: data, defaultProvider: "EMAIL", isNewRegistration: false)
    }

    // MARK: - Cadastro com E-mail e Senha
    public func register(
        name: String,
        email: String,
        password: String,
        termsAccepted: Bool = true,
        sensitiveHealthConsentGranted: Bool = true,
        aiConsentGranted: Bool = false,
        ageConfirmed18Plus: Bool = true
    ) async throws {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanName.isEmpty, !cleanEmail.isEmpty, !cleanPassword.isEmpty else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Preencha todos os campos."])
        }
        guard cleanPassword.count >= 8 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A senha deve ter pelo menos 8 caracteres."])
        }
        guard ageConfirmed18Plus else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "É necessário confirmar que você possui 18 anos ou mais (LGPD Art. 14)."])
        }
        guard termsAccepted && sensitiveHealthConsentGranted else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Aceite os Termos, a Política de Privacidade e o tratamento de dados de saúde (LGPD Art. 11)."])
        }

        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/register",
            method: "POST",
            jsonBody: [
                "name": cleanName,
                "email": cleanEmail,
                "password": cleanPassword,
                "termsAccepted": termsAccepted,
                "termsVersion": "2026-11-v2",
                "sensitiveHealthConsentGranted": sensitiveHealthConsentGranted,
                "aiConsentGranted": aiConsentGranted,
                "ageConfirmed18Plus": ageConfirmed18Plus
            ],
            authenticated: false,
            timeout: 12.0,
            interceptAccountStatus: false
        )
        try await handleAuthSuccess(data: data, defaultProvider: "EMAIL", isNewRegistration: true)
    }

    // MARK: - Login Social Seguro (Google ou Apple ID)
    public func loginWithSocial(
        provider: String,
        providerId: String,
        email: String?,
        name: String?,
        idToken: String?,
        nonce: String? = nil,
        isRegisterIntent: Bool = false,
        confirmExisting: Bool = true,
        authorizationCode: String? = nil,
        oauthRefreshToken: String? = nil
    ) async throws {
        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        var body: [String: Any] = [
            "provider": provider.uppercased(),
            "providerId": providerId,
            "isRegisterIntent": isRegisterIntent,
            "confirmExisting": confirmExisting
        ]
        if let email, !email.isEmpty { body["email"] = email }
        if let name, !name.isEmpty { body["name"] = name }
        if let idToken, !idToken.isEmpty { body["idToken"] = idToken }
        if let nonce, !nonce.isEmpty { body["nonce"] = nonce }
        if let authorizationCode, !authorizationCode.isEmpty { body["authorizationCode"] = authorizationCode }
        if let oauthRefreshToken, !oauthRefreshToken.isEmpty { body["oauthRefreshToken"] = oauthRefreshToken }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/social",
            method: "POST",
            jsonBody: body,
            authenticated: false,
            timeout: 14.0,
            interceptAccountStatus: false
        )
        KeychainHelper.shared.save(key: socialProviderIdKey, string: providerId)
        try await handleAuthSuccess(data: data, defaultProvider: provider.uppercased(), isNewRegistration: false)
    }

    // MARK: - Recuperação, Alteração de Senha e Exclusão de Conta
    public func requestPasswordReset(email: String) async throws -> String {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Informe o e-mail da sua conta."])
        }

        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/forgot-password",
            method: "POST",
            jsonBody: ["email": cleanEmail],
            authenticated: false,
            timeout: 10.0,
            interceptAccountStatus: false
        )

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let msg = json["message"] as? String, !msg.isEmpty {
            return msg
        }
        return "Se o e-mail estiver cadastrado, enviamos o código de 6 dígitos para redefinir sua senha."
    }

    public func resetPassword(email: String, code: String, newPassword: String) async throws {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPassword = newPassword.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanEmail.isEmpty, cleanCode.count == 6 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Informe o e-mail e o código de 6 dígitos."])
        }
        guard cleanPassword.count >= 8 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A nova senha deve ter pelo menos 8 caracteres."])
        }

        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/reset-password",
            method: "POST",
            jsonBody: [
                "email": cleanEmail,
                "code": cleanCode,
                "newPassword": cleanPassword
            ],
            authenticated: false,
            timeout: 12.0,
            interceptAccountStatus: false
        )
        try await handleAuthSuccess(data: data, defaultProvider: "EMAIL", isNewRegistration: false)
    }

    public func changePassword(currentPassword: String, newPassword: String) async throws {
        let cleanCurrent = currentPassword.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNew = newPassword.trimmingCharacters(in: .whitespacesAndNewlines)

        guard cleanNew.count >= 8 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A nova senha deve ter pelo menos 8 caracteres."])
        }

        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/change-password",
            method: "POST",
            jsonBody: [
                "currentPassword": cleanCurrent,
                "newPassword": cleanNew
            ],
            authenticated: true,
            timeout: 10.0
        )
    }

    public func deleteAccount() async throws {
        var freshAppleAuthCode: String? = nil
        if (currentAuthProvider ?? "").uppercased() == "APPLE" {
            let coordinator = AppleDeletionAuthorizationCoordinator()
            self.appleDeletionCoordinator = coordinator
            defer { self.appleDeletionCoordinator = nil }
            freshAppleAuthCode = try await coordinator.requestAuthorizationCode()
        }

        let storedGoogleToken = GoogleAuthService.shared.storedOAuthToken
        var deleteBody: [String: Any] = [:]
        if let freshAppleAuthCode, !freshAppleAuthCode.isEmpty {
            deleteBody["authorizationCode"] = freshAppleAuthCode
        }
        if let storedGoogleToken, !storedGoogleToken.isEmpty {
            deleteBody["googleToken"] = storedGoogleToken
        }

        BackendSyncManager.shared.cancelPendingSyncs()
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/account",
            method: "DELETE",
            jsonBody: deleteBody.isEmpty ? nil : deleteBody,
            authenticated: true,
            timeout: 20.0,
            interceptAccountStatus: false
        )

        if let socialSub = KeychainHelper.shared.loadString(key: socialProviderIdKey), !socialSub.isEmpty {
            KeychainHelper.shared.delete(key: "apple_name_\(socialSub)")
            KeychainHelper.shared.delete(key: "apple_email_\(socialSub)")
        }
        KeychainHelper.shared.delete(key: socialProviderIdKey)
        GoogleAuthService.shared.clearStoredOAuthToken()
        AIDataConsentManager.shared.clearConsentForCurrentUser()
        logout()
    }

    // MARK: - Logout Seguro
    public func logout() {
        let tokenToRevoke = self.jwtToken
        AccountRealtimeSocketManager.shared.disconnect()
        SubscriptionManager.shared.resetOnLogout()
        toastDismissTask?.cancel()
        self.realtimeToastMessage = nil
        self.isAuthenticated = false
        self.isGuest = false
        self.jwtToken = nil
        self.currentUserId = nil
        self.currentUserEmail = nil
        self.currentUserName = nil
        self.currentAuthProvider = nil
        self.accountTier = .free

        if let tokenToRevoke, !tokenToRevoke.isEmpty {
            Task {
                _ = try? await FormaAPIClient.shared.requestData(
                    path: "/api/auth/logout",
                    method: "POST",
                    authenticated: true,
                    timeout: 8.0,
                    interceptAccountStatus: false,
                    overrideBearerToken: tokenToRevoke
                )
            }
        }

        KeychainHelper.shared.delete(key: tokenKey)
        KeychainHelper.shared.delete(key: userIdKey)
        KeychainHelper.shared.delete(key: userEmailKey)
        KeychainHelper.shared.delete(key: userNameKey)
        KeychainHelper.shared.delete(key: authProviderKey)
        KeychainHelper.shared.delete(key: accountTierKey)
        KeychainHelper.shared.delete(key: socialProviderIdKey)
        GoogleAuthService.shared.clearStoredOAuthToken()

        UserDefaults.standard.removeObject(forKey: tokenKey)
        UserDefaults.standard.removeObject(forKey: userIdKey)
        UserDefaults.standard.removeObject(forKey: userEmailKey)
        UserDefaults.standard.removeObject(forKey: userNameKey)
        UserDefaults.standard.removeObject(forKey: authProviderKey)
        UserDefaults.standard.removeObject(forKey: accountTierKey)
        UserDefaults.standard.removeObject(forKey: "forma_is_guest")
        UserDefaults.standard.removeObject(forKey: BackendSyncManager.activeLocalUserIdKey)
        UserDefaults.standard.removeObject(forKey: "forma_last_sync_timestamp")

        self.isAccountPaused = false
        self.accountPausedReason = nil
        self.isAiAccessBlocked = false
        self.isGlobalAiBlocked = false
        BackendSyncManager.shared.cancelPendingSyncs()
        BackendSyncManager.shared.clearAllPendingDeletions()
        BackendSyncManager.shared.markRequiresInitialPull(false)
        BackendSyncManager.shared.hasCompletedInitialSessionPull = false
        AIDataConsentManager.shared.clearConsentForCurrentUser()
        WidgetDataManager.shared.clearAllUserData()
        FormaDataStore.shared.resetLocalDataForUserSwitch(userName: nil, userEmail: nil)
    }

    public func updateLocalUserName(_ newName: String) {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        self.currentUserName = trimmed
        KeychainHelper.shared.save(key: userNameKey, string: trimmed)
        UserDefaults.standard.set(trimmed, forKey: userNameKey)
    }

    // MARK: - Sucesso de Autenticação & Isolamento Total Multi-Usuário
    private func handleAuthSuccess(data: Data, defaultProvider: String, isNewRegistration: Bool) async throws {
        struct AuthProfileDTO: Decodable {
            let goal: String?
            let targetCalorieIntake: Double?
            let targetCalorieBurn: Double?
            let targetSteps: Double?
            let targetWeight: Double?
            let targetProtein: Double?
            let targetCarbohydrates: Double?
            let targetFat: Double?
            let targetWaterMilliliters: Int?
            let heightMeters: Double?
            let currentWeight: Double?
            let onboardingCompleted: Bool?
        }

        struct AuthResponseModel: Decodable {
            let token: String
            let userId: String
            let email: String?
            let name: String?
            let authProvider: String?
            let accountTier: String?
            let profile: AuthProfileDTO?
        }

        let result = try JSONDecoder().decode(AuthResponseModel.self, from: data)

        self.isAccountPaused = false
        self.accountPausedReason = nil
        self.isAiAccessBlocked = false
        self.isGlobalAiBlocked = false
        BackendSyncManager.shared.cancelPendingSyncs()
        BackendSyncManager.shared.clearAllPendingDeletions()
        BackendSyncManager.shared.hasCompletedInitialSessionPull = false

        // 1. Sempre limpa 100% do cache SwiftData local antes de iniciar a sessão da conta autenticada
        FormaDataStore.shared.resetLocalDataForUserSwitch(userName: result.name, userEmail: result.email)
        UserDefaults.standard.set(result.userId, forKey: BackendSyncManager.activeLocalUserIdKey)

        // 2. Grava credenciais em memória e no Keychain antes do pull inicial
        UserDefaults.standard.removeObject(forKey: tokenKey)
        UserDefaults.standard.removeObject(forKey: userIdKey)
        UserDefaults.standard.set(result.email, forKey: userEmailKey)
        UserDefaults.standard.set(result.name, forKey: userNameKey)
        UserDefaults.standard.removeObject(forKey: "forma_is_guest")

        self.jwtToken = result.token
        self.currentUserId = result.userId
        self.currentUserEmail = result.email
        self.currentUserName = result.name
        self.currentAuthProvider = result.authProvider ?? defaultProvider
        UserDefaults.standard.set(self.currentAuthProvider, forKey: authProviderKey)

        KeychainHelper.shared.save(key: tokenKey, string: result.token)
        KeychainHelper.shared.save(key: userIdKey, string: result.userId)
        if let email = result.email, !email.isEmpty {
            KeychainHelper.shared.save(key: userEmailKey, string: email)
        }
        if let name = result.name, !name.isEmpty {
            KeychainHelper.shared.save(key: userNameKey, string: name)
        }
        if let provider = self.currentAuthProvider, !provider.isEmpty {
            KeychainHelper.shared.save(key: authProviderKey, string: provider)
        }

        self.updateAccountTier(result.accountTier)
        AIDataConsentManager.shared.refreshForCurrentUser()
        Task { @MainActor in
            await AIDataConsentManager.shared.refreshFromBackendIfAuthenticated()
        }

        // 3. Aplica dados imediatos do perfil retornados pelo servidor
        let localProfile = FormaDataStore.shared.ensureSingleProfile(in: FormaDataStore.shared.mainContext)
        if let name = result.name, !name.isEmpty {
            localProfile.nome = name
        }
        if let email = result.email, !email.isEmpty {
            localProfile.email = email
        }
        if let p = result.profile {
            if let g = p.goal { localProfile.goalRaw = g }
            if let ci = p.targetCalorieIntake { localProfile.targetCalorieIntake = ci }
            if let cb = p.targetCalorieBurn { localProfile.targetCalorieBurn = cb }
            if let ts = p.targetSteps { localProfile.targetSteps = ts }
            if let tw = p.targetWeight { localProfile.targetWeight = tw }
            if let tp = p.targetProtein { localProfile.targetProtein = tp }
            if let tc = p.targetCarbohydrates { localProfile.targetCarbohydrates = tc }
            if let tf = p.targetFat { localProfile.targetFat = tf }
            if let twm = p.targetWaterMilliliters { localProfile.targetWaterMilliliters = twm }
            if let hm = p.heightMeters { localProfile.height = hm }
            if let cw = p.currentWeight { localProfile.currentWeight = cw }
            localProfile.onboardingCompleted = isNewRegistration ? false : (p.onboardingCompleted ?? false)
        } else {
            localProfile.onboardingCompleted = false
        }
        try? FormaDataStore.shared.mainContext.save()

        // 4. Para contas novas (ou sem onboarding concluído), bloqueia importação retroativa do Apple Saúde do iPhone
        if isNewRegistration || !localProfile.onboardingCompleted {
            FormaDataStore.setHealthKitImportCutoff(Date(), forUserId: result.userId)
        }

        // 5. Em login de conta existente, baixa os dados do Supabase uma única vez para o cache local antes de abrir a tela
        if isNewRegistration {
            BackendSyncManager.shared.markRequiresInitialPull(false)
            BackendSyncManager.shared.hasCompletedInitialSessionPull = true
        } else {
            try? await BackendSyncManager.shared.pullInitialStateForAuthenticatedUser(
                context: FormaDataStore.shared.mainContext
            )
        }

        withAnimation(.easeInOut(duration: 0.25)) {
            self.isGuest = false
            self.isAuthenticated = true
        }
        AccountRealtimeSocketManager.shared.connectIfNeeded(forceReconnect: true)
        Task { @MainActor in
            await SubscriptionManager.shared.refreshAllBeforeDisplay()
        }
    }
}

// MARK: - Sincronização Instantânea da Conta via WebSocket (/ws/account)

@MainActor
public final class AccountRealtimeSocketManager: ObservableObject {
    public static let shared = AccountRealtimeSocketManager()

    @Published public private(set) var isConnected: Bool = false

    private var webSocketTask: URLSessionWebSocketTask? = nil
    private var receiveTask: Task<Void, Never>? = nil
    private var pingTask: Task<Void, Never>? = nil
    private var reconnectTask: Task<Void, Never>? = nil

    private var isAppInForeground: Bool = true
    private var candidateIndex: Int = 0
    private var reconnectAttempt: Int = 0
    private var connectedToken: String? = nil

    private let urlSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.waitsForConnectivity = false
        return URLSession(configuration: config)
    }()

    private init() {}

    public func setAppForeground(_ active: Bool) {
        isAppInForeground = active
        if active {
            reconnectAttempt = 0
            connectIfNeeded()
        } else {
            disconnect()
        }
    }

    public func connectIfNeeded(forceReconnect: Bool = false) {
        guard isAppInForeground,
              AuthManager.shared.isAuthenticated,
              let token = AuthManager.shared.jwtToken,
              !token.isEmpty else {
            disconnect()
            return
        }

        if !forceReconnect, webSocketTask != nil, connectedToken == token {
            return
        }

        reconnectTask?.cancel()
        reconnectTask = nil
        cleanupCurrentSocket()

        let candidates = AuthManager.shared.candidateServerUrls
        guard !candidates.isEmpty else { return }

        let chosenBase = candidates[candidateIndex % candidates.count]
        guard let request = makeWebSocketRequest(baseHttpUrl: chosenBase, token: token) else {
            scheduleReconnect()
            return
        }

        let task = urlSession.webSocketTask(with: request)
        self.webSocketTask = task
        self.connectedToken = token
        task.resume()

        receiveTask = Task { @MainActor [weak self] in
            await self?.runReceiveLoop(for: task, baseHttpUrl: chosenBase)
        }

        pingTask = Task { @MainActor [weak self] in
            await self?.runPingLoop(for: task)
        }
    }

    public func disconnect() {
        reconnectTask?.cancel()
        reconnectTask = nil
        cleanupCurrentSocket()
    }

    private func cleanupCurrentSocket() {
        receiveTask?.cancel()
        receiveTask = nil
        pingTask?.cancel()
        pingTask = nil
        let existing = webSocketTask
        webSocketTask = nil
        connectedToken = nil
        isConnected = false
        existing?.cancel(with: .goingAway, reason: nil)
    }

    private func makeWebSocketRequest(baseHttpUrl: String, token: String) -> URLRequest? {
        var wsBase = baseHttpUrl
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))

        if wsBase.lowercased().hasPrefix("https://") {
            wsBase = "wss://" + wsBase.dropFirst("https://".count)
        } else if wsBase.lowercased().hasPrefix("http://") {
            #if !DEBUG
            return nil
            #else
            wsBase = "ws://" + wsBase.dropFirst("http://".count)
            #endif
        } else {
            #if !DEBUG
            wsBase = "wss://" + wsBase
            #else
            wsBase = "ws://" + wsBase
            #endif
        }

        // Token enviado exclusivamente via cabeçalho Authorization (nunca em query string ?token=)
        guard let url = URL(string: "\(wsBase)/ws/account") else {
            return nil
        }

        var request = URLRequest(url: url, timeoutInterval: 15.0)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return request
    }

    private func runReceiveLoop(for task: URLSessionWebSocketTask, baseHttpUrl: String) async {
        while !Task.isCancelled && self.webSocketTask === task {
            do {
                let message = try await task.receive()
                guard self.webSocketTask === task else { return }

                if !isConnected {
                    isConnected = true
                    reconnectAttempt = 0
                    candidateIndex = 0
                    AuthManager.shared.markWorkingServerUrl(baseHttpUrl)
                }

                switch message {
                case .string(let text):
                    handleIncomingText(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        handleIncomingText(text)
                    }
                @unknown default:
                    break
                }
            } catch {
                guard self.webSocketTask === task else { return }
                cleanupCurrentSocket()
                scheduleReconnect()
                return
            }
        }
    }

    private func runPingLoop(for task: URLSessionWebSocketTask) async {
        while !Task.isCancelled && self.webSocketTask === task {
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            guard !Task.isCancelled, self.webSocketTask === task else { return }
            task.sendPing { _ in }
            try? await task.send(.string("ping"))
        }
    }

    private func handleIncomingText(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.lowercased() != "pong" else { return }
        guard let data = trimmed.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let type = json["type"] as? String else {
            return
        }

        if type == "ACCOUNT_DELETED" {
            AuthManager.shared.logout()
            return
        }

        if type == "BILLING_CATALOG_UPDATED" {
            Task { @MainActor in
                await SubscriptionManager.shared.refreshAllBeforeDisplay()
            }
            return
        }

        if type == "GLOBAL_AI_STATE" {
            let blocked = (json["aiBlocked"] as? Bool) ?? false
            let didChange = AuthManager.shared.isGlobalAiBlocked != blocked
            AuthManager.shared.isGlobalAiBlocked = blocked
            if didChange {
                AuthManager.shared.showRealtimeToast(
                    blocked
                        ? "Serviço de IA temporariamente pausado pelo administrador."
                        : "Serviço de IA reativado em tempo real."
                )
            }
            return
        }

        if type == "ACCOUNT_STATE" {
            if let msgUserId = json["userId"] as? String,
               let activeId = AuthManager.shared.currentUserId,
               !msgUserId.isEmpty,
               msgUserId.lowercased() != activeId.lowercased() {
                return
            }

            let rawTier = json["accountTier"] as? String
            if let rawTier {
                AuthManager.shared.updateAccountTier(rawTier, notifyUser: true)
            }

            let eventName = json["event"] as? String
            if let rawStatus = json["status"] as? String {
                let pausedNow = rawStatus.uppercased() == "PAUSED"
                let wasPaused = AuthManager.shared.isAccountPaused
                AuthManager.shared.isAccountPaused = pausedNow
                let rawReason = (json["pausedReason"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
                AuthManager.shared.accountPausedReason = pausedNow && rawReason?.isEmpty == false ? rawReason : nil

                if eventName == "ADMIN_USER_PAUSED" || (!wasPaused && pausedNow) {
                    AuthManager.shared.showRealtimeToast(
                        rawReason?.isEmpty == false ? "Conta pausada: \(rawReason!)" : "Sua conta foi pausada pelo administrador."
                    )
                } else if eventName == "ADMIN_USER_RESUMED" || (wasPaused && !pausedNow) {
                    AuthManager.shared.showRealtimeToast("Sua conta foi reativada pelo administrador.")
                }
            }

            if let aiBlocked = json["aiBlocked"] as? Bool {
                AuthManager.shared.isAiAccessBlocked = aiBlocked
            }

            if let rawName = json["name"] as? String, !rawName.isEmpty, !rawName.contains("*") {
                AuthManager.shared.updateLocalUserName(rawName)
            }

            let subStatus = json["subscriptionStatus"] as? String
            let overdueDay = json["overdueDay"] as? Int
            let graceEndsAtUtc = json["graceEndsAtUtc"] as? String
            let warningMessage = json["warningMessage"] as? String

            if subStatus != nil || overdueDay != nil || warningMessage != nil ||
                (eventName?.hasPrefix("SUBSCRIPTION_") == true) {
                SubscriptionManager.shared.applyRealtimeEvent(
                    subscriptionStatus: subStatus,
                    overdueDay: overdueDay,
                    graceEndsAtUtc: graceEndsAtUtc,
                    warningMessage: warningMessage
                )
            } else if rawTier != nil || (eventName?.hasPrefix("ADMIN_USER_TIER_") == true) {
                Task { @MainActor in
                    await SubscriptionManager.shared.refreshAllBeforeDisplay()
                }
            }

            if let warningMessage, !warningMessage.isEmpty,
               eventName == "SUBSCRIPTION_OVERDUE_WARNING"
                || eventName == "SUBSCRIPTION_ENFORCED"
                || eventName == "SUBSCRIPTION_EXPIRED" {
                AuthManager.shared.showRealtimeToast(warningMessage)
            }

            if eventName == "ADMIN_USER_RESET" || eventName == "ADMIN_USER_UPDATED" {
                Task { @MainActor in
                    await BackendSyncManager.shared.sync(context: FormaDataStore.shared.mainContext, forcePull: true)
                }
            }
        }
    }

    private func scheduleReconnect() {
        guard isAppInForeground, AuthManager.shared.isAuthenticated else { return }
        reconnectTask?.cancel()
        candidateIndex += 1
        let delaySeconds = min(2.0, 0.35 + Double(reconnectAttempt) * 0.45)
        reconnectAttempt += 1

        reconnectTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delaySeconds * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.connectIfNeeded(forceReconnect: true)
        }
    }
}
