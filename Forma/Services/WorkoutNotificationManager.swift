//
//  WorkoutNotificationManager.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import UserNotifications
import UIKit
import AudioToolbox

/// Gerenciador de alertas sonoros, táteis (vibração) e notificações Time-Sensitive
/// Garante que o celular vibre e acenda a tela ao fim do descanso (ligado ou bloqueado/desligado)
public final class WorkoutNotificationManager: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    public static let shared = WorkoutNotificationManager()
    
    private let restNotificationId = "forma_workout_rest_timer"
    public static let restCategoryId = "WORKOUT_REST_CATEGORY"
    public static let actionIncreaseHalfKgId = "ACTION_INCREASE_HALF_KG"
    public static let actionIncreaseOneKgId = "ACTION_INCREASE_ONE_KG"
    public static let actionDecreaseHalfKgId = "ACTION_DECREASE_HALF_KG"
    public static let actionDecreaseOneKgId = "ACTION_DECREASE_ONE_KG"
    public static let actionIncreaseWeightId = "ACTION_INCREASE_SET_WEIGHT"
    public static let actionDecreaseWeightId = "ACTION_DECREASE_SET_WEIGHT"
    public static let actionSkipRestId = "ACTION_SKIP_REST"
    
    private override init() {
        super.init()
    }
    
    /// Inicializa delegate, categorias de ação de carga e solicita permissões necessárias
    public func setup() {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        registerWorkoutNotificationCategories()
        requestAuthorization()
    }
    
    private func registerWorkoutNotificationCategories() {
        let incOneAction = UNNotificationAction(
            identifier: Self.actionIncreaseOneKgId,
            title: "Aumentar (+1 kg)",
            options: []
        )
        let incHalfAction = UNNotificationAction(
            identifier: Self.actionIncreaseHalfKgId,
            title: "Aumentar (+0.5 kg)",
            options: []
        )
        let decHalfAction = UNNotificationAction(
            identifier: Self.actionDecreaseHalfKgId,
            title: "Reduzir (-0.5 kg)",
            options: []
        )
        let decOneAction = UNNotificationAction(
            identifier: Self.actionDecreaseOneKgId,
            title: "Reduzir (-1 kg)",
            options: []
        )
        let skipRestAction = UNNotificationAction(
            identifier: Self.actionSkipRestId,
            title: "Iniciar Série Agora",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: Self.restCategoryId,
            actions: [incOneAction, incHalfAction, decHalfAction, decOneAction, skipRestAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }
    
    /// Solicita autorização para alertas, sons e avisos Time-Sensitive
    public func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { granted, error in
            if granted {
                print("✅ Permissão de Notificações Time-Sensitive concedida para treinos.")
                Task { @MainActor in
                    WorkoutNotificationManager.shared.scheduleDailyHydrationAndMealReminders(
                        waterConsumedMl: 0,
                        waterTargetMl: 2500,
                        hasLoggedBreakfast: false,
                        hasLoggedLunch: false,
                        hasLoggedDinner: false
                    )
                }
            } else if let error = error {
                print("⚠️ Erro ao solicitar permissões de notificação: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Lembretes Insistentes Estilo Duolingo (Hidratação 06h–22h + Refeições)

    private let hydrationSlotHoursMinutes: [(hour: Int, minute: Int)] = [
        (6, 30),
        (8, 30),
        (10, 0),
        (11, 15),
        (14, 0),
        (15, 15),
        (17, 0),
        (18, 30),
        (19, 30),
        (21, 15)
    ]

    /// Agenda lembretes inteligentes de água (das 06h às 22h, pulando horários de refeição) e lembretes de refeição,
    /// recalculando quanto falta para a meta sempre que o usuário registra água ou comida.
    public func scheduleDailyHydrationAndMealReminders(
        waterConsumedMl: Int,
        waterTargetMl: Int,
        hasLoggedBreakfast: Bool = false,
        hasLoggedLunch: Bool = false,
        hasLoggedDinner: Bool = false
    ) {
        let center = UNUserNotificationCenter.current()

        // Remove agendamentos anteriores de hidratação/refeição para recalibrar com o saldo atualizado
        var idsToClear: [String] = (0..<hydrationSlotHoursMinutes.count).map { "forma_hydration_slot_\($0)" }
        idsToClear.append(contentsOf: ["forma_meal_breakfast", "forma_meal_lunch", "forma_meal_snack", "forma_meal_dinner"])
        center.removePendingNotificationRequests(withIdentifiers: idsToClear)

        let target = max(1000, waterTargetMl)
        let remainingTotal = max(0, target - waterConsumedMl)
        let now = Date()
        let calendar = Calendar.current
        let currentMinutesOfDay = calendar.component(.hour, from: now) * 60 + calendar.component(.minute, from: now)

        // Conta quantos horários de água ainda restam hoje (ou divide em 10 doses de ~250ml para o ciclo diário)
        let futureSlotsCount = max(1, hydrationSlotHoursMinutes.filter { ($0.hour * 60 + $0.minute) > currentMinutesOfDay }.count)
        let standardDose = max(200, min(350, Int((Double(target) / Double(hydrationSlotHoursMinutes.count) / 50.0).rounded() * 50.0)))
        let dynamicDose = remainingTotal > 0
            ? max(200, min(400, Int((Double(remainingTotal) / Double(futureSlotsCount) / 50.0).rounded() * 50.0)))
            : standardDose

        for (index, slot) in hydrationSlotHoursMinutes.enumerated() {
            let slotMinutes = slot.hour * 60 + slot.minute
            let isFutureToday = slotMinutes > currentMinutesOfDay

            // Se já bateu a meta hoje, não incomoda nos horários restantes de hoje
            if isFutureToday && remainingTotal == 0 {
                continue
            }

            let content = UNMutableNotificationContent()
            content.sound = .default
            content.interruptionLevel = .timeSensitive
            content.relevanceScore = 0.9

            let doseToDisplay = isFutureToday ? dynamicDose : standardDose
            let remainingToDisplay = isFutureToday ? remainingTotal : max(250, target - (index * standardDose))

            switch index % 4 {
            case 0:
                content.title = "💧 Hora de tomar \(doseToDisplay) ml de água!"
                content.body = "Faltam \(remainingToDisplay) ml para sua meta de \(target) ml hoje. Mantenha o ritmo no Forma!"
            case 1:
                content.title = "👀 Sua meta de água está te esperando!"
                content.body = "Beba um copo de \(doseToDisplay) ml agora. Ainda faltam \(remainingToDisplay) ml para fechar o dia!"
            case 2:
                content.title = "⚡ Hidratação faz diferença no resultado!"
                content.body = "Pausa rápida para \(doseToDisplay) ml de água. Faltam \(remainingToDisplay) ml para bater os \(target) ml."
            default:
                content.title = "🔥 Não deixe a meta de água escapar!"
                content.body = "Tome \(doseToDisplay) ml agora! Só faltam \(remainingToDisplay) ml para completar seus \(target) ml hoje."
            }

            var dateComponents = DateComponents()
            dateComponents.hour = slot.hour
            dateComponents.minute = slot.minute

            let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
            let request = UNNotificationRequest(
                identifier: "forma_hydration_slot_\(index)",
                content: content,
                trigger: trigger
            )
            center.add(request, withCompletionHandler: nil)
        }

        // Lembretes de Refeições (nos intervalos em que a água dá pausa)
        let mealSchedules: [(id: String, hour: Int, minute: Int, alreadyLoggedToday: Bool, title: String, body: String)] = [
            (
                "forma_meal_breakfast",
                7, 30,
                hasLoggedBreakfast,
                "🍳 Já tomou seu café da manhã?",
                "Registre por foto, voz ou um toque no Forma para começar o dia batendo sua meta!"
            ),
            (
                "forma_meal_lunch",
                12, 15,
                hasLoggedLunch,
                "🍽️ Hora do almoço! O que tem no prato hoje?",
                "Tire uma foto rápida ou escolha nos seus habituais do dia para manter sua meta Forma em dia."
            ),
            (
                "forma_meal_snack",
                16, 0,
                false,
                "🍌 Pausa para o lanche da tarde!",
                "Mantenha sua energia e proteínas em dia. Registre seu lanche no Forma!"
            ),
            (
                "forma_meal_dinner",
                20, 0,
                hasLoggedDinner,
                "🥗 Hora do jantar! Feche o dia na meta",
                "Registre seu jantar no Forma para completar suas calorias e nutrientes de hoje."
            )
        ]

        for meal in mealSchedules {
            let mealMinutes = meal.hour * 60 + meal.minute
            if mealMinutes > currentMinutesOfDay && meal.alreadyLoggedToday {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = meal.title
            content.body = meal.body
            content.sound = .default
            content.interruptionLevel = .timeSensitive

            var comps = DateComponents()
            comps.hour = meal.hour
            comps.minute = meal.minute

            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
            let req = UNNotificationRequest(identifier: meal.id, content: content, trigger: trigger)
            center.add(req, withCompletionHandler: nil)
        }
    }
    
    /// Agenda notificação Time-Sensitive que acorda e acende a tela mesmo com a tela bloqueada/desligada
    public func scheduleRestNotification(
        seconds: Int,
        exerciseName: String,
        nextSet: Int,
        lastSetWeightKg: Double? = nil
    ) {
        cancelRestNotification()
        
        guard seconds > 0 else { return }
        
        let resolvedWeight = lastSetWeightKg
            ?? SharedWorkoutSessionManager.shared.loadSession()?.lastCompletedSetInfo?.weightKg
            ?? SharedWorkoutSessionManager.shared.loadSession()?.currentSetWeightKg
            ?? 0.0
        let weightLabel = resolvedWeight > 0 ? " (\(WorkoutExerciseItem.formatWeightKg(resolvedWeight)))" : ""
        
        let content = UNMutableNotificationContent()
        content.title = "Descanso Concluído! ⏱️"
        if resolvedWeight > 0 {
            content.body = "Hora da Série \(nextSet) de \(exerciseName)\(weightLabel)! Segure para ajustar a carga (+0.5kg / +1kg / -0.5kg / -1kg)."
        } else {
            content.body = "Hora de iniciar a Série \(nextSet) de \(exerciseName)!"
        }
        content.categoryIdentifier = Self.restCategoryId
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        content.relevanceScore = 1.0
        
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(seconds), repeats: false)
        let request = UNNotificationRequest(
            identifier: restNotificationId,
            content: content,
            trigger: trigger
        )
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("⚠️ Erro ao agendar notificação de descanso: \(error.localizedDescription)")
            } else {
                print("⏱️ Notificação de descanso agendada para daqui a \(seconds)s (Acenderá a tela).")
            }
        }
    }
    
    /// Cancela qualquer notificação de descanso pendente ou entregue
    public func cancelRestNotification() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [restNotificationId])
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [restNotificationId])
    }
    
    /// Dispara sequência de vibrações intensas e som de alarme quando o app está aberto / celular em uso
    public func triggerRestFinishedHapticsAndSound() {
        // 1. Vibração de hardware pelo motor tátil (AudioServices)
        AudioServicesPlayAlertSound(kSystemSoundID_Vibrate)
        
        // 2. Toque sonoro de campainha do temporizador nativo do iOS (System Sound 1005)
        AudioServicesPlaySystemSound(1005)
        
        // 3. Sequência de pulsos pesados (Heavy Haptics) no Taptic Engine
        let heavy = UIImpactFeedbackGenerator(style: .heavy)
        let success = UINotificationFeedbackGenerator()
        heavy.prepare()
        success.prepare()
        
        heavy.impactOccurred()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            heavy.impactOccurred()
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
            success.notificationOccurred(.success)
            AudioServicesPlayAlertSound(kSystemSoundID_Vibrate)
        }
    }
    
    // MARK: - UNUserNotificationCenterDelegate
    
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Quando a notificação chega com o app em primeiro plano: vibra intensamente e mostra banner
        triggerRestFinishedHapticsAndSound()
        completionHandler([.banner, .sound, .list])
    }
    
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let actionId = response.actionIdentifier
        Task {
            switch actionId {
            case Self.actionIncreaseOneKgId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 1.0)
            case Self.actionIncreaseHalfKgId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 0.5)
            case Self.actionDecreaseHalfKgId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -0.5)
            case Self.actionDecreaseOneKgId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -1.0)
            case Self.actionIncreaseWeightId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 2.0)
            case Self.actionDecreaseWeightId:
                await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -2.0)
            case Self.actionSkipRestId:
                await SharedWorkoutSessionManager.shared.skipRestFromLiveActivity()
            default:
                break
            }
            completionHandler()
        }
    }
}
