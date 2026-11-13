//
//  FormaWatchComplications.swift
//  FormaWatch
//
//  Complicações de mostrador do Apple Watch (WidgetKit) para saldo de calorias, água e treino ativo.
//

import SwiftUI
import WidgetKit

public struct FormaWatchComplicationEntry: TimelineEntry {
    public let date: Date
    public let remainingCalories: Int
    public let caloriesProgress: Double
    public let waterProgress: Double
}

public struct FormaWatchComplicationProvider: TimelineProvider {
    public init() {}

    public func placeholder(in context: Context) -> FormaWatchComplicationEntry {
        FormaWatchComplicationEntry(date: Date(), remainingCalories: 1250, caloriesProgress: 0.5, waterProgress: 0.6)
    }

    public func getSnapshot(in context: Context, completion: @escaping (FormaWatchComplicationEntry) -> Void) {
        completion(placeholder(in: context))
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<FormaWatchComplicationEntry>) -> Void) {
        let entry = placeholder(in: context)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }
}

public struct FormaWatchComplicationView: View {
    var entry: FormaWatchComplicationEntry

    public var body: some View {
        Gauge(value: entry.caloriesProgress) {
            Image(systemName: "flame.fill")
                .foregroundStyle(Color(red: 0.99, green: 0.29, blue: 0.05))
        } currentValueLabel: {
            Text("\(entry.remainingCalories)")
                .font(.system(size: 11, weight: .bold, design: .rounded))
        }
        .gaugeStyle(.accessoryCircular)
        .tint(Color(red: 0.99, green: 0.29, blue: 0.05))
    }
}
