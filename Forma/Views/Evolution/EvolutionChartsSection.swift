//
//  EvolutionChartsSection.swift
//  Forma
//
//  Gráficos nativos e interativos em Swift Charts para Evolução Corporal, Balanço Energético e Adesão Semanal.
//

import SwiftUI
import Charts

struct DayCalorieSummary: Identifiable {
    let id = UUID()
    let date: Date
    let dayName: String
    let consumed: Double
    let tmb: Double
    let activeBurned: Double
    let proteinPercent: Double
    let waterPercent: Double

    var totalBurned: Double { tmb + activeBurned }
    var netBalance: Double { consumed - totalBurned }
}

// MARK: - 1. Gráfico Interativo de Tendência de Peso (LineMark + AreaMark + RuleMark Meta)

struct WeightTrendChartCard: View {
    let weightEntries: [WeightEntry]
    let currentWeight: Double
    let targetWeight: Double
    let onLogWeightTap: () -> Void

    @State private var selectedDate: Date? = nil

    private struct WeightPoint: Identifiable {
        let id = UUID()
        let date: Date
        let weight: Double
    }

    private var chronologicalPoints: [WeightPoint] {
        let sorted = weightEntries
            .sorted { $0.date < $1.date }
            .suffix(16)
            .map { WeightPoint(date: $0.date, weight: $0.weight) }

        if !sorted.isEmpty {
            return sorted
        }
        return [WeightPoint(date: Date(), weight: currentWeight)]
    }

    private var yDomain: ClosedRange<Double> {
        let allValues = chronologicalPoints.map(\.weight) + [targetWeight]
        let minVal = (allValues.min() ?? 60.0) - 2.5
        let maxVal = (allValues.max() ?? 85.0) + 2.5
        return max(25.0, minVal)...max(minVal + 5.0, maxVal)
    }

    private var selectedPoint: WeightPoint? {
        guard let selectedDate else { return nil }
        return chronologicalPoints.min(by: {
            abs($0.date.timeIntervalSince(selectedDate)) < abs($1.date.timeIntervalSince(selectedDate))
        })
    }

    var body: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Curva de Peso Corporal")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        if let point = selectedPoint {
                            Text("\(point.date.formatted(.dateTime.day().month(.abbreviated).locale(Locale(identifier: "pt_BR")))) • \(String(format: "%.1f kg", point.weight))")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        } else {
                            Text("Toque ou arraste no gráfico para inspecionar")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    FormaBadge(
                        text: String(format: "Alvo %.1f kg", targetWeight),
                        icon: "flag.fill",
                        tint: FormaTheme.Colors.energyGreen
                    )
                }

                Chart {
                    RuleMark(y: .value("Meta", targetWeight))
                        .foregroundStyle(FormaTheme.Colors.energyGreen.opacity(0.75))
                        .lineStyle(StrokeStyle(lineWidth: 1.3, dash: [5, 4]))
                        .annotation(position: .top, alignment: .trailing) {
                            Text("Meta")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(FormaTheme.Colors.energyGreen)
                        }

                    ForEach(chronologicalPoints) { pt in
                        AreaMark(
                            x: .value("Data", pt.date),
                            yStart: .value("Base", yDomain.lowerBound),
                            yEnd: .value("Peso", pt.weight)
                        )
                        .interpolationMethod(.catmullRom)
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    FormaTheme.Colors.brand.opacity(0.28),
                                    FormaTheme.Colors.brand.opacity(0.02)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                        LineMark(
                            x: .value("Data", pt.date),
                            y: .value("Peso", pt.weight)
                        )
                        .interpolationMethod(.catmullRom)
                        .lineStyle(StrokeStyle(lineWidth: 2.6, lineCap: .round))
                        .foregroundStyle(FormaTheme.Colors.brand)

                        PointMark(
                            x: .value("Data", pt.date),
                            y: .value("Peso", pt.weight)
                        )
                        .symbolSize(selectedPoint?.id == pt.id ? 70 : 32)
                        .foregroundStyle(FormaTheme.Colors.brand)
                    }

                    if let selected = selectedPoint {
                        RuleMark(x: .value("Selecionado", selected.date))
                            .foregroundStyle(FormaTheme.Colors.brand.opacity(0.35))
                            .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                    }
                }
                .chartYScale(domain: yDomain)
                .chartXSelection(value: $selectedDate)
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(Color.primary.opacity(0.06))
                        AxisValueLabel {
                            if let kg = value.as(Double.self) {
                                Text("\(Int(kg))kg")
                                    .font(.system(size: 10))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                        AxisValueLabel(format: .dateTime.day().month(.abbreviated).locale(Locale(identifier: "pt_BR")))
                            .font(.system(size: 10))
                    }
                }
                .frame(height: 175)

                if weightEntries.count < 2 {
                    HStack {
                        Text("Registre novas pesagens para visualizar sua tendência semanal.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button("+ Pesagem", action: onLogWeightTap)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                }
            }
        }
    }
}

// MARK: - 2. Gráfico Interativo de Balanço Calórico Semanal (Consumidas vs Basal + Ativas)

struct WeeklyCalorieChartCard: View {
    let summaries: [DayCalorieSummary]
    let dailyCalorieTarget: Double

    @State private var selectedDay: String? = nil

    private struct BarSegment: Identifiable {
        let id = UUID()
        let dayName: String
        let group: String
        let category: String
        let kcal: Double
    }

    private var segments: [BarSegment] {
        var result: [BarSegment] = []
        for day in summaries {
            result.append(BarSegment(dayName: day.dayName, group: "Entrada", category: "Consumidas", kcal: day.consumed))
            result.append(BarSegment(dayName: day.dayName, group: "Saída", category: "Repouso", kcal: day.tmb))
            result.append(BarSegment(dayName: day.dayName, group: "Saída", category: "Treino/Ativas", kcal: day.activeBurned))
        }
        return result
    }

    private var weeklyNetBalance: Double {
        summaries.reduce(0.0) { $0 + $1.netBalance }
    }

    private var totalConsumed: Double {
        summaries.reduce(0.0) { $0 + $1.consumed }
    }

    private var totalBurned: Double {
        summaries.reduce(0.0) { $0 + $1.totalBurned }
    }

    private var selectedSummary: DayCalorieSummary? {
        guard let selectedDay else { return nil }
        return summaries.first { $0.dayName == selectedDay }
    }

    var body: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Balanço Semanal de Calorias")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        if let sel = selectedSummary {
                            Text("\(sel.dayName): Entrou \(Int(sel.consumed)) • Saiu \(Int(sel.totalBurned)) kcal (\(sel.netBalance >= 0 ? "+" : "")\(Int(sel.netBalance)))")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        } else {
                            Text("Comparativo dos últimos 7 dias (Entrou vs Saiu)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    FormaBadge(
                        text: "\(weeklyNetBalance >= 0 ? "+" : "")\(Int(weeklyNetBalance.rounded())) kcal",
                        tint: weeklyNetBalance >= 0 ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.warning
                    )
                }

                Chart {
                    if dailyCalorieTarget > 0 {
                        RuleMark(y: .value("Meta Diária", dailyCalorieTarget))
                            .foregroundStyle(FormaTheme.Colors.brand.opacity(0.55))
                            .lineStyle(StrokeStyle(lineWidth: 1.1, dash: [4, 4]))
                    }

                    ForEach(segments) { seg in
                        BarMark(
                            x: .value("Dia", seg.dayName),
                            y: .value("Calorias", seg.kcal)
                        )
                        .position(by: .value("Fluxo", seg.group))
                        .foregroundStyle(by: .value("Categoria", seg.category))
                        .cornerRadius(4)
                        .opacity(selectedDay == nil || selectedDay == seg.dayName ? 1.0 : 0.45)
                    }
                }
                .chartForegroundStyleScale([
                    "Consumidas": FormaTheme.Colors.brand,
                    "Repouso": FormaTheme.Colors.bodyWeight.opacity(0.72),
                    "Treino/Ativas": FormaTheme.Colors.energyGreen
                ])
                .chartLegend(position: .bottom, alignment: .leading, spacing: 10)
                .chartXSelection(value: $selectedDay)
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(Color.primary.opacity(0.06))
                        AxisValueLabel {
                            if let v = value.as(Double.self) {
                                Text("\(Int(v))")
                                    .font(.system(size: 9))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .frame(height: 190)

                Divider().background(Color.primary.opacity(0.06))

                HStack {
                    HStack(spacing: 4) {
                        Text("Entrou (7d):")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("\(Int(totalConsumed.rounded())) kcal")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }

                    Spacer()

                    HStack(spacing: 4) {
                        Text("Saiu (7d):")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("\(Int(totalBurned.rounded())) kcal")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                    }
                }
            }
        }
    }
}

// MARK: - 3. Gráfico Semanal de Consistência de Proteína & Hidratação

struct WeeklyHabitsChartCard: View {
    let summaries: [DayCalorieSummary]

    private struct HabitMetric: Identifiable {
        let id = UUID()
        let dayName: String
        let type: String
        let percent: Double
    }

    private var metrics: [HabitMetric] {
        summaries.flatMap { day in
            [
                HabitMetric(dayName: day.dayName, type: "Proteína", percent: min(130, day.proteinPercent)),
                HabitMetric(dayName: day.dayName, type: "Água", percent: min(130, day.waterPercent))
            ]
        }
    }

    private var avgProteinAdherence: Int {
        guard !summaries.isEmpty else { return 0 }
        let avg = summaries.reduce(0.0) { $0 + min(100, $1.proteinPercent) } / Double(summaries.count)
        return Int(avg.rounded())
    }

    private var avgWaterAdherence: Int {
        guard !summaries.isEmpty else { return 0 }
        let avg = summaries.reduce(0.0) { $0 + min(100, $1.waterPercent) } / Double(summaries.count)
        return Int(avg.rounded())
    }

    var body: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Meta de Proteína & Água")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Text("Percentual diário da meta nos últimos 7 dias")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    FormaBadge(text: "P \(avgProteinAdherence)% • Água \(avgWaterAdherence)%", tint: FormaTheme.Colors.water)
                }

                Chart {
                    RuleMark(y: .value("Meta 100%", 100))
                        .foregroundStyle(Color.secondary.opacity(0.4))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))

                    ForEach(metrics) { item in
                        BarMark(
                            x: .value("Dia", item.dayName),
                            y: .value("% Meta", item.percent)
                        )
                        .position(by: .value("Hábito", item.type))
                        .foregroundStyle(by: .value("Hábito", item.type))
                        .cornerRadius(4)
                    }
                }
                .chartForegroundStyleScale([
                    "Proteína": FormaTheme.Colors.protein,
                    "Água": FormaTheme.Colors.water
                ])
                .chartYScale(domain: 0...130)
                .chartLegend(position: .bottom, alignment: .leading, spacing: 10)
                .chartYAxis {
                    AxisMarks(position: .leading, values: [0, 50, 100]) { value in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(Color.primary.opacity(0.06))
                        AxisValueLabel {
                            if let pct = value.as(Int.self) {
                                Text("\(pct)%")
                                    .font(.system(size: 9))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .frame(height: 155)
            }
        }
    }
}
