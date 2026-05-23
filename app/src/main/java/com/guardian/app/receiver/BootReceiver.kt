package com.guardian.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.app.lock.LockEngine
import com.guardian.app.service.LocalDnsVpnService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var lockEngine: LockEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        scope.launch {
            // 1 — Restart lock foreground service if a lock was active
            val lockState = lockEngine.lockStateFlow.first()
            if (lockState.isLocked && !lockState.isExpired) {
                val lockIntent = Intent(context, com.guardian.app.lock.LockForegroundService::class.java).apply {
                    putExtra(com.guardian.app.lock.LockForegroundService.EXTRA_LEVEL,   lockState.level)
                    putExtra(com.guardian.app.lock.LockForegroundService.EXTRA_END_MS,  lockState.lockEndMs)
                    putExtra(com.guardian.app.lock.LockForegroundService.EXTRA_MESSAGE, lockState.message)
                }
                context.startForegroundService(lockIntent)
            } else if (lockState.isExpired) {
                lockEngine.releaseLock()
            }

            // 2 — Restart VPN service (if it was active before reboot)
            // VPN is re-checked via user preferences in MainActivity on first launch
        }
    }
}
