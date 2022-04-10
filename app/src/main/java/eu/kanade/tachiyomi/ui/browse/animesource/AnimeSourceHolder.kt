package eu.kanade.tachiyomi.ui.browse.animesource

import android.view.View
import androidx.core.view.isVisible
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.LocalAnimeSource
import eu.kanade.tachiyomi.animesource.icon
import eu.kanade.tachiyomi.databinding.SourceMainControllerItemBinding
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.setVectorCompat

class AnimeSourceHolder(view: View, val adapter: AnimeSourceAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = SourceMainControllerItemBinding.bind(view)

    init {
        binding.sourceLatest.setOnClickListener {
            adapter.clickListener.onLatestClick(bindingAdapterPosition)
        }

        binding.pin.setOnClickListener {
            adapter.clickListener.onPinClick(bindingAdapterPosition)
        }
    }

    fun bind(item: AnimeSourceItem) {
        val source = item.source

        binding.title.text = source.name
        binding.subtitle.isVisible = source !is LocalAnimeSource
        binding.subtitle.text = LocaleHelper.getDisplayName(source.lang)

        // Set source icon
        val icon = source.icon()
        when {
            icon != null -> binding.image.load(icon)
            item.source.id == LocalAnimeSource.ID -> binding.image.load(R.mipmap.ic_local_source)
        }

        binding.sourceLatest.isVisible = source.supportsLatest

        binding.pin.isVisible = true
        if (item.isPinned) {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_24dp, R.attr.colorAccent)
        } else {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_outline_24dp, android.R.attr.textColorHint)
        }
    }
}
