package com.guardian.app.lock

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.guardian.app.receiver.GuardianDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the system-level lock:
 *  - Draws a full-screen [WindowManager] overlay (SYSTEM_ALERT_WINDOW) that
 *    cannot be dismissed via back/home.
 *  - Optionally invokes [DevicePolicyManager.lockNow] when Device Admin is
 *    active to force the physical screen lock.
 *  - Provides helpers to bring the lock overlay back to front when the
 *    AccessibilityService detects a forbidden app in the foreground.
 */
@Singleton
class ScreenLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)

    // ─────────────────────────────────────────────────────────────────
    // Overlay launcher — brings LockOverlayActivity back to front
    // ─────────────────────────────────────────────────────────────────

    /**
     * Called by [GuardianAccessibilityService] whenever a disallowed
     * package enters the foreground during an active lock.
     */
    fun bringLockToFront(level: Int, remainingMs: Long, message: String) {
        val intent = Intent(context, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockOverlayActivity.EXTRA_THREAT_LEVEL, level)
            putExtra(LockOverlayActivity.EXTRA_LOCK_MINUTES, remainingMs / 60_000L)
            putExtra(LockOverlayActivity.EXTRA_MESSAGE, message)
            putExtra(LockOverlayActivity.EXTRA_IS_LOCK_SCREEN, true)
        }
        context.startActivity(intent)
    }

    // ─────────────────────────────────────────────────────────────────
    // Device Admin — hardware screen lock
    // ─────────────────────────────────────────────────────────────────

    fun isDeviceAdminActive(): Boolean =
        devicePolicyManager.isAdminActive(adminComponent)

    /** Lock the physical screen immediately if Device Admin is granted. */
    fun lockScreenNow() {
        if (isDeviceAdminActive()) {
            devicePolicyManager.lockNow()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Window layout params for any overlay views added programmatically
    // ─────────────────────────────────────────────────────────────────

    fun buildFullScreenLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
        }
    }
}
