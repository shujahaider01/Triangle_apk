package com.triangle.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "triangle_native_session")

/** Native session persistence (DataStore) for the Compose Login/Dashboard screens. */
object SessionStore {
    private val KEY_UID = stringPreferencesKey("uid")
    private val KEY_NAME = stringPreferencesKey("name")
    private val KEY_EMAIL = stringPreferencesKey("email")
    private val KEY_ROLE = stringPreferencesKey("role")
    private val KEY_ORG_ID = stringPreferencesKey("org_id")

    data class Session(
        val uid: String,
        val name: String,
        val email: String,
        val role: String,
        val orgId: String
    )

    fun sessionFlow(context: Context): Flow<Session?> =
        context.sessionDataStore.data.map { prefs ->
            val uid = prefs[KEY_UID]
            val orgId = prefs[KEY_ORG_ID]
            if (uid.isNullOrEmpty() || orgId.isNullOrEmpty()) null
            else Session(
                uid = uid,
                name = prefs[KEY_NAME] ?: "",
                email = prefs[KEY_EMAIL] ?: "",
                role = prefs[KEY_ROLE] ?: "individual",
                orgId = orgId
            )
        }

    suspend fun save(context: Context, session: Session) {
        context.sessionDataStore.edit { p ->
            p[KEY_UID] = session.uid
            p[KEY_NAME] = session.name
            p[KEY_EMAIL] = session.email
            p[KEY_ROLE] = session.role
            p[KEY_ORG_ID] = session.orgId
        }
    }

    suspend fun clear(context: Context) {
        context.sessionDataStore.edit { it.clear() }
    }
}
