package com.homeclouds.havs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeclouds.havs.data.EntryDto
import com.homeclouds.havs.data.HavsMath
import com.homeclouds.havs.data.HavsState
import com.homeclouds.havs.data.HavsStore
import com.homeclouds.havs.data.HistoryDto
import com.homeclouds.havs.data.ToolDto
import com.homeclouds.havs.data.ToolsJsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiTool(
    val id: String,
    val maker: String,
    val model: String,
    val vibrationMs2: Double,
    val maxMinutesTo350: Int,
    val noiseDb: Double,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class UiEntry(
    val toolId: String,
    val minutes: Int
)

data class UiHistory(
    val id: String,
    val epochMillis: Long,
    val toolId: String,
    val maker: String,
    val model: String,
    val vibrationMs2: Double,
    val minutes: Int,
    val points: Double
)

data class UiState(
    val dayId: Long = 0L,
    val tools: List<UiTool> = emptyList(),
    val entries: List<UiEntry> = emptyList(),
    val history: List<UiHistory> = emptyList()
)

class HavsViewModel(
    private val store: HavsStore,
    private val toolsStore: ToolsJsonStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // Persist "new day reset" once
    private var lastSavedDayId: Long = -1L

    init {
        viewModelScope.launch {
            store.stateFlow.collectLatest { s ->

                // Persist new-day reset once (store handles day rollover)
                if (s.dayId != lastSavedDayId) {
                    lastSavedDayId = s.dayId
                    store.saveState(s)
                }

                // Load tools from JSON file (NOT DataStore)
                val tools = withContext(Dispatchers.IO) { toolsStore.readTools() }

                _uiState.value = UiState(
                    dayId = s.dayId,
                    tools = tools
                        .sortedByDescending { maxOf(it.updatedAtEpochMillis, it.createdAtEpochMillis) }
                        .map { it.toUi() },
                    entries = s.entries.map { UiEntry(it.toolId, it.minutes) },
                    history = s.history.sortedByDescending { it.epochMillis }.map { it.toUi() }
                )
            }
        }
    }

    fun addTool(maker: String, model: String, vibrationMs2: Double, maxMinutesTo350: Int, noiseDb: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val newTool = ToolDto(
                id = UUID.randomUUID().toString(),
                maker = maker.trim(),
                model = model.trim(),
                vibrationMs2 = vibrationMs2,
                createdAtEpochMillis = now,
                maxMinutesTo350 = maxMinutesTo350.coerceAtLeast(0),
                noiseDb = noiseDb.coerceAtLeast(0.0),
                updatedAtEpochMillis = now
            )

            withContext(Dispatchers.IO) {
                val current = toolsStore.readTools()
                toolsStore.writeTools(current + newTool)
            }

            // Force UI refresh by re-saving store state (history/entries unchanged)
            store.saveState(currentStoreStateCopy())
        }
    }

    fun updateTool(toolId: String, maker: String, model: String, vibrationMs2: Double, maxMinutesTo350: Int, noiseDb: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                val current = toolsStore.readTools()
                val updated = current.map { t ->
                    if (t.id == toolId) {
                        t.copy(
                            maker = maker.trim(),
                            model = model.trim(),
                            vibrationMs2 = vibrationMs2,
                            maxMinutesTo350 = maxMinutesTo350.coerceAtLeast(0),
                            noiseDb = noiseDb.coerceAtLeast(0.0),
                            updatedAtEpochMillis = now
                        )
                    } else t
                }
                toolsStore.writeTools(updated)
            }

            store.saveState(currentStoreStateCopy())
        }
    }

    fun removeTool(toolId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = toolsStore.readTools()
                toolsStore.writeTools(current.filterNot { it.id == toolId })
            }

            // Also remove entries/history for this tool (today)
            val currentUi = _uiState.value
            val newState = HavsState(
                dayId = currentUi.dayId,
                tools = emptyList(), // tools are in JSON now
                entries = currentUi.entries.filterNot { it.toolId == toolId }.map { EntryDto(it.toolId, it.minutes) },
                history = currentUi.history.filterNot { it.toolId == toolId }.map { it.toDto() }
            )
            store.saveState(newState)
        }
    }

    fun addExposure(toolId: String, minutesToAdd: Int) {
        viewModelScope.launch {
            val minutes = minutesToAdd.coerceAtLeast(0)
            if (minutes == 0) return@launch

            val currentUi = _uiState.value
            val tool = currentUi.tools.firstOrNull { it.id == toolId } ?: return@launch

            val now = System.currentTimeMillis()
            val points = HavsMath.pointsForMinutes(tool.vibrationMs2, minutes)

            val historyAdded = currentUi.history + UiHistory(
                id = UUID.randomUUID().toString(),
                epochMillis = now,
                toolId = toolId,
                maker = tool.maker,
                model = tool.model,
                vibrationMs2 = tool.vibrationMs2,
                minutes = minutes,
                points = points
            )

            val existingMinutes = currentUi.entries.firstOrNull { it.toolId == toolId }?.minutes ?: 0
            val updatedEntries =
                currentUi.entries.filterNot { it.toolId == toolId } + UiEntry(toolId, existingMinutes + minutes)

            val newState = HavsState(
                dayId = currentUi.dayId,
                tools = emptyList(), // tools are in JSON now
                entries = updatedEntries.map { EntryDto(it.toolId, it.minutes) },
                history = historyAdded.map { it.toDto() }
            )

            store.saveState(newState)
        }
    }

    fun clearTodayHistory() {
        viewModelScope.launch {
            val currentUi = _uiState.value
            val newState = HavsState(
                dayId = currentUi.dayId,
                tools = emptyList(), // tools are in JSON now
                entries = emptyList(),
                history = emptyList()
            )
            store.saveState(newState)
        }
    }

    // Optional: export/import tools JSON (for your UI buttons later)
    fun exportToolsJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { toolsStore.exportToolsJson() }
            onReady(raw)
        }
    }

    fun importToolsJson(rawJson: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { toolsStore.importToolsJson(rawJson) }
            store.saveState(currentStoreStateCopy())
        }
    }

    // ---- helpers ----

    private fun ToolDto.toUi(): UiTool = UiTool(
        id = id,
        maker = maker,
        model = model,
        vibrationMs2 = vibrationMs2,
        maxMinutesTo350 = maxMinutesTo350,
        noiseDb = noiseDb,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun HistoryDto.toUi(): UiHistory = UiHistory(
        id = id,
        epochMillis = epochMillis,
        toolId = toolId,
        maker = maker,
        model = model,
        vibrationMs2 = vibrationMs2,
        minutes = minutes,
        points = points
    )

    private fun UiHistory.toDto(): HistoryDto = HistoryDto(
        id = id,
        epochMillis = epochMillis,
        toolId = toolId,
        maker = maker,
        model = model,
        vibrationMs2 = vibrationMs2,
        minutes = minutes,
        points = points
    )

    private fun currentStoreStateCopy(): HavsState {
        val currentUi = _uiState.value
        return HavsState(
            dayId = currentUi.dayId,
            tools = emptyList(), // tools are in JSON now
            entries = currentUi.entries.map { EntryDto(it.toolId, it.minutes) },
            history = currentUi.history.map { it.toDto() }
        )
    }
}