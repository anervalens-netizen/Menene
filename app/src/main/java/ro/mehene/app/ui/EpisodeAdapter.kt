package ro.mehene.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ro.mehene.app.R
import ro.mehene.app.data.PlaybackProgressStore
import ro.mehene.app.databinding.ItemEpisodeBinding
import ro.mehene.app.model.EpisodeItem

class EpisodeAdapter(
    private val progressStore: PlaybackProgressStore,
    private val onEpisodeClick: (EpisodeItem) -> Unit,
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    private val items = mutableListOf<EpisodeItem>()
    private val palette = intArrayOf(
        Color.rgb(65, 145, 151),
        Color.rgb(109, 109, 211),
        Color.rgb(232, 126, 91),
        Color.rgb(77, 165, 118),
        Color.rgb(180, 107, 169),
        Color.rgb(53, 140, 205),
    )

    fun submitItems(newItems: List<EpisodeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(items[position], palette[position % palette.size])
    }

    override fun getItemCount(): Int = items.size

    inner class EpisodeViewHolder(
        private val binding: ItemEpisodeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EpisodeItem, fallbackColor: Int) = with(binding) {
            card.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * card.resources.displayMetrics.density
                setColor(fallbackColor)
            }
            fallbackNumber.text = item.number.toString()
            numberBadge.text = card.context.getString(R.string.episode_number, item.number)
            title.text = item.title
            card.contentDescription = "${numberBadge.text}, ${item.title}"

            artwork.setImageDrawable(null)
            val artworkUri = item.artworkUri
            if (artworkUri == null) {
                artwork.visibility = View.GONE
            } else {
                artwork.visibility = View.VISIBLE
                ArtworkLoader.load(card.context, artworkUri, artwork) {
                    artwork.visibility = View.GONE
                }
            }

            val savedProgress = progressStore.progress(item.mediaUri)
            if (savedProgress > 0.01f) {
                progress.visibility = View.VISIBLE
                progress.progress = (savedProgress * 1000).toInt()
            } else {
                progress.visibility = View.GONE
                progress.progress = 0
            }

            card.setOnClickListener { onEpisodeClick(item) }
        }
    }
}
