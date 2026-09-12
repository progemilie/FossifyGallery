package org.fossify.gallery.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import org.fossify.gallery.R
import org.fossify.commons.R as commonsR

/**
 * How a screen's drop-down is laid out: its items gathered into sections, drawn with a dotted rule
 * between them. See [org.fossify.gallery.views.GlassMenu] for the panel that reads one.
 *
 * A spec only says how items are *arranged*. Whether an item is there at all is left to the screen's
 * own menu, so naming something the screen has hidden - because it lives on the toolbar, or on the
 * viewer's bottom bar - simply draws nothing. Anything a spec fails to name is appended to the last
 * shown section rather than dropped, so an action can never go missing by being forgotten here.
 *
 * [hidden] is a further section the drop-down opens without: it is revealed by an arrow the last row
 * of the last shown section wears, along with a rule of its own. Naming the items a screen has least
 * use for keeps the menu short without putting any of them behind a submenu.
 */
class MenuSpec(
    val sections: List<List<MenuEntry>>,
    val hidden: List<MenuEntry> = emptyList(),
)

/** One line of a section: a labelled row, or a row of icons standing in for several items. */
sealed interface MenuEntry {
    data class Row(@param:IdRes val id: Int) : MenuEntry

    data class Icons(val icons: List<MenuIcon>) : MenuEntry
}

/**
 * An item drawn as an icon rather than as a row of its own.
 *
 * The icon is named here rather than taken from the menu item: the menu gives several of these none,
 * and the ones it does give are recoloured in place by the screens that own them.
 */
data class MenuIcon(@param:IdRes val id: Int, @param:DrawableRes val icon: Int)

private fun row(@IdRes id: Int) = MenuEntry.Row(id)

private fun icons(vararg icons: MenuIcon) = MenuEntry.Icons(icons.toList())

/** The folder grid: how the library is shown, then everything else. */
val FOLDER_GRID_MENU = MenuSpec(
    listOf(
        listOf(
            row(R.id.sort),
            row(R.id.filter),
            row(R.id.change_view_type),
            row(R.id.column_count),
        ),
        listOf(
            row(R.id.temporarily_show_hidden),
            row(R.id.stop_showing_hidden),
            row(R.id.temporarily_show_excluded),
            row(R.id.stop_showing_excluded),
            row(R.id.create_new_folder),
            row(R.id.open_recycle_bin),
            row(R.id.settings),
            row(R.id.about),
            row(R.id.more_apps_from_us),
        ),
    )
)

/** The media grid: how this folder is shown and arranged, then everything else. */
val MEDIA_GRID_MENU = MenuSpec(
    listOf(
        listOf(
            row(R.id.sort),
            row(R.id.filter),
            row(R.id.custom_order),
            row(R.id.reset_custom_order),
            row(R.id.change_view_type),
            row(R.id.column_count),
            row(R.id.toggle_filename),
        ),
        listOf(
            row(R.id.temporarily_show_hidden),
            row(R.id.stop_showing_hidden),
            row(R.id.create_new_folder),
            row(R.id.open_recycle_bin),
            row(R.id.empty_recycle_bin),
            row(R.id.empty_disable_recycle_bin),
            row(R.id.restore_all_files),
            row(R.id.slideshow),
            row(R.id.settings),
            row(R.id.about),
        ),
    )
)

/**
 * The viewer: what changes the image, then what is done with the file - and behind the arrow on the
 * last of those, the handful a photo is rarely opened to reach.
 *
 * Only ever one of each pair in an icon row is up at a time, so a row of pairs stays short. The two
 * rotations lead the first row because they are what a crooked photo is opened for; save-as, which
 * exists to commit one, is the first row under them. A half turn is either of them tapped twice.
 */
val VIEWER_MENU = MenuSpec(
    listOf(
        listOf(
            icons(
                MenuIcon(R.id.menu_rotate_left, R.drawable.ic_rotate_left_vector),
                MenuIcon(R.id.menu_rotate_right, R.drawable.ic_rotate_right_vector),
                MenuIcon(R.id.menu_mirror, R.drawable.ic_flip_horizontally_vector),
                MenuIcon(R.id.menu_edit, commonsR.drawable.ic_edit_vector),
                MenuIcon(R.id.menu_resize, R.drawable.ic_minimize_vector),
            ),
            // above save-as: switching tabs is somewhere to go rather than something done to the
            // file, so it competes with none of the file operations under it
            row(R.id.menu_switch_tab),
            row(R.id.menu_save_as),
            row(R.id.menu_rename),
        ),
        listOf(
            icons(
                // what the viewer shows rather than what it does to the file, so it leads the row
                // rather than following the delete that ends it
                MenuIcon(R.id.menu_show_thumbnail_strip, R.drawable.ic_thumbnail_strip_vector),
                MenuIcon(R.id.menu_hide_thumbnail_strip, R.drawable.ic_thumbnail_strip_off_vector),
                MenuIcon(R.id.menu_add_to_favorites, R.drawable.ic_heart_outline_vector),
                MenuIcon(R.id.menu_remove_from_favorites, commonsR.drawable.ic_heart_vector),
                MenuIcon(R.id.menu_hide, commonsR.drawable.ic_hide_vector),
                MenuIcon(R.id.menu_unhide, commonsR.drawable.ic_unhide_vector),
                MenuIcon(R.id.menu_copy_to, commonsR.drawable.ic_copy_vector),
                MenuIcon(R.id.menu_move_to, commonsR.drawable.ic_move_vector),
                MenuIcon(R.id.menu_share, commonsR.drawable.ic_share_vector),
                MenuIcon(R.id.menu_delete, commonsR.drawable.ic_delete_vector),
            ),
            row(R.id.menu_copy_to_clipboard),
            row(R.id.menu_open_with),
            row(R.id.menu_set_as),
            row(R.id.menu_restore_file),
            // last, so the arrow it wears sits at the foot of the menu
            row(R.id.menu_settings),
        ),
    ),
    // change orientation belongs here rather than above: it locks the *screen* rather than turning
    // the photo, and is set once and left alone
    hidden = listOf(
        row(R.id.menu_print),
        row(R.id.menu_show_on_map),
        row(R.id.menu_slideshow),
        row(R.id.menu_create_shortcut),
        row(R.id.menu_change_orientation),
    )
)

/**
 * A selection's actions, less the few the pill along the foot of the screen has already taken as
 * buttons of its own. One column, in the order the action mode's own menu lists them: which of
 * these apply changes with what is selected, so any grouping fixed here would keep coming apart.
 */
val SELECTION_MENU = MenuSpec(listOf(emptyList()))

/**
 * A selection of pictures, laid out the way the viewer's own menu is: what changes the files, then
 * what is done with them, and behind the arrow the handful a selection is rarely made to reach.
 *
 * Picking one picture puts several more items up than picking many - open with, set as, a shortcut
 * - and those go where they fit rather than piling up at the foot, so the menu grows in place
 * instead of changing shape. Several of the icons below are only ever up one of a pair at a time,
 * and the ones the pill has taken as buttons of its own draw nothing at all here.
 */
val SELECTION_MEDIA_MENU = MenuSpec(
    listOf(
        listOf(
            icons(
                MenuIcon(R.id.cab_rotate_left, R.drawable.ic_rotate_left_vector),
                MenuIcon(R.id.cab_rotate_right, R.drawable.ic_rotate_right_vector),
                MenuIcon(R.id.cab_mirror, R.drawable.ic_flip_horizontally_vector),
                MenuIcon(R.id.cab_resize, R.drawable.ic_minimize_vector),
                MenuIcon(R.id.cab_edit, commonsR.drawable.ic_edit_vector),
            ),
            // the one turn with no icon of its own, under the two that have
            row(R.id.cab_rotate_one_eighty),
            row(R.id.cab_rename),
        ),
        listOf(
            icons(
                MenuIcon(R.id.cab_add_to_favorites, R.drawable.ic_heart_outline_vector),
                MenuIcon(R.id.cab_remove_from_favorites, commonsR.drawable.ic_heart_vector),
                MenuIcon(R.id.cab_rate, commonsR.drawable.ic_star_outline_vector),
                MenuIcon(R.id.cab_hide, commonsR.drawable.ic_hide_vector),
                MenuIcon(R.id.cab_unhide, commonsR.drawable.ic_unhide_vector),
                MenuIcon(R.id.cab_share, commonsR.drawable.ic_share_vector),
                MenuIcon(R.id.cab_delete, commonsR.drawable.ic_delete_vector),
            ),
            row(R.id.cab_copy_to),
            row(R.id.cab_move_to),
            row(R.id.cab_properties),
            row(R.id.cab_restore_recycle_bin_files),
        ),
    ),
    hidden = listOf(
        row(R.id.cab_fix_date_taken),
        row(R.id.cab_create_shortcut),
        row(R.id.cab_set_as),
        row(R.id.cab_open_with),
        row(R.id.cab_select_all),
    )
)
