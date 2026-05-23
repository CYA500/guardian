package com.guardian.app.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyses a free-text unlock request written by the user.
 *
 * By design, [analyzeUnlockRequest] always returns **false** — there is no
 * reason sufficient to bypass a lock during an active session.  The analysis
 * is performed anyway to detect automated/spam input and to provide a
 * personalised (but always-refusing) response.
 */
@Singleton
class GuardianAI @Inject constructor() {

    data class AnalysisReport(
        val approved: Boolean,           // always false
        val isSpam: Boolean,
        val isCoherent: Boolean,
        val detectedIntent: String,
        val responseMessage: String,
        val remainingMs: Long
    )

    // ─────────────────────────────────────────────────────────────────
    // Main analyser
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param text        The text typed by the user in the unlock request field.
     * @param remainingMs Milliseconds left on the current lock.
     */
    fun analyzeUnlockRequest(text: String, remainingMs: Long): AnalysisReport {

        val isSpam      = detectSpam(text)
        val isCoherent  = !isSpam && detectCoherence(text)
        val intent      = classifyIntent(text)

        // Remaining time formatted as HH:MM:SS
        val formatted   = formatDuration(remainingMs)

        val response = buildResponseMessage(formatted, isSpam, isCoherent, intent)

        return AnalysisReport(
            approved        = false,   // ← intentionally hardcoded; no reason is sufficient
            isSpam          = isSpam,
            isCoherent      = isCoherent,
            detectedIntent  = intent,
            responseMessage = response,
            remainingMs     = remainingMs
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Spam detection
    // ─────────────────────────────────────────────────────────────────

    /**
     * Detects autocomplete/keyboard spam: measures inter-word timing via
     * simple heuristics (word count vs. text length) and checks for
     * repetitive padding patterns.
     */
    private fun detectSpam(text: String): Boolean {
        if (text.isBlank()) return true

        val words = text.trim().split(Regex("\\s+"))
        if (words.size < 2) return true   // single word — not a real explanation

        // Average characters per word — very short average suggests autocomplete gibberish
        val avgLen = text.replace(" ", "").length.toDouble() / words.size
        if (avgLen < 2.5) return true

        // Detect repeated tokens (spam padding: "please please please")
        val distinct = words.map { it.lowercase() }.toSet()
        val repetitionRatio = distinct.size.toDouble() / words.size
        if (repetitionRatio < 0.4) return true

        return false
    }

    // ─────────────────────────────────────────────────────────────────
    // Coherence detection
    // ─────────────────────────────────────────────────────────────────

    private fun detectCoherence(text: String): Boolean {
        val lower = text.lowercase()

        // Minimum length threshold
        if (lower.length < 20) return false

        // Must contain at least one "reason" connector
        val connectors = listOf(
            "لأن", "بسبب", "أريد", "أحتاج", "ضروري", "مهم", "عاجل",
            "because", "need", "urgent", "important", "must", "have to",
            "emergency", "طوارئ", "ضرورة"
        )
        return connectors.any { lower.contains(it) }
    }

    // ─────────────────────────────────────────────────────────────────
    // Intent classification
    // ─────────────────────────────────────────────────────────────────

    private fun classifyIntent(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("طوار") || lower.contains("emergency") -> "emergency_claim"
            lower.contains("عمل") || lower.contains("work") || lower.contains("دراسة") -> "work_claim"
            lower.contains("أخطأ") || lower.contains("mistake") || lower.contains("خطأ") -> "mistake_claim"
            lower.contains("مرة") || lower.contains("once") || lower.contains("فقط") -> "one_time_claim"
            lower.isBlank() -> "empty"
            else -> "general_plea"
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Response builder
    // ─────────────────────────────────────────────────────────────────

    private fun buildResponseMessage(
        remaining: String,
        isSpam: Boolean,
        isCoherent: Boolean,
        intent: String
    ): String {
        val preamble = when {
            isSpam -> "فَهِمنَا مَا كَتَبتَ.\n"
            !isCoherent -> "فَهِمنَا مَا كَتَبتَ.\n"
            intent == "emergency_claim" ->
                "فَهِمنَا مَا كَتَبتَ.\nإِن كَانَ طَارِئاً حَقِيقِياً، المَكَالِمَاتُ وَالرَّسَائِلُ مَفتُوحَة.\n"
            intent == "work_claim" ->
                "فَهِمنَا مَا كَتَبتَ.\nلَكِنَّ أَنتَ مَن اختَارَ هَذَا القَرَارَ بِنَفسِهِ.\n"
            else -> "فَهِمنَا مَا كَتَبتَ.\nلَكِنَّ أَنتَ مَن اختَارَ هَذَا القَرَارَ بِنَفسِهِ.\n"
        }

        return preamble +
               "الوَقتُ المُتَبَقِّي: $remaining\n\n" +
               "﴿وَاصبِر وَمَا صَبرُكَ إِلَّا بِاللهِ﴾"
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00:00"
        val totalSeconds = ms / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
