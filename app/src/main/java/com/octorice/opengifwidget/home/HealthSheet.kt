package com.octorice.opengifwidget.home

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R

/** How many of the heaviest widgets the panel suggests editing. */
private const val HEAVY_SHOWN = 3

/**
 * Opens when you tap the main screen's header. Always explains what each number means; when the
 * header is amber or red, it also lists what you can do about it (only things the app can really do,
 * each one a tap away), so you can pick what suits you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSheet(
    health: Health,
    widgets: List<WidgetSummary>,
    fpsCap: Int,
    onDismiss: () -> Unit,
    onLowerCap: (Int) -> Unit,
    onOpenWidget: (Int) -> Unit,
    onOpenStorageSettings: () -> Unit
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // On tablets, a readable width in the middle.
                .wrapContentWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.sheet_title), style = MaterialTheme.typography.titleLarge)

            // What the three numbers mean.
            Explanation(
                title = pluralStringResource(R.plurals.sheet_widgets_title, widgets.size, widgets.size),
                text = stringResource(R.string.sheet_widgets_text)
            )
            Explanation(
                title = stringResource(R.string.sheet_storage_title, size(widgets.sumOf { it.storageBytes })),
                text = stringResource(R.string.sheet_storage_text)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Explanation(
                    title = stringResource(R.string.sheet_ram_title, size(health.memoryBytes)),
                    text = stringResource(R.string.sheet_ram_text)
                )
                // How close to the limit, as a bar in the header's color.
                LinearProgressIndicator(
                    progress = { (health.memoryBytes.toFloat() / health.limitBytes.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    color = levelColor(health.level),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Text(
                    stringResource(R.string.sheet_ram_limit, size(health.memoryBytes), size(health.limitBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (health.level != HealthLevel.GOOD) {
                HorizontalDivider()
                Text(stringResource(R.string.sheet_actions_title), style = MaterialTheme.typography.titleMedium)

                // 1. A lower FPS cap: applied right here, with what it saves.
                val lowerCap = health.lowerCap
                val capSaves = lowerCap != null && health.memoryAtLowerCap < health.memoryBytes * 0.9
                if (lowerCap != null && (capSaves || health.reason == HealthReason.ANIMATION)) {
                    Action(
                        title = stringResource(R.string.sheet_action_cap_title, lowerCap),
                        text = if (capSaves) {
                            stringResource(R.string.sheet_action_cap_saves, size(health.memoryBytes - health.memoryAtLowerCap), fpsCap)
                        } else {
                            stringResource(R.string.sheet_action_cap_smoother, fpsCap)
                        },
                        button = stringResource(R.string.sheet_action_cap_button),
                        onClick = { onLowerCap(lowerCap) }
                    )
                }

                // 2. The heaviest widgets: tap one to make it lighter in the editor.
                val heaviest = widgets
                    .mapNotNull { widget -> health.widgetMemory[widget.appWidgetId]?.let { widget to it } }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(HEAVY_SHOWN)
                if (heaviest.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Explanation(
                            title = stringResource(R.string.sheet_action_heavy_title),
                            text = stringResource(R.string.sheet_action_heavy_text)
                        )
                        heaviest.forEach { (widget, bytes) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { onOpenWidget(widget.appWidgetId) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        widget.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.padding(horizontal = 6.dp))
                                    Text(size(bytes), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 3. Removing widgets: only the launcher can, so explain how.
                Explanation(
                    title = stringResource(R.string.sheet_action_remove_title),
                    text = stringResource(R.string.sheet_action_remove_text)
                )

                // 4. Storage, when that's what's low.
                if (health.reason == HealthReason.STORAGE) {
                    Action(
                        title = stringResource(R.string.sheet_action_storage_title),
                        text = stringResource(R.string.sheet_action_storage_text, size(health.freeStorageBytes)),
                        button = stringResource(R.string.sheet_action_storage_button),
                        onClick = onOpenStorageSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun Explanation(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Action(title: String, text: String, button: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Explanation(title, text)
        FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

/** The bar's color: the theme color when fine, amber when heavy, red when too much. */
@Composable
private fun levelColor(level: HealthLevel): Color = when (level) {
    HealthLevel.GOOD -> MaterialTheme.colorScheme.primary
    HealthLevel.HEAVY -> Color(0xFFB26A00)
    HealthLevel.TOO_MUCH -> MaterialTheme.colorScheme.error
}
