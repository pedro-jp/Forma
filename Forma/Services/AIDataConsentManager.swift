//
//  AIDataConsentManager.swift
//  Forma
//
//  Consentimento explícito e revogável para envio de dados a provedor externo de IA
//  (Google Gemini API - App Store Review Guideline 5.1.2(i)).
//

import Foundation
import Combine
import SwiftUI

@MainActor
public final class AIDataConsentManager: ObservableObject {
    public static let shared = AIDataConsentManager()

    nonisolated private static let keyPrefix = "forma_ai_third_party_consent_v1_"
    nonisolated private static let activeUserIdDefaultsKey = "forma_user_id"

    @Published public private(set) var hasGrantedConsent: Bool

    private init() {
        self.hasGrantedConsent = Self.isConsentGranted
    }

    /// Chave isolada por usuário autenticado (ou escopo `guest` quando sem conta ativa).
    nonisolated public static var resolvedStorageKey: String {
        let rawUid = UserDefaults.standard.string(forKey: activeUserIdDefaultsKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let scope = rawUid.isEmpty ? "guest" : rawUid.lowercased()
        return "\(keyPrefix)\(scope)"
    }

    /// Leitura síncrona e thread-safe para serviços `Sendable` fora do `MainActor`.
    nonisolated public static var isConsentGranted: Bool {
        UserDefaults.standard.bool(forKey: resolvedStorageKey)
    }

    public func setConsent(_ granted: Bool) {
        UserDefaults.standard.set(granted, forKey: Self.resolvedStorageKey)
        self.hasGrantedConsent = granted
    }

    public func refreshForCurrentUser() {
        self.hasGrantedConsent = Self.isConsentGranted
    }

    public func clearConsentForCurrentUser() {
        UserDefaults.standard.removeObject(forKey: Self.resolvedStorageKey)
        self.hasGrantedConsent = false
    }
}

struct AIDataConsentSheet: View {
    @Environment(\.dismiss) private var dismiss

    var onAuthorized: () -> Void
    var onDeclined: (() -> Void)? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(FormaTheme.Colors.aiGradient)
                                .frame(width: 50, height: 50)
                            Image(systemName: "hand.raised.shield.fill")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundStyle(.white)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("IA do Google Gemini")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                            Text("Autorização para uso da IA externa da Google LLC")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.top, 4)

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "sparkles")
                                    .foregroundStyle(FormaTheme.Colors.aiAccent)
                                Text("Utilizamos a IA do Google Gemini")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }

                            Text("Para analisar fotos de pratos, alimentos fora da base offline, fichas personalizadas e análises de evolução, o Forma utiliza a inteligência artificial **Google Gemini (fornecida pela Google LLC)**, enviando os dados da solicitação sob demanda por conexão criptografada (HTTPS/TLS).")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Image(systemName: "doc.text.magnifyingglass")
                                    .foregroundStyle(FormaTheme.Colors.brand)
                                Text("Quais dados são compartilhados?")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }

                            consentBullet("Fotos ou textos de refeições enviados por você para estimativa de calorias e macronutrientes.")
                            consentBullet("Descrições de exercícios/treinos e seu peso corporal para cálculo de gasto energético.")
                            consentBullet("Nome de exibição, medidas corporais (peso, altura e objetivo) e histórico recente de refeições, água, treinos e pesagens ao gerar metas ou análises de progresso.")

                            Divider().padding(.vertical, 2)

                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "lock.shield.fill")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(FormaTheme.Colors.energyGreen)
                                    .padding(.top, 2)
                                Text("Seu e-mail, senha e credenciais Apple/Google nunca são enviados para a IA do Google Gemini.")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }
                        }
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "slider.horizontal.3")
                                    .foregroundStyle(FormaTheme.Colors.energyGreen)
                                Text("Finalidade, LGPD e Controle")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }

                            Text("Os dados são processados pelo Google Gemini exclusivamente para retornar suas estimativas nutricionais e de treino no Forma, sem venda a terceiros e sem uso publicitário. Você pode revogar esta permissão a qualquer momento em Perfil > Privacidade, Saúde & Termos de Uso.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

                            if let privacyURL = URL(string: "https://forma-94vp.onrender.com/privacy.html") {
                                Link(destination: privacyURL) {
                                    HStack(spacing: 4) {
                                        Image(systemName: "arrow.up.right.square")
                                        Text("Ler Política de Privacidade Completa (LGPD)")
                                    }
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(FormaTheme.Colors.brand)
                                }
                                .padding(.top, 2)
                            }
                        }
                    }

                    VStack(spacing: 10) {
                        Button {
                            AIDataConsentManager.shared.setConsent(true)
                            dismiss()
                            onAuthorized()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark.shield.fill")
                                    .font(.system(size: 16, weight: .bold))
                                Text("Autorizar e Continuar")
                                    .font(.headline)
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 15)
                            .background(FormaTheme.Colors.aiGradient)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)

                        Button {
                            dismiss()
                            onDeclined?()
                        } label: {
                            Text("Agora Não (usar apenas base local)")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 16)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Privacidade da IA")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") {
                        dismiss()
                        onDeclined?()
                    }
                }
            }
        }
    }

    private func consentBullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Circle()
                .fill(FormaTheme.Colors.brand)
                .frame(width: 6, height: 6)
                .padding(.top, 6)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
