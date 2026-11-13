//
//  FormaApp.swift
//  Forma
//
//  Created by João Pedro on 10/13/26.
//

import SwiftUI
import SwiftData
import ActivityKit

@main
struct FormaApp: App {
    @StateObject private var authManager = AuthManager.shared
    @StateObject private var syncManager = BackendSyncManager.shared
    @State private var isShowingSplash: Bool = true
    
    var body: some Scene {
        WindowGroup {
            Group {
                if isShowingSplash {
                    SplashScreenView {
                        withAnimation(.easeInOut(duration: 0.35)) {
                            isShowingSplash = false
                        }
                    }
                    .transition(.opacity)
                } else if authManager.isAuthenticated {
                    MainView()
                        .transition(.opacity)
                } else {
                    LoginView {
                        triggerSync()
                    }
                    .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.25), value: authManager.isAuthenticated)
            .animation(.easeInOut(duration: 0.3), value: isShowingSplash)
            .onOpenURL { url in
                AppNavigationRouter.shared.handleDeepLink(url)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                cleanupLegacyCalorieLiveActivities()
                guard authManager.isAuthenticated else { return }
                FormaDataStore.shared.syncTodayData()
                FormaDataStore.shared.syncExternalHealthWorkoutsInBackground()
                checkPendingWorkout()
            }
            .task {
                cleanupLegacyCalorieLiveActivities()
                WorkoutNotificationManager.shared.setup()
                WatchConnectivityManager.shared.setup()
                if authManager.isAuthenticated {
                    FormaDataStore.shared.setupBackgroundHealthObserver()
                    FormaDataStore.shared.syncTodayData()
                    checkPendingWorkout()
                    WatchConnectivityManager.shared.syncContextToWatch()
                    if !syncManager.hasCompletedInitialSessionPull {
                        triggerSync()
                    }
                }
            }
            .onChange(of: authManager.isAuthenticated) { _, isAuth in
                if isAuth {
                    FormaDataStore.shared.setupBackgroundHealthObserver()
                    checkPendingWorkout()
                }
            }
        }
        .modelContainer(FormaDataStore.shared.container)
    }
    
    private func triggerSync() {
        Task { @MainActor in
            await BackendSyncManager.shared.sync(context: FormaDataStore.shared.mainContext)
        }
    }
    
    private func cleanupLegacyCalorieLiveActivities() {
        Task { @MainActor in
            for activity in Activity<CalorieActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
    
    private func checkPendingWorkout() {
        if let pending = SharedWorkoutSessionManager.shared.consumePendingWorkout() {
            let workout = WorkoutEntry(
                title: pending.title,
                workoutType: WorkoutType(rawValue: pending.workoutTypeRaw) ?? .bodybuilding,
                durationMinutes: pending.durationMinutes,
                caloriesBurned: pending.caloriesBurned,
                date: pending.date,
                notes: pending.notes
            )
            try? FormaDataStore.shared.insertWorkoutEntry(workout)
        }
    }
}
