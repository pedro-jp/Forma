//
//  FormaLiveActivity.swift
//  FormaWidgets
//
//  Created by João Pedro on 10/29/26.
//

import ActivityKit
import WidgetKit
import SwiftUI

public struct FormaLiveActivity: Widget {
    public init() {}
    
    private let accentColor = Color(red: 0.32, green: 0.80, blue: 0.55)
    
    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: CalorieActivityAttributes.self) { context in
            // MARK: - Banner na Tela Bloqueada
            LockScreenLiveActivityView(state: context.state, userName: context.attributes.userName)
                .activityBackgroundTint(Color.black.opacity(0.88))
                .activitySystemActionForegroundColor(accentColor)
        } dynamicIsland: { context in
            // MARK: - Ilha Dinâmica
            DynamicIsland {
                // Expanded Leading
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: "flame.fill")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(accentColor)
                        
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Consumidas")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.secondary)
                            Text("\(Int(context.state.caloriesConsumed)) kcal")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.leading, 6)
                }
                
                // Expanded Trailing
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("Meta: \(Int(context.state.caloriesTarget))")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.secondary)
                        
                        Text(context.state.remainingCalories > 0 ? "-\(Int(context.state.remainingCalories)) kcal" : "Meta Atingida!")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundColor(context.state.remainingCalories > 0 ? .white : accentColor)
                    }
                    .padding(.trailing, 6)
                }
                
                // Expanded Center
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 3) {
                        ProgressView(value: context.state.progressRatio)
                            .tint(accentColor)
                            .frame(maxWidth: 160)
                        
                        Text("\(Int(context.state.progressRatio * 100))% do dia")
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                            .foregroundColor(accentColor)
                    }
                }
                
                // Expanded Bottom
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        // Linha de Macros Limpa
                        HStack(spacing: 8) {
                            IslandMacroPill(
                                label: "Prot",
                                value: "\(Int(context.state.proteinConsumed))/\(Int(context.state.proteinTarget))g",
                                color: Color(red: 0.93, green: 0.36, blue: 0.42)
                            )
                            IslandMacroPill(
                                label: "Carb",
                                value: "\(Int(context.state.carbsConsumed))/\(Int(context.state.carbsTarget))g",
                                color: Color(red: 0.24, green: 0.56, blue: 0.96)
                            )
                            IslandMacroPill(
                                label: "Gord",
                                value: "\(Int(context.state.fatConsumed))/\(Int(context.state.fatTarget))g",
                                color: Color(red: 0.96, green: 0.65, blue: 0.14)
                            )
                        }
                        
                        // Último alimento registrado
                        if let lastFood = context.state.lastFoodName {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(accentColor)
                                Text(lastFood)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.white.opacity(0.85))
                                    .lineLimit(1)
                                if let cals = context.state.lastFoodCalories {
                                    Text("(+\(Int(cals)) kcal)")
                                        .font(.system(size: 10, weight: .semibold, design: .rounded))
                                        .foregroundColor(accentColor)
                                }
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.06))
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                HStack(spacing: 3) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(accentColor)
                    Text("\(Int(context.state.caloriesConsumed))")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }
            } compactTrailing: {
                HStack(spacing: 2) {
                    Text(context.state.remainingCalories > 0 ? "-\(Int(context.state.remainingCalories))" : "✓")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(context.state.remainingCalories > 0 ? .white : accentColor)
                    Text("kcal")
                        .font(.system(size: 9))
                        .foregroundColor(.secondary)
                }
            } minimal: {
                Image(systemName: "flame.fill")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(accentColor)
            }
        }
    }
}

// MARK: - Componente de Pílula de Macro na Ilha

struct IslandMacroPill: View {
    let label: String
    let value: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 5, height: 5)
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(size: 10, weight: .bold, design: .rounded))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.06))
        .clipShape(Capsule())
    }
}

// MARK: - Banner de Tela Bloqueada para Live Activity

struct LockScreenLiveActivityView: View {
    let state: CalorieActivityAttributes.ContentState
    let userName: String
    
    private let accentColor = Color(red: 0.32, green: 0.80, blue: 0.55)
    
    var body: some View {
        VStack(spacing: 12) {
            // Header do Banner
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "flame.fill")
                        .foregroundColor(accentColor)
                    Text("Forma")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }
                
                Spacer()
                
                Text(state.remainingCalories > 0 ? "Faltam \(Int(state.remainingCalories)) kcal" : "Meta Atingida!")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(state.remainingCalories > 0 ? .white : accentColor)
            }
            
            // Números Centrais e Barra de Progresso Fina
            VStack(spacing: 6) {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(Int(state.caloriesConsumed))")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                    Text("de \(Int(state.caloriesTarget)) kcal consumidas")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("\(Int(state.progressRatio * 100))%")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundColor(accentColor)
                }
                
                ProgressView(value: state.progressRatio)
                    .tint(accentColor)
            }
            
            // Macros em Destaque
            HStack(spacing: 8) {
                LockScreenMacroPill(name: "Proteína", current: state.proteinConsumed, target: state.proteinTarget, color: Color(red: 0.93, green: 0.36, blue: 0.42))
                LockScreenMacroPill(name: "Carboidratos", current: state.carbsConsumed, target: state.carbsTarget, color: Color(red: 0.24, green: 0.56, blue: 0.96))
                LockScreenMacroPill(name: "Gorduras", current: state.fatConsumed, target: state.fatTarget, color: Color(red: 0.96, green: 0.65, blue: 0.14))
            }
        }
        .padding(16)
        .background(Color.black.opacity(0.88))
    }
}

struct LockScreenMacroPill: View {
    let name: String
    let current: Double
    let target: Double
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name)
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(.secondary)
            HStack(spacing: 2) {
                Text("\(Int(current))")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                Text("/\(Int(target))g")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}
