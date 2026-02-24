package com.homeclouds.havs.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ToolsJsonStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val toolsFile: File get() = File(context.filesDir, "tools.json")
    private val seedAssetName = "tools_seed.json"

    fun ensureInitialized() {
        if (toolsFile.exists()) return

        // Copy seed JSON from assets → internal tools.json
        val seed = runCatching {
            context.assets.open(seedAssetName).bufferedReader().use { it.readText() }
        }.getOrElse { "[]" }

        toolsFile.writeText(seed)
    }

    fun readTools(): List<ToolDto> {
        ensureInitialized()
        val raw = toolsFile.readText()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ToolDto>>(raw) }
            .getOrElse { emptyList() }
    }

    fun writeTools(tools: List<ToolDto>) {
        ensureInitialized()
        toolsFile.writeText(json.encodeToString(tools))
    }

    fun exportToolsJson(): String {
        ensureInitialized()
        return toolsFile.readText()
    }

    fun importToolsJson(rawJson: String) {
        // Validate JSON before writing
        val parsed = json.decodeFromString<List<ToolDto>>(rawJson)
        writeTools(parsed)
    }
}