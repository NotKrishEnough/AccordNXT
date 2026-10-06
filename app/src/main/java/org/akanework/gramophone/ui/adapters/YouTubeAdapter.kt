package org.akanework.gramophone.ui.adapters

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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

private fun textRow(parent: ViewGroup): LinearLayout =
    LinearLayout(parent.context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 16, 24, 16)
        layoutParams = RecyclerView.LayoutParams(-1, -2)
    }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(textRow(parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val track = items[position]
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
                    val url = NewPipeAudioResolver.resolve(track.videoId)
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
        val title = TextView(view.context)
        val subtitle = TextView(view.context)
        init {
            view.addView(title)
            view.addView(subtitle)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(textRow(parent))

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
        holder.title.text = playlist.title
        holder.title.textSize = 16f
        holder.title.setTypeface(null, Typeface.NORMAL)
        holder.subtitle.text = if (playlist.itemCount > 0) playlist.itemCount.toString() + " songs" else "YouTube Music playlist"
        holder.itemView.setOnClickListener { onClick(playlist) }
    }

    class Holder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val title = TextView(view.context)
        val subtitle = TextView(view.context)
        init {
            view.addView(title)
            view.addView(subtitle)
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
                    if (track.thumbnail.isNotBlank()) {
                        setArtworkUri(android.net.Uri.parse(track.thumbnail))
                    }
                }
                .setExtras(android.os.Bundle().apply {
                    putString("youtube_video_id", track.videoId)
                    putBoolean("youtube", true)
                })
                .build()
        )
        .build()
