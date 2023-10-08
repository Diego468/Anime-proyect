package eu.kanade.tachiyomi.ui.player.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.settings.sheets.subtitle.toHexString
import eu.kanade.tachiyomi.util.preference.toggle
import `is`.xyz.mpv.MPVLib
import tachiyomi.core.preference.Preference
import tachiyomi.presentation.core.components.material.padding
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream

class PlayerSettingsScreenModel(
    val preferences: PlayerPreferences = Injekt.get(),
    private val hasSubTracks: Boolean = true,
) : ScreenModel {

    fun togglePreference(preference: (PlayerPreferences) -> Preference<Boolean>) {
        preference(preferences).toggle()
    }

    private val standardPadding = PaddingValues(vertical = MaterialTheme.padding.small, horizontal = MaterialTheme.padding.medium)

    @Composable
    fun ToggleableRow(
        @StringRes textRes: Int,
        paddingValues: PaddingValues = standardPadding,
        isChecked: Boolean,
        onClick: () -> Unit,
        coloredText: Boolean = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(paddingValues),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = textRes),
                color = if (coloredText) MaterialTheme.colorScheme.primary else Color.Unspecified,
                style = MaterialTheme.typography.titleSmall,
            )
            Switch(
                checked = isChecked,
                onCheckedChange = null,
            )
        }
    }

    @Composable
    fun OverrideSubtitlesSwitch(
        content: @Composable () -> Unit,
    ) {
        val overrideSubtitles by preferences.overrideSubtitlesStyle().collectAsState()

        val updateOverride = {
            val overrideType = if (overrideSubtitles) "no" else "force"
            togglePreference(PlayerPreferences::overrideSubtitlesStyle)
            MPVLib.setPropertyString("sub-ass-override", overrideType)
            if (overrideSubtitles) updateSubtitleOptions()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoSubtitlesWarning()

            ToggleableRow(
                textRes = R.string.player_override_subtitle_font,
                isChecked = overrideSubtitles,
                onClick = updateOverride,
            )

            if (overrideSubtitles) {
                content()
            }
        }
    }

    private fun updateSubtitleOptions() {
        MPVLib.setPropertyString("sub-bold", if (preferences.boldSubtitles().get()) "yes" else "no")
        MPVLib.setPropertyString("sub-italic", if (preferences.italicSubtitles().get()) "yes" else "no")
        MPVLib.setPropertyInt("sub-font-size", preferences.subtitleFontSize().get())

        MPVLib.setPropertyString("sub-color", preferences.textColorSubtitles().get().toHexString())
        MPVLib.setPropertyString("sub-border-color", preferences.borderColorSubtitles().get().toHexString())
        MPVLib.setPropertyString("sub-back-color", preferences.backgroundColorSubtitles().get().toHexString())
    }

    @Composable
    fun NoSubtitlesWarning() {
        if (!hasSubTracks) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(id = R.string.player_subtitle_empty_warning),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        MPVLib.command(arrayOf("screenshot-to-file", filename, subtitleFlag))
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }
}
