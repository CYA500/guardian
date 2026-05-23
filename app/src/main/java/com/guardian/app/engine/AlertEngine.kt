package com.guardian.app.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.guardian.app.lock.LockOverlayActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ─────────────────────────────────────────────────────────────────
    // Islamic reminder messages (Arabic)
    // ─────────────────────────────────────────────────────────────────
    private val islamicMessages = listOf(
        "أَمَا عَلِمتَ أَنَّ اللهَ يَرَاكَ؟",
        "اصبِر، مَوعِدُكَ الجَنَّة",
        "الحُورُ العِينُ تَنتَظِرُكَ فَلَا تَيأَس",
        "﴿وَلَا يُلَقَّاهَا إِلَّا الصَّابِرُونَ﴾",
        "غُضَّ بَصَرَكَ يَرحَمكَ اللهُ",
        "كُن مَعَ اللهِ يَكُن مَعَكَ",
        "﴿إِنَّ اللهَ مَعَ الصَّابِرِينَ﴾",
        "ذَكِّر نَفسَكَ بِمَن تُحِب",
        "﴿وَاصبِر وَمَا صَبرُكَ إِلَّا بِاللهِ﴾",
        "الجَنَّةُ أَقرَبُ مِمَّا تَظُنّ",
        "﴿إِنَّ اللهَ لَا يُضِيعُ أَجرَ المُحسِنِينَ﴾",
        "كُلُّ نَفسٍ ذَائِقَةُ المَوت — مَاذَا أَعدَدتَ؟",
        "﴿وَمَن يَتَّقِ اللهَ يَجعَل لَهُ مَخرَجاً﴾",
        "تَذَكَّر مَلَكَيِ الكِتَابَة — يَكتُبَانِ الآن",
        "﴿فَمَن يَعمَل مِثقَالَ ذَرَّةٍ خَيراً يَرَهُ﴾",
        "أَنتَ أَغلَى مِن أَن تَرضَى بِالدَّنِيَّات",
        "﴿وَلَا تَقرَبُوا الفَوَاحِشَ مَا ظَهَرَ مِنهَا وَمَا بَطَن﴾",
        "تَخَيَّل وَجهَ أُمِّكَ — تَستَحِقُّ مَنكَ الأَحسَن",
        "﴿إِنَّ الَّذِينَ يَتَّقُونَ إِذَا مَسَّهُم طَائِفٌ مِنَ الشَّيطَانِ تَذَكَّرُوا﴾",
        "الإِيمَانُ يَزِيدُ بِالطَّاعَةِ وَيَنقُصُ بِالمَعصِيَة"
    )

    fun randomMessage(): String = islamicMessages.random()

    // ─────────────────────────────────────────────────────────────────
    // Vibration
    // ─────────────────────────────────────────────────────────────────

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Short vibration — keyword detected (500ms) */
    fun vibrateLevel1() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    /** Long vibration — image detected (1000ms) */
    fun vibrateLevel3() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 100, 300, 100, 500),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 300, 100, 500), -1)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Overlay launcher
    // ─────────────────────────────────────────────────────────────────

    /**
     * Launch the full-screen Islamic message overlay.
     *
     * @param threatLevel 1 = keyword, 2 = link, 3 = image
     * @param lockDurationMinutes  0 = no lock (just 30-second message),
     *                             else the lock duration in minutes
     */
    fun showOverlay(
        threatLevel: Int,
        lockDurationMinutes: Long = 0L
    ) {
        val intent = Intent(context, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(LockOverlayActivity.EXTRA_THREAT_LEVEL, threatLevel)
            putExtra(LockOverlayActivity.EXTRA_LOCK_MINUTES, lockDurationMinutes)
            putExtra(LockOverlayActivity.EXTRA_MESSAGE, randomMessage())
        }
        context.startActivity(intent)
    }

    // ─────────────────────────────────────────────────────────────────
    // Convenience fire-and-forget methods
    // ─────────────────────────────────────────────────────────────────

    /** Level 1: keyword in keyboard — vibrate + 30s message, no lock */
    fun triggerLevel1() {
        vibrateLevel1()
        showOverlay(threatLevel = 1, lockDurationMinutes = 0L)
    }

    /** Level 2: blocked URL/site — vibrate + 30s message + partial lock */
    fun triggerLevel2() {
        vibrateLevel1()
        val lockMinutes = (30L..120L).random()
        showOverlay(threatLevel = 2, lockDurationMinutes = lockMinutes)
    }

    /** Level 3: actual image detected — heavy vibrate + full lock */
    fun triggerLevel3() {
        vibrateLevel3()
        val lockHours = (5L..24L).random()
        showOverlay(threatLevel = 3, lockDurationMinutes = lockHours * 60L)
    }
}
