package org.akanework.gramophone.ui.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.youtube.NewPipeAudioResolver
import org.akanework.gramophone.youtube.YouTubePlaylists
import org.akanework.gramophone.youtube.YouTubePlaylistTrack
import org.akanework.gramophone.youtube.OnlineTrack
import org.akanework.gramophone.ui.adapters.mediaItem

class YouTubePlaylistFragment : BaseFragment(true) {
    private lateinit var status: TextView
    private lateinit var recycler: RecyclerView
    private val tracks = mutableListOf<YouTubePlaylistTrack>()
    private var playlistTitle = "YouTube Music"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistTitle = arguments?.getString(ARG_TITLE) ?: "YouTube Music"
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val toolbar = androidx.appcompat.widget.Toolbar(requireContext()).apply {
            title = playlistTitle
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
        status = TextView(requireContext()).apply {
            text = "Loading playlist…"
            setPadding(24, 12, 24, 12)
        }
        recycler = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
        root.addView(toolbar)
        root.addView(status)
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        load()
        return root
    }

    private fun load() {
        val cookies = org.akanework.gramophone.youtube.YouTubeSessionStore.read(requireContext())
        if (cookies.isNullOrBlank()) {
            status.text = "Connect YouTube Music first."
            return
        }
        val playlistId = arguments?.getString(ARG_ID).orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { YouTubePlaylists.fetchPlaylistTracks(cookies, playlistId) }
                .onSuccess {
                    tracks.clear()
                    tracks.addAll(it)
                    status.text = tracks.size.toString() + " songs"
                    recycler.adapter = TrackAdapter()
                }
                .onFailure {
                    status.text = "Couldn't open playlist: " + (it.message ?: "unknown error")
                }
        }
    }

    private fun playFrom(index: Int) {
        val activity = requireActivity() as MainActivity
        status.text = "Preparing playlist…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) {
                    tracks.mapIndexed { sourceIndex, track ->
                        sourceIndex to track
                    }.map { pair ->
                        async {
                            runCatching {
                                val url = NewPipeAudioResolver.resolve(pair.second.videoId)
                                pair.first to mediaItem(
                                    OnlineTrack(pair.second.videoId, pair.second.title, pair.second.artist, "", pair.second.thumbnail),
                                    url
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }
                if (resolved.isEmpty()) error("No playable songs were found")
                val items = resolved.map { it.second }
                val selected = resolved.indexOfFirst { it.first == index }.let { if (it >= 0) it else 0 }
                activity.getPlayer()?.apply {
                    setMediaItems(items, selected, C.TIME_UNSET)
                    prepare()
                    play()
                } ?: error("Playback service is unavailable")
                status.text = "Playing " + tracks[index].title
            } catch (e: Exception) {
                status.text = "Playlist playback failed: " + (e.message ?: "unknown error")
            }
        }
    }

    private inner class TrackAdapter : RecyclerView.Adapter<TrackAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 16, 24, 16)
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            })

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val track = tracks[position]
            holder.title.text = track.title
            holder.artist.text = track.artist.ifBlank { "YouTube Music" }
            holder.itemView.setOnClickListener { playFrom(position) }
        }

        override fun getItemCount() = tracks.size

        inner class Holder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
            val title = TextView(view.context).apply { textSize = 16f; setTypeface(null, Typeface.NORMAL) }
            val artist = TextView(view.context).apply { textSize = 13f; alpha = 0.7f }
            init {
                view.addView(title)
                view.addView(artist)
            }
        }
    }

    companion object {
        private const val ARG_ID = "youtube_playlist_id"
        private const val ARG_TITLE = "youtube_playlist_title"

        fun newInstance(id: String, title: String) = YouTubePlaylistFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ID, id)
                putString(ARG_TITLE, title)
            }
        }
    }
}
