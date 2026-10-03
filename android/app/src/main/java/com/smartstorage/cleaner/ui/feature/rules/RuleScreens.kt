package com.smartstorage.cleaner.ui.feature.rules

import com.smartstorage.cleaner.ui.i18n.localizedFormat
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.ui.components.ProChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.AfterUploadAction
import com.smartstorage.cleaner.media.CloudProvider
import com.smartstorage.cleaner.media.LocalRuleStore
import com.smartstorage.cleaner.media.RuleProblem
import com.smartstorage.cleaner.media.RuleTrigger
import com.smartstorage.cleaner.media.StorageRule
import com.smartstorage.cleaner.media.TemplateResolver
import com.smartstorage.cleaner.media.preview
import com.smartstorage.cleaner.media.problems
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

// Mirrors ios/SmartStorage/Features/Rules/*.swift.

private val RuleTrigger.icon: ImageVector
    get() = when (this) {
        RuleTrigger.Photo -> Icons.Rounded.Collections
        RuleTrigger.Screenshot -> Icons.Rounded.CropFree
        RuleTrigger.Receipt -> Icons.AutoMirrored.Rounded.ReceiptLong
        RuleTrigger.LargeVideo -> Icons.Rounded.Videocam
        RuleTrigger.ScreenRecording -> Icons.Rounded.RadioButtonChecked
        RuleTrigger.Favorite -> Icons.Rounded.Favorite
    }

private val RuleTrigger.tint: Tint
    get() = when (this) {
        RuleTrigger.Photo, RuleTrigger.ScreenRecording, RuleTrigger.Favorite -> Tint.Coral
        RuleTrigger.Screenshot -> Tint.Blue
        RuleTrigger.Receipt -> Tint.Mint
        RuleTrigger.LargeVideo -> Tint.Purple
    }

/** 14 — Storage Rules: automation that files content where it belongs. */
@Composable
fun StorageRulesScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, onBack: () -> Unit) {
    val store = LocalRuleStore.current
    val rules by store.rules.collectAsState()
    val colors = SmartTheme.colors
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Storage Rules", "Choose where each kind of file would be filed.", Modifier.padding(bottom = 8.dp))

        SmartCard(style = CardStyle.Hero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Automation")
                    val active = rules.count { it.isEnabled }
                    Text(localizedFormat(if (active == 1) "%d rule on" else "%d rules on", active), style = SmartType.metricLarge, color = colors.textPrimary)
                    // Honest until v1.1 lands: rules are ready but nothing uploads yet.
                    Text("No cloud account connected yet", style = SmartType.body, color = colors.textSecondary)
                }
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(colors.icyBlue),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp)) }
            }
        }

        if (rules.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text("No rules yet. Add one to start filing automatically.", style = SmartType.metadata, color = colors.textSecondary)
            }
        }

        AdaptiveGrid(rules, minColumnWidth = 400.dp) { rule -> RuleRow(rule, onOpen = { onEdit(rule.id) }) }

        // The first rule is free; a second opens the paywall. Existing rules are never removed.
        val monetization = LocalMonetization.current
        val proStatus by monetization.status.collectAsState()
        val presentPaywall = LocalPresentPaywall.current
        val canAdd = remember(proStatus, rules) { monetization.allowances().canCreateRule(rules.count { !it.isExample }) }
        Box(contentAlignment = Alignment.Center) {
            PrimaryButton("+  Add Rule", onClick = { if (canAdd) onAdd() else presentPaywall(ProFeature.UnlimitedRules) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), showsArrow = false)
            if (!canAdd) ProChip(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, top = 4.dp))
        }
    }
}

@Composable
private fun RuleRow(rule: StorageRule, onOpen: () -> Unit) {
    val store = LocalRuleStore.current
    val colors = SmartTheme.colors
    SmartCard(contentPadding = PaddingValues(16.dp), modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.alpha(if (rule.isEnabled) 1f else 0.5f)) { IconTile(rule.trigger.icon, rule.trigger.tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(rule.name, style = SmartType.cardHeadline, color = if (rule.isEnabled) colors.textPrimary else colors.textSecondary)
                ProviderBadge(rule.provider)
                // Templates wrap gracefully (spec: long/localized paths).
                Text(rule.folderTemplate, style = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace), color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { store.setEnabled(rule.id, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
            }
        }
    }
}

/** Neutral provider label (spec: "provider identity visible but not brand-heavy"). */
@Composable
fun ProviderBadge(provider: CloudProvider) {
    val colors = SmartTheme.colors
    Row(
        Modifier.clip(CircleShape).background(colors.icyBlue).padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (provider == CloudProvider.GoogleDrive) Icons.Rounded.CloudUpload else Icons.Rounded.Cloud,
            contentDescription = null,
            tint = (if (provider == CloudProvider.GoogleDrive) Tint.Mint else Tint.Blue).foreground(colors.isDark),
            modifier = Modifier.size(14.dp),
        )
        Text(provider.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.accent)
    }
}

/** 15 — New Rule / Edit Rule: a guided form with a live preview of where files will land. */
@Composable
fun RuleEditorScreen(ruleId: String?, onDone: () -> Unit) {
    val store = LocalRuleStore.current
    val existing = remember(ruleId) { ruleId?.let { id -> store.rules.value.firstOrNull { it.id == id } } }
    val colors = SmartTheme.colors
    val start = existing ?: StorageRule(
        name = RuleTrigger.Receipt.title, trigger = RuleTrigger.Receipt, provider = CloudProvider.GoogleDrive,
        folderTemplate = RuleTrigger.Receipt.suggestedFolder, fileNameTemplate = RuleTrigger.Receipt.suggestedFileName,
        afterUpload = AfterUploadAction.SuggestDeletion,
    )
    var trigger by rememberSaveable { mutableStateOf(start.trigger) }
    var provider by rememberSaveable { mutableStateOf(start.provider) }
    var name by rememberSaveable { mutableStateOf(start.name) }
    var folder by rememberSaveable { mutableStateOf(start.folderTemplate) }
    var fileName by rememberSaveable { mutableStateOf(start.fileNameTemplate) }
    var afterUpload by rememberSaveable { mutableStateOf(start.afterUpload) }
    var acknowledged by rememberSaveable { mutableStateOf(existing?.afterUpload == AfterUploadAction.DeleteAfter30Days) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    val draft = start.copy(name = name, trigger = trigger, provider = provider, folderTemplate = folder, fileNameTemplate = fileName, afterUpload = afterUpload)
    val canSave = draft.problems.isEmpty() && (afterUpload != AfterUploadAction.DeleteAfter30Days || acknowledged)

    /** Follow the new trigger's suggestions only where the user hasn't customised anything. */
    fun select(new: RuleTrigger) {
        if (name == trigger.title) name = new.title
        if (folder == trigger.suggestedFolder) folder = new.suggestedFolder
        if (fileName == trigger.suggestedFileName) fileName = new.suggestedFileName
        trigger = new
    }

    ScreenScaffold(onBack = onDone) {
        ScreenHeader(if (existing == null) "New Rule" else "Edit Rule", "Create an automatic filing rule.", Modifier.padding(bottom = 8.dp))

        PickerRow("When", trigger.title, trigger.icon, trigger.tint, RuleTrigger.entries, { it.title }, ::select)
        PickerRow("Save to", provider.title, Icons.Rounded.CloudUpload, Tint.Blue, CloudProvider.entries, { it.title }) { provider = it }

        TemplateField("Folder", Icons.Rounded.Folder, Tint.Blue, folder, TemplateResolver.folderVariables) { folder = it }
        TemplateField("File name", Icons.Rounded.Description, Tint.Purple, fileName, TemplateResolver.fileNameVariables) { fileName = it }

        val preview = draft.preview()
        SmartCard(style = CardStyle.Info, contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Preview")
                Text(preview.rule.provider.title, style = SmartType.metadata, color = colors.textSecondary)
                Text(preview.fullPath, style = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace), color = colors.textPrimary)
                if (trigger == RuleTrigger.Receipt) {
                    Text("Example: a ฿3,450 receipt from Central Department Store, today.", style = TextStyle(fontSize = 12.sp), color = colors.textSecondary)
                }
            }
        }
        draft.problems.forEach { problem ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Tint.Coral.foreground(colors.isDark), modifier = Modifier.size(18.dp))
                Text(
                    when (problem) {
                        RuleProblem.EmptyFolder -> "Add a folder to save into."
                        RuleProblem.EmptyFileName -> "Add a file name."
                        is RuleProblem.UnknownVariables -> "Not a known variable: ${problem.names.joinToString()}. Tap a chip to insert one."
                    },
                    style = SmartType.metadata,
                    color = Tint.Coral.foreground(colors.isDark),
                )
            }
        }

        SmartCard(contentPadding = PaddingValues(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Rule name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, focusedLabelColor = colors.accent),
            )
        }

        SmartCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListTile(Icons.Rounded.Settings, "After upload", tint = Tint.Coral, subtitle = "What should happen to the original file?", showsChevron = false)
                AfterUploadAction.entries.forEach { action ->
                    val selected = action == afterUpload
                    SmartCard(
                        style = if (selected) CardStyle.Selected else CardStyle.Plain,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { afterUpload = action },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) colors.accent else colors.textSecondary,
                            )
                            Column {
                                Text(action.title, style = SmartType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                Text(action.detail, style = SmartType.metadata, color = colors.textSecondary)
                            }
                        }
                    }
                }
                if (afterUpload == AfterUploadAction.DeleteAfter30Days) {
                    // Spec §7.8: delayed deletion must be explicit and conservative.
                    Row(
                        Modifier.toggleable(value = acknowledged, role = Role.Checkbox) { acknowledged = it },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                        Text(
                            "I understand originals will be suggested for deletion 30 days after each verified upload.",
                            style = SmartType.metadata,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        SmartCard(style = CardStyle.Info) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = Tint.Blue.foreground(colors.isDark), modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Classification happens on your device.", style = SmartType.cardHeadline, color = colors.textPrimary)
                    Text(
                        "Files are analyzed and categorized on this device. Nothing is sent to the cloud until you back up.",
                        style = SmartType.metadata,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        PrimaryButton("Save Rule", onClick = { store.save(draft); onDone() }, modifier = Modifier.fillMaxWidth(), enabled = canSave)
        if (existing != null) {
            SecondaryButton("Delete Rule", onClick = { confirmingDelete = true }, modifier = Modifier.fillMaxWidth(), outlined = true)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this rule?") },
            text = { Text("Files already filed stay where they are.") },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; store.delete(start.id); onDone() }) { Text("Delete Rule") } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun <T> PickerRow(label: String, value: String, icon: ImageVector, tint: Tint, options: List<T>, title: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmartCard(contentPadding = PaddingValues(16.dp), modifier = Modifier.clickable(role = Role.Button) { open = true }) {
            ListTile(icon, label, tint = tint, subtitle = value)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(title(option)) }, onClick = { onPick(option); open = false })
            }
        }
    }
}

@Composable
private fun TemplateField(label: String, icon: ImageVector, tint: Tint, value: String, variables: List<String>, onChange: (String) -> Unit) {
    val colors = SmartTheme.colors
    SmartCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, tint, size = 44.dp)
                Text(label, style = SmartType.cardHeadline, color = colors.textPrimary)
            }
            // Paths get long; the field wraps instead of scrolling out of view.
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                variables.forEach { variable ->
                    Text(
                        variable,
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.icyBlue)
                            .clickable(role = Role.Button) { onChange(value + variable) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
