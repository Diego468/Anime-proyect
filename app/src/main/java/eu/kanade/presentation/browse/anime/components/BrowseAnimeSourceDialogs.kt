package eu.kanade.presentation.browse.anime.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize


@Composable
fun RemoveEntryDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    entryToRemove: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = localize(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = localize(MR.strings.action_remove))
            }
        },
        title = {
            Text(text = localize(MR.strings.are_you_sure))
        },
        text = {
            Text(text = localize(MR.strings.remove_manga, entryToRemove))
        },
    )
}
