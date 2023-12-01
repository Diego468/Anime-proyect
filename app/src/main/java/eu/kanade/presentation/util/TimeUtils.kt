package eu.kanade.presentation.util

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize

import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Duration.toDurationString(context: Context, fallback: String): String {
    return toComponents { days, hours, minutes, seconds, _ ->
        buildList(4) {
            if (days != 0L) add(context.localize(MR.strings.day_short, days))
            if (hours != 0) add(context.localize(MR.strings.hour_short, hours))
            if (minutes != 0 && (days == 0L || hours == 0)) {
                add(
                    context.localize(MR.strings.minute_short, minutes),
                )
            }
            if (seconds != 0 && days == 0L && hours == 0) {
                add(
                    context.localize(MR.strings.seconds_short, seconds),
                )
            }
        }.joinToString(" ").ifBlank { fallback }
    }
}

@Composable
@ReadOnlyComposable
fun relativeTimeSpanString(epochMillis: Long): String {
    val now = Date().time
    return when {
        epochMillis <= 0L -> localize(MR.strings.relative_time_span_never)
        now - epochMillis < 1.minutes.inWholeMilliseconds -> localize(MR.strings.updates_last_update_info_just_now)
        else -> DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
