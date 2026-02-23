package com.homeclouds.havs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeclouds.havs.data.EntryDto
import com.homeclouds.havs.data.HavsMath
import com.homeclouds.havs.data.HavsState
import com.homeclouds.havs.data.HavsStore
import com.homeclouds.havs.data.HistoryDto
import com.homeclouds.havs.data.ToolDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    private val store: HavsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // Persist "new day reset" once (prevents repeated re-saving)
    private var lastSavedDayId: Long = -1L

    init {
        viewModelScope.launch {
            store.stateFlow.collectLatest { s ->

                // If the store emitted a "new day reset" state, persist it once.
                if (s.dayId != lastSavedDayId) {
                    lastSavedDayId = s.dayId
                    store.saveState(s)
                }

                _uiState.value = UiState(
                    dayId = s.dayId,
                    tools = s.tools.map { it.toUi() },
                    entries = s.entries.map { UiEntry(it.toolId, it.minutes) },
                    history = s.history.map { it.toUi() }
                )
            }
        }
    }

    fun addTool(
        maker: String,
        model: String,
        vibrationMs2: Double,
        maxMinutesTo350: Int,
        noiseDb: Double
    ) {
        viewModelScope.launch {
            val current = _uiState.value
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

            store.saveState(
                current.toState(
                    tools = current.tools.map { it.toDto() } + newTool
                )
            )
        }
    }

    fun updateTool(
        toolId: String,
        maker: String,
        model: String,
        vibrationMs2: Double,
        maxMinutesTo350: Int,
        noiseDb: Double
    ) {
        viewModelScope.launch {
            val current = _uiState.value
            val now = System.currentTimeMillis()

            val updatedTools = current.tools.map { t ->
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

            store.saveState(
                current.toState(
                    tools = updatedTools.map { it.toDto() }
                )
            )
        }
    }

    fun removeTool(toolId: String) {
        viewModelScope.launch {
            val current = _uiState.value

            val filteredTools = current.tools.filterNot { it.id == toolId }
            val filteredEntries = current.entries.filterNot { it.toolId == toolId }
            val filteredHistory = current.history.filterNot { it.toolId == toolId }

            store.saveState(
                current.toState(
                    tools = filteredTools.map { it.toDto() },
                    entries = filteredEntries.map { EntryDto(it.toolId, it.minutes) },
                    history = filteredHistory.map { it.toDto() }
                )
            )
        }
    }

    /**
     * Adds a calculation record to history (persists even after closing the app),
     * and accumulates today's minutes in entries for that tool.
     */
    fun addExposure(toolId: String, minutesToAdd: Int) {
        viewModelScope.launch {
            val minutes = minutesToAdd.coerceAtLeast(0)
            if (minutes == 0) return@launch

            val current = _uiState.value
            val tool = current.tools.firstOrNull { it.id == toolId } ?: return@launch

            val now = System.currentTimeMillis()
            val points = HavsMath.pointsForMinutes(tool.vibrationMs2, minutes)

            val historyAdded = current.history + UiHistory(
                id = UUID.randomUUID().toString(),
                epochMillis = now,
                toolId = toolId,
                maker = tool.maker,
                model = tool.model,
                vibrationMs2 = tool.vibrationMs2,
                minutes = minutes,
                points = points
            )

            val existingMinutes = current.entries.firstOrNull { it.toolId == toolId }?.minutes ?: 0
            val newMinutes = existingMinutes + minutes

            val updatedEntries =
                current.entries.filterNot { it.toolId == toolId } + UiEntry(toolId, newMinutes)

            store.saveState(
                current.toState(
                    entries = updatedEntries.map { EntryDto(it.toolId, it.minutes) },
                    history = historyAdded.map { it.toDto() }
                )
            )
        }
    }

    fun clearTodayHistory() {
        viewModelScope.launch {
            val current = _uiState.value
            store.saveState(
                current.toState(
                    entries = emptyList(),
                    history = emptyList()
                )
            )
        }
    }

    // -------------------------
    // Helpers (reduce repetition)
    // -------------------------

    private fun UiState.toState(
        dayId: Long = this.dayId,
        tools: List<ToolDto> = this.tools.map { it.toDto() },
        entries: List<EntryDto> = this.entries.map { EntryDto(it.toolId, it.minutes) },
        history: List<HistoryDto> = this.history.map { it.toDto() }
    ): HavsState {
        return HavsState(
            dayId = dayId,
            tools = tools,
            entries = entries,
            history = history
        )
    }

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

    private fun UiTool.toDto(): ToolDto = ToolDto(
        id = id,
        maker = maker,
        model = model,
        vibrationMs2 = vibrationMs2,
        createdAtEpochMillis = createdAtEpochMillis,
        maxMinutesTo350 = maxMinutesTo350,
        noiseDb = noiseDb,
        updatedAtEpochMillis = updatedAtEpochMillis
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
}