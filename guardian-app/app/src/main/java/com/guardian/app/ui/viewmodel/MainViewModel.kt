package com.guardian.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.lock.LockEngine
import com.guardian.app.lock.LockState
import com.guardian.app.service.LocalDnsVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.settingsStore: DataStore<Preferences>
    by preferencesDataStore("guardian_settings")

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockEngine: LockEngine
) : ViewModel() {

    // ── Keys ─────────────────────────────────────────────────────────
    private companion object {
        val KEY_VPN_ACTIVE     = booleanPreferencesKey("vpn_active")
        val KEY_ACCESS_ACTIVE  = booleanPreferencesKey("access_active")
        val KEY_QURAN_PKG      = stringPreferencesKey("quran_pkg")
        val KEY_IMG_ENABLED    = booleanPreferencesKey("img_enabled")
        val KEY_LOCK_HOURS_MIN = intPreferencesKey("lock_h_min")
        val KEY_LOCK_HOURS_MAX = intPreferencesKey("lock_h_max")
    }

    // ── Lock state ───────────────────────────────────────────────────
    val lockState: StateFlow<LockState> = lockEngine.lockStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockState(false, 0, 0L, ""))

    // ── Settings ─────────────────────────────────────────────────────
    private val settings = context.settingsStore.data

    val vpnActive: StateFlow<Boolean> = settings
        .map { it[KEY_VPN_ACTIVE] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accessibilityActive: StateFlow<Boolean> = settings
        .map { it[KEY_ACCESS_ACTIVE] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val quranPackage: StateFlow<String> = settings
        .map { it[KEY_QURAN_PKG] ?: LockEngine.DEFAULT_QURAN_PACKAGE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockEngine.DEFAULT_QURAN_PACKAGE)

    val imageClassificationEnabled: StateFlow<Boolean> = settings
        .map { it[KEY_IMG_ENABLED] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val lockHoursMin: StateFlow<Int> = settings
        .map { it[KEY_LOCK_HOURS_MIN] ?: 5 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    val lockHoursMax: StateFlow<Int> = settings
        .map { it[KEY_LOCK_HOURS_MAX] ?: 24 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 24)

    // ── VPN control ──────────────────────────────────────────────────
    fun startVpn(context: Context) {
        val intent = Intent(context, LocalDnsVpnService::class.java)
        context.startForegroundService(intent)
        viewModelScope.launch {
            context.settingsStore.edit { it[KEY_VPN_ACTIVE] = true }
        }
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, LocalDnsVpnService::class.java)
        context.stopService(intent)
        viewModelScope.launch {
            context.settingsStore.edit { it[KEY_VPN_ACTIVE] = false }
        }
    }

    // ── Settings setters ─────────────────────────────────────────────
    fun setQuranPackage(pkg: String) = viewModelScope.launch {
        context.settingsStore.edit { it[KEY_QURAN_PKG] = pkg }
    }

    fun setImageClassification(enabled: Boolean) = viewModelScope.launch {
        context.settingsStore.edit { it[KEY_IMG_ENABLED] = enabled }
    }

    fun setLockHoursRange(min: Int, max: Int) = viewModelScope.launch {
        context.settingsStore.edit {
            it[KEY_LOCK_HOURS_MIN] = min
            it[KEY_LOCK_HOURS_MAX] = maxOf(min + 1, max)
        }
    }

    fun markAccessibilityActive(active: Boolean) = viewModelScope.launch {
        context.settingsStore.edit { it[KEY_ACCESS_ACTIVE] = active }
    }
}
