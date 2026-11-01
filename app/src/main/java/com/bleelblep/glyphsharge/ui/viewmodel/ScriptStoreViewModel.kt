package com.bleelblep.glyphsharge.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.data.ScriptPermissionStore
import com.bleelblep.glyphsharge.data.ScriptStoreRepository
import com.bleelblep.glyphsharge.data.ServiceScriptRepository
import com.bleelblep.glyphsharge.data.StoreInstall
import com.bleelblep.glyphsharge.data.StoreInstallRepository
import com.bleelblep.glyphsharge.data.StoreItem
import com.bleelblep.glyphsharge.data.StoreItemKind
import com.bleelblep.glyphsharge.glyph.GlyphAnimationManager
import com.bleelblep.glyphsharge.glyph.device.DeviceProfileFactory
import com.bleelblep.glyphsharge.glyph.script.ScriptCapability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * How an animation's name is folded before it is matched against the phone's
 * own scripts.
 *
 * [StoreItem.matchKey] and this are the same rule, deliberately written once
 * each on their own side: the catalogue item folds itself, and a local script's
 * name folds here. A comparison that trimmed on one side and not the other is
 * the kind of thing that passes every test and still fails on a phone.
 */
internal fun matchKey(name: String): String = name.trim().lowercase(Locale.ROOT)

/**
 * Which catalogue entries have a newer version published than the copy on the
 * phone — the ids whose card should offer "Update" where it would otherwise
 * say "Installed".
 *
 * Five things have to hold at once, and each of them rules out a false
 * positive that would cost the user a script:
 *
 *  * **The entry is an animation.** A service script has no local copy for a
 *    version comparison to mean anything: [StoreInstallRepository]'s field is
 *    called `animationId` because only an animation has ever been named in
 *    one, and "Update" would pass that id to an animation importer. Nothing
 *    records a service install today, so this condition is the statement of
 *    the rule rather than the only thing enforcing it — which is the point of
 *    writing it down.
 *  * **The name matches one of the phone's own scripts.** Same fold as
 *    everywhere else, so the two sides cannot disagree about case.
 *  * **There is a record for this catalogue id.** A script imported by hand has
 *    no version behind it, so there is nothing to compare against and no way to
 *    say how old it is.
 *  * **The animation the record names is still on the phone.** If it was
 *    deleted in the studio, the user may well have written a new one under the
 *    same name — and "Update" would then overwrite their own work with someone
 *    else's. This is the check that makes the button safe to press.
 *  * **The catalogue's version is strictly higher.** Equal is the ordinary
 *    case and offers nothing; lower means the phone holds something the
 *    catalogue does not describe, which no button here can improve.
 *
 * A pure function of its inputs so the rule can be stated in one place
 * and checked without a phone, the same reasoning as [matchKey].
 */
internal fun outdatedItems(
    items: List<StoreItem>,
    installed: Set<String>,
    installs: Map<String, StoreInstall>,
    liveAnimationIds: Set<String>,
): Set<String> = items.mapNotNullTo(mutableSetOf()) { item ->
    // Folded, because that is how `StoreInstallRepository` keys its map. It
    // used to be a raw `installs[item.id]`, which returned `null` for any
    // catalogue entry published with a capital — so the badge never lit for
    // exactly those items, and `updateTarget` returned `null` with it.
    val record = installs[StoreInstallRepository.foldedForComparison(item.id)]
    val outdated = (item.kind == StoreItemKind.ANIMATION) &&
        (item.matchKey in installed) &&
        (record != null) &&
        (record.animationId in liveAnimationIds) &&
        (record.version < item.version)
    if (outdated) item.id else null
}

/**
 * Which part of the catalogue the screen shows.
 *
 * Here rather than in the composable because it is a choice the user makes and
 * then navigates away from: a chip selection that resets when the screen
 * redraws is a control that appears not to work. Applied by the screen, not by
 * [ScriptStoreViewModel.derived] — the ordering rule below is the part with
 * consequences, and a filter that silently narrowed `items` would leave a
 * second, hidden input behind a derivation documented as having three.
 */
enum class StoreFilter {
    /** Every entry the catalogue published. */
    ALL,

    /** `.glyphlua` only: what the studio imports and the strip draws. */
    ANIMATION,

    /** `.servicelua` only: what the rule host runs in the background. */
    SERVICE,
    ;

    fun matches(item: StoreItem): Boolean = when (this) {
        ALL -> true
        ANIMATION -> item.kind == StoreItemKind.ANIMATION
        SERVICE -> item.kind == StoreItemKind.SERVICE
    }
}

/**
 * A service script that has been downloaded, hash-verified and scanned for
 * what it reaches for — waiting on the user's yes or no.
 *
 * The bytes are held rather than re-fetched after the answer: the hash was
 * checked against the catalogue's own entry *once*, and a second download
 * would be a second file the prompt never described.
 */
data class PendingServiceInstall(
    val item: StoreItem,
    val text: String,
    val requested: Set<ScriptCapability>,
)

/** Everything the store screen renders, in one immutable snapshot. */
data class ScriptStoreUiState(
    val items: List<StoreItem> = emptyList(),
    val loadState: ScriptStoreRepository.LoadState = ScriptStoreRepository.LoadState.Idle,

    /**
     * The names of the animations already on the phone, folded to compare.
     *
     * **Case-folded, not raw.** A catalogue says `Wave`; a user who imported the
     * same file into the studio and typed the name themselves ends up with
     * `wave`, and [CustomAnimationRepository.uniqueName] only ever appends
     * `(2)` — it never rewrites case. Matching raw strings would light the
     * badge for one user and not for the next, for no reason either could see.
     *
     * These are [StoreItem.matchKey] values, so both sides go through the same
     * fold rather than each side doing its own trimming.
     */
    val installed: Set<String> = emptySet(),

    /**
     * The names of the installed *service scripts*, folded the same way.
     *
     * Held apart from [installed] rather than folded into it: a `.servicelua`
     * and a `.glyphlua` can carry the same name and are still two different
     * things on the phone, and a badge lit off the other half would hide the
     * install button that would actually fix it. Same fold, same reason — only
     * the source differs.
     */
    val installedServices: Set<String> = emptySet(),

    /**
     * Which part of the catalogue the screen is showing; see [StoreFilter].
     *
     * Kept out of [derived] on purpose — the filter narrows what is *rendered*,
     * and `items` stays the whole catalogue so switching chips back does not
     * have to remember what it removed.
     */
    val filter: StoreFilter = StoreFilter.ALL,

    /**
     * A service script waiting on the capability prompt, or `null`.
     *
     * In the state rather than in a composable because the download that
     * produced it is the reason it exists: rotate the phone with the question
     * on screen and the verified bytes it is asking about are still here,
     * rather than a prompt whose answer has nothing behind it.
     */
    val pendingInstall: PendingServiceInstall? = null,

    /**
     * What the phone remembers about store items it has installed, by
     * catalogue id.
     *
     * Held rather than read on demand so that a card asking "is this current?"
     * is a set lookup instead of a preferences read per frame, and so that an
     * install writes once and every card hears about it.
     */
    val installs: Map<String, StoreInstall> = emptyMap(),

    /**
     * Ids of the installed items the catalogue has published something newer
     * than.
     *
     * **Ids, not names, because that is what the button acts on.** An update
     * rewrites a specific local script, and the rule that decides which one is
     * [outdatedItems]. The two are recomputed together with the sort in
     * [ScriptStoreViewModel.derived] — a catalogue, a studio and an install
     * history are three independent sources, and each of them can change what
     * the others mean.
     */
    val outdated: Set<String> = emptySet(),

    /**
     * Ids of the items with a download in flight.
     *
     * A set rather than one id: two taps in a row should mean two downloads,
     * and a single field would have the second silently replace the first.
     */
    val downloading: Set<String> = emptySet(),

    /** The id being run on the glyph right now, or `null`. */
    val testing: String? = null,
)

/**
 * Drives the animation store.
 *
 * Three ways a script reaches this screen, and they are deliberately different
 * in what they leave behind:
 *
 *  * **Install** saves it — hash, then [GlyphAnimationManager.checkScript], then
 *    [CustomAnimationRepository.importFrom], which is the only path that
 *    writes anything to disk. **Update** is the same download ending at
 *    [CustomAnimationRepository.updateFrom] instead, so the copy already on
 *    the phone is the one that changes.
 *  * **A service script installs differently**, and the difference is the whole
 *    of the capability model: hash, then [ScriptCapability.detect], then the
 *    prompt, then [ServiceScriptRepository.importFrom] and a grant against the
 *    freshly installed id. No `checkScript` — that compiles a script against
 *    this phone's LED layout, which is a question about *drawing*, and a
 *    `.servicelua` does not draw; [ServiceScriptFileFormat][com.bleelblep.glyphsharge.glyph.script.ServiceScriptFileFormat]'s
 *    own parser is what one has to satisfy. And no install record:
 *    [StoreInstallRepository] stores an `animationId`, which is what an update
 *    would then hand to an animation importer.
 *  * **Test** runs it on the real glyph and keeps nothing, for animations —
 *    a service script has no animation to run until a rule names one.
 *  * **Neither** on a phone the script does not claim to work on.
 *
 * The check runs before the install, not after, because a store entry that
 * does not parse is a catalogue bug and the user can do nothing about it —
 * whereas a script that parses and then fails at runtime is the author's, and
 * that is what the console is for.
 */
@HiltViewModel
class ScriptStoreViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ScriptStoreRepository,
    private val animations: CustomAnimationRepository,
    private val storeInstalls: StoreInstallRepository,
    private val serviceScripts: ServiceScriptRepository,
    private val scriptPermissions: ScriptPermissionStore,
    private val glyphAnimationManager: GlyphAnimationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScriptStoreUiState())
    val uiState: StateFlow<ScriptStoreUiState> = _uiState.asStateFlow()

    // A channel, not a `StateFlow`, because these are events rather than
    // state. `StateFlow` conflates equal values, so two failures that produced
    // the same sentence — which two installs of the same script would — sent
    // the same value twice and the second was never shown. A channel delivers
    // every send. `HomeViewModel` already works this way.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private fun notify(message: String) {
        _messages.trySend(message)
    }

    /** The models this phone can be asked about; `null` off Nothing hardware. */
    private val currentDevice get() = DeviceProfileFactory.forConnectedDevice()?.type

    init {
        viewModelScope.launch {
            store.items.collect { list ->
                _uiState.update { derived(it.copy(items = list)) }
            }
        }
        viewModelScope.launch {
            store.state.collect { state ->
                _uiState.update { it.copy(loadState = state) }
            }
        }
        viewModelScope.launch {
            // The phone's own scripts, kept in step with the catalogue. An
            // install made in the studio itself — the same file, imported by
            // hand — therefore lights the badge just as an install from here
            // does, which is the honest answer: the animation *is* on the phone.
            animations.animations.collect { scripts ->
                _uiState.update { state ->
                    val keys = scripts.mapTo(mutableSetOf()) { matchKey(it.name) }
                    derived(state.copy(installed = keys))
                }
            }
        }
        viewModelScope.launch {
            // The same question asked of a second list. A `.servicelua` the
            // user imported by hand lights its card here exactly as an
            // animation's does below, and for the same reason: the script
            // genuinely is on the phone, and a card that said otherwise would
            // offer an install that duplicates it.
            serviceScripts.scripts.collect { scripts ->
                _uiState.update { state ->
                    val keys = scripts.mapTo(mutableSetOf()) { matchKey(it.name) }
                    derived(state.copy(installedServices = keys))
                }
            }
        }
        viewModelScope.launch {
            // The install history, which is what turns a badge into a version
            // comparison: without it there is nothing to say that the copy on
            // the phone is behind what the catalogue publishes.
            storeInstalls.installs.collect { records ->
                _uiState.update { derived(it.copy(installs = records)) }
            }
        }
        // Paint the cache first, then ask the network. Opening the store on a
        // phone with no signal should show the catalogue it has seen before
        // rather than a spinner that resolves into an error.
        viewModelScope.launch { store.loadFromCache() }
        refresh()
    }

    /**
     * Everything that follows from the four inputs — the catalogue, the phone's
     * own animations, its installed service scripts, and what has been
     * installed from the store — folded into one step.
     *
     * One place rather than one per collector, because the four arrive
     * independently and each changes what the others mean: a refresh can
     * publish a newer version, deleting a script in the studio can take an
     * available update away, and an install can end one. Whichever collector
     * happened to notice first used to decide, which is how two of them end up
     * sorting the list and neither noticing a change in the update set.
     *
     * Records naming an animation that is no longer on the phone are dropped
     * here. They say nothing true about the phone any more, and keeping one
     * would eventually let "Update" overwrite a script the user has since
     * written themselves.
     *
     * [ScriptStoreUiState.filter] deliberately does not appear: it narrows what
     * the screen renders, not what is on the phone.
     */
    private fun derived(state: ScriptStoreUiState): ScriptStoreUiState {
        val live = animations.animations.value.mapTo(mutableSetOf()) { it.id }
        val installs = state.installs.filterValues { it.animationId in live }
        return state.copy(
            // Sorted here rather than in the composable: the order is part of
            // what the state *means*, so a test sees the same list the screen
            // does. Both halves count as "already here" for the sort — an
            // installed service belongs at the top as much as an installed
            // animation does — while [outdatedItems] keeps asking only the
            // animation half, because only animations can be outdated.
            items = state.items.sortedByDescending {
                (it.matchKey in state.installed) || (it.matchKey in state.installedServices)
            },
            installs = installs,
            outdated = outdatedItems(state.items, state.installed, installs, live),
        )
    }



    /** Fetches the index. [ScriptStoreRepository.refresh] never throws. */
    fun refresh() {
        viewModelScope.launch {
            val result = store.refresh()
            (result as? ScriptStoreRepository.LoadState.Failed)?.let { notify(it.reason) }
        }
    }

    /** Whether the connected phone is one this item claims to work on. */
    fun isSupported(item: StoreItem): Boolean = item.supports(currentDevice)

    /**
     * Downloads, checks and saves — or, when the card offered "Update",
     * downloads, checks and overwrites what is already there.
     *
     * Three refusals, each with its own message: the file not matching the
     * catalogue, the file not parsing, and the store being unreachable. They
     * are different problems for the user and collapsing them into "could not
     * install" would tell them nothing.
     *
     * The animation and update paths differ in one line, and the difference is
     * read once in [write] rather than being split into two functions: the
     * download, the hash check and the parse check are identical either way,
     * and a second copy of them is a second place for them to disagree.
     *
     * A service script branches before any of that, at the prompt, because what
     * it needs is a decision from the user rather than a check — see
     * [PendingServiceInstall].
     */
    fun install(item: StoreItem) {
        if (!isSupported(item)) return
        if (item.id in _uiState.value.downloading) return

        _uiState.update { it.copy(downloading = it.downloading + item.id) }

        viewModelScope.launch {
            val downloaded = runCatching { store.download(item) }
            if (downloaded.isFailure) {
                _uiState.update { it.copy(downloading = it.downloading - item.id) }
                downloaded.exceptionOrNull()?.let { notify(failureMessage(it)) }
                return@launch
            }

            val source = downloaded.getOrThrow()
            val requested = if (item.kind == StoreItemKind.SERVICE) {
                ScriptCapability.detect(source)
            } else {
                emptySet()
            }

            if (requested.isNotEmpty()) {
                // Nothing has been written, and `downloading` goes back to
                // clear: the card is no longer fetching, it is waiting on an
                // answer. The verified bytes are held rather than re-fetched,
                // so the hash that was checked once stays the hash that lands.
                _uiState.update {
                    it.copy(
                        downloading = it.downloading - item.id,
                        pendingInstall = PendingServiceInstall(item, source, requested),
                    )
                }
                return@launch
            }

            write(item, source, requested)
        }
    }

    /**
     * Accepting the prompt: the gate closes and the write runs.
     *
     * Everything before this — download, hash, detect — has already happened,
     * which is what makes the answer immediate. Declining leaves nothing to
     * undo, so [declinePendingInstall] only closes the question.
     */
    fun acceptPendingInstall() {
        val pending = _uiState.value.pendingInstall ?: return
        _uiState.update { it.copy(pendingInstall = null) }
        viewModelScope.launch { write(pending.item, pending.text, pending.requested) }
    }

    /** Declining installs nothing, and nothing has been written to undo. */
    fun declinePendingInstall() {
        _uiState.update { it.copy(pendingInstall = null) }
    }

    /** Which part of the catalogue the screen is showing; see [StoreFilter]. */
    fun setFilter(filter: StoreFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    /**
     * The write, for either kind — the one place anything reaches disk.
     *
     * Two bodies because they are not two versions of one thing: an animation
     * is checked against this phone's LED layout and recorded in an install
     * history an update can read back, while a service script is imported
     * through its own parser and the accepted capabilities are recorded
     * against the *installed copy's* id, which is the key the permissions
     * screen reads. The names are the only part they share, and that is why
     * this returns the name rather than the script.
     */
    private suspend fun write(item: StoreItem, source: String, granted: Set<ScriptCapability>) {
        // Set again as well as at entry: a gated install has it cleared while
        // the prompt is up, and the button has to go back to "Loading…" for the
        // write it is now doing.
        _uiState.update { it.copy(downloading = it.downloading + item.id) }

        val target = updateTarget(item)
        val result = runCatching {
            if (item.kind == StoreItemKind.SERVICE) {
                val installed = serviceScripts.importFrom(source, item.name)
                if (granted.isNotEmpty()) scriptPermissions.grant(installed.id, granted)
                installed.name
            } else {
                check(source)
                val saved = if (target != null) {
                    // Reached only if the script was deleted in the studio
                    // while the file was downloading. That refusal is said in
                    // the store's words rather than the repository's, because it
                    // is the one the user reads.
                    runCatching { animations.updateFrom(target, source) }.getOrElse {
                        throw StoreException(context.getString(R.string.store_msg_update_failed))
                    }
                } else {
                    animations.importFrom(source, item.name)
                }
                storeInstalls.record(item.id, item.version, saved.id)
                saved.name
            }
        }

        _uiState.update { it.copy(downloading = it.downloading - item.id) }
        result
            .onSuccess { name ->
                notify(
                    context.getString(
                        if (target != null) R.string.store_msg_updated else R.string.store_msg_installed,
                        name,
                    ),
                )
            }
            .onFailure { notify(failureMessage(it)) }
    }

    /**
     * The animation an update would overwrite, or `null` for a first install.
     *
     * Taken from [ScriptStoreUiState.outdated] rather than worked out again
     * here, so the button and the write can never disagree about what was
     * pressed — and so the "the script this names is gone from the phone" rule
     * is applied in exactly one place.
     */
    private fun updateTarget(item: StoreItem): String? {
        val state = _uiState.value
        return if (item.id in state.outdated) {
            state.installs[StoreInstallRepository.foldedForComparison(item.id)]?.animationId
        } else {
            null
        }
    }

    /**
     * Runs the item on the real glyph without saving it.
     *
     * The same download as an install, ending one step earlier: the point is
     * to see whether an animation looks right *before* it is on the phone, and
     * a copy the user then has to delete afterwards would not be that.
     *
     * [StoreItemKind.ANIMATION] only, and the screen hides the button for
     * anything else rather than relying on this — but the guard belongs here
     * because "Test" would otherwise preview a `.servicelua` as if it drew: it
     * files handlers in a preview that has no triggers to fire them, and the
     * strip never lights while the toast says it finished.
     */
    fun test(item: StoreItem) {
        if (!isSupported(item)) return
        if (item.kind != StoreItemKind.ANIMATION) return
        if (_uiState.value.testing != null) return

        _uiState.update { it.copy(downloading = it.downloading + item.id, testing = item.id) }

        viewModelScope.launch {
            val result = runCatching {
                val source = store.download(item)
                check(source)
                glyphAnimationManager.previewScript(source, PREVIEW_DURATION_MS)
            }

            _uiState.update {
                it.copy(downloading = it.downloading - item.id, testing = null)
            }
            result
                .onSuccess { notify(context.getString(R.string.store_msg_tested, item.name)) }
                .onFailure { notify(failureMessage(it)) }
        }
    }

    /**
     * Compiles the source without running it.
     *
     * The same [GlyphAnimationManager.checkScript] the editor's Check button
     * reaches, rather than a second path into the VM: it is the one place that
     * knows how to build a throwaway session, and a copy of that would be a
     * second answer to "does this parse". A phone whose LED layout is unknown
     * reports "nothing to check" rather than a failure — there is nothing to
     * check against.
     */
    private fun check(source: String) {
        val result = glyphAnimationManager.checkScript(source)
        if (!result.isOk) {
            throw StoreException(result.message ?: context.getString(R.string.store_msg_failed))
        }
    }

    private fun failureMessage(error: Throwable): String = when (error) {
        is StoreException -> error.message ?: context.getString(R.string.store_msg_failed)
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.store_msg_failed)
    }

    /** A refusal that already has a sentence written for it. */
    private class StoreException(message: String) : Exception(message)

    private companion object {
        /** Long enough to see an animation, short enough not to be stuck. */
        const val PREVIEW_DURATION_MS = 5_000L
    }
}