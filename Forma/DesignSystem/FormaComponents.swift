//
//  FormaComponents.swift
//  Forma
//
//  Componentes reutilizáveis de interface do Design System FormaUI (Apple Health / Fitness+ Clean).
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Containers Padronizados

struct FormaCard<Content: View>: View {
    var padding: CGFloat = FormaTheme.Spacing.cardPadding
    var cornerRadius: CGFloat = FormaTheme.Radius.card
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .background(FormaTheme.Colors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .shadow(color: Color.black.opacity(0.035), radius: 10, x: 0, y: 3)
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(Color.primary.opacity(0.06), lineWidth: 0.6)
            )
    }
}

struct FormaTintedCard<Content: View>: View {
    let tint: Color
    var padding: CGFloat = 16
    var cornerRadius: CGFloat = FormaTheme.Radius.inner
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .background(tint.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(tint.opacity(0.20), lineWidth: 0.8)
            )
    }
}

// MARK: - Cabeçalho de Tela

struct FormaScreenHeader<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder let trailing: () -> Trailing

    init(
        title: String,
        subtitle: String,
        @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }
    ) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
    }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            Spacer(minLength: 8)
            trailing()
                .fixedSize(horizontal: true, vertical: false)
        }
    }
}

// MARK: - Badges e Pílulas

struct FormaBadge: View {
    let text: String
    var icon: String? = nil
    var tint: Color = FormaTheme.Colors.brandInk

    var body: some View {
        HStack(spacing: 4) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .bold))
            }
            Text(text)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(tint.opacity(0.14))
        .clipShape(Capsule())
        .fixedSize(horizontal: true, vertical: false)
    }
}

// MARK: - Banners Semânticos (Erros, Avisos e Sucesso com Retry)

enum FormaBannerStyle {
    case error
    case warning
    case info
    case success

    var color: Color {
        switch self {
        case .error: return FormaTheme.Colors.danger
        case .warning: return FormaTheme.Colors.warning
        case .info: return FormaTheme.Colors.aiAccent
        case .success: return FormaTheme.Colors.success
        }
    }

    var icon: String {
        switch self {
        case .error: return "exclamationmark.triangle.fill"
        case .warning: return "bolt.badge.clock.fill"
        case .info: return "sparkles"
        case .success: return "checkmark.circle.fill"
        }
    }
}

struct FormaBanner: View {
    let message: String
    var style: FormaBannerStyle = .error
    var retryTitle: String = "Tentar novamente"
    var onRetry: (() -> Void)? = nil

    private var sanitizedMessage: String {
        FormaAPIClient.humanizeErrorMessage(message)
    }

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: style.icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(style.color)

            Text(sanitizedMessage)
                .font(.caption.weight(.medium))
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 4)

            if let onRetry {
                Button(action: onRetry) {
                    Text(retryTitle)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(style.color)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(style.color.opacity(0.09))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(style.color.opacity(0.22), lineWidth: 0.8)
        )
    }
}

// MARK: - Empty State Elegante

struct FormaEmptyState: View {
    let icon: String
    let title: String
    let subtitle: String
    var actionTitle: String? = nil
    var onAction: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(FormaTheme.Colors.brandSoft)
                    .frame(width: 42, height: 42)
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(FormaTheme.Colors.brand)
            }

            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .multilineTextAlignment(.center)

            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let actionTitle, let onAction {
                Button(action: onAction) {
                    Text(actionTitle)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }
}

// MARK: - Anel Principal de Energia (Apple Fitness Style)

struct FormaEnergyRing: View {
    let current: Double
    let target: Double
    let color: Color
    var lineWidth: CGFloat = 13
    var unitLabel: String = "kcal"
    var isBulkingMode: Bool = true

    private var normalizedProgress: Double {
        guard target > 0 else { return 0 }
        return min(max(current / target, 0), 1.0)
    }

    private var diff: Double {
        target - current
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.16), lineWidth: lineWidth)

            Circle()
                .trim(from: 0, to: normalizedProgress)
                .stroke(
                    AngularGradient(
                        colors: [color.opacity(0.85), color, color.opacity(0.95)],
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.spring(duration: 0.45), value: normalizedProgress)

            VStack(spacing: 3) {
                Text("\(Int(current.rounded()))")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .contentTransition(.numericText())

                Text("de \(Int(target.rounded())) \(unitLabel)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)

                statusTag
                    .padding(.top, 2)
            }
        }
    }

    @ViewBuilder
    private var statusTag: some View {
        if isBulkingMode {
            if diff > 0 {
                Text("Faltam \(Int(diff.rounded())) kcal")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brand)
            } else {
                Text("Superávit batido! 🔥")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.energyGreen)
            }
        } else {
            if diff > 0 {
                Text("Restam \(Int(diff.rounded())) kcal")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.energyGreen)
            } else {
                Text("Meta atingida! ⚡️")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.energyGreen)
            }
        }
    }
}

// MARK: - Anel Duplo Concêntrico (Consumidas + Queima Ativa)

struct FormaDualEnergyRing: View {
    @Binding var activeMetric: FocusMetric
    let consumed: Double
    let consumedTarget: Double
    let burned: Double
    let burnedTarget: Double
    var isBulkingMode: Bool = true

    private var consumedProgress: Double {
        guard consumedTarget > 0 else { return 0 }
        return min(max(consumed / consumedTarget, 0), 1.0)
    }

    private var burnedProgress: Double {
        guard burnedTarget > 0 else { return 0 }
        return min(max(burned / burnedTarget, 0), 1.0)
    }

    private var consumedDiff: Double {
        consumedTarget - consumed
    }

    var body: some View {
        ZStack {
            // Anel externo: Calorias consumidas
            Circle()
                .stroke(FormaTheme.Colors.brand.opacity(0.15), lineWidth: 14)
                .frame(width: 186, height: 186)

            Circle()
                .trim(from: 0, to: consumedProgress)
                .stroke(
                    AngularGradient(
                        colors: [
                            FormaTheme.Colors.brand.opacity(0.82),
                            FormaTheme.Colors.brand,
                            FormaTheme.Colors.brand.opacity(0.95)
                        ],
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: 14, lineCap: .round)
                )
                .frame(width: 186, height: 186)
                .rotationEffect(.degrees(-90))
                .opacity(activeMetric == .consumed ? 1.0 : 0.72)
                .animation(.spring(duration: 0.45), value: consumedProgress)

            // Anel interno: Queima ativa
            Circle()
                .stroke(FormaTheme.Colors.energyGreen.opacity(0.15), lineWidth: 11)
                .frame(width: 148, height: 148)

            Circle()
                .trim(from: 0, to: burnedProgress)
                .stroke(
                    AngularGradient(
                        colors: [
                            FormaTheme.Colors.energyGreen.opacity(0.82),
                            FormaTheme.Colors.energyGreen,
                            FormaTheme.Colors.energyGreen.opacity(0.95)
                        ],
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: 11, lineCap: .round)
                )
                .frame(width: 148, height: 148)
                .rotationEffect(.degrees(-90))
                .opacity(activeMetric == .burned ? 1.0 : 0.72)
                .animation(.spring(duration: 0.45), value: burnedProgress)

            VStack(spacing: 2) {
                if activeMetric == .consumed {
                    Text("\(Int(consumed.rounded()))")
                        .font(.system(size: 29, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .contentTransition(.numericText())

                    Text("de \(Int(consumedTarget.rounded())) kcal")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)

                    consumedStatusTag
                        .padding(.top, 1)
                } else {
                    Text("+\(Int(burned.rounded()))")
                        .font(.system(size: 29, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .contentTransition(.numericText())

                    Text("meta \(Int(burnedTarget.rounded())) kcal")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)

                    Text(burned >= burnedTarget ? "Queima batida! 🔥" : "Queima ativa")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                        .lineLimit(1)
                        .padding(.top, 1)
                }

                HStack(spacing: 4) {
                    Capsule()
                        .fill(FormaTheme.Colors.brand.opacity(activeMetric == .consumed ? 1.0 : 0.25))
                        .frame(width: activeMetric == .consumed ? 12 : 5, height: 4)
                    Capsule()
                        .fill(FormaTheme.Colors.energyGreen.opacity(activeMetric == .burned ? 1.0 : 0.25))
                        .frame(width: activeMetric == .burned ? 12 : 5, height: 4)
                }
                .padding(.top, 4)
            }
            .frame(maxWidth: 116)
        }
        .frame(width: 196, height: 196)
        .contentShape(Circle())
        .onTapGesture {
            withAnimation(.spring(response: 0.32, dampingFraction: 0.8)) {
                activeMetric = activeMetric == .consumed ? .burned : .consumed
            }
            #if canImport(UIKit)
            UISelectionFeedbackGenerator().selectionChanged()
            #endif
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Toque para alternar o foco entre calorias consumidas e queima ativa")
    }

    @ViewBuilder
    private var consumedStatusTag: some View {
        if isBulkingMode {
            if consumedDiff > 0 {
                Text("Faltam \(Int(consumedDiff.rounded())) kcal")
                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brandInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            } else {
                Text("Superávit batido! 🔥")
                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brandInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        } else {
            if consumedDiff > 0 {
                Text("Restam \(Int(consumedDiff.rounded())) kcal")
                    .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brandInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            } else {
                Text("Meta atingida! ⚡️")
                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brandInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }
}

// MARK: - Barra Horizontal e Linha Unificada de Macronutrientes

struct FormaProgressBar: View {
    let value: Double
    let total: Double
    var color: Color = FormaTheme.Colors.brand
    var height: CGFloat = 6

    private var ratio: Double {
        guard total > 0 else { return 0 }
        return min(max(value / total, 0), 1.0)
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(color.opacity(0.16))
                Capsule()
                    .fill(color)
                    .frame(width: max(0, geo.size.width * ratio))
                    .animation(.spring(duration: 0.35), value: ratio)
            }
        }
        .frame(height: height)
    }
}

struct FormaMacroProgressRow: View {
    let proteinCurrent: Double
    let proteinTarget: Double
    let carbsCurrent: Double
    let carbsTarget: Double
    let fatCurrent: Double
    let fatTarget: Double

    var body: some View {
        HStack(spacing: 12) {
            FormaSingleMacroBar(
                title: "Proteínas",
                current: proteinCurrent,
                target: proteinTarget,
                color: FormaTheme.Colors.protein
            )
            FormaSingleMacroBar(
                title: "Carboidratos",
                current: carbsCurrent,
                target: carbsTarget,
                color: FormaTheme.Colors.carbs
            )
            FormaSingleMacroBar(
                title: "Gorduras",
                current: fatCurrent,
                target: fatTarget,
                color: FormaTheme.Colors.fat
            )
        }
    }
}

struct FormaSingleMacroBar: View {
    let title: String
    let current: Double
    let target: Double
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text("\(Int(current.rounded()))")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .contentTransition(.numericText())
                    .lineLimit(1)
                Text("/\(Int(target.rounded()))g")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }

            FormaProgressBar(value: current, total: target, color: color, height: 5)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Card Unificado de Hidratação (Reutilizado em Início e Alimentação)

struct FormaHydrationCard: View {
    let consumedMl: Int
    let targetMl: Int
    let onAddWater: (Int) -> Void

    private var progress: Double {
        guard targetMl > 0 else { return 0 }
        return min(1.0, Double(consumedMl) / Double(targetMl))
    }

    private var remainingMl: Int {
        max(0, targetMl - consumedMl)
    }

    var body: some View {
        FormaCard(padding: 16) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .stroke(FormaTheme.Colors.water.opacity(0.18), lineWidth: 5)

                        Circle()
                            .trim(from: 0, to: progress)
                            .stroke(
                                FormaTheme.Colors.water,
                                style: StrokeStyle(lineWidth: 5, lineCap: .round)
                            )
                            .rotationEffect(.degrees(-90))
                            .animation(.spring(duration: 0.35), value: progress)

                        Image(systemName: "drop.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.water)
                    }
                    .frame(width: 46, height: 46)

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 8) {
                            Text("Hidratação")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                                .lineLimit(1)

                            Spacer()

                            FormaBadge(text: "\(Int((progress * 100).rounded()))%", tint: FormaTheme.Colors.water)
                        }

                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text("\(consumedMl)")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                                .contentTransition(.numericText())
                            Text("/ \(targetMl) ml")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)

                        Text(remainingMl > 0 ? "Faltam \(remainingMl) ml para a meta diária" : "Meta diária concluída!")
                            .font(.caption)
                            .foregroundStyle(remainingMl > 0 ? Color.secondary : FormaTheme.Colors.water)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                    }
                }

                HStack(spacing: 8) {
                    waterQuickButton(ml: 200, isPrimary: false)
                    waterQuickButton(ml: 250, isPrimary: true)
                    waterQuickButton(ml: 500, isPrimary: false)
                }
            }
        }
    }

    private func waterQuickButton(ml: Int, isPrimary: Bool) -> some View {
        Button {
            onAddWater(ml)
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "plus")
                    .font(.system(size: 10, weight: .bold))
                Text("\(ml) ml")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .lineLimit(1)
            }
            .foregroundStyle(isPrimary ? .white : FormaTheme.Colors.water)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
                    .background(isPrimary ? FormaTheme.Colors.water : FormaTheme.Colors.water.opacity(0.12))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Avatar Dinâmico por Plano (PRO / Essencial)

struct FormaUserAvatar: View {
    let name: String
    let isPro: Bool
    var size: CGFloat = 48
    var badgeBorderColor: Color = FormaTheme.Colors.pageBackground

    private var initials: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = trimmed
            .split(separator: " ")
            .map { String($0.prefix(1)).uppercased() }
            .filter { !$0.isEmpty }
        if parts.count >= 2 {
            return "\(parts[0])\(parts[parts.count - 1])"
        }
        if !trimmed.isEmpty {
            return String(trimmed.prefix(2)).uppercased()
        }
        return "FM"
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            if isPro {
                Circle()
                    .fill(FormaTheme.Colors.aiAccent.opacity(0.14))
                    .frame(width: size, height: size)
                    .overlay(
                        Text(initials)
                            .font(.system(size: round(size * 0.35), weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.aiGradient)
                    )
                    .overlay(
                        Circle()
                            .stroke(FormaTheme.Colors.aiGradient, lineWidth: 2.0)
                    )

                let badgeSize = max(16, round(size * 0.34))
                Circle()
                    .fill(FormaTheme.Colors.aiGradient)
                    .frame(width: badgeSize, height: badgeSize)
                    .overlay(
                        Image(systemName: "sparkles")
                            .font(.system(size: badgeSize * 0.5, weight: .bold))
                            .foregroundStyle(.white)
                    )
                    .overlay(
                        Circle()
                            .stroke(badgeBorderColor, lineWidth: 1.5)
                    )
                    .offset(x: 2, y: 2)
            } else {
                Circle()
                    .fill(FormaTheme.Colors.brandSoft)
                    .frame(width: size, height: size)
                    .overlay(
                        Text(initials)
                            .font(.system(size: round(size * 0.35), weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    )
                    .overlay(
                        Circle()
                            .stroke(FormaTheme.Colors.brand.opacity(0.28), lineWidth: 1.5)
                    )
            }
        }
    }
}
