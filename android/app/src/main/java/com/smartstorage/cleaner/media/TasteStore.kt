package com.smartstorage.cleaner.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Mirrors ios/SmartStorage/Taste/TasteStore.swift.

/**
 * Personalized AI Taste: learns, on this device, which photos a person keeps and lets Best Shot lean
 * that way. Opt-out and resettable; stores only three weights and a count (see [TasteProfile]).
 */
class TasteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("keepspace", Context.MODE_PRIVATE)

    data class State(val enabled: Boolean, val profile: TasteProfile) {
        /** The weights Best Shot should use right now. */
        val weights: ScoreWeights get() = if (enabled) profile.effective else ScoreWeights.Standard
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The weights Best Shot should use right now. */
    val weights: ScoreWeights get() = _state.value.weights

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        _state.value = read()
    }

    /** Records that the photo at [chosen] was kept out of a group. Does nothing while switched off. */
    fun learn(chosen: Int, among: List<ScoreFeatures>) {
        if (!_state.value.enabled) return
        write(_state.value.profile.learn(chosen, among))
    }

    /** Forgets everything learned; Best Shot goes back to the standard mix. */
    fun reset() = write(TasteProfile())

    private fun write(profile: TasteProfile) {
        prefs.edit {
            // Strings keep full double precision.
            putString(KEY_SHARPNESS, profile.learned.sharpness.toString())
            putString(KEY_FACE, profile.learned.face.toString())
            putString(KEY_EXPOSURE, profile.learned.exposure.toString())
            putInt(KEY_DECISIONS, profile.decisions)
        }
        _state.value = read()
    }

    private fun read(): State {
        val standard = ScoreWeights.Standard
        val profile = TasteProfile(
            learned = ScoreWeights(
                prefs.getString(KEY_SHARPNESS, null)?.toDoubleOrNull() ?: standard.sharpness,
                prefs.getString(KEY_FACE, null)?.toDoubleOrNull() ?: standard.face,
                prefs.getString(KEY_EXPOSURE, null)?.toDoubleOrNull() ?: standard.exposure,
            ),
            decisions = prefs.getInt(KEY_DECISIONS, 0),
        )
        return State(prefs.getBoolean(KEY_ENABLED, true), profile)
    }

    private companion object {
        const val KEY_ENABLED = "taste.enabled"
        const val KEY_SHARPNESS = "taste.sharpness"
        const val KEY_FACE = "taste.face"
        const val KEY_EXPOSURE = "taste.exposure"
        const val KEY_DECISIONS = "taste.decisions"
    }
}
