package com.homeclouds.havs.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "havs_store")
private val KEY_STATE = stringPreferencesKey("state_json")

@Serializable
data class HavsState(
    val dayId: Long = 0L,
    val tools: List<ToolDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
    val history: List<HistoryDto> = emptyList()
)

@Serializable
data class HistoryDto(
    val id: String,
    val epochMillis: Long,
    val toolId: String,
    val maker: String,
    val model: String,
    val vibrationMs2: Double,
    val minutes: Int,
    val points: Double
)

@Serializable
data class ToolDto(
    val id: String,
    val maker: String,
    val model: String,
    val vibrationMs2: Double,
    val createdAtEpochMillis: Long,
    val maxMinutesTo350: Int = 0,   // 0 = unknown / not set
    val noiseDb: Double = 0.0,      // 0.0 = unknown / not set
    val updatedAtEpochMillis: Long = 0L
)

@Serializable
data class EntryDto(
    val toolId: String,
    val minutes: Int
)

class HavsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val stateFlow: Flow<HavsState> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_STATE]
            val loaded = if (raw.isNullOrBlank()) {
                HavsState(dayId = currentLocalDayId())
            } else {
                runCatching { json.decodeFromString<HavsState>(raw) }
                    .getOrElse { HavsState(dayId = currentLocalDayId()) }
            }

            val todayId = currentLocalDayId()

            // ✅ New day: keep tools, reset calculations
            if (loaded.dayId != todayId) {
                loaded.copy(
                    dayId = todayId,
                    entries = emptyList(),
                    history = emptyList()
                )
            } else {
                loaded
            }
        }

    suspend fun saveState(state: HavsState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STATE] = json.encodeToString(state)
        }
    }
}

private fun currentLocalDayId(epochMillis: Long = System.currentTimeMillis()): Long {
    val tzOffset = java.util.TimeZone.getDefault().getOffset(epochMillis).toLong()
    val localMillis = epochMillis + tzOffset
    return localMillis / 86_400_000L
}