package com.austinauyeung.nyuma.c9

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.austinauyeung.nyuma.c9.accessibility.AppAccessibilityService
import com.austinauyeung.nyuma.c9.core.logs.LogManager
import com.austinauyeung.nyuma.c9.core.logs.Logger
import com.austinauyeung.nyuma.c9.settings.domain.OverlaySettings
import com.austinauyeung.nyuma.c9.settings.repository.SettingsRepository
import com.austinauyeung.nyuma.c9.settings.repository.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Application class for C9.
 */
class C9 : Application() {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    private val _settingsRepository: Lazy<SettingsRepository> =
        lazy {
            SettingsRepositoryImpl(applicationContext.dataStore)
        }
    val settingsRepository: SettingsRepository by _settingsRepository
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var settingsObserverJob: Job? = null
    private lateinit var _settingsFlow: StateFlow<OverlaySettings>

    fun getSettingsFlow(): StateFlow<OverlaySettings> {
        if (!::_settingsFlow.isInitialized) {
            _settingsFlow = settingsRepository.getSettings().stateIn(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                started = SharingStarted.Eagerly,
                initialValue = OverlaySettings()
            )
        }
        return _settingsFlow
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        settingsObserverJob = getSettingsFlow()
            .onEach { settings ->
                if (!settings.collectLogs) {
                    LogManager.clear()
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(applicationScope)

        Logger.i("C9 application initialized")
    }

    override fun onTerminate() {
        Logger.i("C9 application terminating")

        settingsObserverJob?.cancel()
        applicationScope.cancel()
        super.onTerminate()
    }

    companion object {
        private lateinit var instance: C9

        fun getInstance(): C9 {
            return instance
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            try {
                val am =
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabledServices =
                    am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                    )
                return enabledServices.any {
                    it.id.contains(context.packageName) &&
                            it.id.contains(AppAccessibilityService::class.java.simpleName)
                }
            } catch (e: Exception) {
                Logger.e("Error checking accessibility service status", e)
                return false
            }
        }
    }
}
