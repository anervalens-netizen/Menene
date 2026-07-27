package ro.mehene.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ro.mehene.app.R
import ro.mehene.app.databinding.ItemSeriesBinding
import ro.mehene.app.model.SeriesItem

class SeriesAdapter(
    private val onSeriesClick: (SeriesItem) -> Unit,
) : RecyclerView.Adapter<SeriesAdapter.SeriesViewHolder>() {

    private val items = mutableListOf<SeriesItem>()
    private val palette = intArrayOf(
        Color.rgb(77, 150, 255),
        Color.rgb(255, 107, 107),
        Color.rgb(93, 211, 158),
        Color.rgb(155, 126, 222),
        Color.rgb(255, 168, 76),
        Color.rgb(55, 190, 201),
    )

    fun submitItems(newItems: List<SeriesItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val binding = ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeriesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(items[position], palette[position % palette.size])
    }

    override fun getItemCount(): Int = items.size

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
            title.text = item.title
            count.text = if (item.episodeCount == 1) {
                card.context.getString(R.string.one_episode)
            } else {
                card.context.getString(R.string.episodes_count, item.episodeCount)
            }
            card.contentDescription = "${item.title}, ${count.text}"

            cover.setImageDrawable(null)
            val coverUri = item.coverUri
            if (coverUri == null) {
                cover.visibility = View.GONE
            } else {
                cover.visibility = View.VISIBLE
                ArtworkLoader.load(card.context, coverUri, cover) {
                    cover.visibility = View.GONE
                }
            }
            card.setOnClickListener { onSeriesClick(item) }
        }
    }
}
