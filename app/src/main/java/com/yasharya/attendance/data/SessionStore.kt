package com.yasharya.attendance.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yasharya.attendance.data.local.entity.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Who is signed in, surviving process death. */
data class Session(
    val username: String,
    val role: UserRole,
    val staffId: Long?,
)

class SessionStore(private val context: Context) {

    val session: Flow<Session?> = context.dataStore.data.map { prefs ->
        val username = prefs[KEY_USERNAME] ?: return@map null
        val role = prefs[KEY_ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: return@map null
        Session(username = username, role = role, staffId = prefs[KEY_STAFF_ID])
    }

    suspend fun signIn(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_ROLE] = session.role.name
            if (session.staffId != null) prefs[KEY_STAFF_ID] = session.staffId else prefs.remove(KEY_STAFF_ID)
        }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_STAFF_ID = longPreferencesKey("staff_id")
    }
}
