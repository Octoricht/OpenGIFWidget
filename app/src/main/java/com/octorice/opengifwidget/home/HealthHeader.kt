package com.octorice.opengifwidget.home

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R

/** Amber for "heavy", in light and dark mode (Material 3 has no built-in warning color). */
private val AmberLight = Color(0xFFFFDEA6) to Color(0xFF261900)
private val AmberDark = Color(0xFF5C3F00) to Color(0xFFFFDEA6)

/**
 * The main screen's summary: number of widgets, storage, and estimated home screen memory.
 * Its color shows how hard the widgets push this phone (blue, amber, red), and when it's not
 * blue it explains the biggest problem. Tapping it opens [HealthSheet]: what each number means,
 * and (when not blue) what you can do about it.
 */
@Composable
fun HealthHeader(widgetCount: Int, storageBytes: Long, health: Health?, onClick: () -> Unit) {
    val context = LocalContext.current
    val level = health?.level ?: HealthLevel.GOOD
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (container, content) = when (level) {
        HealthLevel.GOOD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        HealthLevel.HEAVY -> if (dark) AmberDark else AmberLight
        HealthLevel.TOO_MUCH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    // Fade between colors instead of jumping.
    val containerColor by animateColorAsState(container, label = "health color")
    val contentColor by animateColorAsState(content, label = "health text color")

    Surface(
        onClick = onClick,
        enabled = health != null,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row {
                Stat("$widgetCount", pluralStringResource(R.plurals.home_widget_count, widgetCount), Modifier.weight(1f))
                Stat(Formatter.formatShortFileSize(context, storageBytes), stringResource(R.string.home_storage_used), Modifier.weight(1f))
                Stat(
                    health?.let { Formatter.formatShortFileSize(context, it.memoryBytes) } ?: "-",
                    stringResource(R.string.home_memory),
                    Modifier.weight(1f)
                )
            }
            AnimatedVisibility(visible = health != null && level != HealthLevel.GOOD) {
                if (health != null) HealthMessage(health)
            }
            // Says the header can be tapped, and what for.
            Text(
                stringResource(if (level == HealthLevel.GOOD) R.string.health_tap_details else R.string.health_tap_actions),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
}

/** What's wrong and what helps, e.g. "...keep about 180 MB... An FPS cap of 15 would bring that down to about 90 MB." */
@Composable
private fun HealthMessage(health: Health) {
    val context = LocalContext.current
    val lowerCap = health.lowerCap
    val detail = when (health.reason) {
        HealthReason.MEMORY -> {
            val problem = stringResource(R.string.health_memory, Formatter.formatShortFileSize(context, health.memoryBytes))
            // Suggest a lower FPS cap only if it would really help (at least 10% less memory).
            val tip = if (lowerCap != null && health.memoryAtLowerCap < health.memoryBytes * 0.9) {
                stringResource(R.string.health_tip_cap_memory, lowerCap, Formatter.formatShortFileSize(context, health.memoryAtLowerCap))
            } else {
                stringResource(R.string.health_tip_heavy_widget)
            }
            "$problem $tip"
        }
        HealthReason.ANIMATION -> {
            val problem = pluralStringResource(R.plurals.health_animation, health.flipsPerSecond, health.flipsPerSecond)
            val tip = if (lowerCap != null) {
                stringResource(R.string.health_tip_cap_smooth, lowerCap)
            } else {
                stringResource(R.string.health_tip_fewer)
            }
            "$problem $tip"
        }
        HealthReason.STORAGE -> stringResource(R.string.health_storage, Formatter.formatShortFileSize(context, health.freeStorageBytes))
        null -> ""
    }

    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(if (health.level == HealthLevel.TOO_MUCH) R.string.health_too_much_title else R.string.health_heavy_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
