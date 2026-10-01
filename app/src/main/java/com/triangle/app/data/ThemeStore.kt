package com.triangle.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "triangle_theme_prefs")

/** User's Settings > Appearance choice — SYSTEM follows the device's own dark/light setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persists the app-wide light/dark override (see ui/theme/Theme.kt's LocalTriangleDarkTheme). */
object ThemeStore {
    private val KEY_MODE = stringPreferencesKey("mode")

    fun modeFlow(context: Context): Flow<ThemeMode> =
        context.themeDataStore.data.map { prefs ->
            runCatching { ThemeMode.valueOf(prefs[KEY_MODE] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT)
        }

    suspend fun setMode(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { it[KEY_MODE] = mode.name }
    }
}
