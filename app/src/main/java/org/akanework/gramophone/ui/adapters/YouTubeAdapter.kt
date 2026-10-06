package org.akanework.gramophone.ui.adapters

import android.graphics.Typeface
import android.widget.ImageView
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.youtube.NewPipeAudioResolver
import org.akanework.gramophone.youtube.OnlineTrack
import org.akanework.gramophone.youtube.YouTubePlaylist

private fun artRow(parent: ViewGroup): LinearLayout =
    LinearLayout(parent.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16, 10, 16, 10)
        layoutParams = RecyclerView.LayoutParams(-1, -2)
    }

private fun thumbnailUrl(url: String, videoId: String? = null): String =
    url.takeIf { it.isNotBlank() }
        ?: videoId?.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
        ?: ""

class YouTubeSearchAdapter(
    private val fragment: Fragment,
    private val onStatus: (String) -> Unit
) : RecyclerView.Adapter<YouTubeSearchAdapter.Holder>() {
    private val items = mutableListOf<OnlineTrack>()

    fun setItems(value: List<OnlineTrack>) {
        items.clear()
        items.addAll(value)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(artRow(parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val track = items[position]
        holder.cover.load(thumbnailUrl(track.thumbnail, track.videoId)) {
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        holder.title.text = track.title
        holder.title.textSize = 16f
        holder.title.setTypeface(null, Typeface.NORMAL)
        holder.subtitle.text = "YouTube • " + track.author +
            if (track.duration.isNotBlank()) " • " + track.duration else ""
        holder.itemView.setOnClickListener {
            holder.itemView.isEnabled = false
            onStatus("Loading " + track.title + "…")
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val url = NewPipeAudioResolver.resolveToFile(fragment.requireContext(), track.videoId)
                    val item = mediaItem(track, url)
                    (fragment.requireActivity() as MainActivity).getPlayer()?.apply {
                        setMediaItems(listOf(item), 0, C.TIME_UNSET)
                        prepare()
                        play()
                    } ?: error("Playback service is unavailable")
                    onStatus("Playing " + track.title)
                } catch (e: Exception) {
                    onStatus("Couldn't play YouTube song: " + (e.message ?: "unknown error"))
                } finally {
                    holder.itemView.isEnabled = true
                }
            }
        }
    }

    override fun getItemCount() = items.size

    class Holder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val cover = ImageView(view.context).apply {
            layoutParams = LinearLayout.LayoutParams(56, 56).apply { marginEnd = 16 }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val content = LinearLayout(view.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val title = TextView(view.context)
        val subtitle = TextView(view.context)
        init {
            view.addView(cover)
            view.addView(content)
            content.addView(title)
            content.addView(subtitle)
            subtitle.textSize = 13f
            subtitle.alpha = 0.7f
        }
    }
}

class YouTubePlaylistAdapter(
    private val onClick: (YouTubePlaylist) -> Unit
) : RecyclerView.Adapter<YouTubePlaylistAdapter.Holder>() {
    private val items = mutableListOf<YouTubePlaylist>()
    private var connected = false

    fun setItems(value: List<YouTubePlaylist>) {
        connected = true
        items.clear()
        items.addAll(value)
        notifyDataSetChanged()
    }

    fun showDisconnected() {
        connected = false
        items.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(artRow(parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == 0) {
            holder.title.text = "YouTube Music"
            holder.title.textSize = 18f
            holder.title.setTypeface(null, Typeface.BOLD)
            holder.subtitle.text = if (connected) "Your YouTube Music playlists" else "Sign in to load your playlists"
            holder.itemView.setOnClickListener(null)
            return
        }
        val playlist = items[position - 1]
        holder.cover.load(thumbnailUrl(playlist.thumbnail)) {
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        holder.title.text = playlist.title
        holder.title.textSize = 16f
        holder.title.setTypeface(null, Typeface.NORMAL)
        holder.subtitle.text = if (playlist.itemCount > 0) playlist.itemCount.toString() + " songs" else "YouTube Music playlist"
        holder.itemView.setOnClickListener { onClick(playlist) }
    }

    class Holder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val cover = ImageView(view.context).apply {
            layoutParams = LinearLayout.LayoutParams(56, 56).apply { marginEnd = 16 }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val content = LinearLayout(view.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val title = TextView(view.context)
        val subtitle = TextView(view.context)
        init {
            view.addView(cover)
            view.addView(content)
            content.addView(title)
            content.addView(subtitle)
            subtitle.textSize = 13f
            subtitle.alpha = 0.7f
        }
    }
}

fun mediaItem(track: OnlineTrack, url: String): MediaItem =
    MediaItem.Builder()
        .setMediaId("youtube-" + track.videoId)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.author)
                .apply {
                    val artwork = thumbnailUrl(track.thumbnail, track.videoId)
                    if (artwork.isNotBlank()) {
                        setArtworkUri(android.net.Uri.parse(artwork))
                    }
                }
                .setExtras(android.os.Bundle().apply {
                    putString("youtube_video_id", track.videoId)
                    putBoolean("youtube", true)
                })
                .build()
        )
        .build()
