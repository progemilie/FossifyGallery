package org.fossify.gallery.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * The folder picker's Other folder button, put at the very end of the grid with a ConcatAdapter. It
 * takes a whole row of its own, or a whole column where the grid scrolls sideways - the span is the
 * picker's to hand out, see [org.fossify.gallery.dialogs.FolderPickerScreen].
 */
class OtherFolderAdapter(
    context: Context,
    private val textColor: Int,
    private val onClick: () -> Unit,
) : RecyclerView.Adapter<OtherFolderAdapter.ViewHolder>() {
    private val config = context.config
    private val scrollHorizontally = config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_other_folder, parent, false)
        if (scrollHorizontally) {
            view.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        view.findViewById<TextView>(R.id.other_folder).apply {
            setTextColor(textColor)
            setOnClickListener { onClick() }
        }

        return ViewHolder(view)
    }

    // one button that never changes, dressed once as it is made
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit
}
