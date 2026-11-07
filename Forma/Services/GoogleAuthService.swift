//
//  GoogleAuthService.swift
//  Forma
//
//  Created by João Pedro on 11/02/26.
//

import Foundation
import AuthenticationServices
import CryptoKit
import UIKit

@MainActor
public final class GoogleAuthService: NSObject, ASWebAuthenticationPresentationContextProviding {
    public static let shared = GoogleAuthService()
    
    // MARK: - Resolução Dinâmica de Client ID (Sem valores sensíveis hardcoded)
    private static var cachedClientID: String? = nil
    
    public static var clientID: String {
        get {
            // 1. Variável de ambiente no Scheme do Xcode (Arguments -> Environment Variables: GOOGLE_CLIENT_ID)
            if let envID = ProcessInfo.processInfo.environment["GOOGLE_CLIENT_ID"], !envID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return envID.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            
            // 2. Info.plist do app (chave GIDClientID)
            if let infoID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String, !infoID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return infoID.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            
            // 3. Secrets.plist local no projeto (ignorado no Git)
            if let path = Bundle.main.path(forResource: "Secrets", ofType: "plist"),
               let dict = NSDictionary(contentsOfFile: path) as? [String: Any],
               let secretID = dict["GOOGLE_CLIENT_ID"] as? String, !secretID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return secretID.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            
            // 4. Configuração carregada dinamicamente do backend
            if let cached = cachedClientID, !cached.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return cached.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            
            return ""
        }
        set {
            cachedClientID = newValue
        }
    }
    
    /// Busca a configuração pública (como GOOGLE_CLIENT_ID) do backend de forma segura e assíncrona
    public func fetchConfigFromBackend() async {
        do {
            let data = try await FormaAPIClient.shared.requestData(
                path: "/api/auth/config",
                method: "GET",
                authenticated: false,
                timeout: 4.0,
                interceptAccountStatus: false
            )
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let id = json["googleClientId"] as? String,
               !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Self.cachedClientID = id.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        } catch {
            // Ignora falha silenciosamente para permitir fallback ao Secrets.plist / Info.plist
        }
    }
    
    /// Retorna o URL Scheme reverso exigido pelo Google OAuth (ex: com.googleusercontent.apps.12345)
    public static var reverseClientIDScheme: String {
        let id = clientID
        if id.contains(".apps.googleusercontent.com") {
            let prefix = id.replacingOccurrences(of: ".apps.googleusercontent.com", with: "")
            return "com.googleusercontent.apps.\(prefix)"
        }
        return "forma"
    }
    
    private static let googleOAuthTokenKey = "forma_google_oauth_token"

    public var storedOAuthToken: String? {
        KeychainHelper.shared.loadString(key: Self.googleOAuthTokenKey)
    }

    public func clearStoredOAuthToken() {
        KeychainHelper.shared.delete(key: Self.googleOAuthTokenKey)
    }

    private var authSession: ASWebAuthenticationSession?
    
    public struct GoogleUserResult {
        public let idToken: String
        public let refreshToken: String?
        public let accessToken: String?
        public let sub: String?
        public let email: String?
        public let name: String?
        
        public init(
            idToken: String,
            refreshToken: String? = nil,
            accessToken: String? = nil,
            sub: String?,
            email: String?,
            name: String?
        ) {
            self.idToken = idToken
            self.refreshToken = refreshToken
            self.accessToken = accessToken
            self.sub = sub
            self.email = email
            self.name = name
        }
    }
    
    // MARK: - ASWebAuthenticationPresentationContextProviding
    public func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? scenes.compactMap { $0 as? UIWindowScene }.first
        
        if let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first {
            return window
        }
        guard let scene = windowScene else {
            preconditionFailure("Nenhuma UIWindowScene ativa disponível para autenticação Google.")
        }
        return UIWindow(windowScene: scene)
    }
    
    // MARK: - Início do Fluxo Oficial Google OAuth 2.0 com PKCE
    public func startSignIn(rawNonce: String) async throws -> GoogleUserResult {
        // Tenta buscar do backend se o clientID ainda não foi carregado
        if Self.clientID.isEmpty {
            await fetchConfigFromBackend()
        }
        
        let clientID = Self.clientID
        guard !clientID.isEmpty else {
            throw NSError(
                domain: "GoogleAuth",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "O Client ID do Google não foi configurado. Defina a variável GOOGLE_CLIENT_ID no arquivo .env do backend, no Secrets.plist ou no Scheme do Xcode."]
            )
        }
        
        // 1. Geração de PKCE Code Verifier e Code Challenge (RFC 7636)
        let codeVerifier = generateCodeVerifier()
        let codeChallenge = generateCodeChallenge(from: codeVerifier)
        
        let redirectScheme = Self.reverseClientIDScheme
        let redirectUri = "\(redirectScheme):/oauth2redirect"
        
        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")
        components?.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "scope", value: "openid profile email"),
            URLQueryItem(name: "prompt", value: "select_account")
        ]
        
        guard let authUrl = components?.url else {
            throw NSError(domain: "GoogleAuth", code: 400, userInfo: [NSLocalizedDescriptionKey: "URL de autenticação do Google inválida."])
        }
        
        let authCode: String = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authUrl,
                callbackURLScheme: redirectScheme
            ) { callbackURL, error in
                Task { @MainActor in
                    GoogleAuthService.shared.authSession = nil
                    // Aguarda o encerramento da animação do sheet do Safari para evitar travamento de transição no SwiftUI
                    try? await Task.sleep(nanoseconds: 150_000_000)

                    if let error = error {
                        let nsErr = error as NSError
                        if nsErr.domain == ASWebAuthenticationSessionErrorDomain,
                           nsErr.code == ASWebAuthenticationSessionError.canceledLogin.rawValue {
                            continuation.resume(throwing: CancellationError())
                        } else {
                            continuation.resume(throwing: error)
                        }
                        return
                    }

                    guard let callbackURL = callbackURL else {
                        continuation.resume(throwing: NSError(domain: "GoogleAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "Nenhum retorno recebido do Google."]))
                        return
                    }

                    let params = Self.parseCallbackParams(url: callbackURL)
                    if let error = params["error"] {
                        continuation.resume(throwing: NSError(domain: "GoogleAuth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Erro de autorização do Google: \(error)"]))
                        return
                    }

                    guard let code = params["code"], !code.isEmpty else {
                        continuation.resume(throwing: NSError(domain: "GoogleAuth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Código de autorização não recebido do Google."]))
                        return
                    }

                    continuation.resume(returning: code)
                }
            }
            
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.authSession = session
            
            if !session.start() {
                continuation.resume(throwing: NSError(domain: "GoogleAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "Não foi possível abrir a autenticação do Google."]))
            }
        }
        
        // 2. Troca do código de autorização pelos tokens reais via PKCE (sem client_secret)
        return try await exchangeCodeForTokens(code: authCode, codeVerifier: codeVerifier, clientID: clientID, redirectUri: redirectUri)
    }
    
    // MARK: - Troca de Código PKCE por Tokens
    private func exchangeCodeForTokens(code: String, codeVerifier: String, clientID: String, redirectUri: String) async throws -> GoogleUserResult {
        guard let tokenUrl = URL(string: "https://oauth2.googleapis.com/token") else {
            throw NSError(domain: "GoogleAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "URL de token do Google inválida."])
        }
        
        var request = URLRequest(url: tokenUrl)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        let bodyParams = [
            "client_id": clientID,
            "code": code,
            "code_verifier": codeVerifier,
            "grant_type": "authorization_code",
            "redirect_uri": redirectUri
        ]
        
        let bodyString = bodyParams.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }.joined(separator: "&")
        request.httpBody = bodyString.data(using: .utf8)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let errJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let desc = errJson?["error_description"] as? String ?? "Erro ao trocar código por token com Google."
            throw NSError(domain: "GoogleAuth", code: 400, userInfo: [NSLocalizedDescriptionKey: desc])
        }
        
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let idToken = json["id_token"] as? String, !idToken.isEmpty else {
            throw NSError(domain: "GoogleAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "ID Token não retornado pelo Google."])
        }

        let refreshToken = (json["refresh_token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let accessToken = (json["access_token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let revocable = (refreshToken?.isEmpty == false ? refreshToken : accessToken), !revocable.isEmpty {
            KeychainHelper.shared.save(key: Self.googleOAuthTokenKey, string: revocable)
        }
        
        let claims = Self.decodeJwtClaims(idToken)
        let sub = claims["sub"] as? String
        let email = claims["email"] as? String
        let name = claims["name"] as? String
        
        return GoogleUserResult(
            idToken: idToken,
            refreshToken: refreshToken,
            accessToken: accessToken,
            sub: sub,
            email: email,
            name: name
        )
    }
    
    // MARK: - Geradores de PKCE (RFC 7636)
    private func generateCodeVerifier() -> String {
        var randomBytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        return Data(randomBytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func generateCodeChallenge(from verifier: String) -> String {
        let inputData = Data(verifier.utf8)
        let hashedData = SHA256.hash(data: inputData)
        return Data(hashedData).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    // MARK: - Extração de Parâmetros de Redirecionamento
    private static func parseCallbackParams(url: URL) -> [String: String] {
        var params: [String: String] = [:]
        
        if let query = url.query {
            for item in query.components(separatedBy: "&") {
                let parts = item.components(separatedBy: "=")
                if parts.count == 2, let k = parts[0].removingPercentEncoding, let v = parts[1].removingPercentEncoding {
                    params[k] = v
                }
            }
        }
        
        if let fragment = url.fragment {
            for item in fragment.components(separatedBy: "&") {
                let parts = item.components(separatedBy: "=")
                if parts.count == 2, let k = parts[0].removingPercentEncoding, let v = parts[1].removingPercentEncoding {
                    params[k] = v
                }
            }
        }
        
        return params
    }
    
    // MARK: - Decodificação Local de Claims (para UX imediata)
    private static func decodeJwtClaims(_ jwt: String) -> [String: Any] {
        let parts = jwt.components(separatedBy: ".")
        guard parts.count >= 2 else { return [:] }
        var base64 = parts[1]
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64.append("=")
        }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        return json
    }
    
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        return hashedData.compactMap { String(format: "%02x", $0) }.joined()
    }
}
