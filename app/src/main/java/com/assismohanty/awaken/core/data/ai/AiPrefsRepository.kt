package com.assismohanty.awaken.core.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assismohanty.awaken.core.data.di.AiPrefsStore
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Whether the hunter agreed to send Path text to the Oracle (DS §4.8), and when. */
data class AiConsent(val given: Boolean = false, val givenAt: Instant? = null)

/**
 * AI settings (ARCH §4): consent plus the random install ID the proxy keys its limits on. The ID is created when consent
 * is given and deleted when it is revoked, so a hunter who says no never has an identifier (ARCH §3.7).
 */
@Singleton
class AiPrefsRepository @Inject constructor(@AiPrefsStore private val store: DataStore<Preferences>) {

    val consent: Flow<AiConsent> = store.data.map {
        AiConsent(given = it[CONSENT] ?: false, givenAt = it[CONSENT_AT]?.let(Instant::ofEpochMilli))
    }

    suspend fun hasConsent(): Boolean = consent.first().given

    suspend fun installId(): String? = store.data.first()[INSTALL_ID]

    /** Records consent and returns the install ID (created once, kept until revoked). */
    suspend fun grantConsent(now: Instant): String {
        var id = ""
        store.edit { prefs ->
            id = prefs[INSTALL_ID] ?: UUID.randomUUID().toString()
            prefs[INSTALL_ID] = id
            prefs[CONSENT] = true
            prefs[CONSENT_AT] = now.toEpochMilli()
        }
        return id
    }

    suspend fun revokeConsent() {
        store.edit { prefs ->
            prefs[CONSENT] = false
            prefs.remove(CONSENT_AT)
            prefs.remove(INSTALL_ID)
        }
    }

    private companion object {
        val CONSENT = booleanPreferencesKey("consent_given")
        val CONSENT_AT = longPreferencesKey("consent_at")
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
