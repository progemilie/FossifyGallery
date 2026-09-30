package org.fossify.gallery.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.gallery.databinding.SettingsLinkBinding
import org.fossify.gallery.R

/**
 * One page of the settings: a category's groups, under its own title and hue. Only one is up at a
 * time, reached from a [SettingsLink] on the first page - see [org.fossify.gallery.helpers.SettingsPages].
 */
class SettingsPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    val title: String

    /** The page's own colour, fixed whatever the theme, so a page is known by it. */
    @ColorInt
    val hue: Int

    init {
        orientation = VERTICAL
        val (title, hue) = context.obtainStyledAttributes(attrs, R.styleable.SettingsPage).use {
            it.getString(R.styleable.SettingsPage_pageTitle).orEmpty() to
                it.getColor(R.styleable.SettingsPage_pageHue, Color.GRAY)
        }

        this.title = title
        this.hue = hue
    }

    val groups get() = children.filterIsInstance<SettingsGroup>()

    /** The hue as the page's icons wear it, which a search's findings wear too. */
    @ColorInt
    var iconColor: Int = hue
        private set

    /**
     * Takes the theme's colours. The hue its icons and headings wear is carried as far towards white
     * on a dark theme, or black on a light one, as it has to be to read on the cards.
     */
    fun updateColors(cardColor: Int, textColor: Int) {
        iconColor = readableOn(cardColor, hue)
        groups.forEach { it.updateColors(cardColor, textColor, iconColor) }
    }

    /** Hides whichever groups have been left with nothing to show. */
    fun refreshGroups() = groups.forEach { it.refreshVisibility() }
}

/** How far an icon has to stand from the card it is on: WCAG's floor for anything that is not text. */
private const val MIN_ICON_CONTRAST = 3.0
private const val TOWARDS_STEP = 0.05f
private const val MAX_TOWARDS = 0.8f
private const val DARK_CARD_LUMINANCE = 0.5

private fun readableOn(card: Int, color: Int): Int {
    val towards = if (ColorUtils.calculateLuminance(card) < DARK_CARD_LUMINANCE) Color.WHITE else Color.BLACK
    var readable = color
    var blend = 0f
    while (blend < MAX_TOWARDS && ColorUtils.calculateContrast(readable, card) < MIN_ICON_CONTRAST) {
        blend += TOWARDS_STEP
        readable = ColorUtils.blendARGB(color, towards, blend)
    }

    return readable
}

/**
 * A row of the first settings page, opening one of the others: a disc in the page's hue with its
 * glyph, the page's title, a line saying what is on it and a chevron. The page named by `app:opens`
 * gives it its title and hue, so the two cannot disagree.
 */
class SettingsLink @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val binding = SettingsLinkBinding.inflate(LayoutInflater.from(context), this)

    /** The id of the [SettingsPage] this opens. */
    val opens: Int

    init {
        opens = context.obtainStyledAttributes(attrs, R.styleable.SettingsLink).use {
            binding.settingsLinkBadge.setImageResource(it.getResourceId(R.styleable.SettingsLink_linkIcon, 0))
            binding.settingsLinkSummary.text = it.getString(R.styleable.SettingsLink_linkSummary)
            it.getResourceId(R.styleable.SettingsLink_opens, NO_ID)
        }
    }

    /** Takes on the title and the hue of the page it opens. */
    fun showing(page: SettingsPage) {
        binding.settingsLinkTitle.text = page.title
        binding.settingsLinkBadge.backgroundTintList = ColorStateList.valueOf(page.hue)
    }

    fun updateColors(textColor: Int) {
        binding.settingsLinkTitle.setTextColor(textColor)
        binding.settingsLinkSummary.setTextColor(textColor)
        binding.settingsLinkChevron.applyColorFilter(textColor)
    }

    // the chevron points the way the page comes in from
    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        binding.settingsLinkChevron.scaleX = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
    }
}
