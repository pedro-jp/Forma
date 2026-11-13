//
//  WatchWorkoutView.swift
//  FormaWatch
//
//  Tela principal de treino no Apple Watch: concluir série com 1 toque,
//  ajustar carga da série que passou (-2kg / +2kg ou girando a Digital Crown)
//  e controlar o cronômetro de descanso no pulso.
//

import SwiftUI

public struct WatchWorkoutView: View {
    @ObservedObject var controller: WatchSessionController
    @State private var selectedStepKg: Double = 2.0
    @State private var showPrecisionPicker: Bool = false
    @GestureState private var isWatchHoldDown: Bool = false
    @State private var holdTask: Task<Void, Never>? = nil
    @State private var holdDidAutoRepeat: Bool = false
    @State private var activeHoldSign: Double = 0.0

    public init(controller: WatchSessionController = .shared) {
        self.controller = controller
    }

    private var stepLabel: String {
        selectedStepKg.truncatingRemainder(dividingBy: 1.0) == 0
            ? "\(Int(selectedStepKg))kg"
            : String(format: "%.1fkg", selectedStepKg)
    }

    private var displayedWeightKg: Double {
        controller.isResting && controller.lastCompletedSetNumber > 0
            ? controller.lastCompletedSetWeightKg
            : controller.currentSetWeightKg
    }

    private var weightBadgeTitle: String {
        if controller.isResting && controller.lastCompletedSetNumber > 0 {
            return "Série \(controller.lastCompletedSetNumber) feita"
        }
        return "Carga Série \(controller.currentSetNumber)"
    }

    public var body: some View {
        ScrollView {
            if controller.hasActiveWorkout {
                VStack(spacing: 8) {
                    // Cabeçalho com exercício e série
                    VStack(spacing: 2) {
                        Text(controller.currentExerciseName)
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)

                        Text("Série \(controller.currentSetNumber)/\(controller.totalSetsForExercise) • \(controller.completedSetsTotal)/\(controller.totalSetsTotal) total")
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundStyle(.secondary)
                    }

                    // Controle de Carga da Série que Passou / Atual (Segurar repete rápido e abre opções 0.5kg | 1kg | 2kg)
                    VStack(spacing: 4) {
                        HStack {
                            Text(weightBadgeTitle)
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                .foregroundStyle(Color(red: 0.99, green: 0.29, blue: 0.05))
                            Spacer()
                            Button {
                                withAnimation(.spring(duration: 0.2)) {
                                    showPrecisionPicker.toggle()
                                }
                            } label: {
                                Text("±\(stepLabel)")
                                    .font(.system(size: 9, weight: .heavy, design: .rounded))
                                    .foregroundStyle(.white.opacity(0.85))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.white.opacity(0.14))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }

                        if showPrecisionPicker {
                            HStack(spacing: 4) {
                                ForEach([0.5, 1.0, 2.0], id: \.self) { step in
                                    let label = step == 0.5 ? "0.5kg" : "\(Int(step))kg"
                                    let isSelected = selectedStepKg == step
                                    Button {
                                        selectedStepKg = step
                                        withAnimation(.easeOut(duration: 0.15)) {
                                            showPrecisionPicker = false
                                        }
                                    } label: {
                                        Text(label)
                                            .font(.system(size: 10, weight: .bold, design: .rounded))
                                            .foregroundStyle(isSelected ? .black : .white)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 4)
                                            .background(isSelected ? Color(red: 0.99, green: 0.29, blue: 0.05) : Color.white.opacity(0.14))
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        HStack(spacing: 8) {
                            watchHoldWeightButton(isIncrease: false)

                            Text(formatKg(displayedWeightKg))
                                .font(.system(size: 15, weight: .black, design: .rounded))
                                .foregroundStyle(.white)
                                .monospacedDigit()
                                .frame(minWidth: 52)
                                .onTapGesture {
                                    withAnimation(.spring(duration: 0.2)) {
                                        showPrecisionPicker.toggle()
                                    }
                                }

                            watchHoldWeightButton(isIncrease: true)
                        }
                    }
                    .padding(8)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    // Cronômetro de Descanso ou Botão Concluir Série
                    if controller.isResting, let endDate = controller.restEndDate, endDate > Date() {
                        VStack(spacing: 6) {
                            HStack(spacing: 4) {
                                Image(systemName: "timer")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(.orange)
                                Text(timerInterval: Date()...endDate, countsDown: true)
                                    .font(.system(size: 18, weight: .black, design: .rounded))
                                    .monospacedDigit()
                                    .foregroundStyle(.orange)
                            }

                            HStack(spacing: 6) {
                                Button {
                                    controller.addRest30Seconds()
                                } label: {
                                    Text("+30s")
                                        .font(.system(size: 12, weight: .bold, design: .rounded))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 7)
                                        .background(Color.white.opacity(0.14))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)

                                Button {
                                    controller.skipRest()
                                } label: {
                                    Text("Pular")
                                        .font(.system(size: 12, weight: .bold, design: .rounded))
                                        .foregroundStyle(.black)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 7)
                                        .background(Color.orange)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(8)
                        .background(Color.orange.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    } else if controller.isAllCompleted {
                        Button {
                            controller.finishWorkout()
                        } label: {
                            Label("Salvar Treino", systemImage: "flag.checkered")
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                                .foregroundStyle(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color.green)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            controller.completeCurrentSet()
                        } label: {
                            Label("Concluir Série \(controller.currentSetNumber)", systemImage: "checkmark.circle.fill")
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color(red: 0.99, green: 0.29, blue: 0.05))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }

                    Button {
                        controller.finishWorkout()
                    } label: {
                        Text("Encerrar Treino")
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundStyle(.secondary)
                            .padding(.top, 2)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 4)
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "dumbbell.fill")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundStyle(Color(red: 0.99, green: 0.29, blue: 0.05))
                    Text("Nenhum Treino Ativo")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                    Text("Inicie uma ficha no iPhone ou toque abaixo para testar no relógio:")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    Button {
                        controller.startLocalDemoWorkout()
                    } label: {
                        Label("Testar Treino no Relógio", systemImage: "play.fill")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(Color(red: 0.99, green: 0.29, blue: 0.05))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                }
                .padding(.top, 8)
            }
        }
    }

    @ViewBuilder
    private func watchHoldWeightButton(isIncrease: Bool) -> some View {
        let sign: Double = isIncrease ? 1.0 : -1.0
        Text("\(isIncrease ? "+" : "-")\(stepLabel)")
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .foregroundStyle(isIncrease ? Color(red: 0.99, green: 0.29, blue: 0.05) : .white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(isIncrease ? Color(red: 0.99, green: 0.29, blue: 0.05).opacity(0.22) : Color.white.opacity(0.14))
            .clipShape(Capsule())
            .highPriorityGesture(
                DragGesture(minimumDistance: 0)
                    .updating($isWatchHoldDown) { _, state, _ in
                        state = true
                    }
                    .onChanged { value in
                        if holdTask == nil {
                            activeHoldSign = sign
                            holdDidAutoRepeat = false
                            holdTask = Task { @MainActor in
                                try? await Task.sleep(nanoseconds: 220_000_000)
                                guard !Task.isCancelled else { return }
                                withAnimation(.spring(duration: 0.2)) {
                                    showPrecisionPicker = true
                                }
                                try? await Task.sleep(nanoseconds: 340_000_000)
                                guard !Task.isCancelled else { return }
                                while !Task.isCancelled {
                                    holdDidAutoRepeat = true
                                    controller.adjustRecentSetWeight(deltaKg: sign * selectedStepKg)
                                    try? await Task.sleep(nanoseconds: 130_000_000)
                                }
                            }
                        } else if showPrecisionPicker {
                            let dx = value.translation.width
                            if dx < -16 {
                                selectedStepKg = 0.5
                            } else if dx > 16 {
                                selectedStepKg = 2.0
                            } else if abs(dx) > 6 {
                                selectedStepKg = 1.0
                            }
                        }
                    }
                    .onEnded { _ in
                        if !holdDidAutoRepeat {
                            controller.adjustRecentSetWeight(deltaKg: sign * selectedStepKg)
                        }
                        holdTask?.cancel()
                        holdTask = nil
                        activeHoldSign = 0.0
                    }
            )
            .onChange(of: isWatchHoldDown) { _, down in
                if !down && holdTask != nil {
                    holdTask?.cancel()
                    holdTask = nil
                    activeHoldSign = 0.0
                }
            }
            .onDisappear {
                holdTask?.cancel()
                holdTask = nil
            }
    }

    private func formatKg(_ kg: Double) -> String {
        if kg.truncatingRemainder(dividingBy: 1.0) == 0 {
            return "\(Int(kg)) kg"
        }
        return String(format: "%.1f kg", kg)
    }
}

#Preview("Watch - Completo (Treino + Hoje)") {
    TabView {
        NavigationStack {
            WatchWorkoutView(controller: .shared)
                .navigationTitle("Forma")
        }
        NavigationStack {
            WatchWaterAndMacrosView(controller: .shared)
                .navigationTitle("Hoje")
        }
    }
}

