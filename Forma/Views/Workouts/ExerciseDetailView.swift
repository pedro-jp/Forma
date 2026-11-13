//
//  ExerciseDetailView.swift
//  Forma
//
//  Created by João Pedro on 10/30/26.
//

import SwiftUI

/// Tela detalhada com Vídeo 3D de 10s (humanoide branco hiper-musculoso com músculo-alvo em vermelho), variações e guia biomecânico
public struct ExerciseDetailView: View {
    @Environment(\.dismiss) private var dismiss

    public let item: ExerciseCatalogItem
    public var onSelect: (() -> Void)? = nil
    public var onSelectItem: ((ExerciseCatalogItem) -> Void)? = nil

    @State private var activeItem: ExerciseCatalogItem

    public init(
        item: ExerciseCatalogItem,
        onSelect: (() -> Void)? = nil,
        onSelectItem: ((ExerciseCatalogItem) -> Void)? = nil
    ) {
        self.item = item
        self.onSelect = onSelect
        self.onSelectItem = onSelectItem
        self._activeItem = State(initialValue: item)
    }

    private var familyItems: [ExerciseCatalogItem] {
        ExerciseCatalog.shared.familyExercises(for: activeItem.id)
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Vídeo 3D de 10s em Loop com Humanoide Anatômico e Músculo Alvo Vermelho
                    VStack(spacing: 8) {
                        GIFPlayerView(videoName: activeItem.videoName, cornerRadius: 18, showControls: true)
                            .aspectRatio(16/9, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)

                        HStack(spacing: 6) {
                            Circle()
                                .fill(Color.red)
                                .frame(width: 7, height: 7)
                            Text("Vídeo 3D de 10s em loop • Músculo alvo destacado em vermelho")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity)

                    // Card Principal de Músculos & Ênfase
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(alignment: .top, spacing: 8) {
                            Text(activeItem.name)
                                .font(.system(size: 18, weight: .bold, design: .rounded))
                                .foregroundColor(Color("FontNormal"))
                                .fixedSize(horizontal: false, vertical: true)

                            Spacer(minLength: 6)

                            HStack(spacing: 5) {
                                Text(activeItem.isVariation ? "VARIAÇÃO" : "PRINCIPAL")
                                    .font(.system(size: 9, weight: .heavy))
                                    .foregroundColor(activeItem.isVariation ? .orange : .red)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background((activeItem.isVariation ? Color.orange : Color.red).opacity(0.12))
                                    .clipShape(Capsule())

                                Text(activeItem.muscleGroup)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Color("AccentColor"))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4)
                                    .background(Color("AccentColor").opacity(0.1))
                                    .clipShape(Capsule())
                            }
                        }

                        // Destaque de Músculo Alvo Ativo em Vermelho
                        HStack(spacing: 10) {
                            Image(systemName: "flame.fill")
                                .font(.system(size: 15))
                                .foregroundColor(.red)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("MÚSCULO ALVO ATIVO (EM VERMELHO NO VÍDEO)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.secondary)
                                Text(activeItem.emphasisMuscle)
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundColor(.red)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                        // Músculos Sinergistas Recrutados
                        VStack(alignment: .leading, spacing: 4) {
                            Text("MÚSCULOS RECRUTADOS")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.secondary)
                            Text(activeItem.targetMuscles)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color("FontNormal"))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color("CardBackground"))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)

                    // Explorador de Movimento Principal & Variações Associadas
                    if familyItems.count > 1 {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "arrow.triangle.branch")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color("AccentColor"))
                                Text("MOVIMENTO PRINCIPAL & VARIAÇÕES (\(familyItems.count))")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.secondary)
                            }

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(familyItems) { fam in
                                        let isCurrent = fam.id == activeItem.id
                                        Button {
                                            withAnimation(.spring(duration: 0.25)) {
                                                activeItem = fam
                                            }
                                        } label: {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(fam.isVariation ? "Variação" : "Principal")
                                                    .font(.system(size: 9, weight: .heavy))
                                                    .foregroundColor(isCurrent ? .white.opacity(0.9) : (fam.isVariation ? .orange : .red))
                                                Text(fam.name)
                                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                                    .foregroundColor(isCurrent ? .white : Color("FontNormal"))
                                                    .lineLimit(2)
                                                    .multilineTextAlignment(.leading)
                                            }
                                            .frame(width: 155, alignment: .leading)
                                            .padding(10)
                                            .background(isCurrent ? Color("AccentColor") : Color(.systemBackground))
                                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .stroke(isCurrent ? Color.clear : Color.primary.opacity(0.08), lineWidth: 1)
                                            )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color("CardBackground"))
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
                    }

                    // Passo a Passo de Execução
                    VStack(alignment: .leading, spacing: 16) {
                        Text("COMO EXECUTAR CORRETAMENTE")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        instructionRow(
                            stepNumber: "1",
                            title: "Postura & Posição Inicial",
                            description: activeItem.setupInstruction,
                            icon: "figure.walk",
                            accentColor: .blue
                        )

                        instructionRow(
                            stepNumber: "2",
                            title: "Fase Excêntrica (Alongamento)",
                            description: activeItem.eccentricInstruction,
                            icon: "arrow.down.circle.fill",
                            accentColor: .orange
                        )

                        instructionRow(
                            stepNumber: "3",
                            title: "Fase Concêntrica & Pico de Contração",
                            description: activeItem.concentricInstruction,
                            icon: "arrow.up.circle.fill",
                            accentColor: Color("AccentGreen")
                        )

                        instructionRow(
                            stepNumber: "4",
                            title: "Respiração & Firmeza do Core",
                            description: activeItem.breathingInstruction,
                            icon: "lungs.fill",
                            accentColor: .teal
                        )

                        instructionRow(
                            stepNumber: "5",
                            title: "Erro Comum a Evitar",
                            description: activeItem.commonMistake,
                            icon: "exclamationmark.triangle.fill",
                            accentColor: .red
                        )
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color("CardBackground"))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)

                    if onSelect != nil || onSelectItem != nil {
                        Button {
                            if let onSelectItem = onSelectItem {
                                onSelectItem(activeItem)
                            } else {
                                onSelect?()
                            }
                            dismiss()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Vincular Este Vídeo ao Exercício")
                            }
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color("AccentColor"))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .padding(.top, 4)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("Vídeo de Execução (10s)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") {
                        dismiss()
                    }
                }
            }
        }
    }

    private func instructionRow(
        stepNumber: String,
        title: String,
        description: String,
        icon: String,
        accentColor: Color
    ) -> some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle()
                    .fill(accentColor.opacity(0.12))
                    .frame(width: 30, height: 30)
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(accentColor)
            }
            .fixedSize()

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(Color("FontNormal"))
                    .fixedSize(horizontal: false, vertical: true)

                Text(description)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
