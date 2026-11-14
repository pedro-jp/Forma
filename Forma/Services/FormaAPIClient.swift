//
//  FormaAPIClient.swift
//  Forma
//
//  Cliente HTTP centralizado do Forma com failover automático de rede (LAN / Bonjour / Localhost),
//  injeção de JWT, interceptação de status de conta e sanitização amigável de erros.
//

import Foundation

public enum FormaAPIError: LocalizedError, Equatable {
    case invalidURL
    case unreachableServer
    case unauthorized(String)
    case accountDeleted(String)
    case accountPaused(String)
    case accountAlreadyExists(message: String, email: String, name: String?)
    case aiBlocked(String)
    case rateLimited(String)
    case serverError(statusCode: Int, message: String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Endereço de conexão inválido."
        case .unreachableServer:
            return "Não foi possível conectar ao servidor Forma. Verifique sua conexão Wi-Fi ou se o servidor está ativo."
        case .unauthorized(let msg),
             .accountDeleted(let msg),
             .accountPaused(let msg),
             .accountAlreadyExists(let msg, _, _),
             .aiBlocked(let msg),
             .rateLimited(let msg):
            return msg
        case .serverError(_, let message):
            return message
        }
    }
}

@MainActor
public final class FormaAPIClient {
    public static let shared = FormaAPIClient()

    private init() {}

    /// Executa uma requisição HTTP percorrendo automaticamente os hosts candidatos configurados no AuthManager.
    public func requestData(
        path: String,
        method: String = "GET",
        jsonBody: [String: Any]? = nil,
        authenticated: Bool = true,
        timeout: TimeInterval = 14.0,
        interceptAccountStatus: Bool = true,
        overrideBearerToken: String? = nil
    ) async throws -> Data {
        let normalizedPath = path.hasPrefix("/") ? path : "/\(path)"
        let candidates = AuthManager.shared.candidateServerUrls
        var serializedBody: Data? = nil
        if let jsonBody {
            serializedBody = try JSONSerialization.data(withJSONObject: jsonBody)
        }

        for (index, base) in candidates.enumerated() {
            var cleanBase = base.trimmingCharacters(in: .whitespacesAndNewlines)
            if cleanBase.hasSuffix("/") {
                cleanBase = String(cleanBase.dropLast())
            }
            guard let url = URL(string: "\(cleanBase)\(normalizedPath)") else { continue }

            var request = URLRequest(url: url)
            request.httpMethod = method
            if method.uppercased() == "GET" || normalizedPath.hasPrefix("/api/subscriptions") {
                request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
                request.setValue("no-cache, no-store, max-age=0", forHTTPHeaderField: "Cache-Control")
                request.setValue("no-cache", forHTTPHeaderField: "Pragma")
            }
            let isLastCandidate = (index == candidates.count - 1)
            let isPrimaryOrActiveHost = (index == 0)
                || cleanBase == AuthManager.shared.serverBaseUrl
                || cleanBase == AuthManager.configuredPlistUrl
            if cleanBase.lowercased().hasPrefix("https://") {
                // Em servidores cloud (ex: Render Free Tier), aguarda eventual cold-start do container
                request.timeoutInterval = max(timeout, 45.0)
            } else {
                request.timeoutInterval = (isLastCandidate || isPrimaryOrActiveHost) ? timeout : min(timeout, 2.5)
            }
            if serializedBody != nil {
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = serializedBody
            }

            if authenticated {
                if let explicitToken = overrideBearerToken, !explicitToken.isEmpty {
                    request.setValue("Bearer \(explicitToken)", forHTTPHeaderField: "Authorization")
                } else if let token = AuthManager.shared.jwtToken, !token.isEmpty {
                    request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                }
            }

            let data: Data
            let response: URLResponse
            do {
                (data, response) = try await URLSession.shared.data(for: request)
            } catch {
                // Host inacessível (ex: IP antigo de outra rede Wi-Fi) -> tenta próximo candidato rapidamente
                continue
            }

            guard let http = response as? HTTPURLResponse else { continue }

            // Host respondeu na camada HTTP -> promove e memoriza como URL ativa principal
            AuthManager.shared.markWorkingServerUrl(cleanBase)

            if (200...299).contains(http.statusCode) {
                return data
            }

            throw mapHttpError(
                statusCode: http.statusCode,
                data: data,
                interceptAccountStatus: interceptAccountStatus
            )
        }

        throw FormaAPIError.unreachableServer
    }

    private func mapHttpError(statusCode: Int, data: Data, interceptAccountStatus: Bool) -> FormaAPIError {
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        let rawMessage = (json?["error"] as? String)
            ?? (json?["message"] as? String)
            ?? sanitizeRawErrorText(String(data: data, encoding: .utf8), statusCode: statusCode)
        let cleanMessage = Self.humanizeErrorMessage(rawMessage, statusCode: statusCode)

        if statusCode == 401 {
            if (json?["accountDeleted"] as? Bool) == true {
                if interceptAccountStatus {
                    AuthManager.shared.logout()
                }
                return .accountDeleted(cleanMessage)
            }
            return .unauthorized(cleanMessage)
        }

        if statusCode == 403 {
            if (json?["accountPaused"] as? Bool) == true {
                return .accountPaused(cleanMessage)
            }
            if (json?["aiConsentRequired"] as? Bool) == true {
                Task { @MainActor in
                    AIDataConsentManager.shared.setLocalCacheOnly(false)
                }
                return .aiBlocked(cleanMessage)
            }
            if (json?["aiBlockedForUser"] as? Bool) == true {
                return .aiBlocked(cleanMessage)
            }
            return .serverError(statusCode: 403, message: cleanMessage)
        }

        if statusCode == 409 && (json?["accountExists"] as? Bool) == true {
            let existingEmail = (json?["email"] as? String) ?? ""
            let existingName = json?["existingName"] as? String
            return .accountAlreadyExists(message: cleanMessage, email: existingEmail, name: existingName)
        }

        if statusCode == 429 {
            return .rateLimited(cleanMessage)
        }

        if statusCode == 503 && (json?["blocked"] as? Bool) == true {
            return .aiBlocked(cleanMessage)
        }

        return .serverError(statusCode: statusCode, message: cleanMessage)
    }

    private func sanitizeRawErrorText(_ raw: String?, statusCode: Int) -> String {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return Self.defaultMessage(for: statusCode)
        }
        if trimmed.hasPrefix("{") || trimmed.hasPrefix("<") {
            return Self.defaultMessage(for: statusCode)
        }
        return trimmed
    }

    public static func humanizeErrorMessage(_ raw: String, statusCode: Int = 500) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return defaultMessage(for: statusCode) }

        if trimmed.contains("\"error\"") && trimmed.contains("{") {
            if trimmed.contains("503") || trimmed.localizedCaseInsensitiveContains("high demand") || trimmed.localizedCaseInsensitiveContains("UNAVAILABLE") {
                return "A Inteligência Forma está com alta demanda no momento. Aguarde alguns segundos e tente novamente."
            }
            if trimmed.contains("429") || trimmed.localizedCaseInsensitiveContains("RESOURCE_EXHAUSTED") {
                return "Limite temporário de análises atingido. Tente novamente em instantes."
            }
            return defaultMessage(for: statusCode)
        }

        // Preserva transparência exigida pela LGPD Art. 9 e Art. 18 (não oculta o provedor de IA Google Gemini)
        return trimmed
    }

    private static func defaultMessage(for statusCode: Int) -> String {
        switch statusCode {
        case 400: return "Não foi possível processar os dados enviados."
        case 401: return "Sessão expirada ou credenciais inválidas."
        case 403: return "Operação não permitida para esta conta."
        case 404: return "Recurso não encontrado no servidor."
        case 429: return "Muitas solicitações em sequência. Aguarde alguns segundos."
        case 502, 503, 504:
            return "Serviço temporariamente ocupado. Tente novamente em alguns segundos."
        default:
            return "Ocorreu um erro temporário no servidor (\(statusCode))."
        }
    }
}
