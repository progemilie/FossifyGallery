package org.fossify.gallery.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ItemBottomActionBinding
import org.fossify.gallery.databinding.ItemBottomActionSectionBinding
import org.fossify.gallery.helpers.ALL_BOTTOM_ACTIONS
import org.fossify.gallery.helpers.BottomAction

private const val VIEW_TYPE_SECTION = 0
private const val VIEW_TYPE_ACTION = 1

/**
 * The list behind the manage-bottom-actions dialog.
 *
 * A "Visible" section holds the bar's actions in the order the bar draws them, and a "Hidden"
 * section holds the rest. Only the visible half is draggable: the hidden half is always kept in the
 * order the actions ship in, so no arrangement of it is ever the user's to lose.
 */
class ManageBottomActionsAdapter(
    private val context: Context,
    val visible: MutableList<BottomAction>,
    val hidden: MutableList<BottomAction>,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class Section(@param:StringRes val titleId: Int) : Row
        data class Action(val action: BottomAction, val isVisible: Boolean) : Row
    }

    private val textColor = context.getProperTextColor()
    private val primaryColor = context.getProperPrimaryColor()
    private var rows = buildRows()

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Section -> VIEW_TYPE_SECTION
        is Row.Action -> VIEW_TYPE_ACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SECTION) {
            SectionHolder(ItemBottomActionSectionBinding.inflate(inflater, parent, false))
        } else {
            ActionHolder(ItemBottomActionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Section -> (holder as SectionHolder).bind(row)
            is Row.Action -> (holder as ActionHolder).bind(row)
        }
    }

    /** Whether the row at [position] is one of the visible actions, the only rows a drag may touch. */
    fun isDraggable(position: Int) = (rows.getOrNull(position) as? Row.Action)?.isVisible == true

    fun onRowMoved(fromPosition: Int, toPosition: Int): Boolean {
        val from = visibleIndexOf(fromPosition) ?: return false
        val to = visibleIndexOf(toPosition) ?: return false
        visible.add(to, visible.removeAt(from))
        rows = buildRows()
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    private fun visibleIndexOf(position: Int) = (rows.getOrNull(position) as? Row.Action)
        ?.takeIf { it.isVisible }
        ?.let { visible.indexOf(it.action) }
        ?.takeIf { it != -1 }

    // both sections move as a block, and there are sixteen rows in all - working out which ones
    // actually changed would cost more than rebinding them
    @SuppressLint("NotifyDataSetChanged")
    private fun toggle(row: Row.Action) {
        if (row.isVisible) {
            visible.remove(row.action)
            val landsBefore = hidden.indexOfFirst { defaultIndexOf(it) > defaultIndexOf(row.action) }
            hidden.add(if (landsBefore == -1) hidden.size else landsBefore, row.action)
        } else {
            hidden.remove(row.action)
            // last, where it is next to the section heading you just tapped away from
            visible.add(row.action)
        }

        rows = buildRows()
        notifyDataSetChanged()
    }

    private fun defaultIndexOf(action: BottomAction) = ALL_BOTTOM_ACTIONS.indexOfFirst { it.id == action.id }

    private fun buildRows(): List<Row> {
        val built = mutableListOf<Row>(Row.Section(R.string.bottom_actions_visible))
        visible.mapTo(built) { Row.Action(it, isVisible = true) }
        if (hidden.isNotEmpty()) {
            built.add(Row.Section(R.string.bottom_actions_hidden))
            hidden.mapTo(built) { Row.Action(it, isVisible = false) }
        }

        return built
    }

    private inner class SectionHolder(
        private val binding: ItemBottomActionSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row.Section) {
            binding.bottomActionSectionTitle.setTextColor(primaryColor)
            binding.bottomActionSectionTitle.text = context.getString(row.titleId)
        }
    }

    private inner class ActionHolder(
        private val binding: ItemBottomActionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        // the handle is a shortcut for the long press that starts a drag anyway, and the row itself
        // stays a plain clickable - nothing here is reachable only by touch
        @SuppressLint("ClickableViewAccessibility")
        fun bind(row: Row.Action) {
            context.updateTextColors(binding.root)
            binding.bottomActionLabel.text = context.getString(row.action.labelId)
            binding.bottomActionIcon.setImageResource(row.action.iconId)
            binding.bottomActionIcon.applyColorFilter(textColor)
            binding.bottomActionCheckbox.isChecked = row.isVisible

            binding.bottomActionDragHandle.applyColorFilter(textColor)
            binding.bottomActionDragHandle.beInvisibleIf(!row.isVisible)
            binding.bottomActionDragHandle.setOnTouchListener { _, event ->
                if (row.isVisible && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    startDrag(this)
                }

                false
            }

            binding.root.setOnClickListener { toggle(row) }
        }
    }
}
