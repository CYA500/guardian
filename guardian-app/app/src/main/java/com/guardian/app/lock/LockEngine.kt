package com.guardian.app.lock

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("guardian_lock")

/** Describes the current active lock state. */
data class LockState(
    val isLocked: Boolean,
    val level: Int,                   // 1, 2, or 3
    val lockEndMs: Long,              // epoch ms when the lock expires
    val message: String
) {
    val remainingMs: Long get() = maxOf(0L, lockEndMs - System.currentTimeMillis())
    val isExpired: Boolean get() = lockEndMs > 0 && System.currentTimeMillis() >= lockEndMs
}

@Singleton
class LockEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // DataStore keys
        private val KEY_LOCKED        = booleanPreferencesKey("locked")
        private val KEY_LEVEL         = intPreferencesKey("level")
        private val KEY_LOCK_END_MS   = longPreferencesKey("lock_end_ms")
        private val KEY_MESSAGE       = stringPreferencesKey("message")

        // ── Packages allowed during any lock ─────────────────────
        val ALWAYS_ALLOWED_PACKAGES = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
        )

        // ── Additional apps allowed during level-3 lock ───────────
        // User must configure the Quran app package in Settings.
        // Default: com.quran.labs.androidquran
        const val DEFAULT_QURAN_PACKAGE = "com.quran.labs.androidquran"

        // ── Packages fully blocked in level-2 partial lock ────────
        val ENTERTAINMENT_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.twitter.android",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.tiktok.android",
            "com.zhiliaoapp.musically",
            "com.netflix.mediaclient",
            "com.spotify.music",
            "com.google.android.apps.photos",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Reactive state stream
    // ─────────────────────────────────────────────────────────────────

    val lockStateFlow: Flow<LockState> = context.dataStore.data.map { prefs ->
        LockState(
            isLocked  = prefs[KEY_LOCKED]      ?: false,
            level     = prefs[KEY_LEVEL]        ?: 0,
            lockEndMs = prefs[KEY_LOCK_END_MS]  ?: 0L,
            message   = prefs[KEY_MESSAGE]      ?: ""
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Activate a lock
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param level          1 = message only (no lock), 2 = partial, 3 = full
     * @param durationMinutes  Duration of lock in minutes (0 = message overlay only)
     * @param message        Islamic message to display on the lock screen
     */
    suspend fun activateLock(level: Int, durationMinutes: Long, message: String) {
        val endMs = if (durationMinutes > 0)
            System.currentTimeMillis() + durationMinutes * 60_000L
        else 0L

        context.dataStore.edit { prefs ->
            prefs[KEY_LOCKED]      = durationMinutes > 0
            prefs[KEY_LEVEL]       = level
            prefs[KEY_LOCK_END_MS] = endMs
            prefs[KEY_MESSAGE]     = message
        }

        if (durationMinutes > 0) {
            startLockService(level, endMs, message)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Release (called by LockForegroundService when timer expires)
    // ─────────────────────────────────────────────────────────────────

    suspend fun releaseLock() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCKED]      = false
            prefs[KEY_LEVEL]       = 0
            prefs[KEY_LOCK_END_MS] = 0L
            prefs[KEY_MESSAGE]     = ""
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers for AccessibilityService
    // ─────────────────────────────────────────────────────────────────

    suspend fun currentState(): LockState = lockStateFlow.first()

    fun isPackageAllowed(packageName: String, level: Int, quranPackage: String): Boolean {
        // Calls/SMS always allowed
        if (ALWAYS_ALLOWED_PACKAGES.contains(packageName)) return true
        // Guardian app always allowed (to show lock UI)
        if (packageName == "com.guardian.app") return true
        // Quran app always allowed
        if (packageName == quranPackage) return true
        // Level 2: block entertainment
        if (level == 2 && ENTERTAINMENT_PACKAGES.contains(packageName)) return false
        // Level 3: block everything except the above
        if (level == 3) return false
        // Level 1 or 0: everything allowed
        return true
    }

    // ─────────────────────────────────────────────────────────────────
    // Service control
    // ─────────────────────────────────────────────────────────────────

    private fun startLockService(level: Int, endMs: Long, message: String) {
        val intent = Intent(context, LockForegroundService::class.java).apply {
            putExtra(LockForegroundService.EXTRA_LEVEL,   level)
            putExtra(LockForegroundService.EXTRA_END_MS,  endMs)
            putExtra(LockForegroundService.EXTRA_MESSAGE, message)
        }
        context.startForegroundService(intent)
    }

    fun releaseLockSync() {
        scope.launch { releaseLock() }
    }
}
