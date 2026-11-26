package com.octorice.opengifwidget.config

import com.octorice.opengifwidget.ui.rememberHaptics
import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.TapAction
import com.octorice.opengifwidget.ui.InstalledApp
import com.octorice.opengifwidget.ui.InstalledApps
import com.octorice.opengifwidget.ui.rememberInstalledApp

/**
 * "When tapped", the editor's last section: Edit / App / (Next, slow slideshows only) / Nothing, one
 * short line saying what a tap will do, and the picked app with "Change". [onPickApp] opens the app picker.
 */
@Composable
internal fun TapSection(
    action: TapAction,
    app: ComponentName?,
    showNext: Boolean,
    enabled: Boolean,
    onAction: (TapAction) -> Unit,
    onPickApp: () -> Unit
) {
    val haptics = rememberHaptics()
    // "Next picture" only in slow slideshows (5 min or more per picture), where the app itself decides
    // which picture is up. Fast ones flip on the launcher's own timer, which the app can't move on.
    val choices = if (showNext) TapAction.entries else TapAction.entries - TapAction.NEXT
    SectionTitle(stringResource(R.string.tap_title), R.drawable.ic_tap)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = action == choice,
                onClick = { if (action != choice) haptics.pick(); onAction(choice) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {
                    // The picked one shows the usual checkmark; the others their own picture.
                    SegmentedButtonDefaults.Icon(active = action == choice) {
                        Icon(painterResource(choice.icon()), contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                    }
                }
            ) {
                Text(stringResource(choice.label()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(
            when (action) {
                TapAction.EDIT -> R.string.tap_edit_description
                TapAction.APP -> R.string.tap_app_description
                TapAction.NEXT -> R.string.tap_next_description
                TapAction.NOTHING -> R.string.tap_nothing_description
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
    AnimatedVisibility(visible = action == TapAction.APP && app != null) {
        val shown = rememberInstalledApp(app)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                AppIcon(shown, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    shown?.label.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickApp, enabled = enabled) { Text(stringResource(R.string.tap_change_app)) }
            }
        }
    }
}

private fun TapAction.icon() = when (this) {
    TapAction.EDIT -> R.drawable.ic_tap_edit
    TapAction.APP -> R.drawable.ic_tap_app
    TapAction.NEXT -> R.drawable.ic_next_picture
    TapAction.NOTHING -> R.drawable.ic_tap_off
}

private fun TapAction.label() = when (this) {
    TapAction.EDIT -> R.string.tap_edit
    TapAction.APP -> R.string.tap_app
    TapAction.NEXT -> R.string.tap_next
    TapAction.NOTHING -> R.string.tap_nothing
}

/** An app's own icon (in its own colors); a soft circle while it loads. */
@Composable
internal fun AppIcon(app: InstalledApp?, modifier: Modifier) {
    if (app != null) {
        Image(app.icon, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * The app picker: a pull-up sheet, opened tall, with the search box ready to type in and every app
 * A to Z. [selected] is marked. Swiping it away (or Back) calls [onDismiss] without picking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerSheet(selected: ComponentName?, onPick: (ComponentName) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) { value = InstalledApps.all(context.applicationContext) }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                // On tablets, a readable width in the middle.
                .wrapContentWidth()
                .widthIn(max = 640.dp)
                .imePadding()
        ) {
            Text(
                stringResource(R.string.tap_choose_app),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.tap_search_apps)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus)
            )
            // Ready to type: with many apps, a letter or two finds yours.
            LaunchedEffect(Unit) { focus.requestFocus() }
            Spacer(Modifier.height(8.dp))

            val all = apps
            if (all == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val words = query.trim()
                val shown = if (words.isEmpty()) all else all.filter { it.label.contains(words, ignoreCase = true) }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.tap_no_apps_match, words),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
                LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
                    items(shown, key = { it.component.flattenToString() }) { app ->
                        AppRow(app, picked = app.component == selected, onClick = { onPick(app.component) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, picked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            // The app this widget opens now is lit up.
            .background(if (picked) colors.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AppIcon(app, Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (picked) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (picked) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}
