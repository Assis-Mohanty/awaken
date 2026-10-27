package com.assismohanty.awaken.feature.recap

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.assismohanty.awaken.core.data.repo.ShareImageWriter
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.LoadRecapUseCase
import com.assismohanty.awaken.core.domain.ObserveHunterStateUseCase
import com.assismohanty.awaken.core.domain.RecapModel
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.navigation.RecapRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** The hunter on the share image (the existing ShareCard layout, dark in every mode). */
data class RecapHunter(val name: String, val rank: Rank, val level: Int, val statLevels: Map<Stat, Float>, val radarMax: Float)

data class RecapUiState(
    val loading: Boolean = true,
    /** Null once loaded: the recap is gone (it is only a cache) or never existed. */
    val recap: RecapModel? = null,
    val hunter: RecapHunter? = null,
    val page: Int = 0,
    val capturing: Boolean = false,
    val shareUri: Uri? = null,
)

sealed interface RecapEvent {
    data class Page(val page: Int) : RecapEvent
    data object Share : RecapEvent
    data class ShareCaptured(val bitmap: Bitmap) : RecapEvent
    data object ShareFailed : RecapEvent
    data object ShareHandled : RecapEvent
}

@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    loadRecap: LoadRecapUseCase,
    observeHunter: ObserveHunterStateUseCase,
    private val shareImageWriter: ShareImageWriter,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val periodKey = savedStateHandle.toRoute<RecapRoute>().periodKey
    private val _uiState = MutableStateFlow(RecapUiState())
    val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val recap = loadRecap(periodKey)
                val hunter = observeHunter().first()?.let { state ->
                    RecapHunter(
                        name = state.name,
                        rank = state.rank,
                        level = state.level,
                        statLevels = state.statLevels.mapValues { it.value.toFloat() },
                        radarMax = StatusMath.radarMax(state.statLevels.values).toFloat(),
                    )
                }
                _uiState.update { it.copy(loading = false, recap = recap, hunter = hunter) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Loading recap %s failed", periodKey)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun onEvent(event: RecapEvent) {
        when (event) {
            is RecapEvent.Page -> _uiState.update { it.copy(page = event.page) }
            RecapEvent.Share -> _uiState.update { it.copy(capturing = it.hunter != null) }
            is RecapEvent.ShareCaptured -> viewModelScope.launch {
                try {
                    val uri = shareImageWriter.writePng(event.bitmap)
                    _uiState.update { it.copy(capturing = false, shareUri = uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Writing the recap image failed")
                    shareFailed()
                }
            }
            RecapEvent.ShareFailed -> shareFailed()
            RecapEvent.ShareHandled -> _uiState.update { it.copy(shareUri = null) }
        }
    }

    private fun shareFailed() {
        _uiState.update { it.copy(capturing = false, shareUri = null) }
        completionCenter.message(UserMessage.ShareFailed)
    }
}
