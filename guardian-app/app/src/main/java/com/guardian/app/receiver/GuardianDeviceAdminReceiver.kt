package com.guardian.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin activated — no extra action needed
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // User removed admin — guard will lose lock-screen capability
    }
}
