//
//  WorkoutLiveActivity.swift
//  FormaWidgets
//
//  Created by João Pedro on 10/29/26.
//

import ActivityKit
import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Estado Unificado e Reativo em Tempo Real para a Ilha Dinâmica e Tela Bloqueada
struct ResolvedWorkoutLiveState {
    let title: String
    let exerciseName: String
    let currentSetNumber: Int
    let totalSetsForExercise: Int
    let completedSetsTotal: Int
    let totalSetsTotal: Int
    let caloriesBurned: Double
    let workoutStartDate: Date
    let isResting: Bool
    let restStartDate: Date?
    let restEndDate: Date?
    let restDurationSeconds: Int
    let isAllCompleted: Bool
    
    let currentSetWeightKg: Double
    let lastCompletedSetNumber: Int
    let lastCompletedSetWeightKg: Double
    let lastCompletedExerciseName: String
    let weightStepKg: Double
    
    var stepFormatted: String {
        if weightStepKg.truncatingRemainder(dividingBy: 1.0) == 0 {
            return "\(Int(weightStepKg))"
        }
        return String(format: "%.1f", weightStepKg)
    }
    
    var overallProgress: Double {
        guard totalSetsTotal > 0 else { return 0 }
        return min(1.0, max(0.0, Double(completedSetsTotal) / Double(totalSetsTotal)))
    }
    
    /// Número da série exibida no controle de carga:
    /// - Durante o descanso, exibe a série que acabou de passar (`lastCompletedSetNumber`)
    /// - Durante a execução, exibe a série atual em andamento (`currentSetNumber`)
    var displayedAdjustableSetNumber: Int {
        if isResting && lastCompletedSetNumber > 0 {
            return lastCompletedSetNumber
        }
        return max(1, currentSetNumber)
    }
    
    /// Peso exibido para ajuste (série que passou durante o descanso, ou série atual durante a execução)
    var displayedAdjustableWeightKg: Double {
        if isResting && lastCompletedSetNumber > 0 {
            return lastCompletedSetWeightKg
        }
        return currentSetWeightKg
    }
    
    var displayedAdjustableLabel: String {
        let formatted = WorkoutExerciseItem.formatWeightKg(displayedAdjustableWeightKg)
        if isResting && lastCompletedSetNumber > 0 {
            return "Série \(lastCompletedSetNumber) feita: \(formatted)"
        }
        return "Carga Série \(displayedAdjustableSetNumber): \(formatted)"
    }
    
    init(context: ActivityViewContext<WorkoutActivityAttributes>) {
        // Prioriza o context.state entregue em tempo real pelo ActivityKit
        if !context.state.currentExerciseName.isEmpty || context.state.totalSetsTotal > 0 {
            self.title = context.state.workoutTitle
            self.exerciseName = context.state.currentExerciseName.isEmpty ? "Treino" : context.state.currentExerciseName
            self.currentSetNumber = max(1, context.state.currentSetNumber)
            self.totalSetsForExercise = max(1, context.state.totalSetsForExercise)
            self.completedSetsTotal = context.state.completedSetsTotal
            self.totalSetsTotal = context.state.totalSetsTotal
            self.caloriesBurned = context.state.caloriesBurned
            self.workoutStartDate = context.state.workoutStartDate
            self.isResting = context.state.isResting
            self.restStartDate = context.state.restStartDate
            self.restEndDate = context.state.restEndDate
            self.restDurationSeconds = context.state.restDurationSeconds
            self.isAllCompleted = context.state.isAllCompleted
            self.currentSetWeightKg = context.state.currentSetWeightKg
            self.lastCompletedSetNumber = context.state.lastCompletedSetNumber
            self.lastCompletedSetWeightKg = context.state.lastCompletedSetWeightKg > 0 ? context.state.lastCompletedSetWeightKg : context.state.currentSetWeightKg
            self.lastCompletedExerciseName = context.state.lastCompletedExerciseName.isEmpty ? self.exerciseName : context.state.lastCompletedExerciseName
            self.weightStepKg = context.state.weightStepKg > 0 ? context.state.weightStepKg : SharedWorkoutSessionManager.shared.currentWeightStepKg
        } else if let shared = SharedWorkoutSessionManager.shared.loadSession() {
            self.title = shared.title
            self.exerciseName = shared.currentExercise?.name ?? "Treino"
            let maxSets = shared.currentExercise?.sets ?? 3
            self.currentSetNumber = min(maxSets, max(1, shared.currentSetIndex + 1))
            self.totalSetsForExercise = maxSets
            self.completedSetsTotal = shared.completedSetsCount
            self.totalSetsTotal = shared.totalSetsCount
            self.caloriesBurned = shared.liveCalories
            self.workoutStartDate = shared.sessionStartDate
            self.isResting = shared.isResting
            self.restStartDate = shared.restStartDate
            self.restEndDate = shared.restEndDate
            self.restDurationSeconds = shared.restDurationSeconds
            self.isAllCompleted = shared.isAllCompleted
            
            let curWeight = shared.currentSetWeightKg
            let lastInfo = shared.lastCompletedSetInfo
            self.currentSetWeightKg = curWeight
            self.lastCompletedSetNumber = lastInfo?.setNumber ?? 0
            self.lastCompletedSetWeightKg = lastInfo?.weightKg ?? curWeight
            self.lastCompletedExerciseName = lastInfo?.exerciseName ?? self.exerciseName
            self.weightStepKg = SharedWorkoutSessionManager.shared.currentWeightStepKg
        } else {
            self.title = context.state.workoutTitle
            self.exerciseName = "Treino"
            self.currentSetNumber = 1
            self.totalSetsForExercise = 3
            self.completedSetsTotal = 0
            self.totalSetsTotal = 12
            self.caloriesBurned = 0
            self.workoutStartDate = Date()
            self.isResting = false
            self.restStartDate = nil
            self.restEndDate = nil
            self.restDurationSeconds = 90
            self.isAllCompleted = false
            self.currentSetWeightKg = 0
            self.lastCompletedSetNumber = 0
            self.lastCompletedSetWeightKg = 0
            self.lastCompletedExerciseName = "Treino"
            self.weightStepKg = 2.0
        }
    }
}

public struct WorkoutLiveActivity: Widget {
    public init() {}
    
    private let accentEmerald = Color(red: 0.32, green: 0.80, blue: 0.55)
    private let restOrange = Color(red: 1.0, green: 0.58, blue: 0.0)
    
    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: WorkoutActivityAttributes.self) { context in
            // MARK: - Banner Interativo na Tela de Bloqueio (Lock Screen)
            let state = ResolvedWorkoutLiveState(context: context)
            WorkoutLockScreenView(state: state)
                .activityBackgroundTint(Color.black.opacity(0.88))
                .activitySystemActionForegroundColor(accentEmerald)
        } dynamicIsland: { context in
            // MARK: - Ilha Dinâmica Interativa (Dynamic Island)
            let state = ResolvedWorkoutLiveState(context: context)
            
            return DynamicIsland {
                // Top Leading (Canto Superior Esquerdo da Ilha)
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 5) {
                        Image(systemName: state.isResting ? "timer" : "dumbbell.fill")
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(state.isResting ? restOrange : accentEmerald)
                        
                        Text(state.isResting ? "DESCANSO" : "TREINO")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .foregroundColor(state.isResting ? restOrange : accentEmerald)
                        
                        Text("\(state.completedSetsTotal)/\(state.totalSetsTotal)")
                            .font(.system(size: 9.5, weight: .bold, design: .rounded))
                            .foregroundColor(.white.opacity(0.85))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1.5)
                            .background(Color.white.opacity(0.14))
                            .clipShape(Capsule())
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 4)
                    .padding(.top, 2)
                }
                
                // Top Trailing (Canto Superior Direito da Ilha - Cronômetro Ativo)
                DynamicIslandExpandedRegion(.trailing) {
                    HStack(spacing: 0) {
                        Spacer(minLength: 0)
                        if state.isResting, let start = state.restStartDate, let end = state.restEndDate, end > Date() {
                            Text(timerInterval: start...end, countsDown: true)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .multilineTextAlignment(.trailing)
                                .foregroundColor(restOrange)
                        } else if state.isResting {
                            Text("0:00")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .multilineTextAlignment(.trailing)
                                .foregroundColor(restOrange)
                        } else {
                            Text(state.workoutStartDate, style: .timer)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .multilineTextAlignment(.trailing)
                                .foregroundColor(accentEmerald)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 4)
                    .padding(.top, 2)
                }
                
                // Bottom: CONTEÚDO EXPANDIDO COMPACTO COM AJUSTES FINOS DIRETOS (-1kg | -0.5 | +0.5 | +1kg)
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        // Linha 1: Exercício (Esquerda) + Status da Série / Descanso (Direita)
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(state.exerciseName.isEmpty ? state.title : state.exerciseName)
                                .font(.system(size: 13.5, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            
                            if state.isAllCompleted {
                                Text("Treino finalizado!")
                                    .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                    .foregroundColor(accentEmerald)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            } else if state.isResting {
                                Text("Próx: S\(state.currentSetNumber)/\(state.totalSetsForExercise) (\(WorkoutExerciseItem.formatWeightKg(state.currentSetWeightKg)))")
                                    .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                    .foregroundColor(restOrange.opacity(0.95))
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            } else {
                                Text("Série \(state.currentSetNumber)/\(state.totalSetsForExercise) • ~\(Int(state.caloriesBurned)) kcal")
                                    .font(.system(size: 10.5, weight: .medium, design: .rounded))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                        }
                        
                        // Linha 2: Carga da Série + Botões Diretos de Precisão (-1kg, -0.5, +0.5, +1kg)
                        HStack(spacing: 5) {
                            HStack(spacing: 4) {
                                Image(systemName: "scalemass.fill")
                                    .font(.system(size: 9.5, weight: .bold))
                                    .foregroundColor(state.isResting ? restOrange : accentEmerald)
                                
                                Text(state.isResting && state.lastCompletedSetNumber > 0 ? "S\(state.displayedAdjustableSetNumber) feita:" : "Série \(state.displayedAdjustableSetNumber):")
                                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                                    .foregroundColor(.white.opacity(0.78))
                                    .lineLimit(1)
                                
                                Text(WorkoutExerciseItem.formatWeightKg(state.displayedAdjustableWeightKg))
                                    .font(.system(size: 12, weight: .black, design: .rounded))
                                    .monospacedDigit()
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                            }
                            .fixedSize(horizontal: true, vertical: false)
                            
                            Spacer(minLength: 4)
                            
                            HStack(spacing: 4) {
                                Button(intent: DecreaseOneKgLiveIntent()) {
                                    Text("-1kg")
                                        .font(.system(size: 10, weight: .heavy, design: .rounded))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 4.5)
                                        .background(Color(white: 0.22))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(Color(white: 0.22))
                                
                                Button(intent: DecreaseHalfKgLiveIntent()) {
                                    Text("-0.5")
                                        .font(.system(size: 10, weight: .heavy, design: .rounded))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 4.5)
                                        .background(Color(white: 0.28))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(Color(white: 0.28))
                                
                                Button(intent: IncreaseHalfKgLiveIntent()) {
                                    Text("+0.5")
                                        .font(.system(size: 10, weight: .heavy, design: .rounded))
                                        .foregroundColor(.black)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 4.5)
                                        .background(accentEmerald.opacity(0.86))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(accentEmerald.opacity(0.86))
                                
                                Button(intent: IncreaseOneKgLiveIntent()) {
                                    Text("+1kg")
                                        .font(.system(size: 10, weight: .heavy, design: .rounded))
                                        .foregroundColor(.black)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 4.5)
                                        .background(accentEmerald)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(accentEmerald)
                            }
                            .fixedSize(horizontal: true, vertical: true)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.08))
                        .clipShape(Capsule())
                        
                        // Linha 2: Botões Interativos Principais
                        HStack(spacing: 8) {
                            if state.isAllCompleted {
                                Button(intent: FinishWorkoutLiveIntent()) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 13, weight: .bold))
                                        Text("Finalizar e Salvar Treino")
                                            .font(.system(size: 12.5, weight: .bold))
                                    }
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 7)
                                    .background(accentEmerald)
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(accentEmerald)
                            } else if state.isResting {
                                Button(intent: AddRestTimeLiveIntent()) {
                                    HStack(spacing: 3) {
                                        Image(systemName: "plus")
                                            .font(.system(size: 10, weight: .bold))
                                        Text("30s")
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Color(white: 0.22))
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(Color(white: 0.22))
                                
                                Button(intent: SkipRestLiveIntent()) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "play.fill")
                                            .font(.system(size: 10, weight: .bold))
                                        Text("Iniciar Série \(state.currentSetNumber)")
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 7)
                                    .background(accentEmerald)
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(accentEmerald)
                            } else {
                                Button(intent: CompleteSetLiveIntent()) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 13, weight: .bold))
                                        Text("Concluir Série (\(state.currentSetNumber)/\(state.totalSetsForExercise))")
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 7)
                                    .background(accentEmerald)
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                                .tint(accentEmerald)
                                
                                Button(intent: FinishWorkoutLiveIntent()) {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.white.opacity(0.85))
                                        .frame(width: 30, height: 30)
                                        .background(Color(white: 0.20))
                                        .clipShape(Circle())
                                }
                                .buttonStyle(.plain)
                                .tint(Color(white: 0.20))
                            }
                        }
                    }
                    .padding(.horizontal, 6)
                    .padding(.top, 2)
                    .padding(.bottom, 4)
                }
            } compactLeading: {
                Image(systemName: state.isResting ? "timer" : "dumbbell.fill")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundColor(state.isResting ? restOrange : accentEmerald)
                    .frame(width: 16, alignment: .leading)
            } compactTrailing: {
                HStack(spacing: 0) {
                    if state.isResting, let start = state.restStartDate, let end = state.restEndDate, end > Date() {
                        Text(timerInterval: start...end, countsDown: true)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                            .foregroundColor(restOrange)
                    } else if state.isResting {
                        Text("0:00")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                            .foregroundColor(restOrange)
                    } else {
                        Text(state.workoutStartDate, style: .timer)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                            .foregroundColor(accentEmerald)
                    }
                }
                .minimumScaleFactor(0.8)
                .frame(width: 42, alignment: .trailing)
            } minimal: {
                if state.isResting, let start = state.restStartDate, let end = state.restEndDate, end > Date() {
                    Text(timerInterval: start...end, countsDown: true)
                        .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundColor(restOrange)
                        .minimumScaleFactor(0.8)
                        .lineLimit(1)
                } else if state.isResting {
                    Text("0:00")
                        .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundColor(restOrange)
                        .minimumScaleFactor(0.8)
                        .lineLimit(1)
                } else {
                    Text(state.workoutStartDate, style: .timer)
                        .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundColor(accentEmerald)
                        .minimumScaleFactor(0.8)
                        .lineLimit(1)
                }
            }
        }
    }
}

// MARK: - Banner Interativo na Tela Bloqueada
struct WorkoutLockScreenView: View {
    let state: ResolvedWorkoutLiveState
    
    private let accentEmerald = Color(red: 0.32, green: 0.80, blue: 0.55)
    private let restOrange = Color(red: 1.0, green: 0.58, blue: 0.0)
    
    var body: some View {
        VStack(spacing: 10) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(state.isResting ? restOrange.opacity(0.2) : accentEmerald.opacity(0.2))
                            .frame(width: 36, height: 36)
                        Image(systemName: state.isResting ? "timer" : "dumbbell.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(state.isResting ? restOrange : accentEmerald)
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(state.title)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        
                        Text("\(state.exerciseName.isEmpty ? "Treino" : state.exerciseName) • Série \(state.currentSetNumber)/\(state.totalSetsForExercise) (\(WorkoutExerciseItem.formatWeightKg(state.currentSetWeightKg)))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                
                Spacer()
                
                // Cronômetro Regressivo ou Tempo Total
                VStack(alignment: .trailing, spacing: 2) {
                    if state.isResting, let start = state.restStartDate, let end = state.restEndDate, end > Date() {
                        Text(timerInterval: start...end, countsDown: true)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(restOrange)
                        Text("Descanso")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    } else if state.isResting {
                        Text("0:00")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(restOrange)
                        Text("Descanso")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    } else {
                        Text(state.workoutStartDate, style: .timer)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(accentEmerald)
                        Text("Duração")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            // Barra de Progresso
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.white.opacity(0.12))
                        .frame(height: 6)
                    
                    Capsule()
                        .fill(state.isResting ? restOrange : accentEmerald)
                        .frame(width: max(0, geo.size.width * state.overallProgress), height: 6)
                }
            }
            .frame(height: 6)
            
            // Faixa de Ajuste Fino de Carga da Série (-1kg | -0.5 | +0.5 | +1kg) na Tela Bloqueada
            HStack(spacing: 6) {
                Image(systemName: "scalemass.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(accentEmerald)
                
                Text(state.displayedAdjustableLabel)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Spacer(minLength: 4)
                
                HStack(spacing: 4) {
                    Button(intent: DecreaseOneKgLiveIntent()) {
                        Text("-1kg")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(Color(white: 0.22))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .tint(Color(white: 0.22))
                    
                    Button(intent: DecreaseHalfKgLiveIntent()) {
                        Text("-0.5")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(Color(white: 0.28))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .tint(Color(white: 0.28))
                    
                    Button(intent: IncreaseHalfKgLiveIntent()) {
                        Text("+0.5")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .foregroundColor(.black)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(accentEmerald.opacity(0.86))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .tint(accentEmerald.opacity(0.86))
                    
                    Button(intent: IncreaseOneKgLiveIntent()) {
                        Text("+1kg")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .foregroundColor(.black)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(accentEmerald)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .tint(accentEmerald)
                }
                .fixedSize(horizontal: true, vertical: true)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Color.white.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            
            // CONTROLES INTERATIVOS NA TELA BLOQUEADA
            HStack(spacing: 10) {
                if state.isAllCompleted {
                    Button(intent: FinishWorkoutLiveIntent()) {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                            Text("Salvar Treino")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(accentEmerald)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .tint(accentEmerald)
                } else if state.isResting {
                    Button(intent: AddRestTimeLiveIntent()) {
                        Text("+30s")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(Color(white: 0.22))
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .tint(Color(white: 0.22))
                    
                    Button(intent: SkipRestLiveIntent()) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 11))
                            Text("Iniciar Série \(state.currentSetNumber)")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(accentEmerald)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .tint(accentEmerald)
                } else {
                    Button(intent: CompleteSetLiveIntent()) {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 14, weight: .bold))
                            Text("Concluir Série (\(state.currentSetNumber)/\(state.totalSetsForExercise))")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(accentEmerald)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .tint(accentEmerald)
                }
            }
        }
        .padding(16)
    }
}
