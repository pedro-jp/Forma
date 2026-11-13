//
//  WatchWaterAndMacrosView.swift
//  FormaWatch
//
//  Tela de resumo diário e ações rápidas no Apple Watch:
//  registro de +250ml / +500ml de água em 1 toque e visualização de calorias e macros.
//

import SwiftUI

public struct WatchWaterAndMacrosView: View {
    @ObservedObject var controller: WatchSessionController

    public init(controller: WatchSessionController = .shared) {
        self.controller = controller
    }

    private var remainingCalories: Int {
        max(0, Int((controller.caloriesTarget - controller.caloriesConsumed).rounded()))
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                // Card de Calorias e Macros
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Image(systemName: "flame.fill")
                            .foregroundStyle(Color(red: 0.99, green: 0.29, blue: 0.05))
                        Text("\(remainingCalories) kcal")
                            .font(.system(size: 16, weight: .black, design: .rounded))
                        Spacer()
                        Text("restantes")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.secondary)
                    }

                    HStack(spacing: 8) {
                        macroPill(label: "P", value: Int(controller.proteinConsumed), target: Int(controller.proteinTarget), color: .red)
                        macroPill(label: "C", value: Int(controller.carbsConsumed), target: Int(controller.carbsTarget), color: .orange)
                        macroPill(label: "G", value: Int(controller.fatConsumed), target: Int(controller.fatTarget), color: .yellow)
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                // Card de Hidratação Rápida (+250ml / +500ml)
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Image(systemName: "drop.fill")
                            .foregroundStyle(.cyan)
                        Text("\(controller.waterConsumed) / \(controller.waterTarget) ml")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                        Spacer()
                    }

                    HStack(spacing: 6) {
                        Button {
                            controller.addWater(milliliters: 250)
                        } label: {
                            Text("+250 ml")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(Color.cyan)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        Button {
                            controller.addWater(milliliters: 500)
                        } label: {
                            Text("+500 ml")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(.cyan)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(Color.cyan.opacity(0.20))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(.horizontal, 4)
        }
    }

    private func macroPill(label: String, value: Int, target: Int, color: Color) -> some View {
        VStack(spacing: 1) {
            Text(label)
                .font(.system(size: 9, weight: .black, design: .rounded))
                .foregroundStyle(color)
            Text("\(value)/\(target)g")
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .monospacedDigit()
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
        .background(color.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

#Preview("Watch - Resumo Hoje") {
    NavigationStack {
        WatchWaterAndMacrosView(controller: .shared)
            .navigationTitle("Hoje")
    }
}

