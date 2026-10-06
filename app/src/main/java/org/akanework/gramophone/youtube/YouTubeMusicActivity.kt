package org.akanework.gramophone.youtube

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.GramophonePlaybackService

class YouTubeMusicActivity : ComponentActivity() {
    private lateinit var query: EditText
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private var controller: MediaController? = null
    private val api = InnerTubeClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "YouTube Music"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        query = EditText(this).apply {
            hint = "Search YouTube Music"
            setSingleLine(true)
        }
        val search = Button(this).apply {
            text = "Search"
            setOnClickListener { search() }
        }
        header.addView(query, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(search)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val account = Button(this).apply {
            text = if (YouTubeSessionStore.read(this@YouTubeMusicActivity).isNullOrBlank()) "Sign in" else "YouTube Music ✓"
            setOnClickListener { signIn() }
        }
        val playlists = Button(this).apply {
            text = "My playlists"
            setOnClickListener { loadPlaylists() }
        }
        actions.addView(account)
        actions.addView(playlists)

        status = TextView(this).apply {
            text = "Search YouTube / YouTube Music and play audio in Accord's existing player."
            setPadding(0, 12, 0, 12)
        }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val scroll = ScrollView(this).apply { addView(results) }
        root.addView(header)
        root.addView(actions)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val token = SessionToken(this, ComponentName(this, GramophonePlaybackService::class.java))
        MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
        }
    }

    private fun search() {
        val q = query.text.toString().trim()
        if (q.isBlank()) return
        status.text = "Searching…"
        results.removeAllViews()
        lifecycleScope.launch {
            runCatching { api.search(q) }
                .onSuccess { tracks ->
                    status.text = "${tracks.size} results"
                    tracks.forEach { track -> addTrack(track) }
                }
                .onFailure { status.text = it.message ?: "YouTube search failed" }
        }
    }

    private fun addTrack(track: OnlineTrack) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 14, 8, 14)
            setOnClickListener { play(track) }
        }
        row.addView(TextView(this).apply {
            text = track.title
            textSize = 16f
        })
        row.addView(TextView(this).apply {
            text = "${track.author} • ${track.duration}"
            textSize = 13f
        })
        results.addView(row)
    }

    private fun play(track: OnlineTrack) {
        status.text = "Resolving audio: ${track.title}"
        lifecycleScope.launch {
            runCatching { NewPipeAudioResolver.resolveToFile(this@YouTubeMusicActivity, track.videoId) }
                .onSuccess { streamUri ->
                    val item = MediaItem.Builder()
                        .setMediaId("youtube-" + track.videoId)
                        .setUri(streamUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.author)
                                .setArtworkUri(android.net.Uri.parse(track.thumbnail))
                                .setExtras(Bundle().apply { putString("youtube_video_id", track.videoId) })
                                .build()
                        ).build()
                    controller?.apply {
                        setMediaItem(item)
                        prepare()
                        play()
                    } ?: error("Accord playback service is not ready")
                    status.text = "Playing: ${track.title}"
                }
                .onFailure { status.text = "Playback failed: ${it.message}" }
        }
    }

    private fun signIn() {
        startActivityForResult(Intent(this, YouTubeCookieLoginActivity::class.java), LOGIN_REQUEST)
    }

    @Deprecated("Deprecated Android callback; retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST && resultCode == Activity.RESULT_OK) {
            val cookies = data?.getStringExtra(YouTubeCookieLoginActivity.EXTRA_COOKIE_HEADER)
            if (!cookies.isNullOrBlank()) {
                lifecycleScope.launch {
                    status.text = "Verifying YouTube Music session…"
                    if (YouTubeSessionVerifier.verify(cookies)) {
                        YouTubeSessionStore.save(this@YouTubeMusicActivity, cookies)
                        status.text = "YouTube Music connected"
                    } else {
                        status.text = "Session could not be verified"
                    }
                }
            }
        }
    }

    private fun loadPlaylists() {
        val cookies = YouTubeSessionStore.read(this)
        if (cookies.isNullOrBlank()) {
            signIn()
            return
        }
        status.text = "Loading your YouTube Music playlists…"
        results.removeAllViews()
        lifecycleScope.launch {
            runCatching { YouTubePlaylists.fetchFromMusicSession(cookies) }
                .onSuccess { lists ->
                    status.text = "${lists.size} playlists"
                    lists.forEach { playlist ->
                        val row = TextView(this@YouTubeMusicActivity).apply {
                            text = "♫ ${playlist.title}"
                            textSize = 16f
                            setPadding(8, 18, 8, 18)
                            setOnClickListener { loadPlaylistTracks(playlist.id, playlist.title, cookies) }
                        }
                        results.addView(row)
                    }
                }
                .onFailure { status.text = "Playlist sync failed: ${it.message}" }
        }
    }

    private fun loadPlaylistTracks(id: String, title: String, cookies: String) {
        status.text = "Loading $title…"
        results.removeAllViews()
        lifecycleScope.launch {
            runCatching { YouTubePlaylists.fetchPlaylistTracks(cookies, id) }
                .onSuccess { tracks ->
                    status.text = "${tracks.size} tracks • $title"
                    tracks.forEach { track ->
                        val online = OnlineTrack(track.videoId, track.title, track.artist, "", track.thumbnail)
                        addTrack(online)
                    }
                }
                .onFailure { status.text = "Playlist failed: ${it.message}" }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    companion object { private const val LOGIN_REQUEST = 4102 }
}

// CI trigger: build verification

// CI trigger: signed APK verification
