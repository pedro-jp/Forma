//
//  WidgetDataManager.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import WidgetKit

/// Modelo de dados compartilhado entre o App e os Widgets do iOS
public struct WidgetCalorieData: Codable, Sendable {
    public var caloriesConsumed: Double
    public var caloriesTarget: Double
    public var baseCaloriesTarget: Double
    public var caloriesBurned: Double
    public var activeCaloriesBurned: Double
    public var stepsCount: Int
    public var proteinConsumed: Double
    public var proteinTarget: Double
    public var carbsConsumed: Double
    public var carbsTarget: Double
    public var fatConsumed: Double
    public var fatTarget: Double
    public var waterConsumed: Int
    public var waterTarget: Int
    public var goalRaw: String
    public var lastUpdated: Date
    
    enum CodingKeys: String, CodingKey {
        case caloriesConsumed, caloriesTarget, baseCaloriesTarget, caloriesBurned
        case activeCaloriesBurned, stepsCount, proteinConsumed, proteinTarget
        case carbsConsumed, carbsTarget, fatConsumed, fatTarget, waterConsumed
        case waterTarget, goalRaw, lastUpdated
    }
    
    public init(
        caloriesConsumed: Double = 0,
        caloriesTarget: Double = 2500,
        baseCaloriesTarget: Double = 2500,
        caloriesBurned: Double = 0,
        activeCaloriesBurned: Double = 0,
        stepsCount: Int = 0,
        proteinConsumed: Double = 0,
        proteinTarget: Double = 160,
        carbsConsumed: Double = 0,
        carbsTarget: Double = 300,
        fatConsumed: Double = 0,
        fatTarget: Double = 70,
        waterConsumed: Int = 0,
        waterTarget: Int = 2500,
        goalRaw: String = "gain",
        lastUpdated: Date = Date()
    ) {
        self.caloriesConsumed = caloriesConsumed
        self.caloriesTarget = caloriesTarget
        self.baseCaloriesTarget = baseCaloriesTarget
        self.caloriesBurned = caloriesBurned
        self.activeCaloriesBurned = activeCaloriesBurned
        self.stepsCount = stepsCount
        self.proteinConsumed = proteinConsumed
        self.proteinTarget = proteinTarget
        self.carbsConsumed = carbsConsumed
        self.carbsTarget = carbsTarget
        self.fatConsumed = fatConsumed
        self.fatTarget = fatTarget
        self.waterConsumed = waterConsumed
        self.waterTarget = waterTarget
        self.goalRaw = goalRaw
        self.lastUpdated = lastUpdated
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.caloriesConsumed = try container.decodeIfPresent(Double.self, forKey: .caloriesConsumed) ?? 0
        let target = try container.decodeIfPresent(Double.self, forKey: .caloriesTarget) ?? 2500
        self.caloriesTarget = target
        self.baseCaloriesTarget = try container.decodeIfPresent(Double.self, forKey: .baseCaloriesTarget) ?? target
        self.caloriesBurned = try container.decodeIfPresent(Double.self, forKey: .caloriesBurned) ?? 0
        self.activeCaloriesBurned = try container.decodeIfPresent(Double.self, forKey: .activeCaloriesBurned) ?? 0
        self.stepsCount = try container.decodeIfPresent(Int.self, forKey: .stepsCount) ?? 0
        self.proteinConsumed = try container.decodeIfPresent(Double.self, forKey: .proteinConsumed) ?? 0
        self.proteinTarget = try container.decodeIfPresent(Double.self, forKey: .proteinTarget) ?? 160
        self.carbsConsumed = try container.decodeIfPresent(Double.self, forKey: .carbsConsumed) ?? 0
        self.carbsTarget = try container.decodeIfPresent(Double.self, forKey: .carbsTarget) ?? 300
        self.fatConsumed = try container.decodeIfPresent(Double.self, forKey: .fatConsumed) ?? 0
        self.fatTarget = try container.decodeIfPresent(Double.self, forKey: .fatTarget) ?? 70
        self.waterConsumed = try container.decodeIfPresent(Int.self, forKey: .waterConsumed) ?? 0
        self.waterTarget = try container.decodeIfPresent(Int.self, forKey: .waterTarget) ?? 2500
        self.goalRaw = try container.decodeIfPresent(String.self, forKey: .goalRaw) ?? "gain"
        self.lastUpdated = try container.decodeIfPresent(Date.self, forKey: .lastUpdated) ?? Date()
    }
    
    public var remainingCalories: Double {
        max(0, caloriesTarget - caloriesConsumed)
    }
    
    public var progressRatio: Double {
        guard caloriesTarget > 0 else { return 0 }
        return min(1.0, max(0.0, caloriesConsumed / caloriesTarget))
    }
    
    public var waterProgressRatio: Double {
        guard waterTarget > 0 else { return 0 }
        return min(1.0, max(0.0, Double(waterConsumed) / Double(waterTarget)))
    }
    
    /// Reinicia os contadores diários para um novo dia (à meia-noite), preservando apenas metas base
    public func resetForNewDay(date: Date = Date()) -> WidgetCalorieData {
        var copy = self
        copy.caloriesConsumed = 0
        copy.proteinConsumed = 0
        copy.carbsConsumed = 0
        copy.fatConsumed = 0
        copy.waterConsumed = 0
        copy.caloriesBurned = 0
        copy.activeCaloriesBurned = 0
        copy.stepsCount = 0
        copy.caloriesTarget = copy.baseCaloriesTarget > 0 ? copy.baseCaloriesTarget : copy.caloriesTarget
        copy.lastUpdated = date
        return copy
    }
}

/// Gerenciador de sincronização de dados com WidgetKit usando App Groups e File Container
public final class WidgetDataManager: Sendable {
    public static let shared = WidgetDataManager()
    public static let appGroupId = "group.jota.Forma"
    public static let storageKey = "widget_calorie_data"
    public static let jsonFileName = "widget_calorie_data.json"
    public static let pendingWaterKey = "pending_water_ml"
    public static let lastWaterTapEpochMsKey = "last_widget_water_tap_epoch_ms"
    public static let waterTapLockFileName = "widget_water_tap.lock"
    private static let duplicateTapCooldownMs: Int64 = 1200
    
    private init() {}
    
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: Self.appGroupId)
    }
    
    private var sharedFileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId)?
            .appendingPathComponent(Self.jsonFileName)
    }
    
    private var waterTapLockFileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId)?
            .appendingPathComponent(Self.waterTapLockFileName)
    }
    
    /// Verifica e grava atomicamente o timestamp do toque no App Group para evitar disparo duplo
    /// (ex: Continuity Widget no Mac disparando a Intent duas vezes seguidas em < 1.2s).
    private func shouldAcceptWaterTap(nowMs: Int64) -> Bool {
        var lastTapMs: Int64 = 0
        if let shared = sharedDefaults {
            let stored = Int64(shared.double(forKey: Self.lastWaterTapEpochMsKey))
            if stored > lastTapMs { lastTapMs = stored }
        }
        if let lockURL = waterTapLockFileURL,
           let content = try? String(contentsOf: lockURL, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines),
           let fileMs = Int64(content),
           fileMs > lastTapMs {
            lastTapMs = fileMs
        }
        
        if lastTapMs > 0 && (nowMs - lastTapMs) >= 0 && (nowMs - lastTapMs) < Self.duplicateTapCooldownMs {
            return false
        }
        
        if let shared = sharedDefaults {
            shared.set(Double(nowMs), forKey: Self.lastWaterTapEpochMsKey)
            shared.synchronize()
        }
        if let lockURL = waterTapLockFileURL {
            try? "\(nowMs)".data(using: .utf8)?.write(to: lockURL, options: .atomic)
        }
        return true
    }
    
    public func addWater(milliliters: Int = 250) {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000.0)
        guard shouldAcceptWaterTap(nowMs: nowMs) else {
            return
        }
        
        var current = loadWidgetData()
        current.waterConsumed += milliliters
        current.lastUpdated = Date()
        saveWidgetData(current)
        
        // Registra quantidade pendente para salvar no SwiftData quando o app abrir
        if let shared = sharedDefaults {
            let pending = shared.integer(forKey: Self.pendingWaterKey)
            shared.set(pending + milliliters, forKey: Self.pendingWaterKey)
            shared.synchronize()
        }
    }
    
    public func drainPendingWater() -> Int {
        guard let shared = sharedDefaults else { return 0 }
        let pending = shared.integer(forKey: Self.pendingWaterKey)
        if pending > 0 {
            shared.set(0, forKey: Self.pendingWaterKey)
            shared.synchronize()
        }
        return pending
    }
    
    public func saveWidgetData(_ data: WidgetCalorieData) {
        guard let encoded = try? JSONEncoder().encode(data) else { return }
        
        // 1. Grava no UserDefaults do App Group
        if let shared = sharedDefaults {
            shared.set(encoded, forKey: Self.storageKey)
            shared.synchronize()
        }
        
        // 2. Grava no arquivo JSON do Container compartilhado (redundância segura e protegida em repouso)
        if let fileURL = sharedFileURL {
            try? encoded.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
        }
        
        // 3. Fallback no sandbox local
        UserDefaults.standard.set(encoded, forKey: Self.storageKey)
        
        // 4. Notifica o WidgetKit para recarregar imediatamente todas as timelines
        WidgetCenter.shared.reloadAllTimelines()
    }

    public func clearAllUserData() {
        if let shared = sharedDefaults {
            shared.removeObject(forKey: Self.storageKey)
            shared.removeObject(forKey: Self.pendingWaterKey)
            shared.synchronize()
        }
        if let fileURL = sharedFileURL {
            try? FileManager.default.removeItem(at: fileURL)
        }
        UserDefaults.standard.removeObject(forKey: Self.storageKey)
        WidgetCenter.shared.reloadAllTimelines()
    }
    
    public func loadRawWidgetData() -> WidgetCalorieData {
        let decoder = JSONDecoder()
        
        // 1. Tenta recuperar do App Group UserDefaults
        if let shared = sharedDefaults,
           let data = shared.data(forKey: Self.storageKey),
           let decoded = try? decoder.decode(WidgetCalorieData.self, from: data) {
            return decoded
        }
        
        // 2. Tenta recuperar do arquivo JSON compartilhado no App Group
        if let fileURL = sharedFileURL,
           FileManager.default.fileExists(atPath: fileURL.path),
           let fileData = try? Data(contentsOf: fileURL),
           let decoded = try? decoder.decode(WidgetCalorieData.self, from: fileData) {
            return decoded
        }
        
        // 3. Fallback para UserDefaults standard
        if let standardData = UserDefaults.standard.data(forKey: Self.storageKey),
           let decoded = try? decoder.decode(WidgetCalorieData.self, from: standardData) {
            return decoded
        }
        
        return WidgetCalorieData()
    }
    
    public func loadWidgetData(for date: Date = Date()) -> WidgetCalorieData {
        var data = loadRawWidgetData()
        let calendar = Calendar.current
        // Se a data do snapshot for de um dia diferente (passou da meia-noite), reinicia para o novo dia
        if !calendar.isDate(data.lastUpdated, inSameDayAs: date) {
            data = data.resetForNewDay(date: date)
            saveWidgetData(data)
        }
        return data
    }
}
