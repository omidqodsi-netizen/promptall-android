package ir.promptall.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ImageFingerprintResult(
    val phash: String,
    val dhash: String,
    val ahash: String,
    val hist: List<Int>,
)

object ImageFingerprint {
    private val cosTable: Array<DoubleArray> by lazy {
        Array(8) { u ->
            DoubleArray(32) { x ->
                cos((2 * x + 1) * u * PI / 64.0)
            }
        }
    }

    fun fromUri(context: Context, uri: Uri): ImageFingerprintResult {
        val bitmap = decodeSampled(context, uri, 1024)
            ?: throw IllegalArgumentException("تصویر قابل خواندن نیست.")
        return try {
            ImageFingerprintResult(
                phash = pHash(bitmap),
                dhash = dHash(bitmap),
                ahash = aHash(bitmap),
                hist = histogram(bitmap),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun pHash(source: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(source, 32, 32, true)
        val gray = Array(32) { DoubleArray(32) }
        try {
            val pixels = IntArray(32 * 32)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    gray[y][x] = gray(pixels[y * 32 + x])
                }
            }
        } finally {
            if (scaled !== source) scaled.recycle()
        }

        val coeff = DoubleArray(64)
        var index = 0
        for (v in 0 until 8) {
            for (u in 0 until 8) {
                var sum = 0.0
                for (y in 0 until 32) {
                    val cy = cosTable[v][y]
                    for (x in 0 until 32) {
                        sum += gray[y][x] * cosTable[u][x] * cy
                    }
                }
                val au = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val av = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                coeff[index++] = 0.25 * au * av * sum
            }
        }
        val medianValues = coeff.drop(1).sorted()
        val median = medianValues[medianValues.size / 2]
        var hash = 0UL
        coeff.forEachIndexed { bit, value ->
            if (value > median) hash = hash or (1UL shl (63 - bit))
        }
        return hash.toString(16).padStart(16, '0')
    }

    private fun dHash(source: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(source, 9, 8, true)
        return try {
            val pixels = IntArray(9 * 8)
            scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            var hash = 0UL
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    if (gray(pixels[y * 9 + x]) > gray(pixels[y * 9 + x + 1])) {
                        hash = hash or (1UL shl (63 - bit))
                    }
                    bit++
                }
            }
            hash.toString(16).padStart(16, '0')
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun aHash(source: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(source, 8, 8, true)
        return try {
            val pixels = IntArray(64)
            scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            val values = DoubleArray(64) { index -> gray(pixels[index]) }
            val average = values.average()
            var hash = 0UL
            values.forEachIndexed { bit, value ->
                if (value >= average) {
                    hash = hash or (1UL shl (63 - bit))
                }
            }
            hash.toString(16).padStart(16, '0')
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun histogram(source: Bitmap): List<Int> {
        val scaled = Bitmap.createScaledBitmap(source, 96, 96, true)
        return try {
            val pixels = IntArray(96 * 96)
            scaled.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            val bins = IntArray(24)
            pixels.forEach { color ->
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                bins[(r / 32).coerceAtMost(7)]++
                bins[8 + (g / 32).coerceAtMost(7)]++
                bins[16 + (b / 32).coerceAtMost(7)]++
            }
            val total = pixels.size.toDouble()
            bins.map { ((it / total) * 255.0).roundToInt().coerceIn(0, 255) }
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun gray(color: Int): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
