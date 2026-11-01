package com.bleelblep.glyphsharge.services

import android.app.Service
import androidx.annotation.StringRes
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.data.SettingsRepository
import com.bleelblep.glyphsharge.glyph.GlyphFeature

/**
 * Everything the app has to know about one feature, in one record.
 *
 * The failure this prevents is structural rather than visible: with the
 * mapping spread across parallel `when` blocks, a feature could be added to
 * three of them and the fourth would still compile, because a `when` on an enum
 * only complains when a case is *missing*, never when a getter is paired with
 * the wrong setter. The service would start and stop correctly while reading
 * someone else's flag, and nothing would say so. As data, that pairing is
 * checked once, by the compiler, in a single list.
 *
 * The preference accessors are lambdas over [SettingsRepository] rather than
 * methods on it, so the mapping stays declarative and the repository keeps
 * having no idea that features exist as a group.
 *
 * **There is one feature left.** The seven that used to be here — Screen Off,
 * Glow Gate, Power Peek, Low Battery, NFC, Charging and VPN Connected — became
 * rules, and then their rules went the way of every built-in rule the user
 * does not want: the user asked for the app to ship *their* rules and nothing
 * else. A feature is now a thing with a card and a service of its own, and only
 * the music visualiser is one.
 */
data class FeatureSpec(
    val feature: GlyphFeature,
    /**
     * The service that runs this feature.
     *
     * The visualiser's own: a `MediaProjection` token can only be obtained by
     * an Activity, so it cannot be a rule the host runs.
     */
    val serviceClass: Class<out Service>,
    val stopAction: String,
    val isEnabled: (SettingsRepository) -> Boolean,
    val saveEnabled: (SettingsRepository, Boolean) -> Unit,

    /**
     * The toast this feature already shows when its card is opened while the
     * Glyph service is off. It is here because it is the same kind of fact —
     * one per feature, forgotten independently of the others — and because
     * reusing each feature's existing wording beats adding a second, slightly
     * different message next to it.
     */
    @param:StringRes val serviceOffMessage: Int,

    /**
     * The feature's own name, in the user's language.
     *
     * Here because it is needed in two places with no common ancestor: the
     * home card that shows it, and the tile that mirrors it.
     */
    @param:StringRes val titleRes: Int,
) {
    /**
     * Whether this feature is allowed to do anything at all.
     *
     * Every feature service asks the same two questions before it acts: is my
     * own switch on, and is the master Glyph service on. The second is not a
     * detail of this feature — it is the app-wide precondition, and a service
     * that starts while it is off shuts itself down, registers no trigger and
     * leaves the feature card claiming to be on with nothing behind it.
     *
     * Answering it once here is what stops the two halves from drifting: it is
     * the same gate for `onStartCommand`, for `onTaskRemoved` and for the event
     * handler, so a feature cannot pass one and fail another.
     */
    fun isRunnable(settings: SettingsRepository): Boolean =
        isEnabled(settings) && settings.getGlyphServiceEnabled()
}

/**
 * The one place a feature is wired to its service and its preference.
 *
 * Adding a feature means adding one entry here and one value to
 * [GlyphFeature]; nothing else in the app has to know the service exists.
 *
 * The order matches [GlyphFeature.entries] so the list can be read against the
 * enum, and `init` fails the build-up if the two ever disagree — an entry
 * missing from a `when` compiles, an entry missing from a `Map` lookup does
 * not.
 */
object FeatureSpecs {

    val all: List<FeatureSpec> = listOf(
        FeatureSpec(
            feature = GlyphFeature.MUSIC_VISUALIZER,
            serviceClass = MusicVisualizerService::class.java,
            stopAction = MusicVisualizerService.ACTION_STOP,
            isEnabled = { it.isMusicVizEnabled() },
            saveEnabled = { repo, enabled -> repo.saveMusicVizEnabled(enabled) },
            serviceOffMessage = R.string.music_viz_toast,
            titleRes = R.string.music_viz_title,
        ),
    )

    private val byFeature: Map<GlyphFeature, FeatureSpec> = all.associateBy { it.feature }

    /**
     * Strip participants that are in [GlyphFeature] but are not features.
     *
     * [GlyphFeature.PREVIEW] holds the strip exactly as a feature does — it has
     * to, or it draws over a live animation — but it has no service to start
     * and no preference to read, so it has no [FeatureSpec] and is deliberately
     * absent from [all].
     *
     * [GlyphFeature.RULE_HOST] is the same shape: one foreground service runs
     * every user rule, so it is a single strip participant with no per-feature
     * preference and no entry of its own here.
     *
     * Listed rather than allowed implicitly, so the `init` check below still
     * catches a feature that was added and wired up halfway.
     */
    val NON_FEATURE_PARTICIPANTS: Set<GlyphFeature> =
        setOf(GlyphFeature.PREVIEW, GlyphFeature.RULE_HOST)

    init {
        // Fail here rather than at the first lookup. Every caller of `of` is
        // driven by a [GlyphFeature] value, and a feature with no spec has no
        // service to start and no preference to read — so without this check the
        // mistake would surface as a crash deep inside a start intent, or worse,
        // as a feature that silently never runs.
        val missing = GlyphFeature.entries.asSequence()
            .filterNot { it in byFeature }
            .filterNot { it in NON_FEATURE_PARTICIPANTS }
            .toList()
        check(missing.isEmpty()) {
            "FeatureSpecs has no entry for ${missing.joinToString { it.name }}"
        }
    }

    fun of(feature: GlyphFeature): FeatureSpec =
        byFeature[feature] ?: error(
            "No FeatureSpec for ${feature.name}: a feature with no spec has no " +
                "service to start and no preference to read",
        )
}