package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptRunResult
import com.bleelblep.glyphsharge.glyph.script.ScriptStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line in the studio console. */
data class ConsoleLine(val text: String, val isError: Boolean = false)

/** Everything the studio screen renders, in one immutable snapshot. */
data class StudioUiState(
    val animations: List<ScriptAnimation> = emptyList(),
    /**
     * Whether the editor is on screen.
     *
     * Deliberately *not* derived from [editing]: a brand new script has a draft
     * to edit, but a future state where the editor opens with an empty buffer
     * would then be impossible to represent — and that is exactly the bug a
     * derived flag invited.
     */
    val isEditorOpen: Boolean = false,
    /** The script being edited, saved or not. `null` on the list screen. */
    val editing: ScriptAnimation? = null,
    val name: String = "",
    val source: String = "",
    val isDirty: Boolean = false,
    val console: List<ConsoleLine> = emptyList(),
    val isRunning: Boolean = false
)

/**
 * Drives the animation studio: the list, the editor, and the two ways to try a
 * script out.
 *
 * **Check** compiles the source and reports the first syntax error, without
 * touching the glyph. **Glyph** plays it for real, through exactly the same
 * [GlyphAnimationManager] path the feature
 * services use — which is the only way to know a script actually looks right.
 */
@HiltViewModel
class AnimationStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CustomAnimationRepository,
    private val glyphAnimationManager: GlyphAnimationManager,
    private val glyphManager: GlyphManager
) : ViewModel() {

    private fun text(@StringRes id: Int, vararg args: Any): String =
        context.getString(id, *args)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var glyphJob: Job? = null

    init {
        viewModelScope.launch {
            repository.animations.collect { list ->
                _uiState.update { it.copy(animations = list) }
            }
        }
    }

    // region Editor

    /**
     * Starts a new script.
     *
     * [CustomAnimationRepository.draft] already carries a fresh id and the
     * starter source, so the editor has something real to show and Save writes
     * it under that id rather than allocating a second one.
     */
    fun newAnimation() {
        val draft = repository.draft()
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = draft,
                name = draft.name,
                source = draft.source,
                isDirty = true,
                console = listOf(ConsoleLine(text(R.string.studio_msg_new_script)))
            )
        }
    }

    /** Opens an existing animation for editing. */
    fun open(id: String) {
        val animation = repository.getById(id) ?: return
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = animation,
                name = animation.name,
                source = animation.source,
                isDirty = false,
                console = emptyList()
            )
        }
    }

    fun closeEditor() {
        stop()
        _uiState.update {
            it.copy(isEditorOpen = false, editing = null, isDirty = false)
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, isDirty = true) }
    }

    fun updateSource(source: String) {
        _uiState.update { it.copy(source = source, isDirty = true) }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim().ifEmpty { text(R.string.studio_untitled) }
        // A new script already has an id in its draft, so both paths below are a
        // plain save: `editing` is never null while the editor is open.
        val animation = repository.save(
            requireNotNull(state.editing).copy(name = name, source = state.source)
        )
        _uiState.update { it.copy(editing = animation, name = animation.name, isDirty = false) }
        _messages.value = text(R.string.studio_msg_saved, animation.name)
    }

    fun delete(id: String) {
        repository.delete(id)
        _messages.value = text(R.string.studio_msg_deleted)
    }

    fun duplicate(id: String) {
        repository.duplicate(id)?.let { _messages.value = text(R.string.studio_msg_copied, it.name) }
    }

    // endregion

    // region Running

    /** Compiles without drawing anything. */
    fun check() {
        val source = _uiState.value.source
        viewModelScope.launch(Dispatchers.Default) {
            val error = glyphAnimationManager.checkScript(source)
            _uiState.update {
                it.copy(
                    console = listOf(
                        if (error == null) {
                            ConsoleLine(text(R.string.studio_msg_syntax_ok))
                        } else {
                            ConsoleLine(text(R.string.studio_msg_syntax_error, error), isError = true)
                        }
                    )
                )
            }
        }
    }

    /**
     * Plays the script on the real glyph.
     *
     * The pre-flight checks are here rather than inside the manager because
     * they are about what the *studio* is looking at: a phone with no Glyph
     * interface, or a session that was never opened, both used to look
     * identical to a script that simply did nothing.
     */
    fun runOnGlyph() {
        stop()

        when {
            !glyphManager.isNothingPhone() -> {
                _uiState.update {
                    it.copy(console = listOf(ConsoleLine(text(R.string.studio_msg_no_glyph), isError = true)))
                }
                return
            }
            !glyphManager.isSessionActive -> {
                _uiState.update {
                    it.copy(
                        console = listOf(
                            ConsoleLine(text(R.string.studio_msg_session_closed), isError = true)
                        )
                    )
                }
                return
            }
        }

        val source = _uiState.value.source
        _uiState.update { it.copy(isRunning = true) }

        glyphJob = viewModelScope.launch {
            // No duration parameter: a script runs until its code is done, and
            // Stop is the user's way out. The cap only catches a script that
            // never returns.
            val result = glyphAnimationManager.previewScript(source, ScriptAnimation.SAFETY_CAP_MS)
            _uiState.update { it.copy(isRunning = false, console = result.toConsole()) }
        }
    }

    fun stop() {
        stopGlyph()
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun stopGlyph() {
        glyphJob?.cancel()
        glyphJob = null
        glyphAnimationManager.stopAnimations()
    }

    private fun ScriptRunResult.toConsole(): List<ConsoleLine> = when (status) {
        ScriptStatus.COMPLETED ->
            listOf(ConsoleLine(text(R.string.studio_msg_done, frames, elapsedMs)))

        ScriptStatus.TIMED_OUT ->
            listOf(ConsoleLine(message ?: text(R.string.studio_msg_timeout)))

        ScriptStatus.STOPPED ->
            listOf(ConsoleLine(message ?: text(R.string.studio_msg_stopped), isError = true))

        ScriptStatus.SYNTAX_ERROR ->
            listOf(
                ConsoleLine(
                    text(R.string.studio_msg_syntax_error, message.orEmpty()),
                    isError = true
                )
            )

        ScriptStatus.RUNTIME_ERROR ->
            listOf(ConsoleLine(text(R.string.studio_msg_runtime_error, message.orEmpty()), isError = true))
    }

    // endregion

    // region Import and export

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val fallback = uri.lastPathSegment?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: text(R.string.studio_default_import_name)
                val text = repository.readText(uri)
                repository.importFrom(text, fallback)
            }.onSuccess { animation ->
                _messages.value = text(R.string.studio_msg_imported, animation.name)
                open(animation.id)
            }.onFailure {
                _messages.value = text(R.string.studio_msg_import_failed, describe(it))
            }
        }
    }

    /** Writes to a location the user picked in the system file picker. */
    fun exportTo(uri: Uri, id: String) {
        viewModelScope.launch {
            val animation = repository.getById(id)
            if (animation == null) {
                _messages.value = text(R.string.studio_msg_missing)
                return@launch
            }
            runCatching { repository.exportTo(uri, animation) }
                .onSuccess { _messages.value = text(R.string.studio_msg_exported, animation.name) }
                .onFailure { _messages.value = text(R.string.studio_msg_export_failed, describe(it)) }
        }
    }

    /** One-tap export into the public Downloads folder. */
    fun exportToDownloads(id: String) {
        viewModelScope.launch {
            val animation = repository.getById(id)
            if (animation == null) {
                _messages.value = text(R.string.studio_msg_missing)
                return@launch
            }
            val uri = repository.exportToDownloads(animation)
            _messages.value = if (uri != null) {
                text(R.string.studio_msg_exported_downloads, uri.lastPathSegment.orEmpty())
            } else {
                text(R.string.studio_msg_downloads_failed)
            }
        }
    }

    private fun describe(error: Throwable): String =
        error.message ?: text(R.string.studio_msg_unknown_error)

    fun consumeMessage() {
        _messages.value = null
    }

    // endregion

    companion object {
        /** How long a run from the editor lasts before the watchdog stops it. */
        const val RUN_DURATION_MS = ScriptAnimation.SAFETY_CAP_MS
    }
}
