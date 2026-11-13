//
//  FormaWatchApp.swift
//  FormaWatch
//
//  App companheiro para Apple Watch (watchOS 10+) do Forma:
//  - Aba 1: Treino Ao Vivo (Concluir Série, Ajustar Carga da Série que Passou -2kg/+2kg, Cronômetro de Descanso com vibração)
//  - Aba 2: Resumo Diário (Calorias Restantes, Macros P/C/G e Registro Rápido de +250ml / +500ml de Água)
//

import SwiftUI

#if os(watchOS)
@main
#endif
public struct FormaWatchApp: App {
    @StateObject private var controller = WatchSessionController.shared

    public init() {}

    public var body: some Scene {
        WindowGroup {
            TabView {
                NavigationStack {
                    WatchWorkoutView(controller: controller)
                        .navigationTitle("Forma")
                }

                NavigationStack {
                    WatchWaterAndMacrosView(controller: controller)
                        .navigationTitle("Hoje")
                }
            }
            .onAppear {
                controller.requestSync()
            }
        }
    }
}
