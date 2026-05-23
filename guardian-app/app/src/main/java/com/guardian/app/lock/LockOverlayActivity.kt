package com.guardian.app.lock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.guardian.app.engine.AlertEngine
import com.guardian.app.engine.GuardianAI
import com.guardian.app.ui.theme.GuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LockOverlayActivity : ComponentActivity() {

    @Inject lateinit var lockEngine: LockEngine
    @Inject lateinit var guardianAI: GuardianAI
    @Inject lateinit var alertEngine: AlertEngine

    companion object {
        const val EXTRA_THREAT_LEVEL   = "threat_level"
        const val EXTRA_LOCK_MINUTES   = "lock_minutes"
        const val EXTRA_MESSAGE        = "message"
        const val EXTRA_IS_LOCK_SCREEN = "is_lock_screen"

        private const val MESSAGE_DISPLAY_SECONDS = 30
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make this activity show over everything including the lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON          or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED        or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON          or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val threatLevel   = intent.getIntExtra(EXTRA_THREAT_LEVEL, 1)
        val lockMinutes   = intent.getLongExtra(EXTRA_LOCK_MINUTES, 0L)
        val message       = intent.getStringExtra(EXTRA_MESSAGE) ?: alertEngine.randomMessage()
        val isLockScreen  = intent.getBooleanExtra(EXTRA_IS_LOCK_SCREEN, false)

        val lockEndMs = if (isLockScreen) {
            // Already locked — get remaining from engine
            System.currentTimeMillis() + lockMinutes * 60_000L
        } else {
            System.currentTimeMillis() + lockMinutes * 60_000L
        }

        setContent {
            GuardianTheme {
                LockOverlayScreen(
                    threatLevel    = threatLevel,
                    lockEndMs      = lockEndMs,
                    initialMessage = message,
                    isLockScreen   = isLockScreen,
                    onTimerExpired = {
                        if (!isLockScreen) finish()
                        else {
                            lifecycleScope.launch { lockEngine.releaseLock() }
                            finish()
                        }
                    },
                    onMessageOnlyExpired = {
                        if (!isLockScreen) finish()
                    },
                    onAnalyzeUnlock = { text ->
                        val remaining = maxOf(0L, lockEndMs - System.currentTimeMillis())
                        guardianAI.analyzeUnlockRequest(text, remaining)
                    },
                    getNextMessage = { alertEngine.randomMessage() }
                )
            }
        }
    }

    // Block back button during lock
    override fun onBackPressed() { /* intentionally empty */ }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) return true
        return super.onKeyDown(keyCode, event)
    }
    override fun onUserLeaveHint() { /* prevent going to home */ }
}

// ─────────────────────────────────────────────────────────────────────────
// Compose UI
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun LockOverlayScreen(
    threatLevel: Int,
    lockEndMs: Long,
    initialMessage: String,
    isLockScreen: Boolean,
    onTimerExpired: () -> Unit,
    onMessageOnlyExpired: () -> Unit,
    onAnalyzeUnlock: (String) -> GuardianAI.AnalysisReport,
    getNextMessage: () -> String
) {
    val darkBg   = Color(0xFF0D1117)
    val gold     = Color(0xFFC9A84C)
    val softGold = Color(0xFFE8C97A)
    val white    = Color(0xFFF5F5F5)

    var currentMessage by remember { mutableStateOf(initialMessage) }
    var remainingMs    by remember { mutableStateOf(maxOf(0L, lockEndMs - System.currentTimeMillis())) }
    var msgCountdown   by remember { mutableStateOf(30) }      // 30-second visible message
    var showUnlock     by remember { mutableStateOf(false) }
    var unlockText     by remember { mutableStateOf("") }
    var unlockResponse by remember { mutableStateOf("") }

    // ── Countdown timer ──────────────────────────────────────────────
    LaunchedEffect(lockEndMs) {
        while (isActive) {
            delay(1_000)
            remainingMs = maxOf(0L, lockEndMs - System.currentTimeMillis())
            if (msgCountdown > 0) msgCountdown--
            else if (!isLockScreen) { onMessageOnlyExpired(); break }
            if (remainingMs <= 0L && isLockScreen) { onTimerExpired(); break }
        }
    }

    // ── Hourly message rotation (during lock screen) ──────────────────
    LaunchedEffect(isLockScreen) {
        if (!isLockScreen) return@LaunchedEffect
        while (isActive) {
            delay(3_600_000L)   // every hour
            currentMessage = getNextMessage()
        }
    }

    // ── Pulsating animation ───────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D1117), Color(0xFF111820), Color(0xFF0A0D12)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            // ── Shield icon (text-based) ─────────────────────────────
            Text(
                text  = "🛡",
                fontSize = 56.sp,
                modifier = Modifier.alpha(pulseAlpha)
            )

            // ── Main Islamic message ──────────────────────────────────
            Text(
                text       = currentMessage,
                color      = gold,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                style      = TextStyle(textDirection = TextDirection.Rtl),
                lineHeight = 42.sp,
                modifier   = Modifier
                    .fillMaxWidth()
                    .alpha(pulseAlpha)
            )

            // ── Countdown (lock screen only) ──────────────────────────
            if (isLockScreen && remainingMs > 0) {
                Divider(color = gold.copy(alpha = 0.3f), thickness = 1.dp)
                Text(
                    text      = formatDuration(remainingMs),
                    color     = softGold,
                    fontSize  = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text     = "الوقت المتبقي حتى انتهاء القفل",
                    color    = white.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    style    = TextStyle(textDirection = TextDirection.Rtl)
                )
            }

            // ── Message-only countdown (Level 1) ─────────────────────
            if (!isLockScreen && msgCountdown > 0) {
                Text(
                    text      = "$msgCountdown ثانية",
                    color     = white.copy(alpha = 0.4f),
                    fontSize  = 14.sp
                )
            }

            // ── Unlock request section ────────────────────────────────
            if (isLockScreen) {
                Spacer(Modifier.height(8.dp))

                if (!showUnlock) {
                    TextButton(onClick = { showUnlock = true }) {
                        Text(
                            "طلب فتح القفل",
                            color = white.copy(alpha = 0.3f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    UnlockRequestSection(
                        text       = unlockText,
                        onTextChange = { unlockText = it },
                        response   = unlockResponse,
                        onSubmit   = {
                            val report = onAnalyzeUnlock(unlockText)
                            unlockResponse = report.responseMessage
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnlockRequestSection(
    text: String,
    onTextChange: (String) -> Unit,
    response: String,
    onSubmit: () -> Unit
) {
    val gold  = Color(0xFFC9A84C)
    val white = Color(0xFFF5F5F5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "اكتب سبب طلب الفتح:",
            color    = white.copy(alpha = 0.7f),
            fontSize = 13.sp,
            style    = TextStyle(textDirection = TextDirection.Rtl)
        )

        BasicTextField(
            value         = text,
            onValueChange = onTextChange,
            textStyle     = TextStyle(
                color         = white,
                fontSize      = 14.sp,
                textDirection = TextDirection.Rtl
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .heightIn(min = 80.dp)
        )

        Button(
            onClick  = onSubmit,
            colors   = ButtonDefaults.buttonColors(containerColor = gold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إرسال", color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
        }

        if (response.isNotEmpty()) {
            Text(
                text      = response,
                color     = gold,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                style     = TextStyle(textDirection = TextDirection.Rtl),
                lineHeight = 22.sp,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
