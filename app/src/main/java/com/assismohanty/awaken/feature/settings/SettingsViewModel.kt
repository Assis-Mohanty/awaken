package com.assismohanty.awaken.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.export.BackupFiles
import com.assismohanty.awaken.core.data.export.GameDataPorter
import com.assismohanty.awaken.core.data.export.ImportResult
import com.assismohanty.awaken.core.data.export.ImportSummary
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DisableHealthUseCase
import com.assismohanty.awaken.core.domain.EnableHealthResult
import com.assismohanty.awaken.core.domain.EnableHealthUseCase
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveSettingsUseCase
import com.assismohanty.awaken.core.domain.SettingsModel
import com.assismohanty.awaken.core.domain.SettingsResult
import com.assismohanty.awaken.core.domain.UpdateSettingsUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.work.DayStartScheduler
import com.assismohanty.awaken.core.work.NotificationAccess
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.SettingsRules
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
    val loading: Boolean = true,
    val savedName: String = "",
    /** What is in the name field; follows [savedName] until the hunter types. */
    val nameDraft: String = "",
    val showNameError: Boolean = false,
    val savedFocus: List<Stat> = emptyList(),
    val focusDraft: List<Stat> = emptyList(),
    val dayStartHour: Int = SettingsRules.DAY_START_HOURS.first,
    val dayStartOptions: List<Int> = SettingsRules.DAY_START_HOURS.toList(),
    val reminder: LocalTime? = null,
    val showReminderPicker: Boolean = false,
    val equipment: Set<String> = emptySet(),
    val equipmentOptions: List<String> = emptyList(),
    val injuryMode: Boolean = false,
    val injuryTags: Set<String> = DirectiveGenerator.DEFAULT_INJURY_TAGS,
    val injuryOptions: List<String> = DirectiveGenerator.INJURY_TAGS,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    /** A picked backup is waiting for the "replace everything" confirmation. */
    val importPending: Boolean = false,
    val busy: Boolean = false,
    val importSummary: ImportSummary? = null,
    val healthConnected: Boolean = false,
    /** Null until the first status check returns. */
    val healthStatus: HealthStatus? = null,
    val healthAction: HealthAction? = null,
    val healthPermissions: Set<String> = emptySet(),
    /** A reminder is set but the system blocks notifications, so it can't reach the hunter. */
    val notificationsBlocked: Boolean = false,
) {
    val canSaveName: Boolean get() = nameDraft.trim() != savedName && nameDraft.isNotBlank()
    val canSaveFocus: Boolean get() = focusDraft.size == 2 && focusDraft.toSet() != savedFocus.toSet()
}

sealed interface SettingsEvent {
    data class NameChanged(val name: String) : SettingsEvent
    data object SaveName : SettingsEvent
    data class ToggleFocus(val stat: Stat) : SettingsEvent
    data object SaveFocus : SettingsEvent
    data class SetDayStart(val hour: Int) : SettingsEvent
    data object OpenReminderPicker : SettingsEvent
    data object DismissReminderPicker : SettingsEvent
    data class SetReminder(val time: LocalTime?) : SettingsEvent
    data class ToggleEquipment(val id: String) : SettingsEvent
    data class SetInjuryMode(val enabled: Boolean) : SettingsEvent
    data class ToggleInjuryTag(val tag: String) : SettingsEvent
    data class SetHaptics(val enabled: Boolean) : SettingsEvent
    data class SetReduceMotion(val enabled: Boolean) : SettingsEvent
    data class ExportTo(val uri: Uri) : SettingsEvent
    data class ImportPicked(val uri: Uri) : SettingsEvent
    data object ConfirmImport : SettingsEvent
    data object CancelImport : SettingsEvent
    data object DismissImportSummary : SettingsEvent
    data object ConnectHealth : SettingsEvent
    data object HealthRationaleAccepted : SettingsEvent
    data object HealthActionHandled : SettingsEvent
    data class HealthPermissionsResult(val granted: Set<String>) : SettingsEvent
    data object DisconnectHealth : SettingsEvent
    data object DismissHealthRationale : SettingsEvent
    /** The screen came back to the foreground (e.g. from system settings): re-check what the system allows. */
    data object Resumed : SettingsEvent
}

private data class Drafts(
    val name: String? = null,
    val focus: List<Stat>? = null,
    val reminderPicker: Boolean = false,
    val pendingImport: String? = null,
    val busy: Boolean = false,
    val summary: ImportSummary? = null,
    val healthStatus: HealthStatus? = null,
    val healthAction: HealthAction? = null,
    val healthPermissions: Set<String> = emptySet(),
    val notificationsBlocked: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val uiPrefs: UiPrefsRepository,
    private val porter: GameDataPorter,
    private val backupFiles: BackupFiles,
    private val dayRefresher: DayRefresher,
    private val dayStartScheduler: DayStartScheduler,
    private val completionCenter: CompletionCenter,
    private val healthGateway: HealthGateway,
    private val notificationAccess: NotificationAccess,
    private val enableHealth: EnableHealthUseCase,
    private val disableHealth: DisableHealthUseCase,
) : ViewModel() {

    private val drafts = MutableStateFlow(Drafts())

    val uiState: StateFlow<SettingsUiState> = combine(observeSettings(), uiPrefs.prefs, drafts) { model, prefs, drafts ->
        model?.toUiState(prefs, drafts) ?: SettingsUiState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        val current = uiState.value.takeUnless { it.loading }
        when (event) {
            is SettingsEvent.NameChanged -> drafts.update { it.copy(name = event.name) }
            SettingsEvent.SaveName -> {
                val name = drafts.value.name ?: return
                if (!Awakening.isValidName(name)) {
                    return
                }
                save { updateSettings.rename(name) }.also { drafts.update { d -> d.copy(name = null) } }
            }
            is SettingsEvent.ToggleFocus -> current?.let { model ->
                val now = model.focusDraft
                val next = if (event.stat in now) now - event.stat else (now + event.stat).takeLast(2)
                drafts.update { it.copy(focus = next) }
            }
            SettingsEvent.SaveFocus -> {
                val focus = drafts.value.focus?.takeIf { it.size == 2 } ?: return
                save { updateSettings.setFocusStats(focus[0], focus[1]) }.also { drafts.update { d -> d.copy(focus = null) } }
            }
            is SettingsEvent.SetDayStart -> save(afterSaved = { dayStartScheduler.reschedule(); dayRefresher.refresh() }) {
                updateSettings.setDayStartHour(event.hour)
            }
            SettingsEvent.OpenReminderPicker -> drafts.update { it.copy(reminderPicker = true) }
            SettingsEvent.DismissReminderPicker -> drafts.update { it.copy(reminderPicker = false) }
            is SettingsEvent.SetReminder -> {
                drafts.update { it.copy(reminderPicker = false) }
                save { updateSettings.setReminder(event.time) }
            }
            is SettingsEvent.ToggleEquipment -> current?.let { model ->
                val next = if (event.id in model.equipment) model.equipment - event.id else model.equipment + event.id
                save { updateSettings.setEquipment(next) }
            }
            is SettingsEvent.SetInjuryMode -> current?.let { model -> save { updateSettings.setInjury(event.enabled, model.injuryTags) } }
            is SettingsEvent.ToggleInjuryTag -> current?.let { model ->
                val next = if (event.tag in model.injuryTags) model.injuryTags - event.tag else model.injuryTags + event.tag
                // At least one area stays selected; turn Injury Mode off instead of emptying the list.
                if (next.isNotEmpty()) save { updateSettings.setInjury(model.injuryMode, next) }
            }
            is SettingsEvent.SetHaptics -> viewModelScope.launch { uiPrefs.setHaptics(event.enabled) }
            is SettingsEvent.SetReduceMotion -> viewModelScope.launch { uiPrefs.setReduceMotion(event.enabled) }
            is SettingsEvent.ExportTo -> export(event.uri)
            is SettingsEvent.ImportPicked -> pickImport(event.uri)
            SettingsEvent.ConfirmImport -> confirmImport()
            SettingsEvent.CancelImport -> drafts.update { it.copy(pendingImport = null) }
            SettingsEvent.DismissImportSummary -> drafts.update { it.copy(summary = null) }
            SettingsEvent.ConnectHealth -> connectHealth()
            SettingsEvent.HealthRationaleAccepted -> drafts.update {
                it.copy(healthAction = HealthAction.REQUEST_PERMISSIONS, healthPermissions = healthGateway.requiredPermissions)
            }
            SettingsEvent.HealthActionHandled -> drafts.update {
                if (it.healthAction == HealthAction.SHOW_RATIONALE) it else it.copy(healthAction = null)
            }
            is SettingsEvent.HealthPermissionsResult -> launchHealth {
                when (enableHealth()) {
                    EnableHealthResult.Enabled -> completionCenter.message(UserMessage.HealthConnected)
                    EnableHealthResult.PermissionsMissing -> completionCenter.message(UserMessage.HealthDeclined)
                    EnableHealthResult.Unavailable -> completionCenter.message(UserMessage.HealthNotInstalled)
                }
            }
            SettingsEvent.DisconnectHealth -> launchHealth { disableHealth() }
            SettingsEvent.DismissHealthRationale -> drafts.update { it.copy(healthAction = null) }
            SettingsEvent.Resumed -> refreshNotificationAccess()
        }
    }


    init {
        refreshHealthStatus()
        refreshNotificationAccess()
    }

    private fun refreshNotificationAccess() = drafts.update { it.copy(notificationsBlocked = !notificationAccess.isGranted()) }

    private fun refreshHealthStatus() = launchHealth { drafts.update { it.copy(healthStatus = healthGateway.status()) } }

    /** Not installed → Play Store; otherwise the rationale first, then Health Connect's sheet (ARCH §3.4). */
    private fun connectHealth() = launchHealth {
        val status = healthGateway.status()
        drafts.update {
            it.copy(
                healthStatus = status,
                healthAction = if (status == HealthStatus.AVAILABLE) HealthAction.SHOW_RATIONALE else HealthAction.OPEN_STORE,
            )
        }
    }


    private fun launchHealth(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Health Connect action failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }
    /** Suggested name for the export document the system file picker opens with. */
    fun backupFileName(): String = porter.suggestedFileName()

    private fun save(afterSaved: suspend () -> Unit = {}, block: suspend () -> SettingsResult) {
        viewModelScope.launch {
            try {
                when (block()) {
                    SettingsResult.Saved -> afterSaved()
                    SettingsResult.DayStartBlocked -> completionCenter.message(UserMessage.DayStartBlocked)
                    SettingsResult.InvalidName, SettingsResult.InvalidFocus, SettingsResult.NoHunter ->
                        completionCenter.message(UserMessage.Error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving settings failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }

    private fun export(uri: Uri) {
        if (drafts.value.busy) return
        drafts.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                backupFiles.write(uri, porter.export())
                completionCenter.message(UserMessage.ExportDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
                completionCenter.message(UserMessage.ExportFailed)
            } finally {
                drafts.update { it.copy(busy = false) }
            }
        }
    }

    private fun pickImport(uri: Uri) {
        if (drafts.value.busy) return
        drafts.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val text = backupFiles.read(uri)
                if (text == null) completionCenter.message(UserMessage.ImportUnreadable) else drafts.update { it.copy(pendingImport = text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Reading the backup failed")
                completionCenter.message(UserMessage.ImportUnreadable)
            } finally {
                drafts.update { it.copy(busy = false) }
            }
        }
    }

    private fun confirmImport() {
        val text = drafts.value.pendingImport ?: return
        if (drafts.value.busy) return
        drafts.update { it.copy(busy = true, pendingImport = null) }
        viewModelScope.launch {
            try {
                when (val result = porter.import(text)) {
                    is ImportResult.Imported -> {
                        // Re-arm the background job and catch up the days from the restored data.
                        dayStartScheduler.reschedule()
                        dayRefresher.refresh()
                        drafts.update { it.copy(summary = result.summary) }
                    }
                    is ImportResult.Rejected -> completionCenter.message(UserMessage.ImportRejected(result.problem))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                completionCenter.message(UserMessage.Error)
            } finally {
                drafts.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun SettingsModel.toUiState(prefs: UiPrefs, drafts: Drafts) = SettingsUiState(
    loading = false,
    savedName = name,
    nameDraft = drafts.name ?: name,
    showNameError = drafts.name?.let { !Awakening.isValidName(it) } ?: false,
    savedFocus = focusStats,
    focusDraft = drafts.focus ?: focusStats,
    dayStartHour = dayStartHour,
    reminder = reminder,
    showReminderPicker = drafts.reminderPicker,
    equipment = equipment,
    equipmentOptions = equipmentOptions,
    injuryMode = injuryMode,
    injuryTags = injuryTags,
    hapticsEnabled = prefs.hapticsEnabled,
    reduceMotion = prefs.reduceMotion,
    importPending = drafts.pendingImport != null,
    busy = drafts.busy,
    importSummary = drafts.summary,
    healthConnected = healthEnabled,
    healthStatus = drafts.healthStatus,
    healthAction = drafts.healthAction,
    healthPermissions = drafts.healthPermissions,
    notificationsBlocked = drafts.notificationsBlocked && reminder != null,
)
