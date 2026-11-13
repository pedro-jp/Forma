//
//  GIFPlayerView.swift
//  Forma
//
//  Created by João Pedro on 10/30/26.
//

import SwiftUI
import AVFoundation
import ImageIO
import UIKit

/// Alias semântico moderno para o player de vídeos biomecânicos de 10s
public typealias ExerciseVideoPlayerView = GIFPlayerView

/// Componente SwiftUI de alta performance para reproduzir vídeos MP4 de 10s (humanoide 3D anatômico) com fallback para GIF
public struct GIFPlayerView: View {
    public let gifName: String
    public var cornerRadius: CGFloat = 16
    public var showControls: Bool = false

    @State private var videoURL: URL? = nil
    @State private var frames: [UIImage] = []
    @State private var totalDuration: TimeInterval = 1.2
    @State private var isPlaying: Bool = true
    @State private var playbackSpeed: Double = 1.0

    public init(gifName: String, cornerRadius: CGFloat = 16, showControls: Bool = false) {
        self.gifName = gifName
        self.cornerRadius = cornerRadius
        self.showControls = showControls
    }

    public init(videoName: String, cornerRadius: CGFloat = 16, showControls: Bool = false) {
        self.gifName = videoName
        self.cornerRadius = cornerRadius
        self.showControls = showControls
    }

    public var body: some View {
        ZStack {
            if let url = videoURL {
                LoopingMP4PlayerRepresentable(
                    url: url,
                    isPlaying: isPlaying,
                    playbackRate: Float(playbackSpeed)
                )
                .aspectRatio(16/9, contentMode: .fill)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))

                if showControls {
                    controlsOverlay
                }
            } else if !frames.isEmpty {
                UIAnimatedImageView(
                    images: frames,
                    duration: totalDuration / playbackSpeed,
                    isPlaying: isPlaying
                )
                .aspectRatio(16/9, contentMode: .fill)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))

                if showControls {
                    controlsOverlay
                }
            } else {
                ZStack {
                    Color(red: 0.05, green: 0.07, blue: 0.12)
                    VStack(spacing: 8) {
                        Image(systemName: "play.tv.fill")
                            .font(.system(size: 30))
                            .foregroundColor(Color("AccentGreen"))
                        Text("Vídeo de Execução 3D (10s)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
        }
        .onAppear {
            loadMedia()
        }
        .onChange(of: gifName) { _, _ in
            loadMedia()
        }
    }

    private var controlsOverlay: some View {
        VStack {
            HStack {
                Spacer()
                Button {
                    playbackSpeed = playbackSpeed == 1.0 ? 0.5 : 1.0
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "gauge.with.dots.needle.50percent")
                            .font(.system(size: 10, weight: .bold))
                        Text(playbackSpeed == 1.0 ? "1.0x" : "0.5x Lento")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.black.opacity(0.65))
                    .clipShape(Capsule())
                }
                .padding(8)
            }

            Spacer()

            HStack {
                Button {
                    isPlaying.toggle()
                } label: {
                    Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 26))
                        .foregroundColor(.white)
                        .background(Circle().fill(Color.black.opacity(0.55)))
                }
                .padding(8)

                Spacer()
            }
        }
    }

    /// Resolve URL do vídeo MP4 de 10s organizado por nome canônico no Bundle
    public static func resolveVideoURL(for rawName: String) -> URL? {
        let cleanName = rawName
            .replacingOccurrences(of: ".mp4", with: "")
            .replacingOccurrences(of: ".gif", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else { return nil }

        if let subURL = Bundle.main.url(forResource: cleanName, withExtension: "mp4", subdirectory: "ExerciseVideos") {
            return subURL
        }
        if let rootURL = Bundle.main.url(forResource: cleanName, withExtension: "mp4") {
            return rootURL
        }
        return nil
    }

    private func loadMedia() {
        // 1. Prioridade total para o vídeo MP4 3D de 10 segundos
        if let mp4URL = Self.resolveVideoURL(for: gifName) {
            self.videoURL = mp4URL
            self.frames = []
            return
        }

        // Fallback: caso o ID seja uma variação ou sinônimo, consulta o catálogo para obter o vídeo associado
        if let matched = ExerciseCatalog.shared.item(withId: gifName) ?? ExerciseCatalog.shared.matchExercise(query: gifName),
           let matchedURL = Self.resolveVideoURL(for: matched.videoName) {
            self.videoURL = matchedURL
            self.frames = []
            return
        }

        self.videoURL = nil

        // 2. Fallback legado para GIF caso exista
        let cleanName = gifName
            .replacingOccurrences(of: ".mp4", with: "")
            .replacingOccurrences(of: ".gif", with: "")
        let gifURL = Bundle.main.url(forResource: cleanName, withExtension: "gif", subdirectory: "ExerciseGIFs")
            ?? Bundle.main.url(forResource: cleanName, withExtension: "gif")

        guard let url = gifURL,
              let data = try? Data(contentsOf: url),
              let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            return
        }

        let count = CGImageSourceGetCount(source)
        var extracted: [UIImage] = []
        var calculatedDuration: TimeInterval = 0.0

        for i in 0..<count {
            if let cgImage = CGImageSourceCreateImageAtIndex(source, i, nil) {
                extracted.append(UIImage(cgImage: cgImage))
            }
            if let properties = CGImageSourceCopyPropertiesAtIndex(source, i, nil) as? [CFString: Any],
               let gifDict = properties[kCGImagePropertyGIFDictionary] as? [CFString: Any] {
                let delay = (gifDict[kCGImagePropertyGIFUnclampedDelayTime] as? TimeInterval)
                    ?? (gifDict[kCGImagePropertyGIFDelayTime] as? TimeInterval)
                    ?? 0.1
                calculatedDuration += max(delay, 0.05)
            } else {
                calculatedDuration += 0.1
            }
        }

        self.frames = extracted
        self.totalDuration = max(0.5, calculatedDuration)
    }
}

/// UIView customizada com AVPlayerLayer + AVPlayerLooper para loop contínuo de 10s sem pausar música externa
final class LoopingVideoContainerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    private var queuePlayer: AVQueuePlayer?
    private var playerLooper: AVPlayerLooper?
    private(set) var currentURL: URL?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(red: 0.04, green: 0.05, blue: 0.08, alpha: 1.0)
        clipsToBounds = true
        playerLayer.videoGravity = .resizeAspectFill
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(url: URL, isPlaying: Bool, rate: Float) {
        if currentURL != url {
            teardown()
            currentURL = url

            let session = AVAudioSession.sharedInstance()
            if session.category == .soloAmbient {
                try? session.setCategory(.ambient, options: [.mixWithOthers])
            }

            let asset = AVURLAsset(url: url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
            let item = AVPlayerItem(asset: asset)
            let player = AVQueuePlayer()
            player.isMuted = true
            player.preventsDisplaySleepDuringVideoPlayback = false
            player.automaticallyWaitsToMinimizeStalling = false

            let looper = AVPlayerLooper(player: player, templateItem: item)
            self.queuePlayer = player
            self.playerLooper = looper
            self.playerLayer.player = player
        }

        guard let player = queuePlayer else { return }
        if isPlaying {
            if player.rate != rate {
                player.playImmediately(atRate: rate)
            }
        } else {
            if player.rate != 0 {
                player.pause()
            }
        }
    }

    func teardown() {
        queuePlayer?.pause()
        playerLooper?.disableLooping()
        playerLooper = nil
        playerLayer.player = nil
        queuePlayer?.removeAllItems()
        queuePlayer = nil
        currentURL = nil
    }
}

struct LoopingMP4PlayerRepresentable: UIViewRepresentable {
    let url: URL
    let isPlaying: Bool
    let playbackRate: Float

    func makeUIView(context: Context) -> LoopingVideoContainerView {
        let view = LoopingVideoContainerView()
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentHuggingPriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ uiView: LoopingVideoContainerView, context: Context) {
        uiView.configure(url: url, isPlaying: isPlaying, rate: playbackRate)
    }

    static func dismantleUIView(_ uiView: LoopingVideoContainerView, coordinator: ()) {
        uiView.teardown()
    }
}

/// Fallback legado de UIImageView animado caso um GIF seja carregado
struct UIAnimatedImageView: UIViewRepresentable {
    let images: [UIImage]
    let duration: TimeInterval
    let isPlaying: Bool

    func makeUIView(context: Context) -> UIImageView {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.backgroundColor = .clear
        imageView.clipsToBounds = true
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        imageView.setContentHuggingPriority(.defaultLow, for: .horizontal)
        imageView.setContentHuggingPriority(.defaultLow, for: .vertical)
        return imageView
    }

    func updateUIView(_ uiView: UIImageView, context: Context) {
        guard !images.isEmpty else { return }
        if uiView.animationImages != images || uiView.animationDuration != duration {
            uiView.animationImages = images
            uiView.animationDuration = duration
            uiView.image = images.first
            uiView.startAnimating()
        }
        if isPlaying {
            if !uiView.isAnimating { uiView.startAnimating() }
        } else {
            if uiView.isAnimating { uiView.stopAnimating() }
        }
    }
}
