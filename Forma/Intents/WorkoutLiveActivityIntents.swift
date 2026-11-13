//
//  WorkoutLiveActivityIntents.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import AppIntents

/// Intent para concluir a série atual diretamente pela Ilha Dinâmica ou Tela Bloqueada sem abrir o app
public struct CompleteSetLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Concluir Série"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.completeCurrentSetFromLiveActivity()
        return .result()
    }
}

/// Intent para pular o descanso e iniciar a próxima série imediatamente pela Ilha Dinâmica
public struct SkipRestLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Iniciar Série"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.skipRestFromLiveActivity()
        return .result()
    }
}

/// Intent para estender o descanso em +30 segundos pela Ilha Dinâmica
public struct AddRestTimeLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "+30s Descanso"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.addRestSecondsFromLiveActivity(seconds: 30)
        return .result()
    }
}

/// Intent para aumentar a carga da série que passou / atual diretamente pela Ilha Dinâmica ou Tela Bloqueada
public struct IncreaseSetWeightLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Aumentar Carga"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 2.0)
        return .result()
    }
}

/// Intent para reduzir a carga da série que passou / atual diretamente pela Ilha Dinâmica ou Tela Bloqueada
public struct DecreaseSetWeightLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Reduzir Carga"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -2.0)
        return .result()
    }
}

/// Intent para aumentar +0.5 kg na carga da série pela Ilha Dinâmica ou Tela Bloqueada
public struct IncreaseHalfKgLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Aumentar Carga (+0.5kg)"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 0.5)
        return .result()
    }
}

/// Intent para reduzir -0.5 kg na carga da série pela Ilha Dinâmica ou Tela Bloqueada
public struct DecreaseHalfKgLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Reduzir Carga (-0.5kg)"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -0.5)
        return .result()
    }
}

/// Intent para aumentar +1 kg na carga da série pela Ilha Dinâmica ou Tela Bloqueada
public struct IncreaseOneKgLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Aumentar Carga (+1kg)"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: 1.0)
        return .result()
    }
}

/// Intent para reduzir -1 kg na carga da série pela Ilha Dinâmica ou Tela Bloqueada
public struct DecreaseOneKgLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Reduzir Carga (-1kg)"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: -1.0)
        return .result()
    }
}

/// Intent para alternar o passo de ajuste de carga na Ilha Dinâmica (2kg -> 1kg -> 0.5kg)
public struct CycleWeightStepLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Alternar Passo de Carga (2kg / 1kg / 0.5kg)"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.cycleWeightStepFromLiveActivity()
        return .result()
    }
}

/// Intent para finalizar e registrar o treino completo pela Ilha Dinâmica
public struct FinishWorkoutLiveIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Finalizar Treino"
    public static var isDiscoverable: Bool = false
    public static var openAppWhenRun: Bool = false
    
    public init() {}
    
    public func perform() async throws -> some IntentResult {
        await SharedWorkoutSessionManager.shared.finishWorkoutFromLiveActivity()
        return .result()
    }
}
