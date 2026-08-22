package org.fossify.gallery.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateBrightness
import org.fossify.commons.extensions.viewBinding
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.databinding.ActivityPeekViewerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.hideSystemUI
import org.fossify.gallery.extensions.showSystemUI
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PeekSession
import org.fossify.gallery.models.Medium

/**
 * A proper look at one picture while a selection is being made, and nothing more: the media,
 * whether it is selected, and the strip to travel along. No toolbar, no filename, no details, no
 * metadata sheet, no bottom actions, no menu. A thumbnail is too small to choose between two near
 * identical frames, and this is how to look without dropping the selection to do it.
 *
 * Everything it shows it is handed. [PeekSession] carries the list the grid is displaying and the
 * paths picked out of it, and the tick written back into that same set is the whole of what the
 * grid reads on the way in. Nothing here queries MediaStore or Room: the list is the grid's, down
 * to the order a search left it in.
 */
// most of what is below is the FragmentListener contract, a line apiece
@Suppress("TooManyFunctions")
class PeekViewerActivity :
    BaseViewerActivity(),
    ViewPager.OnPageChangeListener,
    ViewPagerFragment.FragmentListener {

    private val binding by viewBinding(ActivityPeekViewerBinding::inflate)

    private var media = emptyList<Medium>()
    private var isFullScreen = false
    private var originalBrightness: Float? = null

    override val contentHolder: View
        get() = binding.peekHolder

    override val appBarLayout: AppBarLayout
        get() = binding.peekAppbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge()

        media = PeekSession.media
        // nothing was handed over, so the process was killed while the peek was up
        if (media.isEmpty()) {
            finish()
            return
        }

        window.decorView.setBackgroundColor(getProperBackgroundColor())
        if (config.blackBackground) {
            binding.peekHolder.background = Color.BLACK.toDrawable()
            binding.viewPager.background = Color.BLACK.toDrawable()
        }

        setupPill()
        setupThumbnailStrip()
        setupViewPager()
        showSystemUI()
    }

    override fun onResume() {
        super.onResume()
        originalBrightness = window.updateBrightness(config.maxBrightness, originalBrightness)
    }

    /** The grid takes the selection off [PeekSession]; the path is what it scrolls back to. */
    override fun finish() {
        setResult(RESULT_OK, Intent().putExtra(PATH, currentMedium()?.path.orEmpty()))
        super.finish()
    }

    private fun setupViewPager() {
        val position = media.indexOfFirst { it.path == PeekSession.startPath }.coerceAtLeast(0)
        val pagerAdapter = MyPagerAdapter(this, supportFragmentManager, media.toMutableList())
        binding.viewPager.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1
            addOnPageChangeListener(this@PeekViewerActivity)
            currentItem = position
        }

        binding.viewerThumbnailStrip.setMedia(media, position)
        binding.viewerThumbnailStrip.setSelection(PeekSession.selectedPaths)
        updatePill()
    }

    private fun setupThumbnailStrip() {
        binding.viewerThumbnailStrip.onMediumPicked = { position ->
            if (binding.viewPager.currentItem != position) {
                binding.viewPager.setCurrentItem(position, false)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.viewerThumbnailStrip) { view, insets ->
            // no buttons under it here, so the strip simply clears the navigation bar
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.getInsetsIgnoringVisibility(Type.systemBars()).bottom
            }
            insets
        }
    }

    private fun setupPill() {
        binding.peekPill.apply {
            cornerRadius = resources.getDimension(R.dimen.peek_pill_radius)
            overlayAlpha = Glass.TEXT_TINT_ALPHA
            frost(binding.peekHolder)
            setOnClickListener { toggleCurrentSelection() }
        }

        binding.peekCount.setTextColor(getProperTextColor())
    }

    /**
     * The one thing this screen changes. Written into [PeekSession] rather than onto the grid's
     * adapter, which is a screen away and has an action mode of its own to keep straight - it takes
     * the whole set at once when the peek closes.
     */
    private fun toggleCurrentSelection() {
        val path = currentMedium()?.path ?: return
        if (!PeekSession.selectedPaths.remove(path)) {
            PeekSession.selectedPaths.add(path)
        }

        updatePill()
        binding.viewerThumbnailStrip.setSelection(PeekSession.selectedPaths)
    }

    /**
     * The pill says two things at once: a filled tick where the item on screen is selected against
     * an empty ring where it is not, and beside it how many are selected altogether. The count is
     * the bare number - there is nothing else on this screen for it to be a count of.
     */
    private fun updatePill() {
        val isSelected = PeekSession.selectedPaths.contains(currentMedium()?.path)
        val primaryColor = getProperPrimaryColor()
        binding.peekCheck.apply {
            if (isSelected) {
                setBackgroundResource(R.drawable.circle_background)
                setImageResource(org.fossify.commons.R.drawable.ic_check_vector)
                background?.applyColorFilter(primaryColor)
                applyColorFilter(primaryColor.getContrastColor())
            } else {
                setBackgroundResource(R.drawable.circle_outline)
                setImageDrawable(null)
                background?.applyColorFilter(getProperTextColor())
            }
        }

        binding.peekCount.text = PeekSession.selectedPaths.size.toString()
    }

    private fun currentMedium() = media.getOrNull(binding.viewPager.currentItem)

    override fun fragmentClicked() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            hideSystemUI()
        } else {
            showSystemUI()
        }

        (binding.viewPager.adapter as? MyPagerAdapter)?.toggleFullscreen(isFullScreen)
        val newAlpha = if (isFullScreen) 0f else 1f
        listOf<View>(binding.peekAppbar, binding.viewerThumbnailStrip).forEach { view ->
            view.animate().alpha(newAlpha).withStartAction {
                view.beVisible()
            }.withEndAction {
                view.beVisibleIf(newAlpha == 1f)
            }.start()
        }
    }

    override fun isFullScreen() = isFullScreen

    override fun videoEnded() = false

    override fun isSlideShowActive() = false

    override fun goToPrevItem() {
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, false)
    }

    override fun goToNextItem() {
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, false)
    }

    /** A video only ever plays in the pager here, so there is no other player to hand it to. */
    override fun launchViewVideoIntent(path: String) = Unit

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        binding.viewerThumbnailStrip.setSelectedPosition(position)
        updatePill()
    }

    override fun onPageScrollStateChanged(state: Int) {}
}
