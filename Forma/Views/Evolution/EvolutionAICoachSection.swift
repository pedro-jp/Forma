//
//  EvolutionAICoachSection.swift
//  Forma
//
//  Seção modular do Treinador & Nutricionista IA na aba Evolução, com tratamento elegante de erros e diagnósticos limpos.
//

import SwiftUI

struct EvolutionAICoachSection: View {
    let analysis: EvolutionAIAnalysis?
    let isAnalyzing: Bool
    let errorMessage: String?
    let appliedTargetsSuccessfully: Bool
    let savedRoutineSuccessfully: Bool
    let onGenerateAnalysis: () -> Void
    let onApplyTargets: (SuggestedNutritionTargets) -> Void
    let onSaveRoutine: (AIRecommendedRoutine) -> Void

    private func cleanText(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        VStack(spacing: FormaTheme.Spacing.sectionGap) {
            coachHeader
            actionButton

            if let errorMessage, !errorMessage.isEmpty {
                FormaBanner(
                    message: errorMessage,
                    style: .error,
                    retryTitle: "Tentar novamente",
                    onRetry: isAnalyzing ? nil : onGenerateAnalysis
                )
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }

            if let analysis {
                VStack(spacing: 16) {
                    diagnosticCard(analysis)

                    if let targets = analysis.suggestedTargets {
                        suggestedTargetsCard(targets)
                    }

                    if let routine = analysis.recommendedRoutine {
                        recommendedRoutineCard(routine)
                    }

                    if !analysis.nutritionSuggestions.isEmpty {
                        suggestionsListCard(
                            title: "Alimentação & Horários",
                            icon: "fork.knife",
                            tint: FormaTheme.Colors.energyGreen,
                            bulletIcon: "checkmark.circle.fill",
                            items: analysis.nutritionSuggestions
                        )
                    }

                    if !analysis.workoutSuggestions.isEmpty {
                        suggestionsListCard(
                            title: "Treinos & Recuperação",
                            icon: "dumbbell.fill",
                            tint: FormaTheme.Colors.warning,
                            bulletIcon: "bolt.fill",
                            items: analysis.workoutSuggestions
                        )
                    }

                    hydrationCard(analysis.hydrationDiagnostic)
                    weeklyActionPlanCard(analysis.weeklyActionPlan)
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            } else if !isAnalyzing {
                FormaCard(padding: 28) {
                    FormaEmptyState(
                        icon: "brain.head.profile",
                        title: "Nenhuma análise gerada ainda",
                        subtitle: "Toque no botão acima para analisar seu balanço calórico, treinos, hidratação e metas com a IA do Google Gemini."
                    )
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "info.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("Análises geradas pela IA do Google Gemini (Google LLC) com caráter exclusivamente informativo e educacional. Não realizam diagnóstico médico/nutricional nem substituem acompanhamento de médico, nutricionista ou profissional de educação física.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 12) {
                    if let mifflinURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/2305711/") {
                        Link("Mifflin-St Jeor (PubMed)", destination: mifflinURL)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    if let tacoURL = URL(string: "https://www.cfn.org.br/wp-content/uploads/2017/03/taco_4_edicao_ampliada_e_revisada.pdf") {
                        Link("Tabela TACO", destination: tacoURL)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    if let whoURL = URL(string: "https://www.who.int/news-room/fact-sheets/detail/physical-activity") {
                        Link("OMS (WHO)", destination: whoURL)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    Spacer()
                    if let reportURL = URL(string: "mailto:joaopedroc035@gmail.com?subject=Relato%20IA%20Forma") {
                        Link("Reportar IA", destination: reportURL)
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.leading, 20)
            }
            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
        }
    }

    private var coachHeader: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(FormaTheme.Colors.brand)
                Text("Análise de Evolução • IA")
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                Spacer()
                FormaBadge(text: "Google Gemini", icon: "sparkles", tint: FormaTheme.Colors.aiAccent)
            }
            Text("Utilizamos a IA do Google Gemini para cruzar seu histórico real de refeições, treinos, passos, água e pesagens e calibrar seu plano.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
    }

    private var actionButton: some View {
        Button(action: onGenerateAnalysis) {
            HStack(spacing: 8) {
                if isAnalyzing {
                    ProgressView()
                        .tint(.white)
                    Text("Analisando sua evolução com Google Gemini...")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                } else {
                    Image(systemName: analysis == nil ? "wand.and.stars" : "arrow.triangle.2.circlepath")
                        .font(.system(size: 14, weight: .bold))
                    Text(analysis == nil ? "Analisar com IA Google Gemini" : "Recalcular com Google Gemini")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(FormaTheme.Colors.brand)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: FormaTheme.Colors.brand.opacity(0.26), radius: 8, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .disabled(isAnalyzing)
        .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
    }

    private func diagnosticCard(_ analysis: EvolutionAIAnalysis) -> some View {
        let summary = cleanText(analysis.statusSummary)
        let isShortBadge = summary.count <= 58

        return FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "target")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Análise de Balanço Calórico")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()
                }

                if isShortBadge {
                    FormaBadge(text: summary, icon: "sparkles", tint: FormaTheme.Colors.energyGreen)
                } else {
                    FormaTintedCard(tint: FormaTheme.Colors.brand, padding: 12, cornerRadius: 12) {
                        Text(summary)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineSpacing(3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                Text(cleanText(analysis.calorieDiagnostic))
                    .font(.subheadline)
                    .foregroundStyle(FormaTheme.Colors.primaryText.opacity(0.92))
                    .lineSpacing(3)

                if !analysis.macroDiagnostic.isEmpty {
                    Divider().background(Color.primary.opacity(0.06))
                    Text(cleanText(analysis.macroDiagnostic))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineSpacing(2)
                }
            }
        }
    }

    private func suggestedTargetsCard(_ targets: SuggestedNutritionTargets) -> some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "bolt.heart.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                        Text("Metas Diárias Sugeridas")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }
                    Spacer()
                    FormaBadge(text: "Recomendado", tint: FormaTheme.Colors.brand)
                }

                Text(cleanText(targets.explanation))
                    .font(.subheadline)
                    .foregroundStyle(FormaTheme.Colors.primaryText.opacity(0.9))
                    .lineSpacing(2)

                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        targetTile(title: "Calorias Alvo", value: "\(Int(targets.dailyCalories)) kcal", tint: FormaTheme.Colors.brand)
                        targetTile(title: "Meta de Água", value: "\(targets.waterMilliliters) ml", tint: FormaTheme.Colors.water)
                    }

                    HStack(spacing: 8) {
                        targetTile(title: "Proteína", value: "\(Int(targets.proteinGrams))g", tint: FormaTheme.Colors.protein)
                        targetTile(title: "Carboidrato", value: "\(Int(targets.carbsGrams))g", tint: FormaTheme.Colors.carbs)
                        targetTile(title: "Gordura", value: "\(Int(targets.fatGrams))g", tint: FormaTheme.Colors.fat)
                    }
                }

                Button {
                    onApplyTargets(targets)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: appliedTargetsSuccessfully ? "checkmark.circle.fill" : "slider.horizontal.3")
                            .font(.system(size: 12, weight: .bold))
                        Text(appliedTargetsSuccessfully ? "Metas Aplicadas ao Perfil!" : "Aplicar Novas Metas ao Perfil")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(appliedTargetsSuccessfully ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.brand)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func targetTile(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(tint.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    private func recommendedRoutineCard(_ routine: AIRecommendedRoutine) -> some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    FormaBadge(text: "Ficha Recomendada", icon: "sparkles", tint: FormaTheme.Colors.aiAccent)
                    Text(routine.title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    if !routine.targetMuscles.isEmpty {
                        Text(routine.targetMuscles)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 8) {
                    FormaBadge(text: "~\(Int(routine.estimatedCalories)) kcal", icon: "flame.fill", tint: FormaTheme.Colors.energyGreen)
                    FormaBadge(text: "\(routine.estimatedDurationMinutes) min", icon: "clock", tint: FormaTheme.Colors.brand)
                    FormaBadge(text: "\(routine.exercises.count) exercícios", icon: "dumbbell.fill", tint: FormaTheme.Colors.warning)
                }

                Divider().background(Color.primary.opacity(0.06))

                VStack(alignment: .leading, spacing: 6) {
                    ForEach(routine.exercises.prefix(5)) { ex in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(ex.name)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Text("\(ex.sets)x \(ex.reps) • \(ex.suggestedWeight.isEmpty ? "\(ex.restSeconds)s descanso" : ex.suggestedWeight)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if ex.animationId != nil {
                                Image(systemName: "play.circle.fill")
                                    .foregroundStyle(FormaTheme.Colors.brand.opacity(0.75))
                                    .font(.system(size: 14))
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }

                Button {
                    onSaveRoutine(routine)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: savedRoutineSuccessfully ? "checkmark.circle.fill" : "plus.circle.fill")
                            .font(.system(size: 13, weight: .bold))
                        Text(savedRoutineSuccessfully ? "Ficha Salva em Treinos!" : "Adicionar à Aba de Treinos")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(savedRoutineSuccessfully ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.warning)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func suggestionsListCard(
        title: String,
        icon: String,
        tint: Color,
        bulletIcon: String,
        items: [String]
    ) -> some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 6) {
                    Image(systemName: icon)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(tint)
                    Text(title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }

                ForEach(items, id: \.self) { tip in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: bulletIcon)
                            .font(.system(size: 12))
                            .foregroundStyle(tint)
                            .padding(.top, 2)
                        Text(cleanText(tip))
                            .font(.subheadline)
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineSpacing(2)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func hydrationCard(_ text: String) -> some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "drop.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.water)
                    Text("Consumo de Água & Hidratação")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }

                Text(cleanText(text))
                    .font(.subheadline)
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineSpacing(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func weeklyActionPlanCard(_ text: String) -> some View {
        FormaTintedCard(tint: FormaTheme.Colors.brand, padding: 18, cornerRadius: FormaTheme.Radius.card) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Plano de Ação para os Próximos 7 Dias")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }

                Text(cleanText(text))
                    .font(.subheadline)
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineSpacing(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
