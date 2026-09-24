package ir.promptall.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocalImageLabel(
    val text: String,
    val confidence: Double,
)

object ImageLabelAnalyzer {
    suspend fun fromUri(context: Context, uri: Uri): List<LocalImageLabel> =
        suspendCancellableCoroutine { continuation ->
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }

            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.50f)
                .build()
            val labeler = ImageLabeling.getClient(options)

            continuation.invokeOnCancellation { labeler.close() }
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    continuation.resume(
                        labels
                            .sortedByDescending { it.confidence }
                            .take(8)
                            .map {
                                LocalImageLabel(
                                    text = it.text.trim(),
                                    confidence = it.confidence.toDouble(),
                                )
                            }
                            .filter { it.text.isNotBlank() }
                    )
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                .addOnCompleteListener { labeler.close() }
        }
}
