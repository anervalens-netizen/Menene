package ro.mehene.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ro.mehene.app.R
import ro.mehene.app.databinding.ItemSeriesBinding
import ro.mehene.app.model.SeriesItem

class SeriesAdapter(
    private val onSeriesClick: (SeriesItem) -> Unit,
) : ListAdapter<SeriesItem, SeriesAdapter.SeriesViewHolder>(DIFF_CALLBACK) {

    private val palette = intArrayOf(
        Color.rgb(77, 150, 255),
        Color.rgb(255, 107, 107),
        Color.rgb(93, 211, 158),
        Color.rgb(155, 126, 222),
        Color.rgb(255, 168, 76),
        Color.rgb(55, 190, 201),
    )

    fun submitItems(newItems: List<SeriesItem>) {
        submitList(newItems.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val binding = ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeriesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(getItem(position), palette[position % palette.size])
    }

    override fun onViewRecycled(holder: SeriesViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class SeriesViewHolder(
        private val binding: ItemSeriesBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SeriesItem, fallbackColor: Int) = with(binding) {
            card.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * card.resources.displayMetrics.density
                setColor(fallbackColor)
            }
            fallbackInitial.text = item.title.firstOrNull()?.uppercase() ?: "M"
            fallbackInitial.visibility = View.VISIBLE
            title.text = item.title
            count.text = card.resources.getQuantityString(
                R.plurals.episodes_count,
                item.episodeCount,
                item.episodeCount,
            )
            card.contentDescription = "${item.title}, ${count.text}"

            ArtworkLoader.cancel(cover)
            val coverUri = item.coverUri
            if (coverUri == null) {
                cover.visibility = View.GONE
            } else {
                cover.visibility = View.VISIBLE
                ArtworkLoader.load(card.context, coverUri, item.coverVersion, cover) {
                    cover.visibility = View.GONE
                }
            }
            card.setOnClickListener { onSeriesClick(item) }
        }

        fun recycle() {
            ArtworkLoader.cancel(binding.cover)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SeriesItem>() {
            override fun areItemsTheSame(oldItem: SeriesItem, newItem: SeriesItem): Boolean =
                oldItem.directoryUri == newItem.directoryUri

            override fun areContentsTheSame(oldItem: SeriesItem, newItem: SeriesItem): Boolean =
                oldItem == newItem
        }
    }
}
