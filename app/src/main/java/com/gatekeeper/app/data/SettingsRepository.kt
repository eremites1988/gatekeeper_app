package com.gatekeeper.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App-level preferences (DataStore). Gate thresholds live in Room ([GateConfig]). */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    /** R-8.4: whether unexpired grants survive a reboot. */
    val grantsSurviveReboot: Boolean = false,
    /** R-6.3: optional per-exercise confirmation timer (0 = off, honor system). */
    val exerciseFrictionSeconds: Int = 0,
    /** R-7.10: optional strict reading mode (off by default). */
    val readingStrictMode: Boolean = false,
    /** Minimum dwell seconds per page in strict reading mode. */
    val readingDwellSeconds: Int = 3,
    /** R-11.3: friction before the user can disable gating for an app. */
    val strictMode: Boolean = false,
    /** Reader preferences. */
    val readerFontSizePercent: Int = 100,
    val readerDarkTheme: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val GRANTS_SURVIVE_REBOOT = booleanPreferencesKey("grants_survive_reboot")
        val EXERCISE_FRICTION_SECONDS = intPreferencesKey("exercise_friction_seconds")
        val READING_STRICT_MODE = booleanPreferencesKey("reading_strict_mode")
        val READING_DWELL_SECONDS = intPreferencesKey("reading_dwell_seconds")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val READER_FONT_SIZE = intPreferencesKey("reader_font_size_percent")
        val READER_DARK_THEME = booleanPreferencesKey("reader_dark_theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
            grantsSurviveReboot = p[Keys.GRANTS_SURVIVE_REBOOT] ?: false,
            exerciseFrictionSeconds = p[Keys.EXERCISE_FRICTION_SECONDS] ?: 0,
            readingStrictMode = p[Keys.READING_STRICT_MODE] ?: false,
            readingDwellSeconds = p[Keys.READING_DWELL_SECONDS] ?: 3,
            strictMode = p[Keys.STRICT_MODE] ?: false,
            readerFontSizePercent = p[Keys.READER_FONT_SIZE] ?: 100,
            readerDarkTheme = p[Keys.READER_DARK_THEME] ?: false,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = value }

    suspend fun setGrantsSurviveReboot(value: Boolean) =
        context.dataStore.edit { it[Keys.GRANTS_SURVIVE_REBOOT] = value }

    suspend fun setExerciseFrictionSeconds(value: Int) =
        context.dataStore.edit { it[Keys.EXERCISE_FRICTION_SECONDS] = value.coerceIn(0, 300) }

    suspend fun setReadingStrictMode(value: Boolean) =
        context.dataStore.edit { it[Keys.READING_STRICT_MODE] = value }

    suspend fun setReadingDwellSeconds(value: Int) =
        context.dataStore.edit { it[Keys.READING_DWELL_SECONDS] = value.coerceIn(1, 60) }

    suspend fun setStrictMode(value: Boolean) =
        context.dataStore.edit { it[Keys.STRICT_MODE] = value }

    suspend fun setReaderFontSizePercent(value: Int) =
        context.dataStore.edit { it[Keys.READER_FONT_SIZE] = value.coerceIn(50, 300) }

    suspend fun setReaderDarkTheme(value: Boolean) =
        context.dataStore.edit { it[Keys.READER_DARK_THEME] = value }
}
