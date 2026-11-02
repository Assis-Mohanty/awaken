package com.assismohanty.awaken.reelcounter.data

import android.content.Context
import com.assismohanty.awaken.reelcounter.core.detect.DetectorConfig
import com.assismohanty.awaken.reelcounter.core.detect.DetectorConfigParser
import com.assismohanty.awaken.reelcounter.core.detect.ParseResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DetectorSource { BUILT_IN, CUSTOM }

/** [error] is set when a custom override exists but could not be used; [config] is then the built-in one. */
data class DetectorLoad(val config: DetectorConfig, val source: DetectorSource, val error: String? = null)

/** Loads `assets/rc_detectors.json`, or the on-phone override in `filesDir` when it parses. */
@Singleton
class DetectorRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val overrideFile: File get() = File(context.filesDir, OVERRIDE_NAME)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<DetectorLoad> = _config

    /** Validates [text] and, if it is good, makes it the active rules. */
    fun saveOverride(text: String): ParseResult {
        val result = DetectorConfigParser.parse(text)
        if (result is ParseResult.Ok) {
            overrideFile.writeText(text)
            _config.value = DetectorLoad(result.config, DetectorSource.CUSTOM)
        }
        return result
    }

    fun resetOverride() {
        overrideFile.delete()
        _config.value = load()
    }

    fun overrideText(): String? = overrideFile.takeIf { it.exists() }?.readText()

    fun builtInText(): String = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }

    private fun load(): DetectorLoad {
        val builtIn = (DetectorConfigParser.parse(builtInText()) as? ParseResult.Ok)?.config
            ?: error("The built-in $ASSET_NAME is invalid")
        val file = overrideFile
        if (!file.exists()) return DetectorLoad(builtIn, DetectorSource.BUILT_IN)
        return when (val parsed = DetectorConfigParser.parse(file.readText())) {
            is ParseResult.Ok -> DetectorLoad(parsed.config, DetectorSource.CUSTOM)
            is ParseResult.Error -> DetectorLoad(builtIn, DetectorSource.BUILT_IN, parsed.message)
        }
    }

    companion object {
        const val ASSET_NAME = "rc_detectors.json"
        const val OVERRIDE_NAME = "rc_detectors_override.json"
    }
}
