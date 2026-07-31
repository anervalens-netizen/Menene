package ro.menene.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ro.menene.app.R
import ro.menene.app.databinding.ItemSeriesBinding
import ro.menene.app.model.SeriesItem

class SeriesAdapter(
    private val onSeriesClick: (SeriesItem) -> Unit,
) : ListAdapter<SeriesItem, SeriesAdapter.SeriesViewHolder>(DIFF_CALLBACK) {
    private val palette = intArrayOf(
        Color.rgb(62, 136, 255),
        Color.rgb(246, 96, 111),
        Color.rgb(55, 190, 145),
        Color.rgb(139, 105, 220),
        Color.rgb(244, 151, 58),
        Color.rgb(42, 178, 194),
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder =
        SeriesViewHolder(ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

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
                cornerRadius = card.resources.getDimension(R.dimen.corner_large)
                setColor(fallbackColor)
            }
            fallbackInitial.text = item.title.firstOrNull()?.uppercase() ?: "M"
            title.text = item.title
            count.text = card.resources.getQuantityString(
                R.plurals.episodes_count,
                item.episodeCount,
                item.episodeCount,
            )
            card.contentDescription = "${item.title}, ${count.text}"

            ArtworkLoader.cancel(cover)
            if (item.coverUri == null) {
                cover.visibility = View.GONE
                fallbackInitial.visibility = View.VISIBLE
            } else {
                cover.visibility = View.VISIBLE
                fallbackInitial.visibility = View.GONE
                ArtworkLoader.load(card.context, item.coverUri, item.coverVersion, cover) {
                    cover.visibility = View.GONE
                    fallbackInitial.visibility = View.VISIBLE
                }
            }
            card.setOnClickListener { onSeriesClick(item) }
        }

        fun recycle() = ArtworkLoader.cancel(binding.cover)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SeriesItem>() {
            override fun areItemsTheSame(oldItem: SeriesItem, newItem: SeriesItem): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SeriesItem, newItem: SeriesItem): Boolean = oldItem == newItem
        }
    }
}
