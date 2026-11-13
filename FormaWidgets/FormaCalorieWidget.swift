//
//  FormaCalorieWidget.swift
//  FormaWidgets
//
//  Created by João Pedro on 10/29/26.
//

import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Timeline Provider

struct CalorieTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> CalorieEntry {
        CalorieEntry(date: Date(), data: WidgetCalorieData(
            caloriesConsumed: 1850,
            caloriesTarget: 2800,
            caloriesBurned: 450,
            proteinConsumed: 140,
            proteinTarget: 180,
            carbsConsumed: 220,
            carbsTarget: 350,
            fatConsumed: 55,
            fatTarget: 75,
            goalRaw: "gain"
        ))
    }
    
    func getSnapshot(in context: Context, completion: @escaping (CalorieEntry) -> Void) {
        let data = WidgetDataManager.shared.loadWidgetData(for: Date())
        let entry = CalorieEntry(date: Date(), data: data)
        completion(entry)
    }
    
    func getTimeline(in context: Context, completion: @escaping (Timeline<CalorieEntry>) -> Void) {
        let now = Date()
        let calendar = Calendar.current
        let currentData = WidgetDataManager.shared.loadWidgetData(for: now)
        let currentEntry = CalorieEntry(date: now, data: currentData)
        
        // Calcula a virada da meia-noite (00:00:00 do próximo dia) para zerar automaticamente mesmo sem abrir o app
        let tomorrowMidnight = calendar.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime
        ) ?? calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now))!
        
        // Entrada agendada exatamente para a meia-noite com dados zerados
        let resetData = currentData.resetForNewDay(date: tomorrowMidnight)
        let midnightEntry = CalorieEntry(date: tomorrowMidnight, data: resetData)
        
        // Recarrega no próximo intervalo de 15 minutos ou na meia-noite
        let next15Min = calendar.date(byAdding: .minute, value: 15, to: now) ?? tomorrowMidnight
        let reloadPolicyDate = min(next15Min, tomorrowMidnight)
        
        let timeline = Timeline(entries: [currentEntry, midnightEntry], policy: .after(reloadPolicyDate))
        completion(timeline)
    }
}

// MARK: - Timeline Entry

struct CalorieEntry: TimelineEntry {
    let date: Date
    let data: WidgetCalorieData
}

// MARK: - Widget Definition

struct FormaCalorieWidget: Widget {
    let kind: String = "FormaCalorieWidget"
    
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CalorieTimelineProvider()) { entry in
            FormaCalorieWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color(.systemBackground)
                }
        }
        .configurationDisplayName("Meta de Calorias")
        .description("Acompanhe suas calorias consumidas, metas e adicione refeições rapidamente.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .accessoryCircular,
            .accessoryRectangular,
            .accessoryInline
        ])
    }
}

// MARK: - Main Widget View Switcher

struct FormaCalorieWidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: CalorieEntry
    
    var body: some View {
        switch family {
        case .systemSmall:
            SmallCalorieWidgetView(entry: entry)
        case .systemMedium:
            MediumCalorieWidgetView(entry: entry)
        case .accessoryCircular:
            LockScreenCircularWidgetView(entry: entry)
        case .accessoryRectangular:
            LockScreenRectangularWidgetView(entry: entry)
        case .accessoryInline:
            LockScreenInlineWidgetView(entry: entry)
        default:
            SmallCalorieWidgetView(entry: entry)
        }
    }
}

// MARK: - Small Widget (Estilo Apple / Airbnb Clean)

struct SmallCalorieWidgetView: View {
    let entry: CalorieEntry
    
    private var progress: Double {
        entry.data.progressRatio
    }
    
    private let accentColor = Color(red: 0.32, green: 0.80, blue: 0.55)
    private let waterColor = Color.cyan
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header sutil
            HStack {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(accentColor)
                    Text("Calorias")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Text("\(Int(progress * 100))%")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundColor(accentColor)
            }
            
            // Ring Central e Números
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .stroke(Color.primary.opacity(0.08), lineWidth: 6)
                    
                    Circle()
                        .trim(from: 0, to: progress)
                        .stroke(
                            accentColor,
                            style: StrokeStyle(lineWidth: 6, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                    
                    Image(systemName: "fork.knife")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(accentColor)
                }
                .frame(width: 42, height: 42)
                
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(Int(entry.data.caloriesConsumed))")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                        .minimumScaleFactor(0.8)
                        .lineLimit(1)
                    
                    Text("de \(Int(entry.data.caloriesTarget)) kcal")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer(minLength: 0)
            
            // Rodapé com Água e Botão Interativo +250ml
            HStack {
                HStack(spacing: 4) {
                    // Gráfico Redondo de Água Mini
                    ZStack {
                        Circle()
                            .stroke(waterColor.opacity(0.2), lineWidth: 3)
                        Circle()
                            .trim(from: 0, to: entry.data.waterProgressRatio)
                            .stroke(waterColor, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Image(systemName: "drop.fill")
                            .font(.system(size: 7, weight: .bold))
                            .foregroundColor(waterColor)
                    }
                    .frame(width: 18, height: 18)
                    
                    Text("\(entry.data.waterConsumed)ml")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
                
                Spacer()
                
                Button(intent: AddWaterWidgetIntent(milliliters: 250)) {
                    Text("+250ml")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(waterColor)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
    }
}

// MARK: - Medium Widget (338x158) Estilo Airbnb / Apple

struct MediumCalorieWidgetView: View {
    let entry: CalorieEntry
    
    private var progress: Double {
        entry.data.progressRatio
    }
    
    private var waterProgress: Double {
        entry.data.waterProgressRatio
    }
    
    private let accentColor = Color(red: 0.32, green: 0.80, blue: 0.55)
    private let waterColor = Color.cyan
    
    var body: some View {
        HStack(spacing: 14) {
            // Lado Esquerdo: Balanço de Calorias e Ações
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 5) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(accentColor)
                    Text("Balanço Diário")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    Text("\(Int(progress * 100))%")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundColor(accentColor)
                }
                
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(Int(entry.data.caloriesConsumed))")
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                    Text("/ \(Int(entry.data.caloriesTarget)) kcal")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                }
                
                // Barra de Progresso Fina
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.primary.opacity(0.08))
                            .frame(height: 5)
                        Capsule()
                            .fill(accentColor)
                            .frame(width: max(0, geo.size.width * progress), height: 5)
                    }
                }
                .frame(height: 5)
                
                Text(entry.data.remainingCalories > 0 ? "Faltam \(Int(entry.data.remainingCalories)) kcal" : "Meta atingida!")
                    .font(.system(size: 10))
                    .foregroundColor(entry.data.remainingCalories > 0 ? .secondary : accentColor)
                
                Spacer()
                
                // Botão Rápido para Adicionar Refeição (Voz / Digitação)
                Link(destination: URL(string: "forma://voice")!) {
                    HStack(spacing: 4) {
                        Image(systemName: "mic.fill")
                            .font(.system(size: 10))
                        Text("Refeição")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(accentColor)
                    .clipShape(Capsule())
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            Divider()
                .padding(.vertical, 2)
            
            // Lado Direito: Barras de Macros e Card Redondo de Água
            VStack(alignment: .leading, spacing: 6) {
                Text("Nutrientes")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.secondary)
                
                WidgetMacroRow(
                    label: "Proteínas",
                    current: entry.data.proteinConsumed,
                    target: entry.data.proteinTarget,
                    color: Color(red: 0.93, green: 0.36, blue: 0.42)
                )
                
                WidgetMacroRow(
                    label: "Carbos",
                    current: entry.data.carbsConsumed,
                    target: entry.data.carbsTarget,
                    color: Color(red: 0.24, green: 0.56, blue: 0.96)
                )
                
                WidgetMacroRow(
                    label: "Gorduras",
                    current: entry.data.fatConsumed,
                    target: entry.data.fatTarget,
                    color: Color(red: 0.96, green: 0.65, blue: 0.14)
                )
                
                Divider()
                    .padding(.vertical, 1)
                
                // Seção Água com Gráfico Redondo e Botão Interativo +250ml
                HStack(spacing: 7) {
                    // Gráfico Redondo de Água
                    ZStack {
                        Circle()
                            .stroke(waterColor.opacity(0.2), lineWidth: 3.5)
                        
                        Circle()
                            .trim(from: 0, to: waterProgress)
                            .stroke(
                                waterColor,
                                style: StrokeStyle(lineWidth: 3.5, lineCap: .round)
                            )
                            .rotationEffect(.degrees(-90))
                        
                        Image(systemName: "drop.fill")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(waterColor)
                    }
                    .frame(width: 22, height: 22)
                    
                    VStack(alignment: .leading, spacing: 1) {
                        Text("\(entry.data.waterConsumed)ml")
                            .font(.system(size: 9, weight: .bold, design: .rounded))
                            .foregroundColor(.primary)
                        Text("/\(entry.data.waterTarget)ml")
                            .font(.system(size: 8))
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer(minLength: 0)
                    
                    // Botão Interativo +250ml que executa instantaneamente
                    Button(intent: AddWaterWidgetIntent(milliliters: 250)) {
                        HStack(spacing: 2) {
                            Image(systemName: "plus")
                                .font(.system(size: 8, weight: .bold))
                            Text("250ml")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .background(waterColor)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(width: 140)
        }
        .padding(12)
    }
}

// MARK: - Linha de Macro para Widget

struct WidgetMacroRow: View {
    let label: String
    let current: Double
    let target: Double
    let color: Color
    
    private var ratio: Double {
        guard target > 0 else { return 0 }
        return min(1.0, max(0.0, current / target))
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(label)
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(Int(current))g")
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .foregroundColor(color)
            }
            
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(color.opacity(0.15))
                        .frame(height: 4)
                    Capsule()
                        .fill(color)
                        .frame(width: max(0, geo.size.width * ratio), height: 4)
                }
            }
            .frame(height: 4)
        }
    }
}

// MARK: - Lock Screen Widgets

struct LockScreenCircularWidgetView: View {
    let entry: CalorieEntry
    
    var body: some View {
        Gauge(value: entry.data.caloriesConsumed, in: 0...max(entry.data.caloriesTarget, 1)) {
            Image(systemName: "flame.fill")
        } currentValueLabel: {
            Text("\(Int(entry.data.caloriesConsumed))")
                .font(.system(size: 12, weight: .bold, design: .rounded))
        }
        .gaugeStyle(.accessoryCircular)
    }
}

struct LockScreenRectangularWidgetView: View {
    let entry: CalorieEntry
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: "flame.fill")
                Text("Forma")
                    .font(.system(size: 11, weight: .bold))
                Spacer()
                Text("\(Int(entry.data.progressRatio * 100))%")
                    .font(.system(size: 10, weight: .bold))
            }
            
            Text("\(Int(entry.data.caloriesConsumed)) / \(Int(entry.data.caloriesTarget)) kcal")
                .font(.system(size: 13, weight: .bold, design: .rounded))
            
            ProgressView(value: entry.data.progressRatio)
            
            Text("Restam \(Int(entry.data.remainingCalories)) kcal")
                .font(.system(size: 10))
                .foregroundColor(.secondary)
        }
    }
}

struct LockScreenInlineWidgetView: View {
    let entry: CalorieEntry
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "flame.fill")
            Text("\(Int(entry.data.caloriesConsumed))/\(Int(entry.data.caloriesTarget)) kcal")
        }
    }
}
