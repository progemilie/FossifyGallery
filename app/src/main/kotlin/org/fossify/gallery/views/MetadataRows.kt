package org.fossify.gallery.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.gallery.databinding.ItemMetadataRowBinding
import org.fossify.gallery.databinding.ItemMetadataSectionBinding
import org.fossify.gallery.models.MetadataSection
import org.fossify.gallery.models.MetadataTag
import java.text.NumberFormat

/**
 * Turns a file's tags into the rows and collapsible sections a [MetadataSheet] is filled with. Kept
 * apart from the sheet, which is about the panel's behaviour rather than about what is printed in
 * it - and a file with a fat XMP packet has hundreds of these.
 *
 * A section builds its rows the first time it is opened, so nothing below the fold is paid for
 * unless someone looks.
 */
class MetadataRows(
    private val context: Context,
    private val textColor: Int,
    private val labelColor: Int,
    private val primaryColor: Int,
    private val onLocationClicked: (path: String) -> Unit,
    private val onDescriptionClicked: (path: String) -> Unit = {},
) {
    fun buildRow(parent: ViewGroup, tag: MetadataTag, path: String): View {
        val row = ItemMetadataRowBinding.inflate(LayoutInflater.from(context), parent, false)
        row.metadataRowLabel.setTextColor(labelColor)
        row.metadataRowLabel.text = tag.name
        row.metadataRowValue.text = tag.value

        // the two rows that answer a tap say so by wearing the accent colour, the editable one with
        // a pencil beside it as well - there is nothing else on the sheet to compare it against
        when {
            tag.opensMap -> {
                row.metadataRowValue.setTextColor(primaryColor)
                row.root.setOnClickListener { onLocationClicked(path) }
            }

            tag.editable -> {
                row.metadataRowValue.setTextColor(primaryColor)
                row.metadataRowEdit.setColorFilter(primaryColor)
                row.metadataRowEdit.beVisible()
                row.root.setOnClickListener { onDescriptionClicked(path) }
            }

            else -> row.metadataRowValue.setTextColor(textColor)
        }

        row.root.setOnLongClickListener {
            context.copyToClipboard("${tag.name}: ${tag.value}")
            true
        }

        return row.root
    }

    fun buildSection(parent: ViewGroup, section: MetadataSection, path: String): View {
        val view = ItemMetadataSectionBinding.inflate(LayoutInflater.from(context), parent, false)
        view.metadataSectionTitle.setTextColor(primaryColor)
        view.metadataSectionTitle.text = section.name
        view.metadataSectionCount.setTextColor(labelColor)
        view.metadataSectionCount.text = NumberFormat.getIntegerInstance().format(section.tags.size)
        view.metadataSectionChevron.setColorFilter(labelColor)
        view.metadataSectionBody.beGone()

        view.metadataSectionHeader.setOnClickListener {
            val expanding = !view.metadataSectionBody.isVisible
            if (expanding && view.metadataSectionBody.isEmpty()) {
                section.tags.forEach { tag ->
                    view.metadataSectionBody.addView(buildRow(view.metadataSectionBody, tag, path))
                }
            }

            view.metadataSectionBody.beVisibleIf(expanding)
            view.metadataSectionChevron.setImageResource(
                if (expanding) {
                    org.fossify.commons.R.drawable.ic_chevron_up_vector
                } else {
                    org.fossify.commons.R.drawable.ic_chevron_down_vector
                }
            )
            view.metadataSectionChevron.setColorFilter(labelColor)
        }

        return view.root
    }
}
