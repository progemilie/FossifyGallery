package org.fossify.gallery.views

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import org.fossify.gallery.R
import org.fossify.gallery.databinding.VideoControlsBinding
import kotlin.math.ceil
import kotlin.math.max
import org.fossify.commons.R as commonsR

/**
 * The video's controls in a row of their own over the frames, or in landscape sharing the frames' row.
 * Sharing it, nothing either side may change width while the video plays, as the scrubber reads its
 * frames again for every width.
 */
class VideoControlsLayout(private val controls: VideoControlsBinding) {
    private class Home(val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)

    private val holder = controls.videoTimeHolder

    // the order they stand in, sharing a row
    private val moved = with(controls) { listOf(videoTogglePlay, videoTimeGroup, videoSeekbar, videoToggles) }

    private val playingControls = with(controls) {
        listOf(videoTogglePlay, videoPlaybackSpeed, videoToggleLoop, videoToggleMute)
    }
    private val homes = moved.associateWith {
        val parent = it.parent as ViewGroup
        Home(parent, parent.indexOfChild(it), it.layoutParams)
    }

    private val holderPaddingTop = holder.paddingTop
    private val currTimeGravity = controls.videoCurrTime.gravity
    private val controlsMargin = holder.resources.getDimensionPixelSize(R.dimen.video_controls_margin)
    private val smallMargin = holder.resources.getDimensionPixelSize(commonsR.dimen.small_margin)
    private val scrubberHeight = holder.resources.getDimensionPixelSize(R.dimen.video_scrubber_height)

    var isInOneRow = false
        private set

    fun arrange(inOneRow: Boolean, isStarted: Boolean) {
        if (inOneRow != isInOneRow) {
            isInOneRow = inOneRow
            moved.forEach { (it.parent as ViewGroup).removeView(it) }
            if (inOneRow) {
                holder.orientation = LinearLayout.HORIZONTAL
                moved.forEach { holder.addView(it, oneRowParams(it)) }
            } else {
                holder.orientation = LinearLayout.VERTICAL
                // by index, so each index is valid as its view goes back
                moved.sortedBy { homes.getValue(it).index }.forEach {
                    val home = homes.getValue(it)
                    home.parent.addView(it, home.index, home.params)
                }
            }

            controls.videoControlsRow.isVisible = !inOneRow
            holder.updatePadding(top = if (inOneRow) 0 else holderPaddingTop)
        }

        if (!isStarted) {
            // GONE along the foot as before, INVISIBLE in a shared row to keep their room
            val notYet = if (inOneRow) View.INVISIBLE else View.GONE
            playingControls.forEach { it.visibility = notYet }
        }

        fitLabels()
    }

    /**
     * In a shared row, tabular figures and each label as wide as anything it can say. Asked again once
     * the duration is known, an hour or more being wider.
     */
    fun fitLabels() {
        val tabular = if (isInOneRow) TABULAR_FIGURES else null
        listOf(controls.videoCurrTime, controls.videoDuration, controls.videoPlaybackSpeed).forEach {
            it.fontFeatureSettings = tabular
        }

        val currTime = controls.videoCurrTime
        val timeWidth = if (isInOneRow) {
            max(currTime.textWidth(SHORT_TIME), currTime.textWidth(controls.videoDuration.text))
        } else {
            0
        }

        currTime.minWidth = timeWidth
        controls.videoDuration.minWidth = timeWidth
        // keeps to the separator
        currTime.gravity = if (isInOneRow) Gravity.END else currTimeGravity

        val speed = controls.videoPlaybackSpeed
        speed.minWidth = if (isInOneRow) {
            speed.textWidth(WIDEST_SPEED) + speed.totalPaddingLeft + speed.totalPaddingRight
        } else {
            0
        }
    }

    private fun oneRowParams(view: View) = when (view) {
        controls.videoTogglePlay -> LinearLayout.LayoutParams(view.layoutParams.width, view.layoutParams.height).apply {
            marginStart = controlsMargin
        }

        controls.videoTimeGroup -> LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = smallMargin }
        controls.videoSeekbar -> LinearLayout.LayoutParams(0, scrubberHeight, 1f)
        else -> LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = smallMargin }
    }.apply { gravity = Gravity.CENTER_VERTICAL }

    private fun TextView.textWidth(text: CharSequence) = ceil(paint.measureText(text.toString())).toInt()

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val TABULAR_FIGURES = "tnum"

        /** The longest a position or duration under an hour is written. */
        const val SHORT_TIME = "00:00"

        const val WIDEST_SPEED = "0.00x"
    }
}
