//
//  FormaTheme.swift
//  Forma
//
//  Tokens semânticos de cor, tipografia, raios e espaçamento do Design System FormaUI.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum FormaTheme {
    enum Colors {
        private static func adaptive(
            light: (r: Double, g: Double, b: Double),
            dark: (r: Double, g: Double, b: Double)
        ) -> Color {
            #if os(iOS) || os(tvOS) || os(visionOS)
            return Color(UIColor { traits in
                let c = traits.userInterfaceStyle == .dark ? dark : light
                return UIColor(red: c.r, green: c.g, blue: c.b, alpha: 1.0)
            })
            #else
            return Color(red: dark.r, green: dark.g, blue: dark.b)
            #endif
        }

        static let brand = Color("AccentColor")
        static let brandInk = adaptive(
            light: (r: 0.027, g: 0.478, b: 0.396), // #077A65 — contraste >= 5.2:1 no modo claro
            dark: (r: 0.329, g: 0.882, b: 0.765)   // #54E1C3 — teal assinatura do Forma no modo escuro
        )
        static let brandSoft = Color("AccentMinor")
        static let energyGreen = adaptive(
            light: (r: 0.043, g: 0.494, b: 0.384), // #0B7E62 — harmonizado com Forma teal e roxo
            dark: (r: 0.263, g: 0.871, b: 0.722)   // #43DEB8
        )
        static let cardSurface = Color("CardBackground")
        static let primaryText = Color("FontNormal")
        static let pageBackground = Color(.systemGroupedBackground)

        static let protein = Color(red: 0.93, green: 0.36, blue: 0.42)
        static let carbs = Color(red: 0.24, green: 0.56, blue: 0.96)
        static let fat = Color(red: 0.96, green: 0.65, blue: 0.14)
        static let bodyWeight = Color(red: 0.38, green: 0.47, blue: 0.96)
        static let water = Color.cyan
        static let aiAccent = adaptive(
            light: (r: 0.357, g: 0.196, b: 0.910), // #5B32E8 — roxo/índigo com alto contraste
            dark: (r: 0.643, g: 0.518, b: 1.000)   // #A484FF
        )
        static let tacoCyan = Color(red: 0.20, green: 0.72, blue: 0.96)

        static let success = adaptive(
            light: (r: 0.043, g: 0.494, b: 0.384), // #0B7E62
            dark: (r: 0.263, g: 0.871, b: 0.722)   // #43DEB8
        )
        static let warning = Color.orange
        static let danger = Color.red

        static var aiGradient: LinearGradient {
            LinearGradient(
                colors: [
                    Color(red: 0.310, green: 0.169, blue: 0.878), // #4F2BE0
                    Color(red: 0.427, green: 0.220, blue: 0.922)  // #6D38EB
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }

        static var brandGradient: LinearGradient {
            LinearGradient(
                colors: [
                    Color(red: 0.027, green: 0.420, blue: 0.349), // #076B59 — contraste >= 4.7:1 com texto branco
                    Color(red: 0.051, green: 0.510, blue: 0.427)  // #0D826D
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
