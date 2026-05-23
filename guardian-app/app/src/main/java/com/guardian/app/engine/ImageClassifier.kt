package com.guardian.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of a single image classification pass. */
data class ClassificationResult(
    val isExplicit: Boolean,
    val confidence: Float,
    val label: String
)

@Singleton
class ImageClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val MODEL_FILENAME = "guardian_classifier.tflite"
        private const val INPUT_SIZE = 224
        private const val EXPLICIT_CONFIDENCE_THRESHOLD = 0.72f

        // ML Kit fallback labels that suggest explicit content
        private val NSFW_MLKIT_LABELS = setOf(
            "Pornography", "Explicit", "Nudity", "Underwear",
            "Swimwear", "Bikini", "Lingerie"
        )
        private const val MLKIT_CONFIDENCE_THRESHOLD = 0.75f
    }

    // ─────────────────────────────────────────────────────────────────
    // TFLite interpreter (nullable — only loaded if model asset exists)
    // ─────────────────────────────────────────────────────────────────
    private val interpreter: Interpreter? by lazy {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = true          // hardware accelerated when available
            }
            Interpreter(model, options)
        } catch (e: Exception) {
            null  // model asset not bundled; fall back to ML Kit
        }
    }

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /** Classify a [Bitmap]. Runs on IO dispatcher. */
    suspend fun classify(bitmap: Bitmap): ClassificationResult = withContext(Dispatchers.IO) {
        interpreter?.let { return@withContext classifyWithTFLite(it, bitmap) }
        classifyWithMlKit(bitmap)
    }

    /** Convenience: load a [Uri] then classify. */
    suspend fun classifyUri(uri: Uri): ClassificationResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return@withContext ClassificationResult(false, 0f, "unreadable")
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()
        classify(bitmap)
    }

    // ─────────────────────────────────────────────────────────────────
    // TFLite path
    // ─────────────────────────────────────────────────────────────────

    private fun classifyWithTFLite(interp: Interpreter, bitmap: Bitmap): ClassificationResult {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Model output: [1, 2] — [safe_prob, explicit_prob]
        val output = Array(1) { FloatArray(2) }
        interp.run(processedImage.buffer, output)

        val safeScore    = output[0][0]
        val explicitScore = output[0][1]
        val isExplicit = explicitScore >= EXPLICIT_CONFIDENCE_THRESHOLD

        return ClassificationResult(
            isExplicit = isExplicit,
            confidence = if (isExplicit) explicitScore else safeScore,
            label = if (isExplicit) "explicit" else "safe"
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // ML Kit fallback path
    // ─────────────────────────────────────────────────────────────────

    private suspend fun classifyWithMlKit(bitmap: Bitmap): ClassificationResult =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                // Uses on-device base model — no custom model needed
                val labeler = ImageLabeling.getClient(
                    com.google.mlkit.vision.label.defaults.ImageLabelerOptions.DEFAULT_OPTIONS
                )
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        val explicitLabel = labels.firstOrNull { label ->
                            NSFW_MLKIT_LABELS.any {
                                label.text.contains(it, ignoreCase = true)
                            } && label.confidence >= MLKIT_CONFIDENCE_THRESHOLD
                        }
                        if (explicitLabel != null) {
                            cont.resume(
                                ClassificationResult(
                                    isExplicit = true,
                                    confidence = explicitLabel.confidence,
                                    label = explicitLabel.text
                                )
                            )
                        } else {
                            cont.resume(ClassificationResult(false, 1f, "safe"))
                        }
                        labeler.close()
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                        labeler.close()
                    }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

    // ─────────────────────────────────────────────────────────────────
    // Model loader
    // ─────────────────────────────────────────────────────────────────

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd(MODEL_FILENAME)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
    }
}
