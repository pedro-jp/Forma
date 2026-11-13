//
//  ActiveExerciseCard.swift
//  Forma
//
//  Componentes visuais da Sessão de Treino Ativa: checklist de séries com carga individual por série e timer de descanso.
//

import SwiftUI

/// Botão inteligente de carga:
/// - Toque rápido: ajusta pelo passo padrão (2 kg)
/// - Manter pressionado: abre um balão estilo teclado de acentos do iPhone (0.5kg | 1kg | 2kg | 5kg).
///   Deslizar e soltar aplica apenas o ajuste fino escolhido (ex: 0.5kg ou 1kg); continuar segurando dispara
///   aumento/redução rápido e contínuo na velocidade/precisão escolhida!
struct HoldAndAccentWeightButton<LabelContent: View>: View {
    let isIncrease: Bool
    let onAdjust: (Double) -> Void
    var onPopupVisibilityChange: ((Bool) -> Void)? = nil
    @ViewBuilder let label: () -> LabelContent

    private let steps: [Double] = [0.5, 1.0, 2.0, 5.0]
    @GestureState private var isFingerDown: Bool = false
    @State private var isPressing: Bool = false
    @State private var showAccentPopup: Bool = false
    @State private var selectedStepIndex: Int = 2 // 2.0 kg padrão
    @State private var repeatCount: Int = 0
    @State private var stepAppliedAfterSlide: Bool = false
    @State private var holdTask: Task<Void, Never>? = nil

    private var sign: Double {
        isIncrease ? 1.0 : -1.0
    }

    var body: some View {
        label()
            .scaleEffect(isPressing ? 0.92 : 1.0)
            .animation(.easeOut(duration: 0.12), value: isPressing)
            .overlay(alignment: isIncrease ? .topTrailing : .topLeading) {
                if showAccentPopup {
                    accentCalloutView
                        .offset(x: isIncrease ? 8 : -8, y: -44)
                        .transition(.scale(scale: 0.85, anchor: isIncrease ? .bottomTrailing : .bottomLeading).combined(with: .opacity))
                        .zIndex(999)
                }
            }
            .zIndex(showAccentPopup ? 999 : 1)
            .highPriorityGesture(
                DragGesture(minimumDistance: 0)
                    .updating($isFingerDown) { _, state, _ in
                        state = true
                    }
                    .onChanged { value in
                        if !isPressing {
                            startHold()
                        } else if showAccentPopup {
                            // Igual ao teclado do iPhone: deslizar para a esquerda/direita seleciona 0.5kg, 1kg, 2kg ou 5kg
                            let deltaX = value.translation.width
                            let slotOffset = Int((deltaX / 26.0).rounded())
                            let newIdx = min(steps.count - 1, max(0, 2 + slotOffset))
                            if newIdx != selectedStepIndex {
                                selectedStepIndex = newIdx
                                stepAppliedAfterSlide = false
                                let sel = UISelectionFeedbackGenerator()
                                sel.selectionChanged()
                            }
                        }
                    }
                    .onEnded { _ in
                        endHold(cancelled: false)
                    }
            )
            .onChange(of: isFingerDown) { _, down in
                if !down && isPressing {
                    endHold(cancelled: true)
                }
            }
            .onDisappear {
                cancelHoldImmediately()
            }
    }

    private var accentCalloutView: some View {
        HStack(spacing: 4) {
            ForEach(steps.indices, id: \.self) { idx in
                let step = steps[idx]
                let isSelected = idx == selectedStepIndex
                Text("\(isIncrease ? "+" : "-")\(formatStep(step))")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(isSelected ? .white : FormaTheme.Colors.primaryText)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 5)
                    .background(
                        Capsule()
                            .fill(isSelected ? FormaTheme.Colors.brand : Color.primary.opacity(0.08))
                    )
                    .scaleEffect(isSelected ? 1.08 : 1.0)
                    .animation(.spring(duration: 0.18), value: selectedStepIndex)
            }
        }
        .padding(5)
        .background(
            Capsule()
                .fill(FormaTheme.Colors.cardSurface)
                .shadow(color: Color.black.opacity(0.24), radius: 10, x: 0, y: 4)
        )
        .overlay(
            Capsule()
                .stroke(FormaTheme.Colors.brand.opacity(0.38), lineWidth: 1)
        )
        .fixedSize()
    }

    private func startHold() {
        isPressing = true
        selectedStepIndex = 2
        repeatCount = 0
        stepAppliedAfterSlide = false
        holdTask?.cancel()

        holdTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 220_000_000)
            guard !Task.isCancelled, isPressing else { return }

            withAnimation(.spring(duration: 0.2)) {
                showAccentPopup = true
            }
            onPopupVisibilityChange?(true)
            let heavy = UIImpactFeedbackGenerator(style: .medium)
            heavy.impactOccurred()

            // Janela rápida estilo teclado de acento (~350ms) para deslizar até 0.5kg ou 1kg sem disparar ±2kg antes
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled, isPressing else { return }

            // Disparo rápido e contínuo enquanto mantém pressionado
            while !Task.isCancelled && isPressing {
                let currentStep = steps[selectedStepIndex]
                onAdjust(sign * currentStep)
                repeatCount += 1
                stepAppliedAfterSlide = true

                let tickHaptic = UIImpactFeedbackGenerator(style: .rigid)
                tickHaptic.impactOccurred(intensity: 0.65)

                let delayNs: UInt64 = repeatCount > 5 ? 85_000_000 : 135_000_000
                try? await Task.sleep(nanoseconds: delayNs)
            }
        }
    }

    private func endHold(cancelled: Bool) {
        guard isPressing else { return }
        let wasPopupVisible = showAccentPopup
        let chosenStep = steps[selectedStepIndex]
        isPressing = false
        holdTask?.cancel()
        holdTask = nil

        if !cancelled {
            if !wasPopupVisible {
                // Toque rápido normal -> aplica ±2kg
                onAdjust(sign * 2.0)
            } else if !stepAppliedAfterSlide || repeatCount == 0 {
                // Abriu o seletor estilo teclado, escolheu 0.5kg / 1kg / 2kg / 5kg e soltou -> aplica 1x exato
                onAdjust(sign * chosenStep)
            }
        }

        withAnimation(.easeOut(duration: 0.15)) {
            showAccentPopup = false
        }
        onPopupVisibilityChange?(false)
    }

    private func cancelHoldImmediately() {
        isPressing = false
        holdTask?.cancel()
        holdTask = nil
        if showAccentPopup {
            showAccentPopup = false
            onPopupVisibilityChange?(false)
        }
    }

    private func formatStep(_ val: Double) -> String {
        if val.truncatingRemainder(dividingBy: 1.0) == 0 {
            return "\(Int(val))kg"
        }
        return String(format: "%.1fkg", val)
    }
}

struct ActiveRestTimerBanner: View {
    let restStartDate: Date?
    let restEndDate: Date?
    let restTimeRemaining: Int
    let restTimeTotal: Int
    var lastSetLabel: String? = nil
    let onAdjustSeconds: (Int) -> Void
    var onAdjustLastSetWeight: ((Double) -> Void)? = nil
    let onSkip: () -> Void

    private var ringProgress: CGFloat {
        guard restTimeTotal > 0 else { return 0 }
        return min(1.0, max(0.0, CGFloat(restTimeRemaining) / CGFloat(restTimeTotal)))
    }

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(FormaTheme.Colors.energyGreen.opacity(0.20), lineWidth: 3.5)
                        .frame(width: 44, height: 44)

                    Circle()
                        .trim(from: 0, to: ringProgress)
                        .stroke(FormaTheme.Colors.energyGreen, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                        .frame(width: 44, height: 44)
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 1.0), value: restTimeRemaining)

                    Image(systemName: "timer")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Tempo de Descanso")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.secondary)

                    if let start = restStartDate, let end = restEndDate {
                        Text(timerInterval: start...end, countsDown: true)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                    } else {
                        Text(formatRestTime(restTimeRemaining))
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                    }
                }

                Spacer()

                HStack(spacing: 6) {
                    Button("-15s") {
                        onAdjustSeconds(-15)
                    }
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(Color.primary.opacity(0.07))
                    .clipShape(Capsule())
                    .buttonStyle(.plain)

                    Button("+30s") {
                        onAdjustSeconds(30)
                    }
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(Color.primary.opacity(0.07))
                    .clipShape(Capsule())
                    .buttonStyle(.plain)

                    Button("Pular", action: onSkip)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.danger)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(FormaTheme.Colors.danger.opacity(0.10))
                        .clipShape(Capsule())
                        .buttonStyle(.plain)
                }
            }

            if let label = lastSetLabel, let adjustWeight = onAdjustLastSetWeight {
                HStack(spacing: 8) {
                    Image(systemName: "scalemass.fill")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)

                    Text(label)
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)

                    Spacer()

                    HoldAndAccentWeightButton(isIncrease: false, onAdjust: { delta in
                        adjustWeight(delta)
                    }) {
                        Text("-2kg")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(Color.primary.opacity(0.07))
                            .clipShape(Capsule())
                    }

                    HoldAndAccentWeightButton(isIncrease: true, onAdjust: { delta in
                        adjustWeight(delta)
                    }) {
                        Text("+2kg")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(FormaTheme.Colors.brand)
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(FormaTheme.Colors.brand.opacity(0.06))
                )
                .zIndex(10)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(FormaTheme.Colors.cardSurface)
                .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 3)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(FormaTheme.Colors.energyGreen.opacity(0.25), lineWidth: 0.8)
        )
        .zIndex(20)
    }

    private func formatRestTime(_ totalSeconds: Int) -> String {
        let mins = totalSeconds / 60
        let secs = totalSeconds % 60
        return mins > 0 ? String(format: "%d:%02d", mins, secs) : "\(secs)s"
    }
}

struct ActiveExerciseCard: View {
    let exercise: WorkoutExerciseItem
    let onOpenInfo: () -> Void
    let onToggleSet: (Int) -> Void
    var onAdjustSetWeight: ((Int, Double) -> Void)? = nil

    @State private var showTip: Bool = false
    @State private var activePopupSetIndex: Int? = nil

    private var hasHistoricalAverage: Bool {
        SharedWorkoutSessionManager.shared.lastAverageWeightKg(
            for: exercise.name,
            animationId: exercise.animationId
        ) != nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(exercise.name)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)

                    HStack(spacing: 6) {
                        if !exercise.targetMuscle.isEmpty {
                            Text(exercise.targetMuscle)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("•").font(.system(size: 11)).foregroundStyle(.secondary)
                        }

                        Text("\(exercise.sets) séries • \(exercise.reps) reps")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(.secondary)

                        let avgKg = exercise.averageWeightKg
                        if avgKg > 0 {
                            Text("• \(hasHistoricalAverage ? "Média" : "Rec."): \(WorkoutExerciseItem.formatWeightKg(avgKg))")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                        }
                    }
                }

                Spacer()

                HStack(spacing: 6) {
                    if !exercise.executionTip.isEmpty {
                        Button {
                            withAnimation(.spring(duration: 0.25)) {
                                showTip.toggle()
                            }
                        } label: {
                            Image(systemName: "lightbulb.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(FormaTheme.Colors.warning)
                                .frame(width: 28, height: 28)
                                .background(FormaTheme.Colors.warning.opacity(0.12))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }

                    Button(action: onOpenInfo) {
                        HStack(spacing: 4) {
                            Image(systemName: "play.tv.fill")
                                .font(.system(size: 13))
                            Text("Vídeo")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            if showTip && !exercise.executionTip.isEmpty {
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 11))
                        .foregroundStyle(FormaTheme.Colors.warning)
                    Text(exercise.executionTip)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(FormaTheme.Colors.warning.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            Divider().background(Color.primary.opacity(0.06))

            VStack(spacing: 8) {
                ForEach(0..<exercise.sets, id: \.self) { setIndex in
                    let isDone = setIndex < exercise.completedSets.count ? exercise.completedSets[setIndex] : false
                    let setWeight = exercise.weightForSet(setIndex)

                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Série \(setIndex + 1)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(isDone ? .secondary : FormaTheme.Colors.primaryText)
                            Text("\(exercise.reps) reps • \(exercise.restSeconds)s")
                                .font(.system(size: 10.5, weight: .medium))
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        // Controle de Carga (kg) por Série (Segure para repetição rápida e escolher 0.5kg / 1kg / 2kg / 5kg)
                        HStack(spacing: 4) {
                            HoldAndAccentWeightButton(
                                isIncrease: false,
                                onAdjust: { delta in
                                    onAdjustSetWeight?(setIndex, delta)
                                },
                                onPopupVisibilityChange: { visible in
                                    activePopupSetIndex = visible ? setIndex : (activePopupSetIndex == setIndex ? nil : activePopupSetIndex)
                                }
                            ) {
                                Image(systemName: "minus")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .frame(width: 26, height: 26)
                                    .background(Color.primary.opacity(0.07))
                                    .clipShape(Circle())
                            }

                            Text(WorkoutExerciseItem.formatWeightKg(setWeight))
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .foregroundStyle(FormaTheme.Colors.brand)
                                .frame(minWidth: 52, alignment: .center)

                            HoldAndAccentWeightButton(
                                isIncrease: true,
                                onAdjust: { delta in
                                    onAdjustSetWeight?(setIndex, delta)
                                },
                                onPopupVisibilityChange: { visible in
                                    activePopupSetIndex = visible ? setIndex : (activePopupSetIndex == setIndex ? nil : activePopupSetIndex)
                                }
                            ) {
                                Image(systemName: "plus")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .frame(width: 26, height: 26)
                                    .background(Color.primary.opacity(0.07))
                                    .clipShape(Circle())
                            }
                        }

                        Button {
                            onToggleSet(setIndex)
                        } label: {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(isDone ? FormaTheme.Colors.brand : Color.primary.opacity(0.08))
                                    .frame(width: 36, height: 36)

                                if isDone {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(.white)
                                } else {
                                    Circle()
                                        .stroke(Color.secondary.opacity(0.35), lineWidth: 1.5)
                                        .frame(width: 16, height: 16)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(isDone ? FormaTheme.Colors.brand.opacity(0.07) : Color(.systemBackground))
                    )
                    .zIndex(activePopupSetIndex == setIndex ? 100 : Double(exercise.sets - setIndex))
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(FormaTheme.Colors.cardSurface)
                .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(exercise.isAllSetsCompleted ? FormaTheme.Colors.brand.opacity(0.35) : Color.primary.opacity(0.05), lineWidth: 1)
        )
        .zIndex(activePopupSetIndex != nil ? 50 : 1)
    }
}
