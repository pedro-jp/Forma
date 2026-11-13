//
//  LoginView.swift
//  Forma
//
//  Created by João Pedro on 11/02/26.
//

import SwiftUI
import AuthenticationServices
import CryptoKit

struct LoginView: View {
    @ObservedObject private var authManager = AuthManager.shared

    private enum LoginFocusField {
        case name
        case email
        case password
    }

    @State private var isRegisterMode: Bool = false
    @State private var nameText: String = ""
    @State private var emailText: String = ""
    @State private var passwordText: String = ""
    @State private var isPasswordVisible: Bool = false
    @FocusState private var focusedField: LoginFocusField?
    
    // Nonce criptográfico para Apple Sign-In
    @State private var currentAppleNonce: String?
    
    @State private var localErrorMessage: String? = nil
    @State private var showForgotPasswordSheet: Bool = false
    @State private var showLegalPrivacySheet: Bool = false
    @State private var isGoogleAuthenticating: Bool = false
    @State private var showExistingAccountAlert: Bool = false
    @State private var pendingSocialSignIn: PendingSocialSignIn? = nil
    @State private var acceptedAge18Plus: Bool = false
    @State private var acceptedTermsAndPrivacy: Bool = false
    @State private var acceptedSensitiveHealthData: Bool = false
    @State private var acceptedOptionalAISharing: Bool = false

    private struct PendingSocialSignIn {
        let provider: String
        let providerId: String
        let email: String?
        let name: String
        let idToken: String?
        let nonce: String?
        let authorizationCode: String?
        let oauthRefreshToken: String?
        let existingEmail: String
    }
    
    var onLoginSuccess: () -> Void
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Header com Logo e Apresentação
                    VStack(spacing: 12) {
                        if let _ = UIImage(named: "app_logo") {
                            Image("app_logo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 72, height: 72)
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                                .shadow(color: FormaTheme.Colors.brand.opacity(0.25), radius: 12, x: 0, y: 4)
                        } else {
                            Circle()
                                .fill(FormaTheme.Colors.brandGradient)
                                .frame(width: 72, height: 72)
                                .overlay(
                                    Image(systemName: "flame.fill")
                                        .font(.system(size: 32, weight: .bold))
                                        .foregroundColor(.white)
                                        .shadow(radius: 2)
                                )
                        }
                        
                        Text("Forma")
                            .font(.system(size: 28, weight: .heavy, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        
                        Text(isRegisterMode ? "Crie sua conta para gerenciar seus dados" : "Entre na sua conta para acessar seus dados")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    .padding(.top, 24)
                    
                    // Seletor de Modo: Entrar vs Criar Conta
                    Picker("Modo", selection: $isRegisterMode) {
                        Text("Entrar").tag(false)
                        Text("Criar Conta").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 20)
                    
                    // Formulário de E-mail / Senha
                    VStack(spacing: 14) {
                        if isRegisterMode {
                            HStack(spacing: 12) {
                                Image(systemName: "person.fill")
                                    .foregroundStyle(focusedField == .name ? FormaTheme.Colors.brand : .secondary)
                                    .frame(width: 20)
                                TextField("Seu Nome Completo", text: $nameText)
                                    .textContentType(.name)
                                    .autocorrectionDisabled()
                                    .focused($focusedField, equals: .name)
                            }
                            .padding(14)
                            .background(FormaTheme.Colors.cardSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(
                                        focusedField == .name ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                        lineWidth: focusedField == .name ? 1.4 : 1
                                    )
                            )
                        }
                        
                        HStack(spacing: 12) {
                            Image(systemName: "envelope.fill")
                                .foregroundStyle(focusedField == .email ? FormaTheme.Colors.brand : .secondary)
                                .frame(width: 20)
                            TextField("E-mail", text: $emailText)
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
                                    lineWidth: focusedField == .email ? 1.4 : 1
                                )
                        )
                        
                        HStack(spacing: 12) {
                            Image(systemName: "lock.fill")
                                .foregroundStyle(focusedField == .password ? FormaTheme.Colors.brand : .secondary)
                                .frame(width: 20)
                            Group {
                                if isPasswordVisible {
                                    TextField(isRegisterMode ? "Senha (mín. 8 caracteres)" : "Senha", text: $passwordText)
                                        .textInputAutocapitalization(.never)
                                        .autocorrectionDisabled()
                                } else {
                                    SecureField(isRegisterMode ? "Senha (mín. 8 caracteres)" : "Senha", text: $passwordText)
                                }
                            }
                            .textContentType(isRegisterMode ? .newPassword : .password)
                            .focused($focusedField, equals: .password)

                            Button {
                                isPasswordVisible.toggle()
                            } label: {
                                Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
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
                                    focusedField == .password ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                    lineWidth: focusedField == .password ? 1.4 : 1
                                )
                        )

                        if !isRegisterMode {
                            HStack {
                                Spacer()
                                Button {
                                    showForgotPasswordSheet = true
                                } label: {
                                    Text("Esqueci minha senha")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(Color("AccentColor"))
                                }
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 8) {
                                lgpdConsentCheckbox(
                                    isChecked: $acceptedAge18Plus,
                                    text: "Confirmo ter 18 anos ou mais (LGPD Art. 14)."
                                )
                                lgpdConsentCheckbox(
                                    isChecked: $acceptedTermsAndPrivacy,
                                    text: "Li e aceito os Termos de Uso e a Política de Privacidade."
                                )
                                lgpdConsentCheckbox(
                                    isChecked: $acceptedSensitiveHealthData,
                                    text: "Autorizo o tratamento dos meus dados de saúde e biometria (LGPD Art. 11) exclusivamente para o app."
                                )
                                lgpdConsentCheckbox(
                                    isChecked: $acceptedOptionalAISharing,
                                    text: "(Opcional) Autorizo enviar descrições/fotos (sem nome ou EXIF) ao Google Gemini (Google LLC) para IA."
                                )
                                Button {
                                    showLegalPrivacySheet = true
                                } label: {
                                    Text("Ler Política, Termos (EULA) e Aviso de Saúde")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(Color("AccentColor"))
                                }
                                .buttonStyle(.plain)
                                .padding(.top, 2)
                            }
                            .padding(.horizontal, 4)
                            .padding(.top, 2)
                        }
                    }
                    .padding(.horizontal, 20)
                    
                    // Alerta de Erro
                    if let error = localErrorMessage ?? authManager.errorMessage {
                        FormaBanner(message: error, style: .error)
                            .padding(.horizontal, 20)
                    }
                    
                    // Botão Principal (Entrar / Cadastrar)
                    Button {
                        handleEmailAuth()
                    } label: {
                        HStack(spacing: 8) {
                            if authManager.isAuthenticating {
                                ProgressView()
                                    .tint(.white)
                            }
                            Text(isRegisterMode ? "Criar Minha Conta" : "Entrar com E-mail")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(FormaTheme.Colors.brandGradient)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .shadow(color: FormaTheme.Colors.brand.opacity(0.3), radius: 8, x: 0, y: 4)
                    }
                    .disabled(authManager.isAuthenticating)
                    .padding(.horizontal, 20)
                    
                    // Divisor "ou continue com"
                    HStack {
                        Rectangle()
                            .fill(Color.secondary.opacity(0.2))
                            .frame(height: 1)
                        Text("ou continue com")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Rectangle()
                            .fill(Color.secondary.opacity(0.2))
                            .frame(height: 1)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 4)
                    
                    // Botões Oficiais de Autenticação Social
                    VStack(spacing: 12) {
                        // Sign in with Apple Oficial com Nonce SHA-256
                        SignInWithAppleButton(
                            .signIn,
                            onRequest: { request in
                                let rawNonce = randomNonceString()
                                self.currentAppleNonce = rawNonce
                                request.requestedScopes = [.fullName, .email]
                                request.nonce = sha256(rawNonce)
                            },
                            onCompletion: { result in
                                handleAppleSignInResult(result)
                            }
                        )
                        .signInWithAppleButtonStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        
                        // Google Sign-In Oficial via ASWebAuthenticationSession
                        Button {
                            handleGoogleSignIn()
                        } label: {
                            HStack(spacing: 10) {
                                if isGoogleAuthenticating {
                                    ProgressView()
                                        .tint(.blue)
                                } else {
                                    Image(systemName: "g.circle.fill")
                                        .font(.system(size: 20, weight: .bold))
                                        .foregroundStyle(.blue)
                                }
                                
                                Text(isGoogleAuthenticating ? "Conectando com Google..." : "Continuar com Google")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(Color("FontNormal"))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color("CardBackground"))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Color.primary.opacity(0.12), lineWidth: 1)
                            )
                        }
                        .disabled(isGoogleAuthenticating || authManager.isAuthenticating)
                    }
                    .padding(.horizontal, 20)

                    Button {
                        showLegalPrivacySheet = true
                    } label: {
                        VStack(spacing: 4) {
                            Text("Recursos de IA na nuvem utilizam o Google Gemini (Google LLC) mediante sua autorização.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text("Ao continuar, você concorda com a Política de Privacidade (LGPD), Termos de Uso & Aviso de Saúde")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color("AccentColor"))
                        }
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    }
                    .padding(.bottom, 24)
                }
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showForgotPasswordSheet) {
                PasswordRecoverySheet(initialEmail: emailText) {
                    onLoginSuccess()
                }
            }
            .sheet(isPresented: $showLegalPrivacySheet) {
                LegalPrivacySheet()
            }
            .alert("Conta já existente", isPresented: $showExistingAccountAlert) {
                Button("Cancelar", role: .cancel) {
                    pendingSocialSignIn = nil
                }
                Button("Sim, Entrar") {
                    confirmPendingSocialSignIn()
                }
            } message: {
                Text("Já existe uma conta cadastrada com o e-mail \(pendingSocialSignIn?.existingEmail ?? "informado"). Deseja entrar nessa conta?")
            }
        }
    }
    
    // MARK: - Tratamento de Ações
    @ViewBuilder
    private func lgpdConsentCheckbox(isChecked: Binding<Bool>, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Button {
                isChecked.wrappedValue.toggle()
            } label: {
                Image(systemName: isChecked.wrappedValue ? "checkmark.square.fill" : "square")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(isChecked.wrappedValue ? Color("AccentColor") : .secondary)
            }
            .buttonStyle(.plain)
            .padding(.top, 1)

            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .onTapGesture {
                    isChecked.wrappedValue.toggle()
                }
        }
    }

    private func handleEmailAuth() {
        localErrorMessage = nil
        if isRegisterMode {
            guard acceptedAge18Plus else {
                localErrorMessage = "Confirme que você possui 18 anos ou mais para criar sua conta (LGPD Art. 14)."
                return
            }
            guard acceptedTermsAndPrivacy else {
                localErrorMessage = "Aceite os Termos de Uso e a Política de Privacidade para continuar."
                return
            }
            guard acceptedSensitiveHealthData else {
                localErrorMessage = "Autorize o tratamento de dados de saúde e biometria (LGPD Art. 11) para criar sua conta."
                return
            }
        }
        Task {
            do {
                if isRegisterMode {
                    try await authManager.register(
                        name: nameText,
                        email: emailText,
                        password: passwordText,
                        termsAccepted: acceptedTermsAndPrivacy,
                        sensitiveHealthConsentGranted: acceptedSensitiveHealthData,
                        aiConsentGranted: acceptedOptionalAISharing,
                        ageConfirmed18Plus: acceptedAge18Plus
                    )
                    if acceptedOptionalAISharing {
                        AIDataConsentManager.shared.setLocalCacheOnly(true)
                    }
                } else {
                    try await authManager.login(email: emailText, password: passwordText)
                }
                onLoginSuccess()
            } catch {
                localErrorMessage = error.localizedDescription
            }
        }
    }
    
    // MARK: - Sign in with Apple Oficial
    private func handleAppleSignInResult(_ result: Result<ASAuthorization, Error>) {
        localErrorMessage = nil
        switch result {
        case .success(let authorization):
            guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                return
            }
            let userId = appleIDCredential.user
            let rawNonce = currentAppleNonce
            
            // 1. Tratamento de Nome do Usuário
            var fullName: String? = nil
            if let appleFullName = appleIDCredential.fullName {
                let parts = [appleFullName.givenName, appleFullName.familyName].compactMap { $0 }
                let joined = parts.joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
                if !joined.isEmpty {
                    fullName = joined
                    KeychainHelper.shared.save(key: "apple_name_\(userId)", string: joined)
                }
            }
            if fullName == nil {
                fullName = KeychainHelper.shared.loadString(key: "apple_name_\(userId)")
            }
            
            // 2. Tratamento de E-mail
            var email: String? = appleIDCredential.email
            if let email = email, !email.isEmpty {
                KeychainHelper.shared.save(key: "apple_email_\(userId)", string: email)
            } else {
                email = KeychainHelper.shared.loadString(key: "apple_email_\(userId)")
            }
            
            // 3. Identity Token e Authorization Code UTF-8 assinados pela Apple
            let idToken = appleIDCredential.identityToken.flatMap { String(data: $0, encoding: .utf8) }
            let authorizationCode = appleIDCredential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            
            let resolvedName = fullName ?? "Usuário Apple"
            Task { @MainActor in
                do {
                    try await authManager.loginWithSocial(
                        provider: "APPLE",
                        providerId: userId,
                        email: email,
                        name: resolvedName,
                        idToken: idToken,
                        nonce: rawNonce,
                        isRegisterIntent: isRegisterMode,
                        confirmExisting: false,
                        authorizationCode: authorizationCode
                    )
                    onLoginSuccess()
                } catch FormaAPIError.accountAlreadyExists(_, let existingEmail, _) {
                    pendingSocialSignIn = PendingSocialSignIn(
                        provider: "APPLE",
                        providerId: userId,
                        email: email,
                        name: resolvedName,
                        idToken: idToken,
                        nonce: rawNonce,
                        authorizationCode: authorizationCode,
                        oauthRefreshToken: nil,
                        existingEmail: existingEmail.isEmpty ? (email ?? "") : existingEmail
                    )
                    showExistingAccountAlert = true
                } catch {
                    localErrorMessage = error.localizedDescription
                }
            }
            
        case .failure(let error):
            let nsError = error as NSError
            if nsError.domain == ASAuthorizationError.errorDomain, nsError.code == ASAuthorizationError.canceled.rawValue {
                return
            }
            localErrorMessage = error.localizedDescription
        }
    }
    
    // MARK: - Google Sign-In Oficial
    private func handleGoogleSignIn() {
        localErrorMessage = nil
        isGoogleAuthenticating = true
        let rawNonce = randomNonceString()
        
        Task { @MainActor in
            defer { isGoogleAuthenticating = false }
            do {
                let userResult = try await GoogleAuthService.shared.startSignIn(rawNonce: rawNonce)
                let resolvedProviderId = userResult.sub ?? "google_user_\(abs(rawNonce.hashValue))"
                let resolvedName = userResult.name ?? "Usuário Google"
                let googleRevocableToken = (userResult.refreshToken?.isEmpty == false)
                    ? userResult.refreshToken
                    : userResult.accessToken
                do {
                    try await authManager.loginWithSocial(
                        provider: "GOOGLE",
                        providerId: resolvedProviderId,
                        email: userResult.email,
                        name: resolvedName,
                        idToken: userResult.idToken,
                        nonce: rawNonce,
                        isRegisterIntent: isRegisterMode,
                        confirmExisting: false,
                        oauthRefreshToken: googleRevocableToken
                    )
                    onLoginSuccess()
                } catch FormaAPIError.accountAlreadyExists(_, let existingEmail, _) {
                    pendingSocialSignIn = PendingSocialSignIn(
                        provider: "GOOGLE",
                        providerId: resolvedProviderId,
                        email: userResult.email,
                        name: resolvedName,
                        idToken: userResult.idToken,
                        nonce: rawNonce,
                        authorizationCode: nil,
                        oauthRefreshToken: googleRevocableToken,
                        existingEmail: existingEmail.isEmpty ? (userResult.email ?? "") : existingEmail
                    )
                    showExistingAccountAlert = true
                }
            } catch is CancellationError {
                // Usuário fechou o sheet de login
                return
            } catch {
                localErrorMessage = error.localizedDescription
            }
        }
    }

    private func confirmPendingSocialSignIn() {
        guard let pending = pendingSocialSignIn else { return }
        pendingSocialSignIn = nil
        localErrorMessage = nil
        isGoogleAuthenticating = true

        Task { @MainActor in
            defer { isGoogleAuthenticating = false }
            do {
                try await authManager.loginWithSocial(
                    provider: pending.provider,
                    providerId: pending.providerId,
                    email: pending.email,
                    name: pending.name,
                    idToken: pending.idToken,
                    nonce: pending.nonce,
                    isRegisterIntent: false,
                    confirmExisting: true,
                    authorizationCode: pending.authorizationCode,
                    oauthRefreshToken: pending.oauthRefreshToken
                )
                onLoginSuccess()
            } catch {
                localErrorMessage = error.localizedDescription
            }
        }
    }
    
    // MARK: - Geradores Criptográficos de Nonce
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        var randomBytes = [UInt8](repeating: 0, count: length)
        let errorCode = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        if errorCode != errSecSuccess {
            return UUID().uuidString
        }
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        let nonce = randomBytes.map { byte in
            charset[Int(byte) % charset.count]
        }
        return String(nonce)
    }
    
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        return hashedData.compactMap { String(format: "%02x", $0) }.joined()
    }
}
