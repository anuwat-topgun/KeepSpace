package com.smartstorage.cleaner.ui.feature.receipts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.smartstorage.cleaner.media.LocalRuleStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.ReceiptEntry
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

// Mirrors ios/SmartStorage/Features/Receipts/ReceiptFilingView.swift.

private fun formatDay(ms: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

/** Receipts found in screenshots, with what was read from each and where it would be filed. */
@Composable
fun ReceiptsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val state = libraryState()
    val rules by LocalRuleStore.current.rules.collectAsState()
    val receipts = state.content.receipts
    val colors = SmartTheme.colors
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Receipt Filing", "Details read on this device, ready to file by your rules.", Modifier.padding(bottom = 8.dp))
        if (receipts.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (state.isScanning) "Reading your screenshots…" else "No receipts found in your screenshots yet.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }
        AdaptiveGrid(receipts, minColumnWidth = 400.dp) { receipt ->
            SmartCard(
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.clickable(role = Role.Button) { onOpen(receipt.id) },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReceiptThumbnail(receipt, Modifier.size(width = 64.dp, height = 84.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(receipt.details.merchant ?: "Unknown merchant", style = SmartType.cardHeadline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(receipt.amountText, formatDay(receipt.details.date ?: receipt.capturedAt)).joinToString(" · "),
                            style = SmartType.metadata,
                            color = colors.textSecondary,
                        )
                        receipt.filingPlan(rules)?.let {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                                Text(it.folder, style = TextStyle(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
                }
            }
        }
    }
}

/** 16 — Receipt Filing: what was extracted, which rule matched, and where the file will go. */
@Composable
fun ReceiptFilingScreen(receiptId: String, onConnectCloud: () -> Unit, onReviewRule: () -> Unit, onBack: () -> Unit) {
    val receipt = libraryState().content.receipts.firstOrNull { it.id == receiptId }
    val rules by LocalRuleStore.current.rules.collectAsState()
    val colors = SmartTheme.colors
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Receipt Filing", "AI extracted details and matched a storage rule.", Modifier.padding(bottom = 8.dp))
        if (receipt == null) {
            SmartCard(style = CardStyle.Info) {
                Text("This receipt is no longer in your library.", style = SmartType.metadata, color = colors.textSecondary)
            }
            return@ScreenScaffold
        }
        BoxWithConstraints {
            if (maxWidth >= 820.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    Box(Modifier.weight(1f)) { ExtractedCard(receipt) }
                    Box(Modifier.width(380.dp)) { RuleCard(receipt) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    ExtractedCard(receipt)
                    RuleCard(receipt)
                }
            }
        }
        if (receipt.details.completeness < 3) {
            Notice(Icons.Rounded.ErrorOutline, Tint.Amber, "Check the details", "Some details couldn't be read. Missing values use placeholders in the file name.")
        }
        Notice(
            Icons.Rounded.CloudOff, Tint.Blue, "Connect a cloud account to upload",
            "The file is prepared and matched with the rule. Uploads go straight from this device to your ${receipt.filingPlan(rules)?.rule?.provider?.title ?: "cloud"}.",
        )
        PrimaryButton("Connect Google Drive", onClick = onConnectCloud, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Review Rule", onClick = onReviewRule, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Text("Details were read on this device and stay here.", style = TextStyle(fontSize = 12.sp), color = colors.textSecondary)
        }
    }
}

@Composable
private fun ExtractedCard(receipt: ReceiptEntry) {
    SmartCard(contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReceiptThumbnail(receipt, Modifier.size(width = 124.dp, height = 170.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Merchant", receipt.details.merchant, Icons.Rounded.Storefront, Tint.Blue)
                // No printed date: filing falls back to when the screenshot was taken, so say that.
                Field("Date", receipt.details.date?.let(::formatDay) ?: "${formatDay(receipt.capturedAt)} (photo date)", Icons.Rounded.CalendarMonth, Tint.Blue)
                Field("Amount", receipt.amountText, Icons.Rounded.Payments, Tint.Purple)
                Field("Category", receipt.details.category.title, Icons.Rounded.ShoppingBag, Tint.Coral)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String?, icon: ImageVector, tint: Tint) {
    val colors = SmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 40.dp)
        Column {
            Text(label, style = TextStyle(fontSize = 12.sp), color = colors.textSecondary)
            Text(
                value ?: "Not found",
                style = SmartType.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (value == null) colors.textSecondary else colors.textPrimary,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun RuleCard(receipt: ReceiptEntry) {
    val colors = SmartTheme.colors
    val rules by LocalRuleStore.current.rules.collectAsState()
    SmartCard(style = CardStyle.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.AutoAwesome, Tint.Teal)
                Column {
                    Text("Matched Rule", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text("This receipt will be saved to:", style = SmartType.metadata, color = colors.textSecondary)
                }
            }
            val plan = receipt.filingPlan(rules)
            if (plan == null) {
                Text("No enabled rule files receipts yet.", style = SmartType.metadata, color = colors.textSecondary)
            } else {
                RuleRow("Destination", plan.rule.provider.title, Icons.Rounded.CloudUpload)
                HorizontalDivider(color = colors.separator)
                RuleRow("Target Folder", plan.folder, Icons.Rounded.Folder)
                HorizontalDivider(color = colors.separator)
                RuleRow("File Name", plan.fileName, Icons.Rounded.Description)
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, value: String, icon: ImageVector) {
    val colors = SmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, Tint.Gray, size = 40.dp)
        Column {
            Text(label, style = TextStyle(fontSize = 12.sp), color = colors.textSecondary)
            // Paths can be long (spec §7.2): wrap rather than truncate.
            Text(value, style = SmartType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, tint: Tint, title: String, detail: String) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = tint.foreground(colors.isDark), modifier = Modifier.size(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
                Text(detail, style = SmartType.metadata, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ReceiptThumbnail(receipt: ReceiptEntry, modifier: Modifier) {
    val colors = SmartTheme.colors
    if (receipt.isDemo) {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = colors.textSecondary)
        }
    } else {
        AssetImage(receipt.id, ThumbnailStyle.Screen, modifier, cornerRadius = 10.dp, cropTop = true)
    }
}
