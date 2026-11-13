//
//  FormaTheme.swift
//  Forma
//
//  Tokens semânticos de cor, tipografia, raios e espaçamento do Design System FormaUI.
//

import SwiftUI

enum FormaTheme {
    enum Colors {
        static let brand = Color("AccentColor")
        static let brandSoft = Color("AccentMinor")
        static let energyGreen = Color("AccentGreen")
        static let cardSurface = Color("CardBackground")
        static let primaryText = Color("FontNormal")
        static let pageBackground = Color(.systemGroupedBackground)

        static let protein = Color(red: 0.93, green: 0.36, blue: 0.42)
        static let carbs = Color(red: 0.24, green: 0.56, blue: 0.96)
        static let fat = Color(red: 0.96, green: 0.65, blue: 0.14)
        static let bodyWeight = Color(red: 0.38, green: 0.47, blue: 0.96)
        static let water = Color.cyan
        static let aiAccent = Color(red: 0.62, green: 0.32, blue: 0.96)
        static let tacoCyan = Color(red: 0.20, green: 0.72, blue: 0.96)

        static let success = Color("AccentGreen")
        static let warning = Color.orange
        static let danger = Color.red

        static var aiGradient: LinearGradient {
            LinearGradient(
                colors: [
                    Color(red: 0.56, green: 0.27, blue: 0.95),
                    Color(red: 0.72, green: 0.36, blue: 0.98)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }

        static var brandGradient: LinearGradient {
            LinearGradient(
                colors: [
                    Color("AccentColor"),
                    Color("AccentGreen")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    enum Radius {
        static let card: CGFloat = 20
        static let inner: CGFloat = 14
        static let badge: CGFloat = 10
    }

    enum Spacing {
        static let screenHorizontal: CGFloat = 20
        static let sectionGap: CGFloat = 18
        static let cardPadding: CGFloat = 18
    }
}
