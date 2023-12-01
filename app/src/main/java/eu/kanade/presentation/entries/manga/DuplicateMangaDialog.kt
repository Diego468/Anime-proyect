package eu.kanade.presentation.entries.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize


@Composable
fun DuplicateMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = localize(MR.strings.are_you_sure))
        },
        text = {
            Text(text = localize(MR.strings.confirm_add_duplicate_manga))
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onOpenManga()
                    },
                ) {
                    Text(text = localize(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onDismissRequest) {
                    Text(text = localize(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                ) {
                    Text(text = localize(MR.strings.action_add))
                }
            }
        },
    )
}
