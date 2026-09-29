package com.bleelblep.glyphsharge.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bleelblep.glyphsharge.data.CustomAnimationRepository
import com.bleelblep.glyphsharge.glyph.script.ScriptAnimation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the animation pickers need from the studio's store.
 *
 * The pickers are plain Composables — a chip row in a feature dialog, a count
 * on the settings screen — so the repository reaches them through a ViewModel:
 * `hiltViewModel()` resolves from inside a dialog as well as from a screen.
 * One injected store means a script saved in the studio appears on every
 * feature card at once.
 */
@HiltViewModel
class CustomAnimationsViewModel @Inject constructor(
    private val repository: CustomAnimationRepository
) : ViewModel() {

    /**
     * Every stored script, newest first.
     *
     * The repository owns the flow and is the only thing that writes to it, so
     * this re-exposes it rather than copying it — two collectors would
     * otherwise watch two copies of the same list.
     */
    val animations: StateFlow<List<ScriptAnimation>> = repository.animations

    /** Just the size, for the settings row that opens the studio. */
    val animationCount: StateFlow<Int> = repository.animations
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        /**
         * Long enough that scrolling the settings row off screen and back does
         * not restart the collection, short enough that the count stops being
         * observed while the user is somewhere else entirely.
         */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
