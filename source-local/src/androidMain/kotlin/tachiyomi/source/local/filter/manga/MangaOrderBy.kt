package tachiyomi.source.local.filter.manga

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import tachiyomi.core.i18n.localize
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize


sealed class MangaOrderBy(context: Context, selection: Selection) : Filter.Sort(
    context.localize(MR.strings.local_filter_order_by),
    arrayOf(context.localize(MR.strings.title), context.localize(MR.strings.date)),
    selection,
) {
    class Popular(context: Context) : MangaOrderBy(context, Selection(0, true))
    class Latest(context: Context) : MangaOrderBy(context, Selection(1, false))
}
