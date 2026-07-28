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
import ro.mehene.app.data.EpisodePlaybackState
import ro.mehene.app.data.PlaybackProgressStore
import ro.mehene.app.databinding.ItemEpisodeBinding
import ro.mehene.app.model.EpisodeItem

class EpisodeAdapter(
    private val progressStore: PlaybackProgressStore,
    private val onEpisodeClick: (EpisodeItem) -> Unit,
) : ListAdapter<EpisodeItem, EpisodeAdapter.EpisodeViewHolder>(DIFF_CALLBACK) {

    private val palette = intArrayOf(
        Color.rgb(65, 145, 151),
        Color.rgb(109, 109, 211),
        Color.rgb(232, 126, 91),
        Color.rgb(77, 165, 118),
        Color.rgb(180, 107, 169),
        Color.rgb(53, 140, 205),
    )

    fun submitItems(newItems: List<EpisodeItem>) {
        submitList(newItems.toList())
    }

    fun refreshPlaybackState() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_PROGRESS)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position), palette[position % palette.size])
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            holder.bindProgress(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: EpisodeViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

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

            ArtworkLoader.cancel(artwork)
            val artworkUri = item.artworkUri
            if (artworkUri == null) {
                artwork.visibility = View.GONE
            } else {
                artwork.visibility = View.VISIBLE
                ArtworkLoader.load(card.context, artworkUri, item.artworkVersion, artwork) {
                    artwork.visibility = View.GONE
                }
            }

            bindProgress(item)
            card.setOnClickListener { onEpisodeClick(item) }
        }

        fun bindProgress(item: EpisodeItem) = with(binding) {
            val playback = progressStore.progress(item.playbackKey)
            completedBadge.visibility = if (playback.state == EpisodePlaybackState.COMPLETED) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (playback.state == EpisodePlaybackState.IN_PROGRESS) {
                progress.visibility = View.VISIBLE
                progress.progress = (playback.fraction * 1000).toInt()
            } else {
                progress.visibility = View.GONE
                progress.progress = 0
            }
        }

        fun recycle() {
            ArtworkLoader.cancel(binding.artwork)
        }
    }

    companion object {
        private const val PAYLOAD_PROGRESS = "progress"
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EpisodeItem>() {
            override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean =
                oldItem.mediaUri == newItem.mediaUri

            override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean =
                oldItem == newItem
        }
    }
}
