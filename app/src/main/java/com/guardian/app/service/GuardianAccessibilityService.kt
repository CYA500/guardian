package com.guardian.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.engine.AlertEngine
import com.guardian.app.engine.ImageClassifier
import com.guardian.app.engine.KeywordEngine
import com.guardian.app.engine.ThreatLevel
import com.guardian.app.lock.LockEngine
import com.guardian.app.lock.ScreenLockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var keywordEngine: KeywordEngine
    @Inject lateinit var alertEngine: AlertEngine
    @Inject lateinit var lockEngine: LockEngine
    @Inject lateinit var screenLockManager: ScreenLockManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Debounce — avoid firing multiple alerts on the same text
    private var lastAnalysedText = ""
    private var lastAlertTime    = 0L
    private val ALERT_DEBOUNCE_MS = 3_000L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED         or
                AccessibilityEvent.TYPE_VIEW_FOCUSED              or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED      or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED    or
                AccessibilityEvent.TYPE_VIEW_SCROLLED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags        = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                           AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                           AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        scope.launch {
            handleEvent(event, pkg)
        }
    }

    private suspend fun handleEvent(event: AccessibilityEvent, pkg: String) {
        val lockState = lockEngine.currentState()

        // ── 1. If locked: enforce allowed-app policy ──────────────────
        if (lockState.isLocked && !lockState.isExpired) {
            val quranPkg = "com.quran.labs.androidquran" // TODO: from user prefs
            val allowed  = lockEngine.isPackageAllowed(pkg, lockState.level, quranPkg)
            if (!allowed) {
                screenLockManager.bringLockToFront(
                    lockState.level,
                    lockState.remainingMs,
                    lockState.message
                )
                return
            }
        } else if (lockState.isExpired) {
            lockEngine.releaseLock()
        }

        // ── 2. Text change — keyboard monitoring ──────────────────────
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString(" ").trim()
                analyseText(text)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Scrape visible text from the window for URL detection
                val root = rootInActiveWindow ?: return
                val fullText = extractAllText(root)
                analyseText(fullText)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Text analysis pipeline
    // ─────────────────────────────────────────────────────────────────

    private suspend fun analyseText(text: String) {
        if (text == lastAnalysedText) return
        if (text.length < 3) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < ALERT_DEBOUNCE_MS) return

        lastAnalysedText = text

        val result = keywordEngine.analyse(text)
        when (result.level) {
            ThreatLevel.LINK -> {
                lastAlertTime = now
                alertEngine.triggerLevel2()
                lockEngine.activateLock(
                    level            = 2,
                    durationMinutes  = (30L..120L).random(),
                    message          = alertEngine.randomMessage()
                )
                // Close the current browser/app
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            ThreatLevel.KEYWORD -> {
                lastAlertTime = now
                alertEngine.triggerLevel1()
                // Close keyboard
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            ThreatLevel.NONE -> { /* clean */ }
            else -> { /* IMAGE handled separately */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tree walker
    // ─────────────────────────────────────────────────────────────────

    private fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectText(node, sb, depth = 0)
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 10) return   // don't recurse too deep
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i) ?: continue, sb, depth + 1)
        }
    }

    override fun onInterrupt() { /* service interrupted */ }
}
