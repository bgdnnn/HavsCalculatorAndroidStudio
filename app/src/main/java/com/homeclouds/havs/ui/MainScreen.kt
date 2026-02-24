package com.homeclouds.havs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.homeclouds.havs.data.HavsMath
import com.homeclouds.havs.ui.components.DropdownField

@Composable
fun MainScreen(nav: NavController) {
    val context = LocalContext.current
    val factory = remember(context) { HavsViewModelFactory(context.applicationContext) }
    val vm: HavsViewModel = viewModel(factory = factory)

    val state by vm.uiState.collectAsStateWithLifecycle()

    // Selector state
    var selectedMaker by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }

    // Prefill maker/model with latest edited/added tool
    val latestTool = remember(state.tools) {
        state.tools.maxByOrNull { maxOf(it.createdAtEpochMillis, it.updatedAtEpochMillis) }
    }
    LaunchedEffect(latestTool?.id) {
        if (selectedMaker.isBlank() && selectedModel.isBlank() && latestTool != null) {
            selectedMaker = latestTool.maker
            selectedModel = latestTool.model
        }
    }

    val makers = remember(state.tools) {
        state.tools.map { it.maker }.distinct().sorted()
    }

    val modelsForMaker = remember(state.tools, selectedMaker) {
        if (selectedMaker.isBlank()) emptyList()
        else state.tools.filter { it.maker == selectedMaker }.map { it.model }.distinct().sorted()
    }

    val selectedTool = remember(state.tools, selectedMaker, selectedModel) {
        state.tools.firstOrNull { it.maker == selectedMaker && it.model == selectedModel }
    }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTool by remember { mutableStateOf<UiTool?>(null) }
    var deletingTool by remember { mutableStateOf<UiTool?>(null) }

    // Last calculation (what you want on top)
    var lastPoints by remember { mutableStateOf<Double?>(null) }
    var lastMaker by remember { mutableStateOf<String?>(null) }
    var lastModel by remember { mutableStateOf<String?>(null) }
    var lastMinutes by remember { mutableStateOf<Int?>(null) }
    var lastPpm by remember { mutableStateOf<Double?>(null) }

    // Calculation preview (for button enable + saving last calculation)
    val vib = selectedTool?.vibrationMs2
    val minutes = minutesText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val pointsThis = if (vib == null) 0.0 else HavsMath.pointsForMinutes(vib, minutes)

    CloudBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HAVS Calculator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { nav.navigate("history") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) { Text("History") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) { Text("Add tool") }
                }

                // Last calculation card (ONLY calculation output here)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.94f)
                    )
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Last calculation", style = MaterialTheme.typography.titleMedium)

                        if (lastPoints == null) {
                            Text("No calculation yet. Pick a tool and minutes, then press Calculate.")
                        } else {
                            val pts = lastPoints ?: 0.0
                            val c = pointsBandColor(pts)

                            /*Text(
                                "${lastMaker.orEmpty()} • ${lastModel.orEmpty()}",
                                style = MaterialTheme.typography.titleSmall
                            )*/
                            Text("Minutes: ${lastMinutes ?: 0}")
                            Text("Points: ${"%.1f".format(pts)}", color = c)
                            Text("Points per minute: ${lastPpm?.let { "%.3f".format(it) } ?: "-"}")
                        }
                    }
                }

                // Calculator card (NO redundant calculation text; tool info at bottom)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.94f)
                    )
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Calculator", style = MaterialTheme.typography.titleMedium)

                        DropdownField(
                            label = "Maker",
                            value = selectedMaker,
                            options = makers,
                            enabled = makers.isNotEmpty(),
                            onSelected = {
                                selectedMaker = it
                                selectedModel = ""
                            }
                        )

                        DropdownField(
                            label = "Model",
                            value = selectedModel,
                            options = modelsForMaker,
                            enabled = selectedMaker.isNotBlank() && modelsForMaker.isNotEmpty(),
                            onSelected = { selectedModel = it }
                        )

                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter(Char::isDigit) },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Tool info only (bottom section)
                        val max350Text =
                            selectedTool?.maxMinutesTo350?.takeIf { it > 0 }?.toString() ?: "-"
                        val noiseText =
                            selectedTool?.noiseDb?.takeIf { it > 0.0 }?.let { "%.1f".format(it) } ?: "-"
                        val vibText =
                            vib?.let { "%.2f".format(it) } ?: "-"

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.88f)
                            )
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Tool info",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Vibration: $vibText m/s²")
                                Text("Max usage to 350: $max350Text min")
                                Text("Noise: $noiseText dB")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val tool = selectedTool ?: return@Button
                                    if (minutes <= 0) return@Button

                                    lastPoints = pointsThis
                                    lastMaker = tool.maker
                                    lastModel = tool.model
                                    lastMinutes = minutes
                                    lastPpm = HavsMath.pointsPerMinute(tool.vibrationMs2)

                                    vm.addExposure(tool.id, minutes)
                                },
                                enabled = selectedTool != null && minutes > 0,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Calculate", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { editingTool = selectedTool },
                                enabled = selectedTool != null,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Edit", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { deletingTool = selectedTool },
                                enabled = selectedTool != null,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Delete", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add tool dialog
    if (showAddDialog) {
        AddOrEditToolDialog(
            title = "Add tool",
            initialMaker = "",
            initialModel = "",
            initialVibration = "",
            initialMaxMinutesTo350 = "",
            initialNoiseDb = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { maker, model, vibration, max350, noiseDb ->
                vm.addTool(maker, model, vibration, max350, noiseDb)
                showAddDialog = false
            }
        )
    }

    // Edit tool dialog
    val edit = editingTool
    if (edit != null) {
        AddOrEditToolDialog(
            title = "Edit tool",
            initialMaker = edit.maker,
            initialModel = edit.model,
            initialVibration = edit.vibrationMs2.toString(),
            initialMaxMinutesTo350 = if (edit.maxMinutesTo350 == 0) "" else edit.maxMinutesTo350.toString(),
            initialNoiseDb = if (edit.noiseDb == 0.0) "" else edit.noiseDb.toString(),
            onDismiss = { editingTool = null },
            onConfirm = { maker, model, vibration, max350, noiseDb ->
                vm.updateTool(edit.id, maker, model, vibration, max350, noiseDb)
                selectedMaker = maker
                selectedModel = model
                editingTool = null
            }
        )
    }

    // Delete confirm
    val del = deletingTool
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deletingTool = null },
            title = { Text("Delete tool?") },
            text = { Text("Delete ${del.maker} • ${del.model}? This also removes today's history entries for this tool.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeTool(del.id)
                    if (selectedTool?.id == del.id) {
                        selectedMaker = ""
                        selectedModel = ""
                        minutesText = ""
                    }
                    deletingTool = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTool = null }) { Text("Cancel") }
            }
        )
    }
}

private fun pointsBandColor(points: Double): Color {
    return when {
        points < 100.0 -> Color(0xFF2E7D32)
        points < 350.0 -> Color(0xFFFF8F00)
        else -> Color(0xFFC62828)
    }
}

@Composable
private fun AddOrEditToolDialog(
    title: String,
    initialMaker: String,
    initialModel: String,
    initialVibration: String,
    initialMaxMinutesTo350: String,
    initialNoiseDb: String,
    onDismiss: () -> Unit,
    onConfirm: (maker: String, model: String, vibrationMs2: Double, maxMinutesTo350: Int, noiseDb: Double) -> Unit
) {
    var maker by remember { mutableStateOf(initialMaker) }
    var model by remember { mutableStateOf(initialModel) }
    var vib by remember { mutableStateOf(initialVibration) }
    var max350 by remember { mutableStateOf(initialMaxMinutesTo350) }
    var noise by remember { mutableStateOf(initialNoiseDb) }

    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }

                OutlinedTextField(
                    value = maker,
                    onValueChange = { maker = it; error = null },
                    label = { Text("Maker") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; error = null },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vib,
                    onValueChange = { vib = it; error = null },
                    label = { Text("Vibration (m/s²)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = max350,
                    onValueChange = { max350 = it.filter(Char::isDigit); error = null },
                    label = { Text("Max minutes to 350") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noise,
                    onValueChange = { noise = it; error = null },
                    label = { Text("Noise (dB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = vib.replace(',', '.').toDoubleOrNull()
                val m350 = max350.toIntOrNull() ?: 0
                val ndb = noise.replace(',', '.').toDoubleOrNull() ?: 0.0

                when {
                    maker.trim().isBlank() -> error = "Maker required"
                    model.trim().isBlank() -> error = "Model required"
                    v == null || v <= 0.0 -> error = "Vibration must be > 0"
                    else -> onConfirm(maker.trim(), model.trim(), v, m350, ndb)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CloudBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D47A1),
                            Color(0xFF1976D2),
                            Color(0xFF42A5F5)
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.15f, size.height * 0.25f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.85f, size.height * 0.70f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.width * 0.35f,
                center = Offset(size.width * 0.55f, size.height * 0.15f)
            )
        }

        content()
    }
}
