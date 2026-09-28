package com.smartstorage.cleaner.ui.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.media.LocalWeeklyReminder
import com.smartstorage.cleaner.media.WeeklyCleanSchedule
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import java.util.Locale

// Mirrors ios/SmartStorage/Features/Settings/NotificationsView.swift.

private val DAYS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

/** Notifications: the opt-in Weekly Smart Clean reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val reminder = LocalWeeklyReminder.current
    val state by reminder.state.collectAsState()
    val colors = SmartTheme.colors
    var pickingTime by remember { mutableStateOf(false) }
    var deniedNote by remember { mutableStateOf(false) }

    // Android 13+ asks at the moment the person turns the reminder on.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminder.setEnabled(granted)
        deniedNote = !granted
    }
    val toggle = { on: Boolean ->
        deniedNote = false
        when {
            !on -> reminder.setEnabled(false)
            reminder.canNotify() -> reminder.setEnabled(true)
            else -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Notifications", "A weekly nudge to keep your storage healthy.", Modifier.padding(bottom = 8.dp))

        SmartCard(contentPadding = PaddingValues(16.dp)) {
            ListTile(Icons.Rounded.NotificationsActive, "Weekly Smart Clean", tint = Tint.Purple, showsChevron = false,
                subtitle = "A reminder to review the safest cleanup suggestions.") {
                Switch(
                    checked = state.enabled,
                    onCheckedChange = toggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
                )
            }
        }

        if (deniedNote && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SmartCard(style = CardStyle.Info) {
                Text("Notifications are turned off for KeepSpace in Android Settings, so the reminder stays off.",
                    style = SmartType.metadata, color = Tint.Amber.foreground(colors.isDark))
            }
        }

        if (state.enabled) {
            SmartCard(contentPadding = PaddingValues(16.dp)) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Day", style = SmartType.metadata, color = colors.textSecondary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DAYS.forEach { (day, label) ->
                            val selected = day == state.schedule.weekday
                            Text(
                                label,
                                style = SmartType.body,
                                color = if (selected) Color.White else colors.accent,
                                modifier = Modifier
                                    .then(if (selected) Modifier.background(colors.accent, androidx.compose.foundation.shape.CircleShape) else Modifier.background(colors.icyBlue, androidx.compose.foundation.shape.CircleShape))
                                    .clickable(role = Role.RadioButton) { reminder.setSchedule(state.schedule.copy(weekday = day)) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { pickingTime = true }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Time", style = SmartType.body, color = colors.textPrimary)
                        Text("%02d:%02d".format(Locale.US, state.schedule.hour, state.schedule.minute), style = SmartType.body, color = colors.accent)
                    }
                }
            }
            SecondaryButton("Send a Test Notification", onClick = reminder::sendTest, modifier = Modifier.fillMaxWidth(), outlined = true)
        }

        SmartCard(style = CardStyle.Info) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Text(
                    "The reminder quotes what KeepSpace found the last time it scanned. It doesn't scan your photos in the background, and nothing is sent anywhere.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }
    }

    if (pickingTime) {
        val picker = rememberTimePickerState(state.schedule.hour, state.schedule.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    reminder.setSchedule(WeeklyCleanSchedule(state.schedule.weekday, picker.hour, picker.minute))
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}
