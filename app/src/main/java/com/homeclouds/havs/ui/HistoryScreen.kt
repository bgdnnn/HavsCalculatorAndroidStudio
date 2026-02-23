package com.homeclouds.havs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(nav: NavController) {
    val context = LocalContext.current
    val factory = remember(context) { HavsViewModelFactory(context.applicationContext) }
    val vm: HavsViewModel = viewModel(factory = factory)

    val state by vm.uiState.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    val todayHistory = remember(state.history) {
        state.history
            .filter {
                val d = Instant.ofEpochMilli(it.epochMillis).atZone(zone).toLocalDate()
                d == today
            }
            .sortedByDescending { it.epochMillis }
    }

    val todayPointsTotal = remember(todayHistory) { todayHistory.sumOf { it.points } }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History (Today)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = { nav.popBackStack() }) {
                    Text("Back")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear")
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Today total points: ${"%.1f".format(todayPointsTotal)}", color = bandColor(todayPointsTotal))
                    Text(bandLabel(todayPointsTotal), color = bandColor(todayPointsTotal))
                }
            }

            if (todayHistory.isEmpty()) {
                Text("No entries today.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(todayHistory, key = { it.id }) { h ->
                        val c = bandColor(h.points)
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${h.maker} • ${h.model}", style = MaterialTheme.typography.titleMedium)
                                Text("Minutes: ${h.minutes}")
                                Text("Points: ${"%.1f".format(h.points)}", color = c)
                                Text(bandLabel(h.points), color = c)
                            }
                        }
                    }
                }
            }
        }
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear today history?") },
                text = { Text("This will remove all today's calculations.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearTodayHistory()
                        showClearDialog = false
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

    }
}

private fun bandColor(points: Double): Color {
    return when {
        points < 100.0 -> Color(0xFF2E7D32)
        points < 350.0 -> Color(0xFFFF8F00)
        else -> Color(0xFFC62828)
    }
}

private fun bandLabel(points: Double): String {
    return when {
        points < 100.0 -> "Below 100 points"
        points < 350.0 -> "Between 100 and 350 points"
        else -> "350+ points"
    }
}
