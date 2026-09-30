package org.fossify.gallery.helpers

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.children
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.updateTextColors
import org.fossify.gallery.databinding.SettingsSearchResultBinding
import org.fossify.gallery.views.SettingsGroup
import org.fossify.gallery.views.SettingsPage
import java.util.Locale

/** Between a page's title and its heading, under a setting a search has found. */
private const val RESULT_PATH_SEPARATOR = " › "

/** How much of the text colour the field's magnifier keeps. */
private const val ICON_ALPHA = 0.7f

/**
 * Finds a setting by what it is called, on whichever page it is - the way a phone's own settings let
 * one be found without knowing where it lives. While anything is typed into the field at the top of
 * the first page, the links give way to every setting whose title holds it, each under the page and
 * heading it sits on, and picking one opens that page and points the setting out.
 *
 * What is searched is read off the rows as they stand when the text changes, so a row its setup has
 * hidden, or retitled, is found or not found as it currently is.
 */
class SettingsSearch(
    private val holder: ViewGroup,
    private val field: EditText,
    private val clear: ImageView,
    /** What the findings stand in for while there is anything typed. */
    private val links: View,
    private val results: SettingsGroup,
    private val empty: TextView,
    private val pages: SettingsPages,
) {
    private val context = field.context
    private var textColor = field.currentTextColor

    private class Found(val row: View, val page: SettingsPage, val heading: CharSequence)

    init {
        field.doAfterTextChanged { show() }
        field.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }

        clear.setOnClickListener { field.text = null }
    }

    /** Empties the field, answering whether there was anything in it - back's first job on this page. */
    fun clear(): Boolean {
        if (field.text.isNullOrEmpty()) {
            return false
        }

        field.text = null
        hideKeyboard()
        return true
    }

    /** Takes the theme's colours onto the field, and redraws whatever it has found in it. */
    fun updateColors(cardColor: Int, textColor: Int) {
        this.textColor = textColor
        holder.background = GradientDrawable().apply {
            cornerRadius = holder.layoutParams.height / 2f
            setColor(cardColor)
        }

        context.updateTextColors(holder)
        empty.setTextColor(textColor)
        holder.findViewById<ImageView>(org.fossify.gallery.R.id.settings_search_icon)
            .applyColorFilter(textColor.adjustAlpha(ICON_ALPHA))
        clear.applyColorFilter(textColor)
        show()
    }

    private fun show() {
        val query = field.text?.toString().orEmpty().normalized()
        val found = if (query.isEmpty()) emptyList() else find(query)
        clear.isVisible = query.isNotEmpty()
        links.isVisible = query.isEmpty()
        empty.isVisible = query.isNotEmpty() && found.isEmpty()
        results.isVisible = found.isNotEmpty()
        results.settings.removeAllViews()
        found.forEach { results.settings.addView(rowFor(it)) }
    }

    private fun find(query: String) = pages.all.flatMap { page ->
        page.groups.filter { it.isVisible }.flatMap { group ->
            group.settings.children
                .filter { it.isVisible && titleOf(it).toString().normalized().contains(query) }
                .map { Found(it, page, group.title) }
                .toList()
        }
    }

    private fun rowFor(found: Found): View {
        val binding = SettingsSearchResultBinding.inflate(LayoutInflater.from(context), results.settings, false)
        binding.settingsResultTitle.text = titleOf(found.row)
        binding.settingsResultTitle.setTextColor(textColor)
        binding.settingsResultPath.text = listOf(found.page.title, found.heading)
            .filter { it.isNotEmpty() }
            .joinToString(RESULT_PATH_SEPARATOR)
        binding.settingsResultPath.setTextColor(textColor)

        // a drawable of its own: one drawable shared between two views keeps only the last one's bounds
        val icon = ((found.row as? ViewGroup)?.getChildAt(0) as? ImageView)?.drawable
        binding.settingsResultIcon.setImageDrawable(icon?.constantState?.newDrawable()?.mutate())
        binding.settingsResultIcon.setColorFilter(found.page.iconColor)

        binding.root.setOnClickListener {
            hideKeyboard()
            pages.show(found.page, reveal = found.row)
        }

        return binding.root
    }

    /** A setting's title: the first text it shows, which a switch row's switch carries itself. */
    private fun titleOf(row: View): CharSequence =
        (row as? ViewGroup)?.descendants?.filterIsInstance<TextView>()?.firstOrNull()?.text ?: ""

    private fun String.normalized() = trim().normalizeString().lowercase(Locale.getDefault())

    private fun hideKeyboard() {
        field.clearFocus()
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(field.windowToken, 0)
    }
}
