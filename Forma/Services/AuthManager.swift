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

@MainActor
public final class AuthManager: ObservableObject {
    public static let shared = AuthManager()

    // MARK: - Chaves de Armazenamento Seguro (mantidas para compatibilidade de sessão)
    private let tokenKey = "forma_jwt_token"
    private let userIdKey = "forma_user_id"
    private let userEmailKey = "forma_user_email"
    private let userNameKey = "forma_user_name"
    private let authProviderKey = "forma_auth_provider"

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
    public static let fallbackCurrentLanUrl = "http://10.0.0.106:8085"
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
        if serverBaseUrl != trimmed {
            serverBaseUrl = trimmed
        }
        UserDefaults.standard.set(trimmed, forKey: Self.lastWorkingServerUrlKey)
    }

    public var candidateServerUrls: [String] {
        // Quando apontado para servidor HTTPS em nuvem (ex: Render), usa exclusivamente a URL de produção
        // sem tentar IPs locais nem reduzir o timeout em cold-starts.
        if Self.isConfiguredRemoteHttps {
            return [Self.defaultServerUrl]
        }

        var list: [String] = []
        if let plistUrl = Self.configuredPlistUrl, !plistUrl.isEmpty {
            list.append(plistUrl)
        }
        if let saved = UserDefaults.standard.string(forKey: Self.lastWorkingServerUrlKey),
           !saved.isEmpty,
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
    }

    // MARK: - Estados Publicados
    @Published public var isAuthenticated: Bool = false
    @Published public var isGuest: Bool = false
    @Published public var currentUserId: String? = nil
    @Published public var currentUserEmail: String? = nil
    @Published public var currentUserName: String? = nil
    @Published public var currentAuthProvider: String? = nil
    @Published public var jwtToken: String? = nil
    @Published public var isAuthenticating: Bool = false
    @Published public var errorMessage: String? = nil
    @Published public var serverBaseUrl: String

    private init() {
        let resolvedUrl = Self.defaultServerUrl
        self.serverBaseUrl = resolvedUrl

        let previousOrigin = UserDefaults.standard.string(forKey: Self.boundServerOriginKey)
        if Self.isConfiguredRemoteHttps, previousOrigin != resolvedUrl {
            // Trocou de servidor local para nuvem (Render): invalida apenas o JWT antigo sem apagar os dados locais do iPhone,
            // permitindo que no novo login todos os registros locais sejam enviados ao Render / Supabase.
            UserDefaults.standard.set(resolvedUrl, forKey: Self.boundServerOriginKey)
            UserDefaults.standard.set(resolvedUrl, forKey: Self.lastWorkingServerUrlKey)
            KeychainHelper.shared.delete(key: tokenKey)
            UserDefaults.standard.removeObject(forKey: tokenKey)
        } else if previousOrigin == nil {
            UserDefaults.standard.set(resolvedUrl, forKey: Self.boundServerOriginKey)
        }

        restoreSession()
    }

    // MARK: - Restauração de Sessão Segura via Keychain
    public func restoreSession() {
        let token = KeychainHelper.shared.loadString(key: tokenKey) ?? UserDefaults.standard.string(forKey: tokenKey)
        let userId = KeychainHelper.shared.loadString(key: userIdKey) ?? UserDefaults.standard.string(forKey: userIdKey)

        if let token, !token.isEmpty, let userId, !userId.isEmpty {
            self.jwtToken = token
            self.currentUserId = userId
            self.currentUserEmail = KeychainHelper.shared.loadString(key: userEmailKey) ?? UserDefaults.standard.string(forKey: userEmailKey)
            self.currentUserName = KeychainHelper.shared.loadString(key: userNameKey) ?? UserDefaults.standard.string(forKey: userNameKey)
            self.currentAuthProvider = KeychainHelper.shared.loadString(key: authProviderKey) ?? UserDefaults.standard.string(forKey: authProviderKey)

            let activeLocalId = UserDefaults.standard.string(forKey: BackendSyncManager.activeLocalUserIdKey)
            if activeLocalId != userId {
                BackendSyncManager.shared.markRequiresInitialPull(true)
                BackendSyncManager.shared.hasCompletedInitialSessionPull = false
            } else if let localProfile = try? FormaDataStore.shared.mainContext.fetch(FetchDescriptor<Profile>()).first,
                      localProfile.onboardingCompleted {
                BackendSyncManager.shared.hasCompletedInitialSessionPull = true
            }

            self.isAuthenticated = true
            self.isGuest = false
        } else {
            self.jwtToken = nil
            self.currentUserId = nil
            self.currentUserEmail = nil
            self.currentUserName = nil
            self.currentAuthProvider = nil
            self.isAuthenticated = false
            self.isGuest = false
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
    public func register(name: String, email: String, password: String) async throws {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanName.isEmpty, !cleanEmail.isEmpty, !cleanPassword.isEmpty else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Preencha todos os campos."])
        }
        guard cleanPassword.count >= 6 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A senha deve ter pelo menos 6 caracteres."])
        }

        isAuthenticating = true
        errorMessage = nil
        defer { isAuthenticating = false }

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/register",
            method: "POST",
            jsonBody: ["name": cleanName, "email": cleanEmail, "password": cleanPassword],
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
        authorizationCode: String? = nil
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

        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/social",
            method: "POST",
            jsonBody: body,
            authenticated: false,
            timeout: 14.0,
            interceptAccountStatus: false
        )
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
        guard cleanPassword.count >= 6 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A nova senha deve ter pelo menos 6 caracteres."])
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

        guard cleanNew.count >= 6 else {
            throw NSError(domain: "Auth", code: 400, userInfo: [NSLocalizedDescriptionKey: "A nova senha deve ter pelo menos 6 caracteres."])
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
        BackendSyncManager.shared.cancelPendingSyncs()
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/auth/account",
            method: "DELETE",
            authenticated: true,
            timeout: 15.0,
            interceptAccountStatus: false
        )
        AIDataConsentManager.shared.clearConsentForCurrentUser()
        logout()
    }

    // MARK: - Logout Seguro
    public func logout() {
        self.isAuthenticated = false
        self.isGuest = false
        self.jwtToken = nil
        self.currentUserId = nil
        self.currentUserEmail = nil
        self.currentUserName = nil
        self.currentAuthProvider = nil

        KeychainHelper.shared.delete(key: tokenKey)
        KeychainHelper.shared.delete(key: userIdKey)
        KeychainHelper.shared.delete(key: userEmailKey)
        KeychainHelper.shared.delete(key: userNameKey)
        KeychainHelper.shared.delete(key: authProviderKey)

        UserDefaults.standard.removeObject(forKey: tokenKey)
        UserDefaults.standard.removeObject(forKey: userIdKey)
        UserDefaults.standard.removeObject(forKey: userEmailKey)
        UserDefaults.standard.removeObject(forKey: userNameKey)
        UserDefaults.standard.removeObject(forKey: authProviderKey)
        UserDefaults.standard.removeObject(forKey: "forma_is_guest")
        UserDefaults.standard.removeObject(forKey: BackendSyncManager.activeLocalUserIdKey)
        UserDefaults.standard.removeObject(forKey: "forma_last_sync_timestamp")

        BackendSyncManager.shared.cancelPendingSyncs()
        BackendSyncManager.shared.clearAllPendingDeletions()
        BackendSyncManager.shared.markRequiresInitialPull(false)
        BackendSyncManager.shared.hasCompletedInitialSessionPull = false
        AIDataConsentManager.shared.refreshForCurrentUser()
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
            let profile: AuthProfileDTO?
        }

        let result = try JSONDecoder().decode(AuthResponseModel.self, from: data)

        BackendSyncManager.shared.cancelPendingSyncs()
        BackendSyncManager.shared.clearAllPendingDeletions()
        BackendSyncManager.shared.hasCompletedInitialSessionPull = false

        // 1. Sempre limpa 100% do cache SwiftData local antes de iniciar a sessão da conta autenticada
        FormaDataStore.shared.resetLocalDataForUserSwitch(userName: result.name, userEmail: result.email)
        UserDefaults.standard.set(result.userId, forKey: BackendSyncManager.activeLocalUserIdKey)

        // 2. Grava credenciais em memória e no Keychain antes do pull inicial
        self.jwtToken = result.token
        self.currentUserId = result.userId
        self.currentUserEmail = result.email
        self.currentUserName = result.name
        self.currentAuthProvider = result.authProvider ?? defaultProvider

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

        UserDefaults.standard.set(result.userId, forKey: userIdKey)
        UserDefaults.standard.set(result.email, forKey: userEmailKey)
        UserDefaults.standard.set(result.name, forKey: userNameKey)
        UserDefaults.standard.set(self.currentAuthProvider, forKey: authProviderKey)
        UserDefaults.standard.removeObject(forKey: "forma_is_guest")
        AIDataConsentManager.shared.refreshForCurrentUser()

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
    }
}
