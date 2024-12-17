@file:Suppress("DEPRECATION")

package com.radzdev.exoplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.common.collect.ImmutableList

class ExoPlayerManager : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: View

    private lateinit var backButton: LinearLayout
    private lateinit var rotateButton: ImageView
    private lateinit var playbackSpeedButton: ImageView
    private lateinit var lockButton: ImageView
    private lateinit var playPauseButton: ImageView
    private lateinit var seekBar: DefaultTimeBar
    private lateinit var exoPosition: TextView
    private lateinit var exoDuration: TextView
    private lateinit var rewindButton: ImageView
    private lateinit var forwardButton: ImageView
    private lateinit var exoplayerResize: ImageView
    private lateinit var audioDetails: ImageView

    private var isLocked = false // To track the lock status
    private val REWIND_DURATION_MS = 10000 // 10 seconds
    private val FORWARD_DURATION_MS = 10000 // 10 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.requestFeature(Window.FEATURE_NO_TITLE)
        window?.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        setContentView(R.layout.activity_main)

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        progressBar = findViewById(R.id.video_buffer_indicator)
        playerView.player = exoPlayer

        // Apply custom subtitle style
        playerView.subtitleView?.setStyle(
            CaptionStyleCompat(
                Color.WHITE,               // White text
                Color.TRANSPARENT,         // Transparent background
                Color.TRANSPARENT,         // Transparent window
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, // Shadow effect
                Color.BLACK,               // Shadow color (black)
                null                       // Default font
            )
        )

        // Retrieve video URL and subtitle URL from the intent
        val videoUrl = intent.getStringExtra("videoUrl")
        val subtitleUrl = intent.getStringExtra("subtitleUrl")

        if (videoUrl != null) {
            val assetVideoUri = Uri.parse(videoUrl)
            val assetSubtitleUri = subtitleUrl?.let { Uri.parse(it) }

            // Determine the MIME type based on the subtitle URL
            val mimeType = when {
                subtitleUrl != null && subtitleUrl.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                subtitleUrl != null && subtitleUrl.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                else -> null // Handle unsupported formats or do something else
            }

            // Build the subtitle configuration if MIME type is valid
            val subtitleConfiguration = mimeType?.let {
                assetSubtitleUri?.let { it1 ->
                    MediaItem.SubtitleConfiguration.Builder(it1)
                        .setMimeType(it)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            }

            // Create media item and set video URI and subtitle configurations
            val mediaItemBuilder = MediaItem.Builder().setUri(assetVideoUri)
            subtitleConfiguration?.let { mediaItemBuilder.setSubtitleConfigurations(ImmutableList.of(it)) }
            val mediaItem = mediaItemBuilder.build()

            // Set media item to the player and prepare
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

            // Ensure visibility of SubtitleView and log its initial state
            val subtitleView = playerView.subtitleView
            subtitleView?.visibility = View.VISIBLE // Ensure subtitle view is visible
            Log.d("ExoPlayerManager", "SubtitleView initialized and set to visible")



            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    progressBar.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                }
            })
        }

        // Initialize buttons and UI elements
        initializeUI()

        // Set click listeners
        setClickListeners()
    }


    private fun initializeUI() {
        backButton = findViewById(R.id.OnBackPress)
        rotateButton = findViewById(R.id.rotateButton)
        playbackSpeedButton = findViewById(R.id.exo_playback_speed)
        lockButton = findViewById(R.id.lockButton)
        playPauseButton = findViewById(R.id.exo_play_pause)
        seekBar = findViewById(R.id.exo_progress)
        exoPosition = findViewById(R.id.exo_position)
        exoDuration = findViewById(R.id.exo_duration)
        rewindButton = findViewById(R.id.rew)
        forwardButton = findViewById(R.id.fwd)
        exoplayerResize = findViewById(R.id.screen_resize)
        audioDetails = findViewById(R.id.exo_audio_details)
    }

    private fun setClickListeners() {
        backButton.setOnClickListener { onBackPressed() }
        rotateButton.setOnClickListener { toggleRotation() }
        playbackSpeedButton.setOnClickListener { togglePlaybackSpeed() }
        lockButton.setOnClickListener { toggleLock() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        rewindButton.setOnClickListener { rewindVideo() }
        forwardButton.setOnClickListener { forwardVideo() }

        exoplayerResize.setOnClickListener {
            val currentResizeMode = playerView.resizeMode
            val nextResizeMode = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    exoplayerResize.setImageResource(R.drawable.fullscreen)
                    showToastMessage("Fit Mode")
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    exoplayerResize.setImageResource(R.drawable.full_screen_zoom)
                    showToastMessage("Zoom Mode")
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                else -> {
                    exoplayerResize.setImageResource(R.drawable.fullscreen)
                    showToastMessage("Fit Mode")
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            playerView.resizeMode = nextResizeMode
        }

        audioDetails.setOnClickListener { displayAudioDetails() }
    }

    private fun displayAudioDetails() {
        val audioFormat = exoPlayer.audioFormat
        val dialog = AlertDialog.Builder(this)

        if (audioFormat != null) {
            val sampleRate = audioFormat.sampleRate
            val channels = audioFormat.channelCount
            val audioType = audioFormat.sampleMimeType
            val audioDetailsMessage = "Audio Sample Rate: $sampleRate\n" +
                    "Audio Channels: $channels\n" +
                    "Audio Type: $audioType"

            dialog.setTitle("Audio Tracks")
                .setMessage(audioDetailsMessage)
                .setPositiveButton("OK", null)
                .create()
        } else {
            dialog.setTitle("Audio Tracks")
                .setMessage("None")
                .setPositiveButton("OK", null)
                .create()
        }

        dialog.show()
    }

    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            playPauseButton.setImageResource(R.drawable.exo_ic_play_circle_filled)
        } else {
            exoPlayer.play()
            playPauseButton.setImageResource(R.drawable.exo_ic_pause_circle_filled)
        }
    }

    private fun toggleRotation() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun togglePlaybackSpeed() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Speed")
        val speeds = arrayOf("0.25X", "0.5X", "Normal", "1.5X", "2X")
        builder.setItems(speeds) { _, which ->
            val speed = when (which) {
                0 -> 0.25f
                1 -> 0.5f
                2 -> 1f
                3 -> 1.5f
                4 -> 2f
                else -> 1f
            }
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        }
        builder.show()
    }


    private fun toggleLock() {
        isLocked = !isLocked
        lockButton.setImageResource(if (isLocked) R.drawable.lock else R.drawable.unlock)
        playPauseButton.isEnabled = !isLocked
        seekBar.isEnabled = !isLocked
    }

    private fun rewindVideo() {
        exoPlayer.currentPosition.let { currentPosition ->
            val newPosition = (currentPosition - REWIND_DURATION_MS).coerceAtLeast(0)
            exoPlayer.seekTo(newPosition)
        }
    }

    private fun forwardVideo() {
        exoPlayer.currentPosition.let { currentPosition ->
            val newPosition = (currentPosition + FORWARD_DURATION_MS).coerceAtMost(exoPlayer.duration)
            exoPlayer.seekTo(newPosition)
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.release()
    }
}