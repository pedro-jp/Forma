//
//  AddWaterWidgetIntent.swift
//  Forma
//
//  Created by João Pedro on 11/01/26.
//

import AppIntents
import WidgetKit

public struct AddWaterWidgetIntent: AppIntent {
    public static var title: LocalizedStringResource = "Adicionar Água"
    public static var description: IntentDescription = IntentDescription("Registra ingestão de água rapidamente pelo Widget")
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    @Parameter(title: "Quantidade (ml)", default: 250)
    public var milliliters: Int
    
    public init() {
        self.milliliters = 250
    }
    
    public init(milliliters: Int) {
        self.milliliters = milliliters
    }
    
    @MainActor
    public func perform() async throws -> some IntentResult {
        WidgetDataManager.shared.addWater(milliliters: milliliters)
        FormaDataStore.shared.syncTodayData()
        return .result()
    }
}
