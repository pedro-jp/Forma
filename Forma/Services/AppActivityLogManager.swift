//
//  AppActivityLogManager.swift
//  Forma
//
//  Created by João Pedro on 11/01/26.
//

import Foundation
import SwiftData

/// Registro estruturado de atividade para persistência em arquivo
public struct ActivityLogRecord: Codable, Sendable {
    public var id: String
    public var timestamp: String
    public var formattedTime: String
    public var dateString: String
    public var category: String // "meal", "workout", "water", "weight"
    public var title: String
    public var isSnack: Bool?
    public var caloriesChange: Double // Positivo = consumo, Negativo = gasto
    public var proteinGrams: Double?
    public var carbsGrams: Double?
    public var fatGrams: Double?
    public var waterMilliliters: Int?
    public var weightKg: Double?
    public var details: String?
    
    public init(
        id: String = UUID().uuidString,
        timestamp: Date = Date(),
        category: String,
        title: String,
        isSnack: Bool? = nil,
        caloriesChange: Double = 0.0,
        proteinGrams: Double? = nil,
        carbsGrams: Double? = nil,
        fatGrams: Double? = nil,
        waterMilliliters: Int? = nil,
        weightKg: Double? = nil,
        details: String? = nil
    ) {
        self.id = id
        
        let isoFormatter = ISO8601DateFormatter()
        self.timestamp = isoFormatter.string(from: timestamp)
        
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm"
        self.formattedTime = timeFormatter.string(from: timestamp)
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        self.dateString = dateFormatter.string(from: timestamp)
        
        self.category = category
        self.title = title
        self.isSnack = isSnack
        self.caloriesChange = caloriesChange
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
        self.waterMilliliters = waterMilliliters
        self.weightKg = weightKg
        self.details = details
    }
}

/// Gerenciador autônomo que salva todos os eventos do usuário em um arquivo físico
/// para consumo, leitura cronológica e diagnóstico pela Inteligência Artificial.
public final class AppActivityLogManager: @unchecked Sendable {
    public static let shared = AppActivityLogManager()
    
    private let queue = DispatchQueue(label: "com.forma.activitylog", qos: .utility)
    private let fileName = "user_activity_log.json"
    
    private var fileURL: URL {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return documents.appendingPathComponent(fileName)
    }
    
    private init() {
        createFileIfNotExists()
    }
    
    private func createFileIfNotExists() {
        queue.async { [weak self] in
            guard let self = self else { return }
            if !FileManager.default.fileExists(atPath: self.fileURL.path) {
                let emptyData = "[]".data(using: .utf8) ?? Data()
                try? emptyData.write(to: self.fileURL, options: .atomic)
            }
        }
    }
    
    // MARK: - Leitura e Gravação Pública para Sincronização
    
    public func getAllRecords() -> [ActivityLogRecord] {
        queue.sync {
            self.readAllRecords()
        }
    }
    
    /// Limpa completamente o histórico de logs de atividades locais (usado no logout / troca de conta)
    public func clearAll() {
        queue.sync {
            self.writeRecords([])
        }
    }

    /// Remove registros específicos pelo ID (ex: ao excluir refeição, treino ou pesagem)
    public func removeRecords(ids: [String]) {
        guard !ids.isEmpty else { return }
        let normalizedIds = Set(ids.map { $0.lowercased() })
        queue.async { [weak self] in
            guard let self = self else { return }
            let current = self.readAllRecords()
            let filtered = current.filter { !normalizedIds.contains($0.id.lowercased()) }
            if filtered.count != current.count {
                self.writeRecords(filtered)
            }
        }
    }
    
    public func mergeRemoteRecords(_ remoteRecords: [ActivityLogRecord]) {
        guard !remoteRecords.isEmpty else { return }
        queue.async { [weak self] in
            guard let self = self else { return }
            var current = self.readAllRecords()
            let existingIds = Set(current.map { $0.id })
            
            var didAdd = false
            for rem in remoteRecords {
                if !existingIds.contains(rem.id) {
                    current.append(rem)
                    didAdd = true
                }
            }
            if didAdd {
                current.sort { $0.timestamp < $1.timestamp }
                if current.count > 1000 {
                    current = Array(current.suffix(1000))
                }
                self.writeRecords(current)
            }
        }
    }
    
    private func readAllRecords() -> [ActivityLogRecord] {
        guard let data = try? Data(contentsOf: fileURL),
              let records = try? JSONDecoder().decode([ActivityLogRecord].self, from: data) else {
            return []
        }
        return records
    }
    
    private func writeRecords(_ records: [ActivityLogRecord]) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(records) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
    
    // MARK: - Registro de Eventos
    
    public func appendRecord(_ record: ActivityLogRecord) {
        queue.async { [weak self] in
            guard let self = self else { return }
            var records = self.readAllRecords()
            records.removeAll { $0.id.caseInsensitiveCompare(record.id) == .orderedSame }
            records.append(record)
            // Mantém os últimos 1000 registros para alta performance
            if records.count > 1000 {
                records = Array(records.suffix(1000))
            }
            self.writeRecords(records)
        }
    }
    
    public func logMeal(
        id: String = UUID().uuidString,
        name: String,
        mealType: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        isSnack: Bool = false,
        date: Date = Date()
    ) {
        let record = ActivityLogRecord(
            id: id,
            timestamp: date,
            category: "meal",
            title: "\(mealType): \(name)",
            isSnack: isSnack,
            caloriesChange: calories,
            proteinGrams: protein,
            carbsGrams: carbs,
            fatGrams: fat,
            details: "Refeição registrada com macros"
        )
        appendRecord(record)
    }
    
    public func logWorkout(
        id: String = UUID().uuidString,
        title: String,
        workoutType: String,
        durationMinutes: Int,
        caloriesBurned: Double,
        date: Date = Date(),
        details: String? = nil
    ) {
        let record = ActivityLogRecord(
            id: id,
            timestamp: date,
            category: "workout",
            title: "\(workoutType): \(title)",
            caloriesChange: -abs(caloriesBurned),
            details: "Duração: \(durationMinutes) min. \(details ?? "")"
        )
        appendRecord(record)
    }
    
    public func logWater(
        id: String = UUID().uuidString,
        milliliters: Int,
        date: Date = Date()
    ) {
        let record = ActivityLogRecord(
            id: id,
            timestamp: date,
            category: "water",
            title: "Ingestão de Água",
            waterMilliliters: milliliters,
            details: "\(milliliters) ml ingeridos"
        )
        appendRecord(record)
    }
    
    public func logWeight(
        id: String = UUID().uuidString,
        weightKg: Double,
        date: Date = Date(),
        notes: String? = nil
    ) {
        let record = ActivityLogRecord(
            id: id,
            timestamp: date,
            category: "weight",
            title: "Pesagem Corporal",
            weightKg: weightKg,
            details: notes
        )
        appendRecord(record)
    }
    
    // MARK: - Sincronização Completa com o Banco de Dados
    
    /// Sincroniza e consolida todas as entradas reais do SwiftData para o arquivo de log físico
    func syncFromDatabase(
        foods: [FoodEntry],
        workouts: [WorkoutEntry],
        waters: [WaterEntry],
        weights: [WeightEntry]
    ) {
        queue.async { [weak self] in
            guard let self = self else { return }
            
            var allRecords: [ActivityLogRecord] = []
            
            for f in foods {
                let isSnack = f.mealType == .snacks
                allRecords.append(ActivityLogRecord(
                    id: f.id.uuidString,
                    timestamp: f.date,
                    category: "meal",
                    title: "\(f.mealType.rawValue): \(f.name)",
                    isSnack: isSnack,
                    caloriesChange: f.calories,
                    proteinGrams: f.protein,
                    carbsGrams: f.carbohydrates,
                    fatGrams: f.fat,
                    details: "Porção: \(Int(f.portionGrams))g"
                ))
            }
            
            for w in workouts {
                allRecords.append(ActivityLogRecord(
                    id: w.id.uuidString,
                    timestamp: w.date,
                    category: "workout",
                    title: "\(w.workoutType.rawValue): \(w.title)",
                    caloriesChange: -abs(w.caloriesBurned),
                    details: "\(w.durationMinutes) min • \(w.notes)"
                ))
            }
            
            for wt in waters {
                allRecords.append(ActivityLogRecord(
                    id: wt.id.uuidString,
                    timestamp: wt.date,
                    category: "water",
                    title: "Ingestão de Água",
                    waterMilliliters: wt.milliliters,
                    details: "\(wt.milliliters) ml"
                ))
            }
            
            for wg in weights {
                allRecords.append(ActivityLogRecord(
                    id: wg.id.uuidString,
                    timestamp: wg.date,
                    category: "weight",
                    title: "Pesagem",
                    weightKg: wg.weight,
                    details: wg.notes
                ))
            }
            
            // Ordena cronologicamente
            allRecords.sort { $0.timestamp < $1.timestamp }
            
            self.writeRecords(allRecords)
        }
    }
    
    // MARK: - Exportação para Análise da IA
    
    /// Retorna o log formatado dos últimos X dias pronto para ser injetado no prompt do Gemini
    public func exportRecentLogForAI(days: Int = 14) -> String {
        var result: [ActivityLogRecord] = []
        queue.sync {
            let records = self.readAllRecords()
            let calendar = Calendar.current
            let cutoff = calendar.date(byAdding: .day, value: -days, to: Date()) ?? Date()
            
            let isoFormatter = ISO8601DateFormatter()
            result = records.filter { record in
                guard let d = isoFormatter.date(from: record.timestamp) else { return true }
                return d >= cutoff
            }
        }
        
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted]
        if let data = try? encoder.encode(result), let str = String(data: data, encoding: .utf8) {
            return str
        }
        return "[]"
    }
}
