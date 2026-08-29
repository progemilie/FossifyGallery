package org.fossify.gallery.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintSet
import org.fossify.commons.R as commonsR
import org.fossify.gallery.R
import org.fossify.gallery.databinding.BottomActionsBinding

/**
 * One button of the viewer's bottom bar: the bit it holds in [Config.visibleBottomActions], the row
 * it gets in the manage dialog, and the view it drives in bottom_actions.xml.
 */
data class BottomAction(
    val id: Int,
    @param:IdRes val viewId: Int,
    @param:StringRes val labelId: Int,
    @param:DrawableRes val iconId: Int,
)

/**
 * Every action there is, in the order they ship in.
 *
 * Also the fallback for anything a saved order does not mention, so an action added here later
 * lands in its default spot for everyone rather than being dropped.
 */
val ALL_BOTTOM_ACTIONS = listOf(
    BottomAction(
        BOTTOM_ACTION_TOGGLE_FAVORITE, R.id.bottom_favorite,
        R.string.toggle_favorite, R.drawable.ic_heart_outline_vector
    ),
    BottomAction(
        BOTTOM_ACTION_RATING, R.id.bottom_rating,
        R.string.rating, commonsR.drawable.ic_star_outline_vector
    ),
    BottomAction(
        BOTTOM_ACTION_EDIT, R.id.bottom_edit,
        R.string.edit, commonsR.drawable.ic_edit_vector
    ),
    BottomAction(
        BOTTOM_ACTION_SHARE, R.id.bottom_share,
        commonsR.string.share, commonsR.drawable.ic_share_vector
    ),
    BottomAction(
        BOTTOM_ACTION_DELETE, R.id.bottom_delete,
        commonsR.string.delete, commonsR.drawable.ic_delete_vector
    ),
    BottomAction(
        BOTTOM_ACTION_ROTATE, R.id.bottom_rotate,
        R.string.rotate, R.drawable.ic_rotate_right_vector
    ),
    BottomAction(
        BOTTOM_ACTION_MIRROR, R.id.bottom_mirror,
        R.string.mirror, R.drawable.ic_flip_horizontally_vector
    ),
    BottomAction(
        BOTTOM_ACTION_PROPERTIES, R.id.bottom_properties,
        commonsR.string.properties, commonsR.drawable.ic_info_vector
    ),
    BottomAction(
        BOTTOM_ACTION_CHANGE_ORIENTATION, R.id.bottom_change_orientation,
        R.string.change_orientation, commonsR.drawable.ic_orientation_auto_vector
    ),
    BottomAction(
        BOTTOM_ACTION_SLIDESHOW, R.id.bottom_slideshow,
        R.string.slideshow, R.drawable.ic_slideshow_vector
    ),
    BottomAction(
        BOTTOM_ACTION_SHOW_ON_MAP, R.id.bottom_show_on_map,
        R.string.show_on_map, commonsR.drawable.ic_place_vector
    ),
    BottomAction(
        BOTTOM_ACTION_TOGGLE_VISIBILITY, R.id.bottom_toggle_file_visibility,
        R.string.toggle_file_visibility, commonsR.drawable.ic_hide_vector
    ),
    BottomAction(
        BOTTOM_ACTION_RENAME, R.id.bottom_rename,
        commonsR.string.rename, commonsR.drawable.ic_rename_vector
    ),
    BottomAction(
        BOTTOM_ACTION_SET_AS, R.id.bottom_set_as,
        commonsR.string.set_as, commonsR.drawable.ic_set_as_vector
    ),
    BottomAction(
        BOTTOM_ACTION_COPY, R.id.bottom_copy,
        commonsR.string.copy, commonsR.drawable.ic_copy_vector
    ),
    BottomAction(
        BOTTOM_ACTION_MOVE, R.id.bottom_move,
        commonsR.string.move, commonsR.drawable.ic_move_vector
    ),
    BottomAction(
        BOTTOM_ACTION_RESIZE, R.id.bottom_resize,
        commonsR.string.resize, R.drawable.ic_minimize_vector
    ),
    BottomAction(
        BOTTOM_ACTION_TABS, R.id.bottom_tabs,
        R.string.switch_tab, R.drawable.ic_tabs_vector
    ),
)

/**
 * The saved order as a full list of every known action.
 *
 * Anything the stored string does not name is appended in [ALL_BOTTOM_ACTIONS] order, and anything
 * it names twice or does not recognise is dropped, so the result always describes every action
 * exactly once however old or hand-edited the preference is.
 */
fun parseBottomActionsOrder(stored: String): List<Int> {
    val known = ALL_BOTTOM_ACTIONS.map { it.id }
    val saved = stored.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in known }
        .distinct()

    return saved + known.filterNot { it in saved }
}

fun serializeBottomActionsOrder(order: List<Int>) = order.joinToString(",")

/**
 * Lays the bar's buttons out left to right in [order].
 *
 * Order lives in the horizontal chain rather than in child order: the chain is what spreads the
 * buttons evenly and what skips the ones that are GONE, and simply re-adding the views would leave
 * every constraint pointing at the neighbour it used to have. Every button is chained whatever its
 * visibility, so the buttons that come and go with the current file - rating, rotate - need no
 * re-chaining when they do.
 */
fun BottomActionsBinding.applyBottomActionsOrder(order: List<Int>) {
    val byAction = ALL_BOTTOM_ACTIONS.associateBy { it.id }
    val viewIds = order.mapNotNull { byAction[it]?.viewId }.toIntArray()
    if (viewIds.size < 2) {
        return
    }

    val set = ConstraintSet()
    set.clone(root)
    viewIds.forEach { viewId ->
        // start/end and left/right cannot both be set on one view, and the chain sets start/end
        set.clear(viewId, ConstraintSet.START)
        set.clear(viewId, ConstraintSet.END)
        set.clear(viewId, ConstraintSet.LEFT)
        set.clear(viewId, ConstraintSet.RIGHT)
    }

    set.createHorizontalChainRtl(
        ConstraintSet.PARENT_ID, ConstraintSet.START,
        ConstraintSet.PARENT_ID, ConstraintSet.END,
        viewIds, null, ConstraintSet.CHAIN_SPREAD
    )
    set.applyTo(root)
}
