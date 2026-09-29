@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package org.fossify.gallery.fragments

import android.graphics.drawable.BitmapDrawable
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getVideoResolution
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.setDrawablesRelativeWithIntrinsicBounds
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.VideoActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.screenRect
import org.fossify.gallery.extensions.displayedImageRect
import org.fossify.gallery.extensions.getActionBarHeight
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.extensions.getFriendlyMessage
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.parseFileChannel
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.DisplayedMedia
import org.fossify.gallery.helpers.EXOPLAYER_MAX_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_MIN_BUFFER_MS
import org.fossify.gallery.helpers.FAST_FORWARD_VIDEO_MS
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.interfaces.PlaybackSpeedListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.ChromeAutoHide
import org.fossify.gallery.views.MediaSideScroll
import org.fossify.gallery.views.RewindScan
import org.fossify.gallery.views.SeekHints
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import kotlin.math.abs

class VideoFragment : ViewPagerFragment(), TextureView.SurfaceTextureListener,
    SeekBar.OnSeekBarChangeListener, PlaybackSpeedListener {
    companion object {
        private const val PROGRESS = "progress"
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TOUCH_HOLD_DURATION_MS = 500L
        private const val TOUCH_HOLD_SPEED_MULTIPLIER = 2.0f
        private const val TOUCH_SLOP_DIVIDER = 3
        private const val SKIP_SECONDS = FAST_FORWARD_VIDEO_MS / 1000
        private const val LOOP_OFF_ALPHA = 0.6f

        /** A hold or a double tap this near either side of the video acts on that side: back on the left. */
        private const val SIDE_ZONE = 1 / 3f
    }

    private var mIsFullscreen = false
    private var mWasFragmentInit = false
    private var mIsPanorama = false
    private var mIsFragmentVisible = false
    private var mIsDragged = false
    private var mWasVideoStarted = false
    private var mWasPlayerInited = false
    private var mWasLastPositionRestored = false
    private var mPlayOnPrepared = false
    private var mIsPlayerPrepared = false
    private var mCurrTime = 0L
    private var mDuration = 0L
    private var mPositionWhenInit = 0L
    private var mPositionAtPause = 0L
    var mIsPlaying = false

    private var mExoPlayer: ExoPlayer? = null
    private var mVideoSize = Point(1, 1)
    private var mTimerHandler = Handler()

    private var mStoredBottomActions = true
    private var mStoredRememberLastVideoPosition = false
    private var mOriginalPlaybackSpeed = 1f
    private var mIsLongPressActive = false
    private var mHasAudio = true

    private val mTouchHoldRunnable = Runnable {
        val player = mExoPlayer ?: return@Runnable
        mView.parent.requestDisallowInterceptTouchEvent(true)
        // This code runs after the delay, only if the user is still holding down.
        mIsLongPressActive = true
        mView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // held on the left third it winds back, anywhere else it plays twice as fast
        if (mHoldIsRewind) {
            mWasPlayingBeforeRewind = mIsPlaying
            player.playWhenReady = false
            player.isScrubbingModeEnabled = true
            mPlaybackSpeedPill.setText(R.string.playback_rewind_display_text)
            mRewindScan.start(player.currentPosition)
        } else {
            mOriginalPlaybackSpeed = player.playbackParameters.speed
            mPlaybackSpeedPill.setText(R.string.playback_speed_display_text)
            updatePlaybackSpeed(TOUCH_HOLD_SPEED_MULTIPLIER)
        }

        mPlaybackSpeedPill.fadeIn()
    }

    private lateinit var mTimeHolder: View
    private lateinit var mBrightnessSideScroll: MediaSideScroll
    private lateinit var mVolumeSideScroll: MediaSideScroll
    private lateinit var binding: PagerVideoItemBinding
    private lateinit var mView: View
    private lateinit var mMedium: Medium
    private lateinit var mConfig: Config
    private lateinit var mTextureView: TextureView
    private lateinit var mCurrTimeView: TextView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mSeekBar: SeekBar
    private lateinit var mPlaybackSpeedPill: TextView
    private var mTouchSlop = 0
    private var mInitialX = 0f
    private var mInitialY = 0f

    private lateinit var mSeekHints: SeekHints
    private lateinit var mRewindScan: RewindScan
    private lateinit var mChromeAutoHide: ChromeAutoHide
    private var mHoldIsRewind = false
    private var mWasPlayingBeforeRewind = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mConfig = context.config
        mTouchSlop = (ViewConfiguration.get(context).scaledTouchSlop) / TOUCH_SLOP_DIVIDER
        binding = PagerVideoItemBinding.inflate(inflater, container, false).apply {
            panoramaOutline.setOnClickListener { openPanorama() }
            bottomVideoTimeHolder.videoCurrTime.setOnClickListener { skip(false) }
            bottomVideoTimeHolder.videoDuration.setOnClickListener { skip(true) }
            videoHolder.setOnClickListener { toggleFullscreen() }
            videoPreview.setOnClickListener { toggleFullscreen() }
            bottomVideoTimeHolder.videoPlaybackSpeed.setOnClickListener {
                mChromeAutoHide.cancel()
                showPlaybackSpeedPicker()
            }

            bottomVideoTimeHolder.videoToggleMute.setOnClickListener {
                mConfig.muteVideos = !mConfig.muteVideos
                updatePlayerMuteState(showToast = true)
                keepChromeUp()
            }

            bottomVideoTimeHolder.videoToggleLoop.setOnClickListener {
                toggleLoop()
                keepChromeUp()
            }

            videoSurfaceFrame.controller.settings.swallowDoubleTaps = true

            // the middle of the video's controls, and until it is played the one control it has
            mPlayPauseButton = videoPlayOutline
            mPlayPauseButton.setOnClickListener {
                if (mConfig.gestureVideoPlayer) activity.launchGesturePlayer(mMedium.path) else togglePlayPause()
            }

            videoSkipBack.setOnClickListener { skipWithHint(false) }
            videoSkipForward.setOnClickListener { skipWithHint(true) }

            mSeekBar = bottomVideoTimeHolder.videoSeekbar
            bottomVideoTimeHolder.videoSeekbar.setVideo(mMedium.path, mMedium.getSignature())
            mPlaybackSpeedPill = playbackSpeedPill
            mSeekBar.setOnSeekBarChangeListener(this@VideoFragment)
            // adding an empty click listener just to avoid ripple animation at toggling fullscreen
            mSeekBar.setOnClickListener { }

            mSeekHints = SeekHints(videoSeekHintBack, videoSeekHintForward)
            mRewindScan = RewindScan(videoHolder) { setPosition(it) }
            mChromeAutoHide = ChromeAutoHide(videoHolder) {
                val canHide = mIsPlaying && mIsFragmentVisible && listener?.isSlideShowActive() == false
                if (canHide && listener?.isFullScreen() == false) {
                    listener?.fragmentClicked()
                }
            }

            mTimeHolder = bottomVideoTimeHolder.videoTimeHolder
            mCurrTimeView = bottomVideoTimeHolder.videoCurrTime
            mBrightnessSideScroll = videoBrightnessController
            mVolumeSideScroll = videoVolumeController
            mBrightnessSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mVolumeSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mTextureView = videoSurface
            mTextureView.surfaceTextureListener = this@VideoFragment

            val gestureDetector =
                GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mConfig.allowInstantChange) {
                            toggleFullscreen()
                            return true
                        }

                        val viewWidth = root.width
                        val instantWidth = viewWidth / 7
                        val clickedX = e.rawX
                        when {
                            clickedX <= instantWidth -> listener?.goToPrevItem()
                            clickedX >= viewWidth - instantWidth -> listener?.goToNextItem()
                            else -> toggleFullscreen()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        handleDoubleTap(e.x)
                        return true
                    }
                })

            videoPreview.setOnTouchListener { view, event ->
                handleEvent(event)
                false
            }

            videoSurfaceFrame.setOnTouchListener { view, event ->
                handleEvent(event) { isFlickEligible() }
                handleTouchHoldEvent(event)
                if (mIsLongPressActive) {
                    return@setOnTouchListener true
                }

                gestureDetector.onTouchEvent(event)
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())

            val pillTopMargin = system.top + resources.getActionBarHeight(context) +
                resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()
            (mPlaybackSpeedPill.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                setMargins(
                    0, pillTopMargin, 0, 0
                )
            }

            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        mView = binding.root

        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        storeStateVariables()
        Glide.with(context).load(mMedium.path).into(binding.videoPreview)

        // setMenuVisibility is not called at VideoActivity (third party intent)
        if (!mIsFragmentVisible && activity is VideoActivity) {
            mIsFragmentVisible = true
        }

        mIsFullscreen = listener?.isFullScreen() == true
        initTimeHolder()
        // checkIfPanorama() TODO: Implement panorama using a FOSS library

        ensureBackgroundThread {
            activity.getVideoResolution(mMedium.path)?.apply {
                mVideoSize.x = x
                mVideoSize.y = y
            }
        }

        if (mIsPanorama) {
            binding.apply {
                panoramaOutline.beVisible()
                videoPlayOutline.beGone()
                mVolumeSideScroll.beGone()
                mBrightnessSideScroll.beGone()
                Glide.with(context).load(mMedium.path).into(videoPreview)
            }
        }

        if (!mIsPanorama) {
            if (savedInstanceState != null) {
                mCurrTime = savedInstanceState.getLong(PROGRESS, 0L)
            }

            mWasFragmentInit = true
            setVideoSize()

            binding.apply {
                mBrightnessSideScroll.initialize(
                    activity,
                    slideInfo,
                    true,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToPrevItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        skipWithHint(false)
                    })
                mVolumeSideScroll.initialize(
                    activity,
                    slideInfo,
                    false,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToNextItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        skipWithHint(true)
                    })

                videoSurface.onGlobalLayout {
                    if (mIsFragmentVisible && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                        playVideo()
                    }
                }
            }
        }

        setupVideoDuration()
        if (mStoredRememberLastVideoPosition) {
            restoreLastVideoSavedPosition()
        }

        return mView
    }

    override fun onResume() {
        super.onResume()
        mConfig =
            requireContext().config      // make sure we get a new config, in case the user changed something in the app settings
        requireActivity().updateTextColors(binding.videoHolder)
        val allowVideoGestures = mConfig.allowVideoGestures
        mTextureView.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        binding.videoSurfaceFrame.beGoneIf(mTextureView.isGone())

        mVolumeSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)
        mBrightnessSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)

        initTimeHolder()
        storeStateVariables()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        pauseVideo()
        if (mStoredRememberLastVideoPosition && mIsFragmentVisible && mWasVideoStarted) {
            saveVideoProgress()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == false) {
            cleanup()
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (mIsFragmentVisible && !menuVisible) {
            pauseVideo()
        }

        mIsFragmentVisible = menuVisible
        val shouldPlayVideo = mWasFragmentInit && menuVisible && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer
        if (shouldPlayVideo) playVideo()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setVideoSize()
        binding.videoSurfaceFrame.onGlobalLayout {
            binding.videoSurfaceFrame.controller.resetState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(PROGRESS, mCurrTime)
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mStoredBottomActions = bottomActions
            mStoredRememberLastVideoPosition = rememberLastVideoPosition
        }
    }

    private fun saveVideoProgress() {
        if (!videoEnded()) {
            if (mExoPlayer != null) {
                mConfig.saveLastVideoPosition(
                    mMedium.path,
                    mExoPlayer!!.currentPosition.toInt() / 1000
                )
            } else {
                mConfig.saveLastVideoPosition(mMedium.path, mPositionAtPause.toInt() / 1000)
            }
        }
    }

    private fun restoreLastVideoSavedPosition() {
        val seconds = mConfig.getLastVideoPosition(mMedium.path)
        if (seconds > 0) {
            mPositionAtPause = seconds * 1000L
            setPosition(seconds * 1000L)
        }
    }

    private fun setupTimeHolder() {
        mSeekBar.max = mDuration.toInt()
        binding.bottomVideoTimeHolder.videoDuration.text = mDuration.getFormattedDuration()
        setupTimer()
    }

    private fun setupTimer() {
        activity?.runOnUiThread(object : Runnable {
            override fun run() {
                if (mExoPlayer != null && !mIsDragged && mIsPlaying) {
                    mCurrTime = mExoPlayer!!.currentPosition
                    mSeekBar.progress = mCurrTime.toInt()
                    mCurrTimeView.text = mCurrTime.getFormattedDuration()
                }

                mTimerHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        })
    }

    private fun initExoPlayer() {
        val shouldSkipInit = activity == null || mConfig.gestureVideoPlayer || mIsPanorama || mExoPlayer != null
        if (shouldSkipInit) return

        val isContentUri = mMedium.path.startsWith("content://")
        val uri = if (isContentUri) Uri.parse(mMedium.path) else Uri.fromFile(File(mMedium.path))
        val dataSpec = DataSpec(uri)
        val fileDataSource = if (isContentUri) {
            ContentDataSource(requireContext())
        } else {
            FileDataSource()
        }

        try {
            fileDataSource.open(dataSpec)
        } catch (e: Exception) {
            fileDataSource.close()
            activity?.showErrorToast(e)
            return
        }

        val factory = DataSource.Factory { fileDataSource }
        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(fileDataSource.uri!!))

        fileDataSource.close()

        mPlayOnPrepared = true

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MAX_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        mExoPlayer = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(DefaultMediaSourceFactory(requireContext()))
            .setSeekParameters(SeekParameters.EXACT)
            .setLoadControl(loadControl)
            .build()
            .apply {
                if (mConfig.loopVideos && listener?.isSlideShowActive() == false) {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
                setPlaybackSpeed(mConfig.playbackSpeed)
                setMediaSource(mediaSource)
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), false
                )
                prepare()

                if (mTextureView.surfaceTexture != null) {
                    setVideoSurface(Surface(mTextureView.surfaceTexture))
                }

                initListeners()
            }

        updatePlayerMuteState()
    }

    private fun ExoPlayer.initListeners() {
        addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                @Player.DiscontinuityReason reason: Int,
            ) {
                // Reset progress views when video loops.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mSeekBar.progress = 0
                    mCurrTimeView.text = 0.getFormattedDuration()
                }
            }

            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> videoPrepared()
                    Player.STATE_ENDED -> videoCompleted()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                mVideoSize.x = videoSize.width
                mVideoSize.y = (videoSize.height / videoSize.pixelWidthHeightRatio).toInt()
                setVideoSize()
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                binding.errorMessageHolder.errorMessage.apply {
                    if (error != null) {
                        binding.videoPreview.beGone()
                        binding.videoTransport.beGone()
                        text = error.getFriendlyMessage(context)
                        setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                        fadeIn()
                    } else {
                        beGone()
                        binding.videoTransport.beVisible()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                mHasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
                updatePlayerMuteState()
            }
        })
    }

    private fun toggleFullscreen() {
        listener?.fragmentClicked()
    }

    // the same thirds as a hold: back on the left, forward on the right
    private fun handleDoubleTap(x: Float) {
        val sideWidth = mView.width * SIDE_ZONE
        when {
            x <= sideWidth -> skipWithHint(false)
            x >= mView.width - sideWidth -> skipWithHint(true)
            else -> togglePlayPause()
        }
    }

    /** Ten seconds either way, said on that side of the video - a double tap's, or a skip button's. */
    private fun skipWithHint(forward: Boolean) {
        if (mExoPlayer == null || mIsPanorama) {
            return
        }

        doSkip(forward)
        mSeekHints.show(forward, SKIP_SECONDS)
        keepChromeUp()
    }

    /** A control was touched: a playing video keeps its chrome a while longer. */
    private fun keepChromeUp() {
        if (mIsPlaying) {
            mChromeAutoHide.restart()
        }
    }

    /** The same setting as the app's own, so a video looped here loops the next time too. */
    private fun toggleLoop() {
        mConfig.loopVideos = !mConfig.loopVideos
        mExoPlayer?.repeatMode = if (mConfig.loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        updateLoopButton()
    }

    // on, it wears the looping arrows with a one in them; off, the plain ones, dimmed
    private fun updateLoopButton() {
        val isLooping = mConfig.loopVideos
        val icon = if (isLooping) R.drawable.ic_video_repeat_one_vector else R.drawable.ic_video_repeat_vector
        binding.bottomVideoTimeHolder.videoToggleLoop.apply {
            setImageResource(icon)
            alpha = if (isLooping) 1f else LOOP_OFF_ALPHA
        }
    }

    private fun showPlaying(isPlaying: Boolean) {
        mPlayPauseButton.apply {
            setImageResource(if (isPlaying) R.drawable.ic_video_pause_vector else R.drawable.ic_video_play_vector)
            contentDescription = getString(if (isPlaying) R.string.video_pause else R.string.video_play)
        }
    }

    private fun initTimeHolder() {
        // a separate screen plays the video, so there is nothing here for the controls to drive
        val showsControls = !mIsFullscreen && !mConfig.gestureVideoPlayer
        mTimeHolder.beVisibleIf(showsControls)
        mTimeHolder.alpha = if (showsControls) 1f else 0f
        // a video not yet started keeps its play button whatever the chrome is doing: it is the way in
        val showsTransport = !mIsFullscreen || !mWasVideoStarted
        binding.videoTransport.beVisibleIf(showsTransport)
        binding.videoTransport.alpha = if (showsTransport) 1f else 0f
        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(mTimeHolder)
    }

    private fun checkIfPanorama() {
        try {
            val fis = FileInputStream(File(mMedium.path))
            fis.use {
                requireContext().parseFileChannel(mMedium.path, it.channel, 0, 0, 0) {
                    mIsPanorama = true
                }
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
    }

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    /**
     * The still stands in until playback starts and then gives way to the surface, so whichever of
     * the two is up is what a flight lands on and what a shrink sets off from. A playing video is
     * read frame and all - shrinking the first frame of something halfway through would be a cut.
     */
    override fun displayedMedia(): DisplayedMedia? {
        if (!isAdded) {
            return null
        }

        if (binding.videoPreview.isVisible()) {
            val rect = binding.videoPreview.displayedImageRect() ?: return null
            return DisplayedMedia(rect, (binding.videoPreview.drawable as? BitmapDrawable)?.bitmap)
        }

        if (!mTextureView.isVisible() || mTextureView.width == 0) {
            return null
        }

        // the surface is laid out at the video's own size, so its bounds are the picture's
        return DisplayedMedia(mTextureView.screenRect(), runCatching { mTextureView.bitmap }.getOrNull())
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        mIsFullscreen = isFullscreen

        mSeekBar.setOnSeekBarChangeListener(if (mIsFullscreen) null else this)
        arrayOf(
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoDuration,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute,
            binding.bottomVideoTimeHolder.videoToggleLoop,
            binding.videoSkipBack,
            binding.videoSkipForward
        ).forEach {
            it.isClickable = !mIsFullscreen
        }

        if (isFullscreen) {
            mChromeAutoHide.cancel()
            if (!mConfig.gestureVideoPlayer) {
                mTimeHolder.fadeOut(DEFAULT_ANIMATION_DURATION)
            }

            if (mWasVideoStarted) {
                binding.videoTransport.fadeOut(DEFAULT_ANIMATION_DURATION)
            }

            binding.bottomActionsDummy.fadeOut(DEFAULT_ANIMATION_DURATION)
        } else {
            binding.bottomActionsDummy.beVisible()
            if (!mConfig.gestureVideoPlayer) {
                mTimeHolder.fadeIn(DEFAULT_ANIMATION_DURATION)
            }

            binding.videoTransport.fadeIn(DEFAULT_ANIMATION_DURATION)
            keepChromeUp()
        }
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        childFragmentManager.beginTransaction().add(fragment, fragment::class.java.simpleName)
            .commit()
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag as? Boolean) {
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag = isSlow

            val drawableId =
                if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            context?.let {
                binding.bottomVideoTimeHolder.videoPlaybackSpeed
                    .setDrawablesRelativeWithIntrinsicBounds(
                        AppCompatResources.getDrawable(it, drawableId)
                    )
            }
        }

        @SuppressLint("SetTextI18n")
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(speed)}x"
        mExoPlayer?.setPlaybackSpeed(speed)
    }

    private fun skip(forward: Boolean) {
        if (mIsPanorama) {
            return
        } else if (mExoPlayer == null) {
            playVideo()
            return
        }

        mPositionAtPause = 0L
        doSkip(forward)
    }

    private fun doSkip(forward: Boolean) {
        if (mExoPlayer == null) {
            return
        }

        val curr = mExoPlayer!!.currentPosition
        var newPosition =
            if (forward) curr + FAST_FORWARD_VIDEO_MS else curr - FAST_FORWARD_VIDEO_MS
        newPosition = newPosition.coerceIn(0, maxOf(mExoPlayer!!.duration, 0))
        setPosition(newPosition)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val newPosition = progress.toLong()
            if (mExoPlayer != null) {
                if (!mWasPlayerInited) {
                    mPositionWhenInit = newPosition
                }
                setPosition(newPosition)
            }

            if (mExoPlayer == null) {
                mPositionAtPause = newPosition
                playVideo()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (mExoPlayer == null) {
            return
        }

        // or every seek of a drag cancels the last before it has drawn, and the picture stands still
        mExoPlayer!!.isScrubbingModeEnabled = true
        mExoPlayer!!.playWhenReady = false
        mIsDragged = true
        mChromeAutoHide.cancel()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (mIsPanorama) {
            openPanorama()
            return
        }

        if (mExoPlayer == null) {
            return
        }

        mExoPlayer!!.isScrubbingModeEnabled = false
        if (mIsPlaying) {
            mExoPlayer!!.playWhenReady = true
        }

        mIsDragged = false
        keepChromeUp()
    }

    private fun togglePlayPause() {
        if (activity == null || !isAdded) {
            return
        }

        if (mIsPlaying) {
            pauseVideo()
        } else {
            playVideo()
        }
    }

    private fun updatePlayerMuteState(showToast: Boolean = false) {
        val isMuted = mConfig.muteVideos
        if (mHasAudio) {
            if (isMuted) mExoPlayer?.mute() else mExoPlayer?.unmute()
        } else if (showToast && mWasVideoStarted) {
            activity?.toast(R.string.video_no_sound)
        }

        val drawableId = when {
            !mHasAudio -> R.drawable.ic_vector_no_sound
            isMuted -> R.drawable.ic_vector_speaker_off
            else -> R.drawable.ic_vector_speaker_on
        }

        binding.bottomVideoTimeHolder.videoToggleMute.setImageResource(drawableId)
    }

    fun playVideo() {
        if (mExoPlayer == null) {
            initExoPlayer()
            return
        }

        if (binding.videoPreview.isVisible()) {
            binding.videoPreview.beGone()
            initExoPlayer()
        }

        val wasEnded = videoEnded()
        if (wasEnded) {
            setPosition(0)
        }

        if (mStoredRememberLastVideoPosition && !mWasLastPositionRestored) {
            mWasLastPositionRestored = true
            restoreLastVideoSavedPosition()
        }

        if (!wasEnded || !mConfig.loopVideos) {
            showPlaying(true)
        }

        if (!mWasVideoStarted) {
            // the rest of the controls come once there is a video running for them to drive
            binding.videoSkipBack.beVisible()
            binding.videoSkipForward.beVisible()
            binding.bottomVideoTimeHolder.videoToggleMute.beVisible()
            binding.bottomVideoTimeHolder.videoToggleLoop.beVisible()
            updateLoopButton()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
                "${DecimalFormat("#.##").format(mConfig.playbackSpeed)}x"
        }

        mWasVideoStarted = true
        if (mIsPlayerPrepared) {
            mIsPlaying = true
        }
        mExoPlayer?.playWhenReady = true
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mChromeAutoHide.restart()
    }

    private fun pauseVideo() {
        if (mExoPlayer == null) {
            return
        }

        mIsPlaying = false
        if (!videoEnded()) {
            mExoPlayer?.playWhenReady = false
        }

        showPlaying(false)
        mChromeAutoHide.cancel()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mPositionAtPause = mExoPlayer?.currentPosition ?: 0L
    }

    private fun videoEnded(): Boolean {
        val currentPos = mExoPlayer?.currentPosition ?: 0
        val duration = mExoPlayer?.duration ?: 0
        return currentPos != 0L && currentPos >= duration
    }

    private fun setPosition(milliseconds: Long) {
        mExoPlayer?.seekTo(milliseconds)
        mSeekBar.progress = milliseconds.toInt()
        mCurrTimeView.text = milliseconds.getFormattedDuration()

        if (!mIsPlaying) {
            mPositionAtPause = milliseconds
        }
    }

    private fun setupVideoDuration() {
        ensureBackgroundThread {
            mDuration = context?.getDuration(mMedium.path)?.times(1000L)?.coerceAtLeast(0L) ?: 0L

            activity?.runOnUiThread {
                setupTimeHolder()
                setPosition(0)
            }
        }
    }

    private fun videoPrepared() {
        if (mDuration == 0L) {
            mDuration = mExoPlayer!!.duration
            setupTimeHolder()
            setPosition(mCurrTime)

            if (mIsFragmentVisible && (mConfig.autoplayVideos)) {
                playVideo()
            }
        }

        if (mPositionWhenInit != 0L && !mWasPlayerInited) {
            setPosition(mPositionWhenInit)
            mPositionWhenInit = 0
        }

        mIsPlayerPrepared = true
        if (mPlayOnPrepared && !mIsPlaying) {
            if (mPositionAtPause != 0L) {
                mExoPlayer?.seekTo(mPositionAtPause)
                mPositionAtPause = 0L
            }
            updatePlaybackSpeed(mConfig.playbackSpeed)
            playVideo()
        }
        mWasPlayerInited = true
        mPlayOnPrepared = false
    }

    private fun videoCompleted() {
        if (!isAdded || mExoPlayer == null) {
            return
        }

        mCurrTime = mExoPlayer!!.duration
        if (listener?.videoEnded() == false && mConfig.loopVideos) {
            playVideo()
        } else {
            mSeekBar.progress = mSeekBar.max
            mCurrTimeView.text = mDuration.getFormattedDuration()
            pauseVideo()
            // the chrome went while it played, and a finished video is one to be done something with
            if (mIsFragmentVisible && listener?.isSlideShowActive() == false && listener?.isFullScreen() == true) {
                listener?.fragmentClicked()
            }
        }
    }

    private fun cleanup() {
        pauseVideo()
        mChromeAutoHide.cancel()
        mRewindScan.stop()
        releaseExoPlayer()

        if (mWasFragmentInit) {
            mCurrTimeView.text = 0.getFormattedDuration()
            mSeekBar.progress = 0
            mTimerHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun releaseExoPlayer() {
        mIsPlayerPrepared = false
        mExoPlayer?.apply {
            stop()
            release()
        }
        mExoPlayer = null
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mExoPlayer?.setVideoSurface(Surface(mTextureView.surfaceTexture))
    }

    private fun setVideoSize() {
        if (activity == null || mConfig.gestureVideoPlayer) return

        val videoProportion = mVideoSize.x.toFloat() / mVideoSize.y.toFloat()
        val display = requireActivity().windowManager.defaultDisplay
        val screenWidth: Int
        val screenHeight: Int

        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels

        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()

        mTextureView.layoutParams.apply {
            if (videoProportion > screenProportion) {
                width = screenWidth
                height = (screenWidth.toFloat() / videoProportion).toInt()
            } else {
                width = (videoProportion * screenHeight.toFloat()).toInt()
                height = screenHeight
            }
            mTextureView.layoutParams = this
        }
    }

    override fun isFlickEligible() = binding.videoSurfaceFrame.controller.state.zoom == 1f

    private fun handleTouchHoldEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mIsPlaying && event.pointerCount == 1) {
                    mInitialX = event.x
                    mInitialY = event.y
                    mHoldIsRewind = event.x < mView.width * SIDE_ZONE
                    mTimerHandler.postDelayed(mTouchHoldRunnable, TOUCH_HOLD_DURATION_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - mInitialX)
                val deltaY = abs(event.y - mInitialY)
                if (!mIsLongPressActive && (deltaX > mTouchSlop || deltaY > mTouchSlop)) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mIsLongPressActive) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                stopHoldSpeedMultiplierGesture()
            }
        }
    }

    private fun stopHoldSpeedMultiplierGesture() {
        if (mIsLongPressActive) {
            if (mRewindScan.isRunning) {
                mRewindScan.stop()
                mExoPlayer?.isScrubbingModeEnabled = false
                if (mWasPlayingBeforeRewind) {
                    mExoPlayer?.playWhenReady = true
                }
            } else {
                updatePlaybackSpeed(mOriginalPlaybackSpeed)
            }

            mIsLongPressActive = false
            mPlaybackSpeedPill.fadeOut()
        }
    }
}
