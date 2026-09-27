package com.smartstorage.cleaner.media

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The user's storage rules, saved on the device. Starts from the spec's example rules. Mirrors `RuleStore.swift`. */
class RuleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(prefs.getString(KEY, null)?.let(RuleCodec::decode) ?: StorageRule.defaults)
    val rules: StateFlow<List<StorageRule>> = _rules.asStateFlow()

    /** The first enabled rule for a trigger wins, in list order (spec §7.9). */
    fun ruleFor(trigger: RuleTrigger): StorageRule? = _rules.value.firstOrNull { it.isEnabled && it.trigger == trigger }

    fun save(rule: StorageRule) = update { list ->
        val index = list.indexOfFirst { it.id == rule.id }
        if (index >= 0) list.toMutableList().also { it[index] = rule } else list + rule
    }

    fun setEnabled(id: String, enabled: Boolean) = update { list -> list.map { if (it.id == id) it.copy(isEnabled = enabled) else it } }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private fun update(transform: (List<StorageRule>) -> List<StorageRule>) {
        _rules.value = transform(_rules.value)
        prefs.edit { putString(KEY, RuleCodec.encode(_rules.value)) }
    }

    private companion object {
        const val KEY = "storageRules.v1"
    }
}

/** JSON encoding for saved rules; unknown enum values fall back instead of dropping the rule. */
object RuleCodec {
    fun encode(rules: List<StorageRule>): String = JSONArray(rules.map { r ->
        JSONObject()
            .put("id", r.id).put("name", r.name).put("trigger", r.trigger.name).put("provider", r.provider.name)
            .put("folder", r.folderTemplate).put("fileName", r.fileNameTemplate)
            .put("afterUpload", r.afterUpload.name).put("enabled", r.isEnabled)
    }).toString()

    fun decode(json: String): List<StorageRule>? = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StorageRule(
                id = o.getString("id"),
                name = o.getString("name"),
                trigger = RuleTrigger.entries.firstOrNull { it.name == o.getString("trigger") } ?: RuleTrigger.Photo,
                provider = CloudProvider.entries.firstOrNull { it.name == o.getString("provider") } ?: CloudProvider.GoogleDrive,
                folderTemplate = o.getString("folder"),
                fileNameTemplate = o.getString("fileName"),
                afterUpload = AfterUploadAction.entries.firstOrNull { it.name == o.getString("afterUpload") } ?: AfterUploadAction.KeepOnDevice,
                isEnabled = o.optBoolean("enabled", true),
            )
        }
    }.getOrNull()
}

val LocalRuleStore = staticCompositionLocalOf<RuleStore> { error("RuleStore not provided") }
