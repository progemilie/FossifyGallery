package org.fossify.gallery.helpers

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.google.gson.Gson
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import kotlin.math.abs

/*
 * TEMPORARY - the outline lab. Every style here can be tried out from OutlineLabDialog on the glass
 * pill and on folder covers separately. Once one is picked, the rest go along with the lab.
 */

private const val PERCENT = 100
private const val QUARTERS_PER_DP = 4f
private const val FULL_ALPHA = 255
private const val HSL_COMPONENTS = 3
private const val DARK_THEME_TEXT_LUMINANCE = 0.5

// how far a frost is carried towards the outline's colour at full strength
private const val MAX_BLEED = 0.5f

// where the lightness slider ends: short of white and black, which have no hue left to keep
private const val MAX_LIGHTNESS = 0.9f
private const val MIN_LIGHTNESS = 0.1f

/**
 * A style's sliders. [size] and [strength] mean something of their own to each style, see
 * [OutlineStyle.sizeLabel] and [OutlineStyle.strengthLabel].
 */
data class OutlineParams(
    /** How far the line is carried from the text colour towards the source colour, in percent. */
    val tint: Int,
    /** The line's opacity, in percent. */
    val opacity: Int,
    /** In quarters of a dp. */
    val width: Int,
    /** In dp. */
    val size: Int,
    /** In percent. */
    val strength: Int,
    /** How far the colour is carried lighter, or darker below zero, keeping its hue, in percent. */
    val lightness: Int = 0
)

enum class OutlineStyle(
    @StringRes val title: Int,
    val defaults: OutlineParams,
    @StringRes val sizeLabel: Int?,
    @StringRes val strengthLabel: Int?,
    val usesLine: Boolean = true
) {
    NONE(
        R.string.outline_style_none,
        OutlineParams(tint = 0, opacity = 0, width = 4, size = 0, strength = 0),
        sizeLabel = null,
        strengthLabel = null,
        usesLine = false
    ),
    HAIRLINE(
        R.string.outline_style_hairline,
        OutlineParams(tint = 50, opacity = 60, width = 4, size = 0, strength = 0),
        sizeLabel = null,
        strengthLabel = null
    ),
    THICK(
        R.string.outline_style_thick,
        OutlineParams(tint = 60, opacity = 80, width = 10, size = 0, strength = 0),
        sizeLabel = null,
        strengthLabel = null
    ),
    SOFT_GLOW(
        R.string.outline_style_soft_glow,
        OutlineParams(tint = 70, opacity = 50, width = 4, size = 10, strength = 30),
        sizeLabel = R.string.outline_glow_size,
        strengthLabel = R.string.outline_glow_strength
    ),
    INNER_GLOW(
        R.string.outline_style_inner_glow,
        OutlineParams(tint = 70, opacity = 45, width = 4, size = 12, strength = 35),
        sizeLabel = R.string.outline_glow_size,
        strengthLabel = R.string.outline_glow_strength
    ),
    FROST_BLEED(
        R.string.outline_style_frost_bleed,
        OutlineParams(tint = 80, opacity = 55, width = 4, size = 16, strength = 40),
        sizeLabel = R.string.outline_bleed_depth,
        strengthLabel = R.string.outline_bleed_strength
    ),
    LIT_EDGE(
        R.string.outline_style_lit_edge,
        OutlineParams(tint = 60, opacity = 85, width = 5, size = 0, strength = 80),
        sizeLabel = null,
        strengthLabel = R.string.outline_falloff
    ),
    DOUBLE(
        R.string.outline_style_double,
        OutlineParams(tint = 70, opacity = 70, width = 6, size = 2, strength = 35),
        sizeLabel = R.string.outline_gap,
        strengthLabel = R.string.outline_inner_line
    ),
    IRIDESCENT(
        R.string.outline_style_iridescent,
        OutlineParams(tint = 90, opacity = 75, width = 6, size = 0, strength = 40),
        sizeLabel = null,
        strengthLabel = R.string.outline_hue_spread
    ),
    OUTSET_RING(
        R.string.outline_style_outset_ring,
        OutlineParams(tint = 80, opacity = 70, width = 5, size = 3, strength = 0),
        sizeLabel = R.string.outline_gap,
        strengthLabel = null
    ),
    CORNERS(
        R.string.outline_style_corners,
        OutlineParams(tint = 80, opacity = 90, width = 8, size = 14, strength = 0),
        sizeLabel = R.string.outline_bracket_length,
        strengthLabel = null
    ),
    TINTED_SHADOW(
        R.string.outline_style_tinted_shadow,
        OutlineParams(tint = 90, opacity = 0, width = 4, size = 12, strength = 45),
        sizeLabel = R.string.outline_shadow_size,
        strengthLabel = R.string.outline_shadow_strength
    ),
    NEON(
        R.string.outline_style_neon,
        OutlineParams(tint = 100, opacity = 90, width = 5, size = 8, strength = 40),
        sizeLabel = R.string.outline_glow_size,
        strengthLabel = R.string.outline_glow_strength
    );

    /** Whether anything is drawn outside the shape, which then has to be left room to draw into. */
    val reachesOutside get() = this == SOFT_GLOW || this == OUTSET_RING || this == TINTED_SHADOW || this == NEON

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: HAIRLINE
    }
}

/** What one surface is outlined with. Every style keeps the sliders it was last left at. */
class OutlineProfile(
    var style: OutlineStyle,
    val params: MutableMap<OutlineStyle, OutlineParams>,
    /** Covers only: outline each cover in its own photo's colour rather than the theme's. */
    var photoColor: Boolean
) {
    var current: OutlineParams
        get() = params[style] ?: style.defaults
        set(value) {
            params[style] = value
        }

    fun look(context: Context, photoColor: Boolean = this.photoColor) = OutlineLook(
        style = style,
        params = current,
        neutral = context.getProperTextColor(),
        source = context.getProperPrimaryColor(),
        photoColor = photoColor
    )
}

/**
 * A style made concrete. [source] is the theme's primary colour - the accent on the white and black
 * and white themes - swapped per cover through [withSource] where covers take their photo's colour.
 */
data class OutlineLook(
    val style: OutlineStyle,
    val params: OutlineParams,
    val neutral: Int,
    val source: Int,
    val photoColor: Boolean
) {
    /** The source colour carried off the text colour by the tint and lightened or darkened, opaque. */
    val hue: Int
        get() {
            val tinted = ColorUtils.blendARGB(neutral, source, params.tint / PERCENT.toFloat())
            if (params.lightness == 0) {
                return tinted
            }

            val hsl = FloatArray(HSL_COMPONENTS)
            ColorUtils.colorToHSL(tinted, hsl)
            val towards = if (params.lightness > 0) MAX_LIGHTNESS else MIN_LIGHTNESS
            val amount = abs(params.lightness) / PERCENT.toFloat()
            hsl[2] += (towards - hsl[2]) * amount
            return ColorUtils.HSLToColor(hsl)
        }

    val lineColor get() = ColorUtils.setAlphaComponent(hue, alphaOf(params.opacity))

    val glowColor get() = ColorUtils.setAlphaComponent(hue, alphaOf(params.strength))

    /** Whether the theme is dark, going by its text being light. */
    val isDarkTheme get() = ColorUtils.calculateLuminance(neutral) > DARK_THEME_TEXT_LUMINANCE

    fun withSource(color: Int) = copy(source = color)

    fun widthPx(density: Float) = params.width / QUARTERS_PER_DP * density

    fun sizePx(density: Float) = params.size * density

    /** [color] with this look's colour bled into it, alpha kept, for a frost. Only FROST_BLEED bleeds. */
    fun bleedInto(color: Int): Int {
        if (style != OutlineStyle.FROST_BLEED) {
            return color
        }

        val mixed = ColorUtils.blendARGB(color, hue, params.strength / PERCENT.toFloat() * MAX_BLEED)
        return ColorUtils.setAlphaComponent(mixed, android.graphics.Color.alpha(color))
    }

    /** A wash of this look's colour to lay over a frost that has no colour of its own to mix into. */
    val frostWash: Int
        get() = if (style == OutlineStyle.FROST_BLEED) {
            ColorUtils.setAlphaComponent(hue, alphaOf((params.strength * MAX_BLEED).toInt()))
        } else {
            android.graphics.Color.TRANSPARENT
        }

    private fun alphaOf(percent: Int) = percent.coerceIn(0, PERCENT) * FULL_ALPHA / PERCENT
}

object OutlineSettings {
    private class StoredProfile(val style: String?, val photoColor: Boolean?, val params: Map<String, OutlineParams>?)

    fun pill(context: Context) = read(context.config.outlinePillProfile)

    fun cover(context: Context) = read(context.config.outlineCoverProfile)

    fun save(context: Context, pill: OutlineProfile, cover: OutlineProfile, coversMatchPill: Boolean) {
        context.config.apply {
            outlinePillProfile = write(pill)
            outlineCoverProfile = write(cover)
            outlineCoversMatchPill = coversMatchPill
        }
    }

    fun pillLook(context: Context) = pill(context).look(context)

    /** The covers' look: the pill's own where they are set to match it, in whichever colour covers take. */
    fun coverLook(context: Context): OutlineLook {
        val cover = cover(context)
        val shape = if (context.config.outlineCoversMatchPill) pill(context) else cover
        return shape.look(context, photoColor = cover.photoColor)
    }

    private fun read(json: String): OutlineProfile {
        val stored = runCatching { Gson().fromJson(json, StoredProfile::class.java) }.getOrNull()
        // a style dropped since it was stored is dropped with it
        val params = stored?.params.orEmpty().entries.mapNotNull { (name, params) ->
            OutlineStyle.entries.firstOrNull { it.name == name }?.let { it to params }
        }

        return OutlineProfile(
            style = OutlineStyle.from(stored?.style),
            params = params.toMap(mutableMapOf()),
            photoColor = stored?.photoColor ?: false
        )
    }

    private fun write(profile: OutlineProfile) = Gson().toJson(
        StoredProfile(
            style = profile.style.name,
            photoColor = profile.photoColor,
            params = profile.params.mapKeys { it.key.name }
        )
    )
}
