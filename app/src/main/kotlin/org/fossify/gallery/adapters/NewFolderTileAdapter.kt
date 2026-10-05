package org.fossify.gallery.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.FolderCoverStyle
import org.fossify.gallery.helpers.FolderLabelPlacement
import org.fossify.gallery.views.NewFolderOutline

/**
 * The folder picker's last tile, a dashed outline that makes a new folder, put after the folders with a
 * ConcatAdapter. Laid out like the [DirectoryAdapter] tiles beside it - the same view type, cover style,
 * spacing and scroll direction.
 */
class NewFolderTileAdapter(
    context: Context,
    private val textColor: Int,
    private val onClick: () -> Unit,
) : RecyclerView.Adapter<NewFolderTileAdapter.ViewHolder>() {
    private val config = context.config
    private val isListViewType = config.viewTypeFolders == VIEW_TYPE_LIST
    private val coverStyle = FolderCoverStyle.from(config.folderStyle)
    private val scrollHorizontally = config.scrollHorizontally && !isListViewType

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (isListViewType) R.layout.item_new_folder_list else R.layout.item_new_folder_grid
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        val outline = view.findViewById<NewFolderOutline>(R.id.new_folder_outline)
        val label = view.findViewById<TextView>(R.id.new_folder_label)
        outline.setColor(textColor)
        label.setTextColor(textColor)
        if (!isListViewType) {
            dressAsCover(view, outline, label)
        }

        view.setOnClickListener { onClick() }
        return ViewHolder(view)
    }

    // one tile that never changes, dressed once as it is made
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit

    private fun dressAsCover(tile: View, outline: NewFolderOutline, label: TextView) {
        val resources = tile.resources
        val margin = coverStyle.tileMargin(resources, config.folderSpacing)
        (tile.layoutParams as ViewGroup.MarginLayoutParams).setMargins(margin, margin, margin, margin)
        outline.aspectRatio = coverStyle.aspectRatio
        outline.cornerRadius = coverStyle.shapeRadius(resources)
        outline.isHorizontalScrolling = scrollHorizontally

        val labelParams = label.layoutParams as RelativeLayout.LayoutParams
        if (coverStyle.label == FolderLabelPlacement.ON_COVER) {
            // written along the foot of the outline, where the other tiles have their names
            labelParams.removeRule(RelativeLayout.BELOW)
            labelParams.addRule(RelativeLayout.ALIGN_BOTTOM, outline.id)
            val padding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            label.setPadding(padding, padding, padding, padding)
        } else if (scrollHorizontally) {
            // a row's height is fixed when it scrolls sideways, so the label takes its share first
            labelParams.removeRule(RelativeLayout.BELOW)
            labelParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            (outline.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, label.id)
        }
    }
}
