package ir.promptall.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import ir.promptall.app.data.remote.ImageSearchAiAnalysis
import ir.promptall.app.data.remote.ImageSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class VerifiedImageSearchMatch(
    val id: Long,
    val confidence: Double,
)

object GeminiImageSearchClient {
    private const val MAX_EDGE = 1400
    private const val JPEG_QUALITY = 84
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(
        context: Context,
        uri: Uri,
        apiKey: String,
        models: List<String>,
        vpnNote: String,
    ): ImageSearchAiAnalysis = withContext(Dispatchers.IO) {
        val image = encodeImage(context, uri)
        val instruction = """
            You analyze a user image for a Persian prompt marketplace.
            Return ONLY strict JSON, without markdown or code fences.
            JSON schema:
            {"short_description":"...","keywords":["..."],"persian_keywords":["..."],"english_prompt":"...","persian_prompt":"..."}
            Describe the visual concept faithfully for image generation. Include subject type, pose, composition,
            clothing, environment, camera framing, lighting, mood, visual style and distinctive objects.
            The exact identity/face is not important for retrieval; the composition and concept are.
        """.trimIndent()

        val raw = callWithFallback(
            apiKey = apiKey,
            models = normalizeModels(models),
            instruction = instruction,
            imageMime = image.mime,
            imageBase64 = image.base64,
            vpnNote = vpnNote,
        )
        parseAnalysis(raw)
    }

    suspend fun verify(
        context: Context,
        uri: Uri,
        candidates: List<ImageSearchItem>,
        apiKey: String,
        models: List<String>,
        vpnNote: String,
    ): List<VerifiedImageSearchMatch> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()
        val image = encodeImage(context, uri)
        val compactCandidates = candidates.take(6).joinToString("\n\n") { item ->
            "ID=${item.id}\nTitle: ${item.title.take(220)}\nPrompt: ${item.promptText.take(1400)}"
        }
        val instruction = """
            You are the final verifier for an image-to-prompt search engine.
            Compare the attached user image with the candidate prompts below.
            IMPORTANT: the person's exact face/identity may be different and must NOT by itself cause rejection.
            Judge the visual concept: subject type, pose, composition, clothing, environment, camera framing,
            lighting, mood, visual style and distinctive objects.
            Generic similarities such as both being portraits, both having a dark background, or both showing a man
            are not enough. Select only candidates that would plausibly generate an image strongly similar in composition
            and concept to the uploaded image.
            Return ONLY strict JSON:
            {"none":true|false,"matches":[{"id":123,"confidence":0.0}]}
            Maximum 3 matches. Only include confidence >= 0.78.

            $compactCandidates
        """.trimIndent()

        val raw = callWithFallback(
            apiKey = apiKey,
            models = normalizeModels(models),
            instruction = instruction,
            imageMime = image.mime,
            imageBase64 = image.base64,
            vpnNote = vpnNote,
        )
        parseVerification(raw)
    }

    private data class EncodedImage(val mime: String, val base64: String)

    private fun encodeImage(context: Context, uri: Uri): EncodedImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("تصویر قابل خواندن نیست.")
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("تصویر قابل خواندن نیست.")
        return try {
            val largest = max(source.width, source.height)
            val scale = if (largest > MAX_EDGE) MAX_EDGE.toDouble() / largest.toDouble() else 1.0
            val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = if (targetWidth != source.width || targetHeight != source.height) {
                Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            } else source
            try {
                val stream = ByteArrayOutputStream()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IllegalStateException("آماده‌سازی تصویر برای هوش مصنوعی انجام نشد.")
                }
                EncodedImage("image/jpeg", Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP))
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun normalizeModels(models: List<String>): List<String> {
        val defaults = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-1.5-flash", "gemini-1.5-flash-8b")
        return (models + defaults).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun callWithFallback(
        apiKey: String,
        models: List<String>,
        instruction: String,
        imageMime: String,
        imageBase64: String,
        vpnNote: String,
    ): String {
        if (apiKey.isBlank()) throw IllegalStateException("کلید Gemini در تنظیمات سایت ثبت نشده است.")
        var lastError = ""
        models.forEach { model ->
            try {
                return callModel(apiKey, model, instruction, imageMime, imageBase64)
            } catch (error: Throwable) {
                lastError = error.message.orEmpty()
            }
        }
        val prefix = vpnNote.trim().takeIf { it.isNotEmpty() }?.let { "$it " }.orEmpty()
        throw IllegalStateException(prefix + "اتصال به Gemini برقرار نشد. " + lastError)
    }

    private fun callModel(
        apiKey: String,
        model: String,
        instruction: String,
        imageMime: String,
        imageBase64: String,
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            URLEncoder.encode(model, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8")
        val payload = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to instruction),
                        mapOf("inlineData" to mapOf("mimeType" to imageMime, "data" to imageBase64)),
                    ),
                )
            ),
            "generationConfig" to mapOf("temperature" to 0.1, "responseMimeType" to "application/json"),
        )
        val request = Request.Builder().url(url).post(gson.toJson(payload).toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(body, JsonObject::class.java)?.getAsJsonObject("error")?.get("message")?.asString
                }.getOrNull()
                throw IllegalStateException(message ?: "Gemini HTTP ${response.code}")
            }
            return extractCandidateText(body)
        }
    }

    private fun extractCandidateText(body: String): String {
        val root = gson.fromJson(body, JsonObject::class.java) ?: throw IllegalStateException("پاسخ Gemini خالی است.")
        val candidates = root.getAsJsonArray("candidates") ?: throw IllegalStateException("پاسخ Gemini نتیجه‌ای ندارد.")
        if (candidates.size() == 0) throw IllegalStateException("پاسخ Gemini نتیجه‌ای ندارد.")
        val content = candidates[0].asJsonObject.getAsJsonObject("content") ?: throw IllegalStateException("پاسخ Gemini قابل خواندن نیست.")
        val parts = content.getAsJsonArray("parts") ?: throw IllegalStateException("پاسخ Gemini قابل خواندن نیست.")
        val text = buildString {
            parts.forEach { part -> part.asJsonObject.get("text")?.takeIf { !it.isJsonNull }?.asString?.let(::append) }
        }.trim()
        if (text.isBlank()) throw IllegalStateException("پاسخ Gemini قابل خواندن نیست.")
        return text
    }

    private fun cleanJson(text: String): String {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    private fun parseAnalysis(raw: String): ImageSearchAiAnalysis {
        val result = gson.fromJson(cleanJson(raw), ImageSearchAiAnalysis::class.java)
            ?: throw IllegalStateException("پاسخ تحلیل Gemini قابل خواندن نیست.")
        if (result.englishPrompt.isBlank() && result.persianPrompt.isBlank() && result.keywords.isEmpty() && result.persianKeywords.isEmpty()) {
            throw IllegalStateException("Gemini تحلیل معتبری برنگرداند.")
        }
        return result
    }

    private fun parseVerification(raw: String): List<VerifiedImageSearchMatch> {
        val root = gson.fromJson(cleanJson(raw), JsonObject::class.java) ?: return emptyList()
        if (root.get("none")?.asBoolean == true) return emptyList()
        val matches = root.getAsJsonArray("matches") ?: return emptyList()
        return matches.mapNotNull { element ->
            val item = element.asJsonObject
            val id = runCatching { item.get("id")?.asLong ?: 0L }.getOrDefault(0L)
            val confidence = runCatching { item.get("confidence")?.asDouble ?: 0.0 }.getOrDefault(0.0)
            if (id > 0 && confidence >= 0.78) VerifiedImageSearchMatch(id, confidence) else null
        }.sortedByDescending { it.confidence }.take(3)
    }
}

