//
//  WorkoutRoutineCard.swift
//  Forma
//
//  Cartões modulares de Fichas de Treino (RoutineCard) e Histórico de Sessões (WorkoutCard).
//

import SwiftUI

struct RoutineCard: View {
    let routine: WorkoutRoutine
    let onStart: () -> Void
    let onToggleFavorite: () -> Void
    let onDelete: () -> Void
    var onShowExerciseDetail: ((WorkoutExerciseItem) -> Void)? = nil

    @State private var isExpanded: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(FormaTheme.Colors.brandSoft)
                        .frame(width: 44, height: 44)
                    Image(systemName: routine.workoutType.icon)
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 3) {
                    if routine.notes.contains("Análise") || routine.notes.contains("Inteligência") {
                        FormaBadge(text: "Inteligência Forma", icon: "sparkles", tint: FormaTheme.Colors.aiAccent)
                    }

                    Text(routine.title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(2)

                    if !routine.targetMuscles.isEmpty {
                        Text(routine.targetMuscles)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }

                Spacer(minLength: 6)

                Button(action: onToggleFavorite) {
                    Image(systemName: routine.isFavorite ? "star.fill" : "star")
                        .font(.system(size: 18))
                        .foregroundStyle(routine.isFavorite ? FormaTheme.Colors.warning : .secondary.opacity(0.4))
                        .padding(6)
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 8) {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                    Text("~\(Int(routine.estimatedCalories)) kcal")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(Color(.systemBackground))
                .clipShape(Capsule())
                .fixedSize(horizontal: true, vertical: false)

                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                    Text("\(routine.estimatedDurationMinutes) min")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(Color(.systemBackground))
                .clipShape(Capsule())
                .fixedSize(horizontal: true, vertical: false)

                Button {
                    withAnimation(.spring(duration: 0.25)) {
                        isExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 11))
                        Text("\(routine.exercises.count) exercícios")
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 9, weight: .bold))
                    }
                    .foregroundStyle(FormaTheme.Colors.brand)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(FormaTheme.Colors.brandSoft)
                    .clipShape(Capsule())
                    .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)
            }

            if isExpanded && !routine.exercises.isEmpty {
                VStack(spacing: 6) {
                    ForEach(routine.exercises) { ex in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(ex.name)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Text("\(ex.sets)x \(ex.reps)\(ex.suggestedWeight.isEmpty ? "" : " • \(ex.suggestedWeight)")")
                                    .font(.system(size: 11))
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            Button {
                                onShowExerciseDetail?(ex)
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "play.tv.fill")
                                        .font(.system(size: 12))
                                    Text("Vídeo")
                                        .font(.system(size: 11, weight: .bold))
                                }
                                .foregroundStyle(FormaTheme.Colors.brand)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(FormaTheme.Colors.brandSoft)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(9)
                        .background(Color(.systemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }

            Divider().background(Color.primary.opacity(0.06))

            HStack(spacing: 10) {
                Button(action: onStart) {
                    HStack(spacing: 6) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12))
                        Text("Iniciar Treino com Checklist")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(FormaTheme.Colors.brand)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)

                Menu {
                    Button(role: .destructive, action: onDelete) {
                        Label("Excluir Ficha", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.secondary)
                        .padding(10)
                        .background(Color.primary.opacity(0.06))
                        .clipShape(Circle())
                }
            }
        }
        .padding(16)
        .background(FormaTheme.Colors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(routine.isFavorite ? FormaTheme.Colors.warning.opacity(0.32) : Color.primary.opacity(0.06), lineWidth: 0.9)
        )
    }
}

struct WorkoutCard: View {
    let workout: WorkoutEntry
    let onDelete: () -> Void

    var body: some View {
        FormaCard(padding: 16, cornerRadius: 18) {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(FormaTheme.Colors.brandSoft)
                        .frame(width: 44, height: 44)
                    Image(systemName: workout.workoutType.icon)
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .top, spacing: 8) {
                        Text(workout.title)
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)

                        Spacer(minLength: 6)

                        HStack(spacing: 8) {
                            Text(workout.date.formatted(.dateTime.day().month().locale(Locale(identifier: "pt_BR"))))
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)

                            Menu {
                                Button(role: .destructive, action: onDelete) {
                                    Label("Excluir Sessão", systemImage: "trash")
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.secondary)
                                    .frame(width: 26, height: 26)
                                    .background(Color.primary.opacity(0.05))
                                    .clipShape(Circle())
                            }
                        }
                    }

                    HStack(spacing: 6) {
                        Text(workout.workoutType.rawValue)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)

                        Text("•")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        Text("\(workout.durationMinutes) min")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)

                        Text("•")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        Text("+\(Int(workout.caloriesBurned.rounded())) kcal")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }

                    if !workout.notes.isEmpty {
                        Text(workout.notes)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                            .padding(.top, 1)
                    }
                }
            }
        }
    }
}
