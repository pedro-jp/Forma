package com.forma.app.ui.screens.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

// Porte de FoodImagePicker.swift: captura (câmera/galeria) e compressão otimizada para o Gemini Vision.

/** Foto escolhida: [preview] para exibição e [jpegData] já comprimido para envio ao Gemini. */
class PickedFoodImage(val preview: Bitmap, val jpegData: ByteArray)

/** Cria o arquivo temporário + URI do FileProvider usados pelo `TakePicture`. */
fun createFoodCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "food_photos").apply { mkdirs() }
    val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Lê a imagem do [uri] (câmera ou galeria), aplica a rotação EXIF e comprime em JPEG
 * (maior lado 1024 px, qualidade 72%), como `UIImage.compressedForGemini` do iOS.
 * Retorna `null` quando a imagem não puder ser processada.
 */
suspend fun loadFoodImage(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1024,
    quality: Int = 72
): PickedFoodImage? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        // Decodifica já reduzida (no máximo ~2x o lado alvo) para poupar memória.
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxDimension * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        } else decoded

        val data = upright.compressedForGemini(maxDimension, quality) ?: return@runCatching null
        PickedFoodImage(preview = upright, jpegData = data)
    }.getOrNull()
}

/** Redimensiona (maior lado = [maxDimension]) e comprime em JPEG otimizado para envio rápido ao Gemini Vision. */
fun Bitmap.compressedForGemini(maxDimension: Int = 1024, quality: Int = 72): ByteArray? {
    if (width <= 0 || height <= 0) return null
    val longestSide = max(width, height)
    val ratio = if (longestSide > maxDimension) maxDimension.toFloat() / longestSide.toFloat() else 1f
    val target = if (ratio < 1f) {
        Bitmap.createScaledBitmap(this, (width * ratio).roundToInt().coerceAtLeast(1), (height * ratio).roundToInt().coerceAtLeast(1), true)
    } else this
    val out = ByteArrayOutputStream()
    if (!target.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
    return out.toByteArray()
}
