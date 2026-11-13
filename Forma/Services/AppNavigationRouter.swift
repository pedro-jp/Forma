//
//  AppNavigationRouter.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import SwiftUI
import Combine

/// Roteador de navegação profunda para suporte a Widgets, Ilha Dinâmica e Ações Rápidas
@MainActor
public final class AppNavigationRouter: ObservableObject {
    public static let shared = AppNavigationRouter()
    
    @Published public var showAddFoodSheet: Bool = false
    @Published public var showVoiceFoodSheet: Bool = false
    @Published public var showAIFoodSheet: Bool = false
    @Published public var showAIWorkoutSheet: Bool = false
    @Published public var selectedTab: String = "inicio"
    
    private init() {}
    
    public func cutAllAIFlowsForFreeTier() {
        showAIWorkoutSheet = false
        showAIFoodSheet = false
    }

    public func handleDeepLink(_ url: URL) {
        guard let rawScheme = url.scheme?.lowercased(),
              rawScheme == "forma" else { return }
        
        let host = url.host?.lowercased() ?? url.path.replacingOccurrences(of: "/", with: "").lowercased()
        
        switch host {
        case "add-food", "addfood", "voice-food", "voice", "falar", "ai-food", "gemini-food":
            selectedTab = "inicio"
            showAddFoodSheet = false
            showAIFoodSheet = false
            showVoiceFoodSheet = true
        case "ai-workout", "gemini-workout", "workout":
            selectedTab = "treinos"
            showAIWorkoutSheet = AuthManager.shared.isPro
        case "open", "inicio":
            selectedTab = "inicio"
        default:
            selectedTab = "inicio"
        }
    }
}
