//
//  InitialOnboardingModalView.swift
//  Forma
//
//  Modal obrigatório e inescapável de configuração inicial para novas contas.
//  Calcula metas com Inteligência Forma (IA) e grava primeiro no Supabase PostgreSQL
//  antes de salvar no cache local do iPhone e liberar o uso do app.
//

import SwiftUI
import SwiftData

struct InitialOnboardingModalView: View {
    @ObservedObject private var authManager = AuthManager.shared
    @ObservedObject private var aiConsent = AIDataConsentManager.shared

    enum ActivityLevelOption: String, CaseIterable, Identifiable {
        case light = "Leve (1-2x/semana)"
        case moderate = "Moderado (3-4x/semana)"
        case intense = "Intenso (5-6x/semana)"

        var id: String { rawValue }

        var shortTitle: String {
            switch self {
            case .light: return "Leve"
            case .moderate: return "Moderado"
            case .intense: return "Intenso"
            }
        }

        var subtitle: String {
            switch self {
            case .light: return "1 a 2 treinos/sem"
            case .moderate: return "3 a 4 treinos/sem"
            case .intense: return "5+ treinos/sem"
            }
        }
    }

    // Identificação & Objetivo
    @State private var nameText: String = ""
    @State private var selectedSex: BiologicalSex = BiologicalSex.currentStored
    @State private var selectedGoal: FitnessGoal = .gain
    @State private var selectedActivity: ActivityLevelOption = .moderate

    // Biometria Obrigatória (inicia vazia para exigir preenchimento real)
    @State private var currentWeightText: String = ""
    @State private var heightText: String = ""
    @State private var targetWeightText: String = ""
    @State private var hasManuallyEditedTargetWeight: Bool = false

    // Metas Diárias (preenchidas automaticamente pela IA assim que peso e altura forem informados)
    @State private var dailyCaloriesText: String = ""
    @State private var proteinText: String = ""
    @State private var carbsText: String = ""
    @State private var fatText: String = ""
    @State private var waterMlText: String = ""
    @State private var calorieBurnText: String = ""
    @State private var stepsText: String = ""

    // Estado da IA e Persistência
    @State private var isLoadingAISuggestion: Bool = false
    @State private var aiExplanation: String? = nil
    @State private var aiDebounceTask: Task<Void, Never>? = nil
    @State private var lastAISignature: String = ""
    @State private var isSavingToDatabase: Bool = false
    @State private var saveErrorMessage: String? = nil
    @State private var showAIConsentSheet: Bool = false

    // MARK: - Parsers & Validações Estritas

    private var validName: String? {
        let trimmed = nameText.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.count >= 2 ? trimmed : nil
    }

    private var validCurrentWeight: Double? {
        ProfileInputValidator.parseFlexibleWeight(currentWeightText)
    }

    private var validHeight: Double? {
        ProfileInputValidator.parseFlexibleHeight(heightText)
    }

    private var validTargetWeight: Double? {
        ProfileInputValidator.parseFlexibleWeight(targetWeightText)
    }

    private var validCalories: Double? {
        parseBoundedDouble(dailyCaloriesText, min: 1200, max: 6000)
    }

    private var validProtein: Double? {
        parseBoundedDouble(proteinText, min: 30, max: 450)
    }

    private var validCarbs: Double? {
        parseBoundedDouble(carbsText, min: 20, max: 900)
    }

    private var validFat: Double? {
        parseBoundedDouble(fatText, min: 15, max: 250)
    }

    private var validWaterMl: Int? {
        guard let val = parseBoundedDouble(waterMlText, min: 500, max: 8000) else { return nil }
        return Int(val.rounded())
    }

    private var validCalorieBurn: Double? {
        parseBoundedDouble(calorieBurnText, min: 100, max: 3000)
    }

    private var validSteps: Double? {
        parseBoundedDouble(stepsText, min: 1000, max: 40000)
    }

    private var missingFields: [String] {
        var missing: [String] = []
        if validName == nil { missing.append("Nome") }
        if validCurrentWeight == nil { missing.append("Peso atual") }
        if validHeight == nil { missing.append("Altura") }
        if validTargetWeight == nil { missing.append("Meta de peso") }
        if validCalories == nil { missing.append("Calorias") }
        if validProtein == nil || validCarbs == nil || validFat == nil { missing.append("Macros") }
        if validWaterMl == nil { missing.append("Água") }
        if validCalorieBurn == nil || validSteps == nil { missing.append("Gasto/Passos") }
        return missing
    }

    private var isFormCompletelyValid: Bool {
        missingFields.isEmpty
    }

    private var calculatedBMI: (value: Double, label: String)? {
        guard let w = validCurrentWeight, let h = validHeight, h > 0 else { return nil }
        let bmi = w / (h * h)
        return (bmi, EvolutionCalculator.classifyIMC(bmi))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    heroHeaderCard

                    identityAndGoalSection

                    biometricsSection

                    aiGoalsSection

                    validationAndSubmitSection
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.top, 12)
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .withDoneKeyboardToolbar()
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Configuração Inicial")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        aiDebounceTask?.cancel()
                        authManager.logout()
                    } label: {
                        Text("Trocar conta")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .disabled(isSavingToDatabase)
                }
            }
            .onAppear {
                prefillInitialNameIfAvailable()
            }
            .onChange(of: authManager.accountTier) { _, _ in
                aiDebounceTask?.cancel()
                lastAISignature = ""
                scheduleAutoAISuggestion(immediate: true)
            }
            .onChange(of: currentWeightText) { _, _ in scheduleAutoAISuggestion() }
            .onChange(of: heightText) { _, _ in scheduleAutoAISuggestion() }
            .onChange(of: selectedGoal) { _, _ in
                hasManuallyEditedTargetWeight = false
                scheduleAutoAISuggestion(immediate: true)
            }
            .onChange(of: selectedSex) { _, newSex in
                BiologicalSex.persistToDefaults(newSex)
                scheduleAutoAISuggestion(immediate: true)
            }
            .onChange(of: selectedActivity) { _, _ in
                scheduleAutoAISuggestion(immediate: true)
            }
            .sheet(isPresented: $showAIConsentSheet) {
                AIDataConsentSheet {
                    triggerAISuggestionNow(forceTargetWeightRefresh: !hasManuallyEditedTargetWeight)
                }
            }
        }
        .interactiveDismissDisabled(true)
    }

    // MARK: - Subviews

    private var heroHeaderCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(authManager.isPro ? AnyShapeStyle(FormaTheme.Colors.aiGradient) : AnyShapeStyle(FormaTheme.Colors.brandSoft))
                        .frame(width: 46, height: 46)
                    Image(systemName: authManager.isPro ? "sparkles" : "heart.text.square.fill")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(authManager.isPro ? .white : FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Bem-vindo ao Forma!")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Text("Preencha todos os dados abaixo para liberar sua conta.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Text(
                authManager.isPro
                    ? "Assim que você informar seu peso e altura, nossa IA calculará automaticamente suas metas ideais de calorias, proteínas, carboidratos, gorduras e hidratação."
                    : "Assim que você informar seu peso e altura, calcularemos automaticamente suas metas ideais de calorias, macronutrientes e hidratação pelas equações fisiológicas Mifflin-St Jeor e ISSN."
            )
            .font(.footnote)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FormaTheme.Colors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.card, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: FormaTheme.Radius.card, style: .continuous)
                .stroke((authManager.isPro ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand).opacity(0.22), lineWidth: 1)
        )
    }

    private var identityAndGoalSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("1. Seu Perfil e Objetivo", icon: "flag.checkered")

            VStack(alignment: .leading, spacing: 6) {
                Text("Como quer ser chamado?")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)

                TextField("Digite seu nome", text: $nameText)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .padding(13)
                    .background(Color(.secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Objetivo Principal")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)

                ForEach(FitnessGoal.allCases) { goal in
                    let isSelected = selectedGoal == goal
                    Button {
                        withAnimation(.spring(duration: 0.25)) {
                            selectedGoal = goal
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: goal.icon)
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(isSelected ? FormaTheme.Colors.brand : .secondary)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(goal.rawValue)
                                    .font(.system(size: 15, weight: .bold, design: .rounded))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Text(goal.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(isSelected ? FormaTheme.Colors.brand : .secondary.opacity(0.4))
                        }
                        .padding(12)
                        .background(isSelected ? FormaTheme.Colors.brandSoft.opacity(0.6) : Color(.secondarySystemGroupedBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(isSelected ? FormaTheme.Colors.brand : Color.clear, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Rotina Semanal de Treinos")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 8) {
                    ForEach(ActivityLevelOption.allCases) { level in
                        let isSelected = selectedActivity == level
                        Button {
                            withAnimation(.spring(duration: 0.25)) {
                                selectedActivity = level
                            }
                        } label: {
                            VStack(spacing: 3) {
                                Text(level.shortTitle)
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundStyle(isSelected ? .white : FormaTheme.Colors.primaryText)
                                Text(level.subtitle)
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(isSelected ? .white.opacity(0.9) : .secondary)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .padding(.horizontal, 4)
                            .background(isSelected ? FormaTheme.Colors.brand : Color(.secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(18)
        .background(FormaTheme.Colors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.card, style: .continuous))
    }

    private var biometricsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("2. Medidas Corporais", icon: "scalemass.fill")

            VStack(alignment: .leading, spacing: 8) {
                Text("Sexo Biológico (Exemplos de Treino e TMB)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    ForEach(BiologicalSex.allCases) { sex in
                        let isSelected = selectedSex == sex
                        Button {
                            withAnimation(.spring(duration: 0.25)) {
                                selectedSex = sex
                            }
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: sex.icon)
                                    .font(.system(size: 15, weight: .semibold))
                                Text(sex.rawValue)
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                            }
                            .foregroundStyle(isSelected ? .white : FormaTheme.Colors.primaryText)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(isSelected ? FormaTheme.Colors.brand : Color(.secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack(spacing: 12) {
                onboardingNumberField(
                    title: "Peso Atual (kg)",
                    placeholder: "Ex: 72,5",
                    text: $currentWeightText,
                    isValid: validCurrentWeight != nil
                )

                onboardingNumberField(
                    title: "Altura (m ou cm)",
                    placeholder: "Ex: 1,75 ou 175",
                    text: $heightText,
                    isValid: validHeight != nil
                )
            }

            onboardingNumberField(
                title: "Meta de Peso (kg)",
                placeholder: authManager.isPro ? "Sugerida pela IA ou digite (ex: 78,0)" : "Sugerida automaticamente ou digite (ex: 78,0)",
                text: Binding(
                    get: { targetWeightText },
                    set: { newVal in
                        targetWeightText = newVal
                        hasManuallyEditedTargetWeight = !newVal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    }
                ),
                isValid: validTargetWeight != nil
            )

            if let bmi = calculatedBMI {
                HStack(spacing: 8) {
                    Image(systemName: "heart.text.square.fill")
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                    Text(String(format: "IMC atual: %.1f • %@", bmi.value, bmi.label))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()
                }
                .padding(10)
                .background(FormaTheme.Colors.energyGreen.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .padding(18)
        .background(FormaTheme.Colors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.card, style: .continuous))
    }

    private var aiGoalsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                sectionTitle("3. Metas Diárias", icon: authManager.isPro ? "sparkles" : "bolt.heart.fill")
                Spacer()
                Button {
                    if !authManager.isPro || aiConsent.hasGrantedConsent {
                        triggerAISuggestionNow(forceTargetWeightRefresh: !hasManuallyEditedTargetWeight)
                    } else {
                        showAIConsentSheet = true
                    }
                } label: {
                    HStack(spacing: 5) {
                        if isLoadingAISuggestion {
                            ProgressView()
                                .controlSize(.mini)
                                .tint(.white)
                        } else {
                            Image(systemName: authManager.isPro ? "sparkles" : "function")
                                .font(.system(size: 11, weight: .bold))
                        }
                        Text(isLoadingAISuggestion ? "Calculando..." : (authManager.isPro ? "IA Google Gemini" : "Recalcular TMB"))
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        (validCurrentWeight != nil && validHeight != nil)
                        ? (authManager.isPro ? AnyShapeStyle(FormaTheme.Colors.aiGradient) : AnyShapeStyle(FormaTheme.Colors.brand))
                        : AnyShapeStyle(Color.gray.opacity(0.4))
                    )
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(validCurrentWeight == nil || validHeight == nil || isLoadingAISuggestion)
            }

            HStack(spacing: 6) {
                Image(systemName: (authManager.isPro && aiConsent.hasGrantedConsent) ? "sparkles" : "function")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle((authManager.isPro && aiConsent.hasGrantedConsent) ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand)
                Text(
                    !authManager.isPro
                        ? "Cálculo fisiológico automático (Mifflin-St Jeor & ISSN)"
                        : (aiConsent.hasGrantedConsent
                            ? "Sugestões ativas via IA do Google Gemini (Google LLC)"
                            : "Cálculo local (Mifflin-St Jeor) • Toque em \"IA Google Gemini\" para ativar a IA na nuvem")
                )
                .font(.caption2)
                .foregroundStyle(.secondary)
            }

            if isLoadingAISuggestion {
                HStack(spacing: 10) {
                    ProgressView()
                        .tint(authManager.isPro ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand)
                    Text((authManager.isPro && aiConsent.hasGrantedConsent)
                         ? "A IA do Google Gemini está calculando suas metas personalizadas..."
                         : "Calculando suas metas fisiológicas iniciais...")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background((authManager.isPro ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand).opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else if let explanation = aiExplanation, !explanation.isEmpty {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: authManager.isPro ? "sparkles" : "bolt.heart.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(authManager.isPro ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand)
                        .padding(.top, 2)
                    Text(explanation)
                        .font(.caption)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background((authManager.isPro ? FormaTheme.Colors.aiAccent : FormaTheme.Colors.brand).opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else if validCurrentWeight == nil || validHeight == nil {
                Text(
                    authManager.isPro
                        ? "Informe seu peso atual e altura acima para gerar automaticamente a sugestão de metas com IA."
                        : "Informe seu peso atual e altura acima para calcular automaticamente suas metas diárias."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            HStack(spacing: 12) {
                onboardingNumberField(
                    title: "Calorias/dia (kcal)",
                    placeholder: "Ex: 2500",
                    text: $dailyCaloriesText,
                    isValid: validCalories != nil,
                    keyboard: .numberPad
                )

                onboardingNumberField(
                    title: "Água/dia (ml)",
                    placeholder: "Ex: 2800",
                    text: $waterMlText,
                    isValid: validWaterMl != nil,
                    keyboard: .numberPad
                )
            }

            HStack(spacing: 10) {
                onboardingNumberField(
                    title: "Proteína (g)",
                    placeholder: "150",
                    text: $proteinText,
                    isValid: validProtein != nil,
                    keyboard: .numberPad
                )

                onboardingNumberField(
                    title: "Carbo (g)",
                    placeholder: "280",
                    text: $carbsText,
                    isValid: validCarbs != nil,
                    keyboard: .numberPad
                )

                onboardingNumberField(
                    title: "Gordura (g)",
                    placeholder: "65",
                    text: $fatText,
                    isValid: validFat != nil,
                    keyboard: .numberPad
                )
            }

            HStack(spacing: 12) {
                onboardingNumberField(
                    title: "Meta de Queima (kcal)",
                    placeholder: "Ex: 500",
                    text: $calorieBurnText,
                    isValid: validCalorieBurn != nil,
                    keyboard: .numberPad
                )

                onboardingNumberField(
                    title: "Meta de Passos",
                    placeholder: "Ex: 8000",
                    text: $stepsText,
                    isValid: validSteps != nil,
                    keyboard: .numberPad
                )
            }
        }
        .padding(18)
        .background(FormaTheme.Colors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.card, style: .continuous))
    }

    private var validationAndSubmitSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !missingFields.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "lock.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.warning)
                        .padding(.top, 2)
                    Text("Preencha todos os campos para liberar o app: \(missingFields.joined(separator: ", ")).")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(FormaTheme.Colors.warning.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            if let errorMsg = saveErrorMessage {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(FormaTheme.Colors.danger)
                    Text(errorMsg)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(FormaTheme.Colors.danger)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(FormaTheme.Colors.danger.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            Button {
                submitInitialConfiguration()
            } label: {
                HStack(spacing: 10) {
                    if isSavingToDatabase {
                        ProgressView()
                            .tint(.white)
                        Text("Salvando no banco de dados...")
                            .font(.headline)
                    } else {
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 16, weight: .bold))
                        Text("Concluir e Entrar no Forma")
                            .font(.headline)
                    }
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    isFormCompletelyValid && !isSavingToDatabase
                    ? AnyShapeStyle(FormaTheme.Colors.brand)
                    : AnyShapeStyle(Color.gray.opacity(0.38))
                )
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(!isFormCompletelyValid || isSavingToDatabase)

            VStack(alignment: .leading, spacing: 5) {
                Text("Estimativas de IMC, TMB e macronutrientes têm finalidade informativa e educacional e não substituem avaliação médica ou nutricional.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    if let mifflinURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/2305711/") {
                        Link("Fonte TMB: Mifflin-St Jeor (PubMed)", destination: mifflinURL)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    if let whoURL = URL(string: "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight") {
                        Link("Fonte IMC: OMS (WHO)", destination: whoURL)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                }
            }
            .padding(.top, 2)
        }
    }

    private func sectionTitle(_ text: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(FormaTheme.Colors.brand)
            Text(text)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(FormaTheme.Colors.primaryText)
        }
    }

    private func onboardingNumberField(
        title: String,
        placeholder: String,
        text: Binding<String>,
        isValid: Bool,
        keyboard: UIKeyboardType = .decimalPad
    ) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 4) {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 2)
                if !text.wrappedValue.isEmpty {
                    Image(systemName: isValid ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(isValid ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.warning)
                }
            }

            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .padding(12)
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(
                            text.wrappedValue.isEmpty
                            ? Color.clear
                            : (isValid ? FormaTheme.Colors.energyGreen.opacity(0.45) : FormaTheme.Colors.warning.opacity(0.7)),
                            lineWidth: 1.2
                        )
                )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Lógica de IA & Gravação Remote-First

    private func prefillInitialNameIfAvailable() {
        if nameText.isEmpty,
           let rawName = authManager.currentUserName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !rawName.isEmpty,
           rawName.lowercased() != "usuário" {
            nameText = rawName
        }
    }

    private func parseBoundedDouble(_ raw: String, min: Double, max: Double) -> Double? {
        let cleaned = raw
            .replacingOccurrences(of: ",", with: ".")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty,
              let val = Double(cleaned),
              val.isFinite,
              val >= min,
              val <= max else {
            return nil
        }
        return val
    }

    private func scheduleAutoAISuggestion(immediate: Bool = false) {
        aiDebounceTask?.cancel()
        guard let weight = validCurrentWeight, let height = validHeight else { return }

        let signature = String(
            format: "%.1f|%.2f|%@|%@|%@",
            weight,
            height,
            selectedGoal.rawValue,
            selectedActivity.rawValue,
            selectedSex.rawValue
        )
        guard signature != lastAISignature else { return }

        aiDebounceTask = Task { @MainActor in
            if !immediate {
                try? await Task.sleep(nanoseconds: 650_000_000)
            }
            guard !Task.isCancelled else { return }
            await requestAISuggestion(weight: weight, height: height, signature: signature, forceTargetWeightRefresh: !hasManuallyEditedTargetWeight)
        }
    }

    private func triggerAISuggestionNow(forceTargetWeightRefresh: Bool) {
        aiDebounceTask?.cancel()
        guard let weight = validCurrentWeight, let height = validHeight else { return }
        let signature = String(
            format: "%.1f|%.2f|%@|%@|%@",
            weight,
            height,
            selectedGoal.rawValue,
            selectedActivity.rawValue,
            selectedSex.rawValue
        )
        Task { @MainActor in
            await requestAISuggestion(weight: weight, height: height, signature: signature, forceTargetWeightRefresh: forceTargetWeightRefresh)
        }
    }

    @MainActor
    private func requestAISuggestion(
        weight: Double,
        height: Double,
        signature: String,
        forceTargetWeightRefresh: Bool
    ) async {
        isLoadingAISuggestion = true
        let suggestion = await GeminiAIService.shared.suggestInitialGoals(
            userName: validName ?? authManager.currentUserName ?? "Atleta",
            goal: selectedGoal,
            currentWeight: weight,
            heightMeters: height,
            targetWeight: hasManuallyEditedTargetWeight ? validTargetWeight : nil,
            activityLevel: selectedActivity.rawValue
        )
        lastAISignature = signature

        withAnimation(.spring(duration: 0.3)) {
            if forceTargetWeightRefresh || targetWeightText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                targetWeightText = String(format: "%.1f", suggestion.suggestedTargetWeight)
            }
            dailyCaloriesText = "\(Int(suggestion.dailyCalories.rounded()))"
            proteinText = "\(Int(suggestion.proteinGrams.rounded()))"
            carbsText = "\(Int(suggestion.carbsGrams.rounded()))"
            fatText = "\(Int(suggestion.fatGrams.rounded()))"
            waterMlText = "\(suggestion.waterMilliliters)"
            calorieBurnText = "\(Int(suggestion.targetCalorieBurn.rounded()))"
            stepsText = "\(Int(suggestion.targetSteps.rounded()))"
            aiExplanation = suggestion.explanation
            isLoadingAISuggestion = false
        }
    }

    private func submitInitialConfiguration() {
        guard let name = validName,
              let currentWeight = validCurrentWeight,
              let height = validHeight,
              let targetWeight = validTargetWeight,
              let calories = validCalories,
              let protein = validProtein,
              let carbs = validCarbs,
              let fat = validFat,
              let waterMl = validWaterMl,
              let burn = validCalorieBurn,
              let steps = validSteps else {
            return
        }

        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        saveErrorMessage = nil
        isSavingToDatabase = true

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.completeInitialOnboardingRemoteFirst(
                    name: name,
                    goal: selectedGoal,
                    currentWeight: currentWeight,
                    heightMeters: height,
                    targetWeight: targetWeight,
                    targetCalorieIntake: calories,
                    targetProtein: protein,
                    targetCarbohydrates: carbs,
                    targetFat: fat,
                    targetWaterMilliliters: waterMl,
                    targetCalorieBurn: burn,
                    targetSteps: steps
                )
                BiologicalSex.persistToDefaults(selectedSex)
                let savedProfile = FormaDataStore.shared.ensureSingleProfile(in: FormaDataStore.shared.mainContext)
                savedProfile.biologicalSex = selectedSex
                try? FormaDataStore.shared.mainContext.save()
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                isSavingToDatabase = false
            } catch {
                isSavingToDatabase = false
                saveErrorMessage = "Não foi possível sincronizar com o banco de dados agora (\(FormaAPIClient.humanizeErrorMessage(error.localizedDescription))). Verifique sua conexão e tente novamente."
            }
        }
    }
}
