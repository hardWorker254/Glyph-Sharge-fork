package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.glyph.script.LogLevel
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import com.bleelblep.glyphsharge.glyph.script.ScriptCheckResult
import com.bleelblep.glyphsharge.glyph.script.ScriptCheckStatus
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

/**
 * One line in the studio console.
 *
 * [level] rather than an `isError` flag because a script's own output now
 * reaches this console, and that output has three severities: a script that
 * warns, then fails, has said something the author needs to see, and a flag
 * with two states cannot tell those apart. The outcome line the studio writes
 * itself uses the same three, so everything in the console is coloured by one
 * rule.
 */
data class ConsoleLine(val text: String, val level: LogLevel = LogLevel.INFO)

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

    /**
     * The plural-aware sibling of [text]: [quantity] picks the wording, and it
     * is the first [args] entry so the formatted string can interpolate it too.
     */
    private fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, quantity, *args)

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

    /**
     * Compiles without drawing anything.
     *
     * One line, whatever the verdict, because Check reports a single finding:
     * the first thing wrong with the file. A missing module gets its own
     * wording rather than being reported as a syntax error, which it is not,
     * and which would send the author hunting for a bracket that was never
     * missing.
     */
    fun check() {
        val source = _uiState.value.source
        viewModelScope.launch(Dispatchers.Default) {
            val result = glyphAnimationManager.checkScript(source)
            _uiState.update { it.copy(console = listOf(result.toConsoleLine())) }
        }
    }

    /**
     * Plays the script on the real glyph.
     *
     * The pre-flight checks are here rather than inside the manager because
     * they are about what the *studio* is looking at: a phone with no Glyph
     * interface, or a session that was never opened, would otherwise look
     * identical to a script that simply did nothing.
     */
    fun runOnGlyph() {
        stop()

        when {
            !glyphManager.isNothingPhone() -> {
                _uiState.update {
                    it.copy(
                        console = listOf(
                            ConsoleLine(text(R.string.studio_msg_no_glyph), LogLevel.ERROR)
                        )
                    )
                }
                return
            }
            !glyphManager.isSessionActive -> {
                _uiState.update {
                    it.copy(
                        console = listOf(
                            ConsoleLine(text(R.string.studio_msg_session_closed), LogLevel.ERROR)
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

    /**
     * The console for one finished run.
     *
     * The script's own lines come first and the outcome last, so a script
     * that logs its way to a failure reads top to bottom: the last thing it
     * said is above the verdict, which is the order the two happened in. A
     * run that logged nothing produces the outcome line on its own — there is
     * no empty section to render and no "the script said nothing" filler to
     * explain an absence.
     */
    private fun ScriptRunResult.toConsole(): List<ConsoleLine> {
        val fromScript = logLines.map { ConsoleLine(it.text, it.level) }
        val outcome = when (status) {
            ScriptStatus.COMPLETED ->
                ConsoleLine(plural(R.plurals.studio_msg_done, frames, frames, elapsedMs))

            // A timeout carries the watchdog's own sentence when it has one,
            // and the plain fallback otherwise. Not an error: the script did
            // nothing wrong, the run simply ran out of time.
            ScriptStatus.TIMED_OUT ->
                ConsoleLine(message ?: text(R.string.studio_msg_timeout))

            ScriptStatus.STOPPED ->
                ConsoleLine(message ?: text(R.string.studio_msg_stopped), LogLevel.ERROR)

            ScriptStatus.SYNTAX_ERROR ->
                ConsoleLine(
                    text(R.string.studio_msg_syntax_error, message.orEmpty()),
                    LogLevel.ERROR
                )

            ScriptStatus.RUNTIME_ERROR ->
                ConsoleLine(text(R.string.studio_msg_runtime_error, message.orEmpty()), LogLevel.ERROR)
        }
        return fromScript + outcome
    }

    /**
     * One line for one Check verdict.
     *
     * Each case gets its own string rather than a shared "check failed": the
     * point of the typed result is that the author is told which of the two
     * things is actually wrong with their file.
     */
    private fun ScriptCheckResult.toConsoleLine(): ConsoleLine = when (status) {
        ScriptCheckStatus.OK ->
            ConsoleLine(text(R.string.studio_msg_syntax_ok))

        ScriptCheckStatus.SYNTAX_ERROR ->
            ConsoleLine(text(R.string.studio_msg_syntax_error, message.orEmpty()), LogLevel.ERROR)

        ScriptCheckStatus.MISSING_MODULE ->
            ConsoleLine(text(R.string.studio_msg_missing_module, message.orEmpty()), LogLevel.ERROR)
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
