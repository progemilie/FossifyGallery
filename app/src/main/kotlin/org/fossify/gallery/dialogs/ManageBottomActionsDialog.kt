package org.fossify.gallery.dialogs

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.adapters.ManageBottomActionsAdapter
import org.fossify.gallery.databinding.DialogManageBottomActionsBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ALL_BOTTOM_ACTIONS
import org.fossify.gallery.helpers.MAX_VISIBLE_BOTTOM_ACTIONS

private const val DRAGGED_ROW_ELEVATION = 8f

/**
 * Picks which actions the viewer's bottom bar carries and what order it carries them in. Dragging
 * within the visible section is what sets the order; the bar reads it back through
 * [org.fossify.gallery.helpers.applyBottomActionsOrder].
 */
class ManageBottomActionsDialog(val activity: BaseSimpleActivity, val callback: (result: Int) -> Unit) {
    private val binding = DialogManageBottomActionsBinding.inflate(activity.layoutInflater)
    private val touchHelper = ItemTouchHelper(DragCallback())
    private val adapter: ManageBottomActionsAdapter

    init {
        val visibleActions = activity.config.visibleBottomActions
        val byId = ALL_BOTTOM_ACTIONS.associateBy { it.id }
        val visible = activity.config.bottomActionsOrder
            .mapNotNull { byId[it] }
            .filter { visibleActions and it.id != 0 }
            // a config from before the cap existed keeps its first eight; the rest fall to hidden,
            // where the user can see what happened and put back whichever they wanted
            .take(MAX_VISIBLE_BOTTOM_ACTIONS)
            .toMutableList()
        val hidden = ALL_BOTTOM_ACTIONS.filterNot { it in visible }.toMutableList()

        adapter = ManageBottomActionsAdapter(activity, visible, hidden) { touchHelper.startDrag(it) }
        binding.manageBottomActionsList.adapter = adapter
        touchHelper.attachToRecyclerView(binding.manageBottomActionsList)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed() {
        val result = adapter.visible.fold(0) { actions, action -> actions or action.id }
        activity.config.visibleBottomActions = result
        // the hidden ones are saved too, so an action switched back on returns to a known place
        activity.config.bottomActionsOrder = adapter.visible.map { it.id } + adapter.hidden.map { it.id }
        callback(result)
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (adapter.isDraggable(viewHolder.bindingAdapterPosition)) {
                super.getMovementFlags(recyclerView, viewHolder)
            } else {
                0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ) = adapter.onRowMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

        // the section headings and the hidden rows are not places an action can be dropped
        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ) = adapter.isDraggable(target.bindingAdapterPosition)

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.elevation = DRAGGED_ROW_ELEVATION
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.elevation = 0f
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // nothing to do - swiping is off, the checkbox is what shows and hides an action
        }
    }
}
