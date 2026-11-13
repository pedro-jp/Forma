//
//  TabView.swift
//  Forma
//
//  Created by João Pedro on 10/17/26.
//

import SwiftUI
import SwiftData

struct MainView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Query private var profiles: [Profile]
    @ObservedObject private var router = AppNavigationRouter.shared
    @ObservedObject private var syncManager = BackendSyncManager.shared
    @ObservedObject private var authManager = AuthManager.shared
    @State private var lastForegroundSyncCheck: Date = .distantPast

    private var primaryProfile: Profile? {
        profiles.first
    }

    private var isAwaitingFirstSessionPull: Bool {
        guard authManager.isAuthenticated else { return false }
        if let p = primaryProfile, p.onboardingCompleted && !p.requiresInitialSetup {
            return false
        }
        return !syncManager.hasCompletedInitialSessionPull
    }

    private var shouldPresentInitialSetup: Bool {
        guard authManager.isAuthenticated, !isAwaitingFirstSessionPull else { return false }
        guard let p = primaryProfile else { return true }
        return p.requiresInitialSetup
    }

    var body: some View {
        Group {
            if isAwaitingFirstSessionPull {
                VStack(spacing: 14) {
                    ProgressView()
                        .controlSize(.large)
                        .tint(FormaTheme.Colors.brand)
                    Text("Carregando sua conta...")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            } else {
                TabView(selection: $router.selectedTab) {
                    Tab("Início", systemImage: "house.fill", value: "inicio") {
                        HomeView()
                    }

                    Tab("Alimentação", systemImage: "fork.knife", value: "alimentacao") {
                        NutritionView()
                    }

                    Tab("Treinos", systemImage: "dumbbell.fill", value: "treinos") {
                        WorkoutsView()
                    }

                    Tab("Evolução", systemImage: "chart.line.uptrend.xyaxis", value: "evolucao") {
                        EvolutionView()
                    }

                    Tab("Perfil", systemImage: "person.fill", value: "perfil") {
                        ProfileView()
                    }
                }
                .tint(Color("AccentColor"))
            }
        }
        .overlay(alignment: .top) {
            if let toast = authManager.realtimeToastMessage {
                HStack(spacing: 8) {
                    Image(systemName: authManager.isPro ? "sparkles" : "leaf.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                    Text(toast)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    authManager.isPro
                        ? AnyShapeStyle(FormaTheme.Colors.aiGradient)
                        : AnyShapeStyle(FormaTheme.Colors.brand)
                )
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.18), radius: 10, x: 0, y: 4)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .task(id: authManager.currentUserId) {
            guard authManager.isAuthenticated else { return }
            AccountRealtimeSocketManager.shared.setAppForeground(true)
            await syncManager.sync(context: modelContext, forcePull: true)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                guard authManager.isAuthenticated else { return }
                AccountRealtimeSocketManager.shared.setAppForeground(true)
                if Date().timeIntervalSince(lastForegroundSyncCheck) > 4.0 {
                    lastForegroundSyncCheck = Date()
                    Task {
                        await syncManager.sync(context: modelContext, forcePull: true)
                    }
                }
            } else if newPhase == .background {
                AccountRealtimeSocketManager.shared.setAppForeground(false)
            }
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { shouldPresentInitialSetup },
                set: { _ in }
            )
        ) {
            InitialOnboardingModalView()
                .interactiveDismissDisabled(true)
        }
    }
}

#Preview {
    MainView()
        .modelContainer(PreviewContainer.shared)
}
