package com.livetube.player.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.livetube.player.R
import com.livetube.player.databinding.FragmentPlayerBinding
import com.livetube.player.extractor.Yt
import com.livetube.player.playback.Playback
import com.livetube.player.util.Downloads
import com.livetube.player.util.Prefs
import com.livetube.player.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val vm: PlayerViewModel by viewModels()
    private var audioOnly = true
    private var userSeeking = false
    private var pendingDownloadUrl: String? = null
    private var fullscreen = false

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val url = pendingDownloadUrl
            pendingDownloadUrl = null
            if (granted && url != null && _binding != null) {
                startDownload(url)
            } else if (!granted) {
                setStatus(getString(R.string.download_permission_required))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerBinding.bind(view)

        binding.playerView.player = Playback.player
        binding.fullscreenBtn.setOnClickListener { toggleFullscreen() }

        audioOnly = Prefs.audioOnly(requireContext())
        binding.audioSwitch.isChecked = audioOnly

        binding.playBtn.setOnClickListener { playPastedUrl() }
        binding.downloadBtn.setOnClickListener { downloadVideo() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                playPastedUrl()
                true
            } else {
                false
            }
        }
        binding.audioSwitch.setOnCheckedChangeListener { _, checked ->
            onAudioModeChanged(checked)
        }
        binding.autoNextSwitch.isChecked = Prefs.autoNext(requireContext())
        binding.autoNextSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoNext(requireContext(), checked)
        }
        binding.audioPlayPause.setOnClickListener {
            Playback.togglePlayPause()
            updatePlayPauseIcon()
        }
        binding.audioStop.setOnClickListener { Playback.stop() }

        binding.seekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) = if (fromUser) updateTimeLabel(progress.toLong()) else Unit

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    userSeeking = false
                    val position = seekBar?.progress?.toLong() ?: 0L
                    Playback.player.seekTo(position)
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.nowPlaying.collect { updateUi(it) } }
                launch { ticker() }
            }
        }
    }

    override fun onDestroyView() {
        if (fullscreen) exitFullscreen()
        binding.playerView.player = null
        _binding = null
        super.onDestroyView()
    }

    private fun toggleFullscreen() {
        if (fullscreen) exitFullscreen() else enterFullscreen()
    }

    private fun enterFullscreen() {
        val activity = requireActivity()
        fullscreen = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        activity.findViewById<View>(R.id.bottom_nav)?.isVisible = false

        binding.playerTitle.isVisible = false
        binding.urlInputLayout.isVisible = false
        binding.playDownloadRow.isVisible = false
        binding.audioSwitch.isVisible = false
        binding.autoNextSwitch.isVisible = false
        binding.status.isVisible = false
        binding.audioControls.isVisible = false
        binding.timeLabel.isVisible = false

        binding.rootScroll.isFillViewport = true
        binding.contentContainer.setPadding(0, 0, 0, 0)
        binding.contentContainer.layoutParams = binding.contentContainer.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.videoCard.layoutParams = binding.videoCard.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.videoFrame.layoutParams = binding.videoFrame.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.playerView.layoutParams = binding.playerView.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.fullscreenBtn.contentDescription = getString(R.string.exit_fullscreen)
        binding.fullscreenBtn.setImageResource(R.drawable.ic_fullscreen_exit)
    }

    private fun exitFullscreen() {
        val activity = activity ?: return
        fullscreen = false
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        activity.findViewById<View>(R.id.bottom_nav)?.isVisible = true

        binding.playerTitle.isVisible = true
        binding.urlInputLayout.isVisible = true
        binding.playDownloadRow.isVisible = true
        binding.audioSwitch.isVisible = true
        binding.autoNextSwitch.isVisible = true
        binding.status.isVisible = true
        binding.contentContainer.setPadding(
            (16 * resources.displayMetrics.density).toInt(),
            (16 * resources.displayMetrics.density).toInt(),
            (16 * resources.displayMetrics.density).toInt(),
            (16 * resources.displayMetrics.density).toInt(),
        )
        binding.contentContainer.layoutParams = binding.contentContainer.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        binding.videoCard.layoutParams = binding.videoCard.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.videoFrame.layoutParams = binding.videoFrame.layoutParams.apply {
            height = (200 * resources.displayMetrics.density).toInt()
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.playerView.layoutParams = binding.playerView.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.fullscreenBtn.contentDescription = getString(R.string.fullscreen)
        binding.fullscreenBtn.setImageResource(R.drawable.ic_fullscreen)
        updateLayoutVisibility(Playback.nowPlaying.value != null)
    }

    private fun playPastedUrl() {
        val raw = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        setStatus(getString(R.string.preparing))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (isYouTubeUrl(raw)) {
                runCatching { Yt.resolveStream(raw, audioOnly) }
            } else {
                val title = raw.substringAfterLast('/').take(40).ifBlank { "Direct URL" }
                Result.success(Yt.StreamPlay(raw, false, title))
            }
            result.fold(
                onSuccess = { play -> Playback.play(raw, play.url, play.title, play.live) },
                onFailure = { e -> setStatus(e.message ?: "Failed to play") },
            )
        }
    }

    private fun downloadVideo() {
        val pasted = binding.urlInput.text?.toString()?.trim().orEmpty()
        val raw = pasted.takeIf { binding.urlInput.hasFocus() && it.isNotBlank() }
            ?: Playback.watchUrl.value?.takeIf { it.isNotBlank() }
            ?: pasted

        if (raw.isBlank()) {
            setStatus(getString(R.string.download_no_video))
            return
        }
        if (!isYouTubeUrl(raw)) {
            setStatus(getString(R.string.download_youtube_only))
            return
        }

        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadUrl = raw
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        startDownload(raw)
    }

    private fun startDownload(url: String) {
        _binding?.downloadBtn?.isEnabled = false
        setStatus(getString(R.string.preparing_download))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val download = Yt.resolveDownload(url)
                Downloads.enqueue(requireContext(), download)
                setStatus(getString(R.string.download_started, download.title))
            } catch (e: Exception) {
                setStatus(e.message ?: getString(R.string.download_failed))
            } finally {
                _binding?.downloadBtn?.isEnabled = true
            }
        }
    }

    private fun isYouTubeUrl(raw: String): Boolean =
        raw.contains("youtube.com", ignoreCase = true) ||
            raw.contains("youtu.be", ignoreCase = true)

    private fun onAudioModeChanged(checked: Boolean) {
        vm.setAudioOnly(checked)
        audioOnly = checked
        val np = Playback.nowPlaying.value ?: run {
            updateLayoutVisibility(false)
            return
        }
        if (np.live) {
            updateLayoutVisibility(true)
            return
        }
        val watch = Playback.watchUrl.value ?: return
        val position = Playback.player.currentPosition
        setStatus(getString(R.string.preparing))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { Yt.resolveStream(watch, checked) }
            result.fold(
                onSuccess = { play ->
                    Playback.restartWith(watch, play.url, play.title, play.live, position)
                    setStatus(play.title)
                },
                onFailure = { e -> setStatus(e.message ?: "Failed to switch") },
            )
        }
    }

    private fun updateUi(np: Playback.NowPlaying?) {
        if (np == null) {
            binding.status.text = ""
            binding.seekBar.progress = 0
            binding.seekBar.max = 0
            updateTimeLabel(0)
            updateLayoutVisibility(false)
        } else {
            binding.status.text = np.title
            updateLayoutVisibility(true)
        }
    }

    private fun updateLayoutVisibility(playing: Boolean) {
        val showVideo = playing && !audioOnly
        val showAudio = playing && audioOnly
        binding.videoCard.isVisible = showVideo
        binding.fullscreenBtn.isVisible = showVideo
        binding.audioControls.isVisible = showAudio
        binding.timeLabel.isVisible = showAudio
    }

    private suspend fun ticker() {
        while (true) {
            val currentBinding = _binding ?: return
            val player = Playback.player
            val duration = player.duration.takeIf { it > 0 }?.toInt() ?: 0
            if (duration != currentBinding.seekBar.max) currentBinding.seekBar.max = duration
            if (!userSeeking) currentBinding.seekBar.progress = player.currentPosition.toInt()
            if (!userSeeking) updateTimeLabel(player.currentPosition)
            updatePlayPauseIcon()
            delay(500)
        }
    }

    private fun updatePlayPauseIcon() {
        val currentBinding = _binding ?: return
        val playing = Playback.player.isPlaying
        currentBinding.audioPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play,
        )
    }

    private fun updateTimeLabel(positionMs: Long) {
        val currentBinding = _binding ?: return
        val duration = Playback.player.duration.takeIf { it > 0 }?.toLong() ?: 0L
        val text = if (duration > 0) {
            "${formatDuration(positionMs / 1000)} / ${formatDuration(duration / 1000)}"
        } else {
            ""
        }
        currentBinding.timeLabel.text = text
    }

    private fun setStatus(text: String) {
        _binding?.status?.text = text
    }
}