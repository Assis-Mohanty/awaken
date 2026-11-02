package com.assismohanty.awaken.reelcounter.core.detect

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ParseResult {
    data class Ok(val config: DetectorConfig) : ParseResult
    data class Error(val message: String) : ParseResult
}

object DetectorConfigParser {
    const val SUPPORTED_VERSION = 1
    val VIEW_ID_REGEX = Regex("^[A-Za-z0-9._]+:id/[A-Za-z0-9_.]+$")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ParseResult {
        val config = try {
            json.decodeFromString<DetectorConfig>(text)
        } catch (e: SerializationException) {
            return ParseResult.Error("malformed JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return ParseResult.Error("malformed JSON: ${e.message}")
        }
        validate(config)?.let { return ParseResult.Error(it) }
        return ParseResult.Ok(config)
    }

    private fun validate(config: DetectorConfig): String? {
        if (config.version != SUPPORTED_VERSION) {
            return "version: expected $SUPPORTED_VERSION but was ${config.version}"
        }
        val seen = mutableSetOf<Any>()
        config.apps.forEachIndexed { a, app ->
            if (!seen.add(app.app)) return "apps[$a]: duplicate app ${app.app}"
            if (app.screens.isEmpty()) return "apps[$a].screens: must not be empty"
            app.screens.forEachIndexed { s, rule ->
                val at = "apps[$a].screens[$s]"
                if (rule.containerIds.isEmpty()) return "$at.containerIds: must not be empty"
                checkIds(rule.containerIds, "$at.containerIds")?.let { return it }
                checkIds(rule.excludeIds, "$at.excludeIds")?.let { return it }
                checkIds(rule.signalSourceIds, "$at.signalSourceIds")?.let { return it }
                if (!(rule.minCoverage > 0.0 && rule.minCoverage <= 1.0)) {
                    return "$at.minCoverage: must be in (0, 1] but was ${rule.minCoverage}"
                }
            }
        }
        return null
    }

    private fun checkIds(ids: List<String>, path: String): String? {
        ids.forEachIndexed { i, id ->
            if (!VIEW_ID_REGEX.matches(id)) return "$path[$i]: invalid view id '$id'"
        }
        return null
    }
}
