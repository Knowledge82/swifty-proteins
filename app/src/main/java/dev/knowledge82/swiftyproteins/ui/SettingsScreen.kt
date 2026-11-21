package dev.knowledge82.swiftyproteins.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.prefs.Settings
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

/**
 * "Configuration rites" (bonus VII.2, document 16): the model and hydrogens a molecule opens with, and the sounds.
 * Every change is saved at once; there is no "save" button to forget.
 *
 * @param onChange receives a CHANGE (a function of the current settings), not new settings: two quick taps can never
 *                 undo each other by both starting from the same old value
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    CogitatorBackground(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CogitatorPanel(label = "COGITATOR // CONFIGURATION", contentPadding = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackKey(onClick = onBack)
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", style = MaterialTheme.typography.headlineMedium.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold)
                }
            }

            CogitatorPanel(label = "AUSPEX DEFAULTS", modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Model at start",
                    value = settings.defaultModel.label,
                    key = "CHANGE",
                    keyDescription = "Model at start: ${settings.defaultModel.label}. Change it",
                    onKey = { onChange { it.copy(defaultModel = it.defaultModel.next()) } },
                )
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    title = "Hydrogens at start",
                    value = if (settings.showHydrogens) "Shown" else "Hidden",
                    key = if (settings.showHydrogens) "ON" else "OFF",
                    keyDescription = "Hydrogens at start",
                    active = settings.showHydrogens,
                    onKey = { onChange { it.copy(showHydrogens = !it.showHydrogens) } },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Applies to the next molecule you open.",
                    style = MaterialTheme.typography.bodySmall.glowing(SwiftyColors.Phosphor),
                    color = SwiftyColors.Phosphor,
                )
            }

            CogitatorPanel(label = "VOX-CASTER", modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Sounds",
                    value = if (settings.sounds) "On (silent mode still wins)" else "Off",
                    key = if (settings.sounds) "ON" else "OFF",
                    keyDescription = "Sounds",
                    active = settings.sounds,
                    onKey = { onChange { it.copy(sounds = !it.sounds) } },
                )
            }

            Spacer(Modifier.weight(1f))
            StatusFooter(line = "RITES OF CONFIGURATION // SAVED", detail = "EVERY CHANGE IS KEPT AT ONCE")
        }
    }
}

/** A setting: its name and current value on the left, a 52 dp key on the right. */
@Composable
private fun SettingRow(
    title: String,
    value: String,
    key: String,
    keyDescription: String,
    onKey: () -> Unit,
    active: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Bone)
            Text(value, style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
        }
        ConsoleKey(
            text = key,
            onClick = onKey,
            contentDescription = keyDescription,
            active = active,
            stateDescription = value,
        )
    }
}
