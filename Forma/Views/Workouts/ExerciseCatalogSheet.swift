//
//  ExerciseCatalogSheet.swift
//  Forma
//
//  Created by João Pedro on 10/30/26.
//

import SwiftUI

/// Modal interativo para explorar o catálogo organizado de 82 vídeos 3D de 10s (27 principais + 55 variações)
public struct ExerciseCatalogSheet: View {
    @Environment(\.dismiss) private var dismiss

    public var onSelectExercise: ((ExerciseCatalogItem) -> Void)? = nil

    private enum HierarchyFilter: String, CaseIterable {
        case all = "Todos"
        case mains = "Principais"
        case variations = "Variações"
    }

    @State private var searchText: String = ""
    @State private var selectedGroup: String = "Todos"
    @State private var hierarchyFilter: HierarchyFilter = .all
    @State private var itemForDetail: ExerciseCatalogItem? = nil

    private let catalog = ExerciseCatalog.shared

    public init(onSelectExercise: ((ExerciseCatalogItem) -> Void)? = nil) {
        self.onSelectExercise = onSelectExercise
    }

    private var orderedBaseItems: [ExerciseCatalogItem] {
        if selectedGroup == "Todos" {
            var allOrganized: [ExerciseCatalogItem] = []
            for group in catalog.muscleGroups {
                allOrganized.append(contentsOf: catalog.items(for: group))
            }
            return allOrganized
        } else {
            return catalog.items(for: selectedGroup)
        }
    }

    private var filteredItems: [ExerciseCatalogItem] {
        orderedBaseItems.filter { item in
            let matchesHierarchy: Bool
            switch hierarchyFilter {
            case .all: matchesHierarchy = true
            case .mains: matchesHierarchy = !item.isVariation
            case .variations: matchesHierarchy = item.isVariation
            }

            let matchesSearch = searchText.isEmpty ||
                item.name.localizedCaseInsensitiveContains(searchText) ||
                item.id.localizedCaseInsensitiveContains(searchText) ||
                item.emphasisMuscle.localizedCaseInsensitiveContains(searchText) ||
                item.targetMuscles.localizedCaseInsensitiveContains(searchText) ||
                item.synonyms.contains { $0.localizedCaseInsensitiveContains(searchText) }

            return matchesHierarchy && matchesSearch
        }
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Barra de Busca
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("Buscar exercício principal, variação ou músculo...", text: $searchText)
                        .textFieldStyle(.plain)
                        .font(.system(size: 14))
                    if !searchText.isEmpty {
                        Button {
                            searchText = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.gray)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 6)

                // Seletor de Hierarquia: Todos (82) | Principais (27) | Variações (55)
                Picker("Tipo", selection: $hierarchyFilter) {
                    Text("Todos (\(catalog.items.count))").tag(HierarchyFilter.all)
                    Text("Principais (\(catalog.mainExercises.count))").tag(HierarchyFilter.mains)
                    Text("Variações (\(catalog.items.count - catalog.mainExercises.count))").tag(HierarchyFilter.variations)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.bottom, 6)

                // Chips de Filtro por Grupo Muscular
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        groupChip("Todos")
                        ForEach(catalog.muscleGroups, id: \.self) { group in
                            groupChip(group)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }

                // Grid de Cards com Vídeos 3D de 10s e Músculo Alvo em Vermelho
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                        ForEach(filteredItems) { item in
                            exerciseCard(item: item)
                        }
                    }
                    .padding(16)
                }
                .scrollIndicators(.hidden)
            }
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("Vídeos de Execução 3D")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") {
                        dismiss()
                    }
                }
            }
            .sheet(item: $itemForDetail) { item in
                ExerciseDetailView(
                    item: item,
                    onSelectItem: onSelectExercise != nil ? { chosen in
                        onSelectExercise?(chosen)
                        dismiss()
                    } : nil
                )
            }
        }
    }

    private func groupChip(_ group: String) -> some View {
        Button {
            selectedGroup = group
        } label: {
            Text(group)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(selectedGroup == group ? .white : Color("FontNormal"))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selectedGroup == group ? Color("AccentColor") : Color("CardBackground"))
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.03), radius: 3, x: 0, y: 1)
        }
    }

    private func exerciseCard(item: ExerciseCatalogItem) -> some View {
        Button {
            itemForDetail = item
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .topTrailing) {
                    GIFPlayerView(videoName: item.videoName, cornerRadius: 14, showControls: false)
                        .aspectRatio(16/9, contentMode: .fill)
                        .frame(height: 108)
                        .clipped()

                    HStack(spacing: 4) {
                        Text(item.isVariation ? "VARIAÇÃO" : "PRINCIPAL")
                            .font(.system(size: 8, weight: .heavy))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background((item.isVariation ? Color.orange : Color.red).opacity(0.85))
                            .clipShape(Capsule())

                        Text("10s")
                            .font(.system(size: 8, weight: .heavy))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.65))
                            .clipShape(Capsule())
                    }
                    .padding(6)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(item.name)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundColor(Color("FontNormal"))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 6, height: 6)
                        Text(item.emphasisMuscle)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.red)
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 10)
            }
            .background(Color("CardBackground"))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }
}
