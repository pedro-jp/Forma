@file:Suppress("DEPRECATION")

package com.forma.app.ui.screens.workouts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.catalog.ExerciseGifAssets
import com.forma.app.data.catalog.ExerciseVideoAssets
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reprodutor de vídeos 3D de 10s (`assets/ExerciseVideos/<id>.mp4`) com fallback transparente para GIF legado.
 */
@Composable
fun GifPlayer(
    gifName: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    showControls: Boolean = false
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(cornerRadius)

    val resolvedVideoKey = remember(gifName) {
        if (ExerciseVideoAssets.exists(context, gifName)) {
            ExerciseVideoAssets.cleanName(gifName)
        } else {
            val matched = ExerciseCatalog.item(gifName) ?: ExerciseCatalog.matchExercise(gifName)
            if (matched != null && ExerciseVideoAssets.exists(context, matched.videoName)) {
                matched.videoName
            } else {
                null
            }
        }
    }

    var isPlaying by remember(gifName) { mutableStateOf(true) }
    var playbackSpeed by remember(gifName) { mutableStateOf(1.0) }

    Box(modifier = modifier.clip(shape)) {
        if (resolvedVideoKey != null) {
            LoopingAssetMp4Texture(
                videoName = resolvedVideoKey,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed.toFloat(),
                modifier = Modifier.fillMaxSize()
            )
            if (showControls) {
                PlayerControlsOverlay(
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onToggleSpeed = { playbackSpeed = if (playbackSpeed == 1.0) 0.5 else 1.0 }
                )
            }
        } else {
            LegacyGifMovieFallback(
                gifName = gifName,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                showControls = showControls,
                onTogglePlay = { isPlaying = !isPlaying },
                onToggleSpeed = { playbackSpeed = if (playbackSpeed == 1.0) 0.5 else 1.0 },
                colors = colors
            )
        }
    }
}

@Composable
private fun LoopingAssetMp4Texture(
    videoName: String,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val holder = remember(videoName) { Mp4PlayerHolder() }

    DisposableEffect(videoName) {
        onDispose {
            holder.release()
        }
    }

    LaunchedEffect(videoName, isPlaying, playbackSpeed) {
        holder.updatePlayback(isPlaying = isPlaying, speed = playbackSpeed)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        holder.attachAndStart(ctx, videoName, Surface(surfaceTexture), this@apply, width, height, isPlaying, playbackSpeed)
                    }

                    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        holder.applyCenterCropTransform(this@apply, width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        holder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { tv ->
            holder.applyCenterCropTransform(tv, tv.width, tv.height)
            holder.updatePlayback(isPlaying = isPlaying, speed = playbackSpeed)
        }
    )
}

private class Mp4PlayerHolder {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoW: Int = 960
    private var videoH: Int = 540
    private var prepared: Boolean = false

    fun attachAndStart(
        context: Context,
        videoName: String,
        newSurface: Surface,
        textureView: TextureView,
        viewW: Int,
        viewH: Int,
        isPlaying: Boolean,
        speed: Float
    ) {
        release()
        surface = newSurface
        val afd = ExerciseVideoAssets.openFd(context, videoName) ?: return
        try {
            val mp = MediaPlayer()
            mp.setSurface(newSurface)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoW = w
                    videoH = h
                    applyCenterCropTransform(textureView, textureView.width, textureView.height)
                }
            }
            mp.setOnPreparedListener { player ->
                prepared = true
                videoW = max(1, player.videoWidth)
                videoH = max(1, player.videoHeight)
                applyCenterCropTransform(textureView, viewW, viewH)
                updatePlayback(isPlaying = isPlaying, speed = speed)
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (_: Throwable) {
            runCatching { afd.close() }
        }
    }

    fun updatePlayback(isPlaying: Boolean, speed: Float) {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        runCatching {
            if (isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        }
    }

    fun applyCenterCropTransform(tv: TextureView, viewW: Int, viewH: Int) {
        if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return
        val viewRatio = viewW.toFloat() / viewH.toFloat()
        val videoRatio = videoW.toFloat() / videoH.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoRatio > viewRatio) {
            scaleX = videoRatio / viewRatio
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewRatio / videoRatio
        }
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        tv.setTransform(matrix)
    }

    fun release() {
        prepared = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { surface?.release() }
        surface = null
    }
}

@Composable
private fun PlayerControlsOverlay(
    isPlaying: Boolean,
    playbackSpeed: Double,
    onTogglePlay: () -> Unit,
    onToggleSpeed: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (playbackSpeed == 1.0) "1.0x" else "0.5x Lento",
            color = Color.White,
            style = FormaTypography.rounded(11f, FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .formaPlainClickable { onToggleSpeed() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .formaPlainClickable { onTogglePlay() }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LegacyGifMovieFallback(
    gifName: String,
    isPlaying: Boolean,
    playbackSpeed: Double,
    showControls: Boolean,
    onTogglePlay: () -> Unit,
    onToggleSpeed: () -> Unit,
    colors: FormaTheme.ColorTokens
) {
    val context = LocalContext.current
    var movie by remember(gifName) { mutableStateOf<Movie?>(null) }
    var timeMs by remember(gifName) { mutableLongStateOf(0L) }

    LaunchedEffect(gifName) {
        movie = withContext(Dispatchers.IO) { GifMovieCache.load(context, gifName) }
    }

    val loaded = movie
    LaunchedEffect(loaded, isPlaying, playbackSpeed) {
        if (loaded == null || !isPlaying) return@LaunchedEffect
        var last = withFrameNanosCompat()
        while (true) {
            val now = withFrameNanosCompat()
            val realDeltaMs = (now - last) / 1_000_000.0
            if (realDeltaMs >= 33) {
                timeMs += (realDeltaMs * playbackSpeed).toLong()
                last = now
            }
        }
    }

    if (loaded != null) {
        val duration = max(500, loaded.duration())
        val bitmap = remember(loaded) {
            Bitmap.createBitmap(max(1, loaded.width()), max(1, loaded.height()), Bitmap.Config.ARGB_8888)
        }
        val bitmapCanvas = remember(bitmap) { android.graphics.Canvas(bitmap) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val t = (timeMs % duration).toInt()
                    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                    loaded.setTime(if (loaded.duration() > 0) t else 0)
                    loaded.draw(bitmapCanvas, 0f, 0f)

                    val gw = bitmap.width.toFloat()
                    val gh = bitmap.height.toFloat()
                    val destAspect = size.width / size.height
                    val gifAspect = gw / gh
                    val srcW: Float
                    val srcH: Float
                    if (gifAspect > destAspect) {
                        srcH = gh; srcW = gh * destAspect
                    } else {
                        srcW = gw; srcH = gw / destAspect
                    }
                    val srcX = (gw - srcW) / 2f
                    val srcY = (gh - srcH) / 2f
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        srcOffset = IntOffset(srcX.roundToInt(), srcY.roundToInt()),
                        srcSize = IntSize(srcW.roundToInt(), srcH.roundToInt()),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
        )
        if (showControls) {
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                onTogglePlay = onTogglePlay,
                onToggleSpeed = onToggleSpeed
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(red = 0.05f, green = 0.07f, blue = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = colors.energyGreen,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = ExerciseVideoAssets.PLACEHOLDER_TEXT,
                    color = colors.secondaryText,
                    style = FormaTypography.system(12f, FontWeight.SemiBold)
                )
            }
        }
    }
}

private suspend fun withFrameNanosCompat(): Long =
    androidx.compose.runtime.withFrameNanos { it }

private object GifMovieCache {
    private val cache = object : android.util.LruCache<String, Movie>(24) {}

    @Synchronized
    fun load(context: Context, gifName: String): Movie? {
        val key = ExerciseGifAssets.cleanName(gifName)
        if (key.isEmpty()) return null
        cache.get(key)?.let { return it }
        val bytes = ExerciseGifAssets.open(context, key)?.use { it.readBytes() } ?: return null
        val decoded = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        cache.put(key, decoded)
        return decoded
    }
}
