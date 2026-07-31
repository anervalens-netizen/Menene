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
import ro.menene.app.data.EpisodePlaybackState
import ro.menene.app.data.EpisodeProgress
import ro.menene.app.databinding.ItemEpisodeBinding
import ro.menene.app.model.EpisodeItem

class EpisodeAdapter(
    private val onEpisodeClick: (EpisodeItem) -> Unit,
) : ListAdapter<EpisodeItem, EpisodeAdapter.EpisodeViewHolder>(DIFF_CALLBACK) {
    private var progress: Map<String, EpisodeProgress> = emptyMap()
    private val palette = intArrayOf(
        Color.rgb(49, 143, 151),
        Color.rgb(100, 103, 210),
        Color.rgb(226, 116, 80),
        Color.rgb(60, 159, 110),
        Color.rgb(170, 93, 161),
        Color.rgb(41, 132, 198),
    )

    fun updateProgress(newProgress: Map<String, EpisodeProgress>) {
        progress = newProgress
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_PROGRESS)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder =
        EpisodeViewHolder(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position), palette[position % palette.size])
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) holder.bindProgress(getItem(position))
        else super.onBindViewHolder(holder, position, payloads)
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
                cornerRadius = card.resources.getDimension(R.dimen.corner_large)
                setColor(fallbackColor)
            }
            fallbackNumber.text = item.number.toString()
            numberBadge.text = card.context.getString(R.string.episode_number, item.number)
            title.text = item.title

            ArtworkLoader.cancel(artwork)
            if (item.artworkUri == null) {
                artwork.visibility = View.GONE
                fallbackNumber.visibility = View.VISIBLE
            } else {
                artwork.visibility = View.VISIBLE
                fallbackNumber.visibility = View.GONE
                ArtworkLoader.load(card.context, item.artworkUri, item.artworkVersion, artwork) {
                    artwork.visibility = View.GONE
                    fallbackNumber.visibility = View.VISIBLE
                }
            }
            bindProgress(item)
            card.setOnClickListener { onEpisodeClick(item) }
        }

        fun bindProgress(item: EpisodeItem) = with(binding) {
            val episodeProgress = progress[item.id]
            val state = episodeProgress?.state ?: EpisodePlaybackState.UNWATCHED
            completedBadge.visibility = if (state == EpisodePlaybackState.COMPLETED) View.VISIBLE else View.GONE
            continueBadge.visibility = if (state == EpisodePlaybackState.IN_PROGRESS) View.VISIBLE else View.GONE
            if (state == EpisodePlaybackState.IN_PROGRESS) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = ((episodeProgress?.fraction ?: 0f) * 1000).toInt()
            } else {
                progressBar.visibility = View.GONE
                progressBar.progress = 0
            }
            card.contentDescription = buildString {
                append(numberBadge.text)
                append(", ")
                append(item.title)
                when (state) {
                    EpisodePlaybackState.IN_PROGRESS -> append(", ${card.context.getString(R.string.continue_watching)}")
                    EpisodePlaybackState.COMPLETED -> append(", ${card.context.getString(R.string.episode_completed)}")
                    EpisodePlaybackState.UNWATCHED -> Unit
                }
            }
        }

        fun recycle() = ArtworkLoader.cancel(binding.artwork)
    }

    companion object {
        private const val PAYLOAD_PROGRESS = "progress"
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EpisodeItem>() {
            override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean = oldItem == newItem
        }
    }
}
