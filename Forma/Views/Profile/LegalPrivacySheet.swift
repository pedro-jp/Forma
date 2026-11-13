//
//  LegalPrivacySheet.swift
//  Forma
//
//  Política de Privacidade, aviso de saúde/nutrição e fluxos de redefinição e troca de senha.
//

import SwiftUI

struct LegalPrivacySheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiConsent = AIDataConsentManager.shared
    @ObservedObject private var authManager = AuthManager.shared

    @State private var isExportingData: Bool = false
    @State private var exportErrorMessage: String? = nil
    @State private var exportedFileURL: URL? = nil
    @State private var showExportShareSheet: Bool = false

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
                            }
                            Text("Exporte a qualquer momento uma cópia completa dos seus dados cadastrais, perfil, refeições, treinos, hidratação, histórico de consentimentos e registros de auditoria de IA em formato JSON.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

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
        }
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
                let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("forma-dados-lgpd.json")
                try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
                exportedFileURL = fileURL
                isExportingData = false
                showExportShareSheet = true
            } catch {
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
