//
//  SplashScreenView.swift
//  Forma
//
//  Created by João Pedro on 11/02/26.
//

import SwiftUI

struct SplashScreenView: View {
    var onFinished: () -> Void
    
    @State private var logoScale: CGFloat = 0.7
    @State private var logoOpacity: Double = 0.0
    @State private var textOpacity: Double = 0.0
    @State private var glowOpacity: Double = 0.0
    
    var body: some View {
        ZStack {
            // Fundo escuro com gradiente radial sutil
            FormaTheme.Colors.pageBackground
                .ignoresSafeArea()
            
            RadialGradient(
                gradient: Gradient(colors: [
                    FormaTheme.Colors.brand.opacity(0.18),
                    FormaTheme.Colors.pageBackground.opacity(0.0)
                ]),
                center: .center,
                startRadius: 20,
                endRadius: 260
            )
            .ignoresSafeArea()
            .opacity(glowOpacity)
            
            VStack(spacing: 24) {
                Spacer()
                
                // Logo animada do Forma
                ZStack {
                    Circle()
                        .fill(FormaTheme.Colors.brand.opacity(0.12))
                        .frame(width: 130, height: 130)
                        .blur(radius: 12)
                    
                    if let _ = UIImage(named: "app_logo") {
                        Image("app_logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 104, height: 104)
                            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                            .shadow(color: FormaTheme.Colors.brand.opacity(0.35), radius: 20, x: 0, y: 8)
                    } else {
                        // Fallback elegante
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(FormaTheme.Colors.brandGradient)
                            .frame(width: 104, height: 104)
                            .overlay(
                                Image(systemName: "flame.fill")
                                    .font(.system(size: 48, weight: .bold))
                                    .foregroundColor(.white)
                            )
                            .shadow(color: FormaTheme.Colors.brand.opacity(0.35), radius: 20, x: 0, y: 8)
                    }
                }
                .scaleEffect(logoScale)
                .opacity(logoOpacity)
                
                // Tipografia e Slogan
                VStack(spacing: 8) {
                    Text("Forma")
                        .font(.system(size: 38, weight: .heavy, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    
                    Text("Nutrição & Treinos Inteligentes")
                        .font(.system(size: 15, weight: .medium, design: .rounded))
                        .foregroundStyle(.secondary)
                }
                .opacity(textOpacity)
                
                Spacer()
                
                // Indicador minimalista de carregamento
                HStack(spacing: 8) {
                    ProgressView()
                        .tint(FormaTheme.Colors.brand)
                        .scaleEffect(0.9)
                    
                    Text("Iniciando...")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
                .padding(.bottom, 32)
                .opacity(textOpacity)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.5).delay(0.2)) {
                glowOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.4).delay(0.35)) {
                textOpacity = 1.0
            }
            
            // Transição suave para a próxima tela
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                withAnimation(.easeInOut(duration: 0.3)) {
                    onFinished()
                }
            }
        }
    }
}
