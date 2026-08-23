package org.fossify.gallery.views

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.gallery.R
import org.fossify.gallery.databinding.NavPillBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.ScrollPanner

private const val ANIMATION_DURATION = 200L

/** How much of the panel's own colour a segment that is not the screen you are on is painted in. */
private const val IDLE_ALPHA = 0.65f

/** The wash behind the segment whose screen is up. */
private const val PLATE_ALPHA = 0.15f

/** The two top level browsing screens, drawn as two tabs of the one library. */
enum class NavDestination { PICTURES, ALBUMS }

/**
 * The frosted pill the folder grid and the all media grid both float over the foot of their
 * content: Pictures, Albums, and a second door to the drop-down the three dots opens.
 *
 * Painting itself and getting out of the way of a scroll are its own business - all a screen has to
 * say is which of the two it is showing and what a tap should do about it.
 */
class NavPill(private val binding: NavPillBinding) {
    private val panner = ScrollPanner(binding.root.resources, ::hide, ::show)
    private var isHidden = false

    /** The whole of it, shadow room included, for the inset handling that has to know where it sits. */
    val root get() = binding.root

    /** What the drop-down hangs off. It binds its own listener rather than going through here. */
    val menuButton: View get() = binding.navPillMenu

    var onDestination: ((NavDestination) -> Unit)? = null

    /** Which screen is up, and so which segment wears the plate. */
    var selected = NavDestination.ALBUMS
        set(value) {
            field = value
            updateColors()
        }

    /**
     * Whether the screen wants the pill at all. Gone rather than panned away: a group stepped into,
     * a selection started or a search opened is not something a scroll should bring it back from.
     */
    var isAvailable = true
        set(value) {
            field = value
            if (value) {
                root.beVisible()
                show()
            } else {
                root.beGone()
            }
        }

    /** Stops the pill from panning away and brings it back up if it had. */
    var isPanningEnabled: Boolean
        get() = panner.isPanningEnabled
        set(value) {
            panner.isPanningEnabled = value
        }

    init {
        binding.navPillPictures.setOnClickListener { pick(NavDestination.PICTURES) }
        binding.navPillAlbums.setOnClickListener { pick(NavDestination.ALBUMS) }
    }

    /** Frosts the pill over [contentBehind] and hangs it over [grid]. */
    fun setup(contentBehind: ViewGroup, grid: RecyclerView, selected: NavDestination) {
        this.selected = selected
        binding.navPillPanel.apply {
            cornerRadius = resources.getDimension(R.dimen.nav_pill_radius)
            blurRadius = Glass.DEFAULT_RADIUS
            // it is covered in labels, which have to read over whatever photo is scrolling past
            overlayAlpha = Glass.TEXT_TINT_ALPHA
            elevation = resources.getDimension(R.dimen.floating_chrome_elevation)
            frost(contentBehind)
        }

        panner.panWith(grid)
    }

    /** Repainted on every resume rather than set once: the theme can change while it is away. */
    fun updateColors() {
        binding.navPillPanel.updateColors()
        binding.apply {
            paint(navPillPictures, navPillPicturesIcon, navPillPicturesLabel, selected == NavDestination.PICTURES)
            paint(navPillAlbums, navPillAlbumsIcon, navPillAlbumsLabel, selected == NavDestination.ALBUMS)
            // the menu is an action rather than somewhere to be, so it never wears the plate
            paint(navPillMenu, navPillMenuIcon, navPillMenuLabel, isSelected = false)
        }
    }

    fun show() {
        if (!isHidden && root.translationY == 0f) {
            return
        }

        isHidden = false
        binding.navPillPanel.isFrostPaused = false
        root.animate().translationY(0f).setDuration(ANIMATION_DURATION).start()
    }

    private fun hide() {
        if (isHidden) {
            return
        }

        isHidden = true
        root.animate()
            .translationY(root.height.toFloat())
            .setDuration(ANIMATION_DURATION)
            // the pill stays VISIBLE while panned away, so the glass has to be told to stop copying
            .withEndAction { if (isHidden) binding.navPillPanel.isFrostPaused = true }
            .start()
    }

    private fun paint(segment: View, icon: ImageView, label: TextView, isSelected: Boolean) {
        val content = Glass.contentColor(segment.context)
        val color = if (isSelected) content else content.adjustAlpha(IDLE_ALPHA)
        segment.setBackgroundResource(if (isSelected) R.drawable.nav_pill_selected else 0)
        segment.background?.setTint(content.adjustAlpha(PLATE_ALPHA))
        icon.applyColorFilter(color)
        label.setTextColor(color)
        label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun pick(destination: NavDestination) {
        if (destination != selected) {
            onDestination?.invoke(destination)
        }
    }
}
