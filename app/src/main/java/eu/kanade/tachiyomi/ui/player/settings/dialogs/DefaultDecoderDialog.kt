package eu.kanade.tachiyomi.ui.player.settings.dialogs

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize

import eu.kanade.tachiyomi.ui.player.viewer.HwDecState
import tachiyomi.presentation.core.components.RadioItem

@Composable
fun DefaultDecoderDialog(
    currentDecoder: String,
    onSelectDecoder: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val items = mutableListOf(
        Pair("${HwDecState.HW.title} (${HwDecState.HW.mpvValue})", HwDecState.HW.mpvValue),
        Pair(HwDecState.SW.title, HwDecState.SW.mpvValue),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        items.add(
            index = 0,
            Pair(
                "${HwDecState.HW_PLUS.title} (${HwDecState.HW_PLUS.mpvValue})",
                HwDecState.HW_PLUS.mpvValue,
            ),
        )
    }

    fun selectDecoder(decoder: String) {
        onSelectDecoder(decoder)
        onDismissRequest()
    }

    PlayerDialog(
        titleRes = MR.strings.player_hwdec_mode,
        modifier = Modifier.fillMaxWidth(fraction = 0.8F),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            items.forEach {
                Spacer(Modifier.height(16.dp))
                RadioItem(
                    label = it.first,
                    selected = it.second == currentDecoder,
                    onClick = { selectDecoder(it.second) },
                )
            }
        }
    }
}
