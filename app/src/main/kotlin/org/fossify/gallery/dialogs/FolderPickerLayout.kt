package org.fossify.gallery.dialogs

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.applyEdgeFade

private val PADDED_INSETS = WindowInsetsCompat.Type.systemBars() or
    WindowInsetsCompat.Type.displayCutout() or
    WindowInsetsCompat.Type.ime()

/**
 * Lays the folder picker out the way the browsing grids are: the grid edge to edge under fades, the
 * pills floating over it clear of the system bars, and the grid padded clear of the pills. The header -
 * the title, and what a search finding nothing says - is carried along with the grid's first row, and
 * Other folder with its last, so both scroll with the tiles.
 */
class FolderPickerLayout(private val binding: DialogDirectoryPickerBinding) {
    private val topBarPadding = binding.directoriesTopBar.paddingTop

    // the search pill is watched by its FloatingTopBar, see FolderPickerScreen
    private val heightWatcher = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        if (bottom - top != oldBottom - oldTop) {
            keepGridClear()
        }
    }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyInsets(insets.getInsets(PADDED_INSETS))
            WindowInsetsCompat.CONSUMED
        }

        binding.directoriesTopFade.applyEdgeFade(atTop = true)
        binding.directoriesBottomFade.applyEdgeFade(atTop = false)
        listOf(binding.directoriesTopBar, binding.directoriesHeader, binding.directoriesOtherFolder).forEach {
            it.addOnLayoutChangeListener(heightWatcher)
        }

        // every frame rather than on scroll, as a new list or a jump to the top moves the rows too
        binding.directoriesGrid.viewTreeObserver.addOnPreDrawListener {
            followGrid()
            true
        }
    }

    // the header's own height is read before its padding moves, which is what it is measured with
    fun keepGridClear() = with(binding) {
        val barHeight = directoriesTopBar.height
        val headerContent = directoriesHeader.height - directoriesHeader.paddingTop
        val otherFolderRoom = with(directoriesOtherFolder) {
            if (isVisible) marginTop + height + marginBottom else 0
        }

        // a grid at its top stays there as the bar grows - as it does when the status bar comes back
        // with a turn to portrait - rather than keeping its first row where it was, under the header
        val wasAtTop = directoriesGrid.layoutManager?.canScrollVertically() == true &&
            !directoriesGrid.canScrollVertically(-1)
        directoriesHeader.updatePadding(top = barHeight)
        directoriesGrid.updatePadding(
            top = barHeight + headerContent,
            bottom = folderSearchView.height + otherFolderRoom
        )

        if (wasAtTop) {
            directoriesGrid.scrollToPosition(0)
        }
    }

    private fun applyInsets(insets: Insets) = with(binding) {
        root.setPadding(insets.left, 0, insets.right, 0)
        directoriesTopBar.updatePadding(top = topBarPadding + insets.top)
        folderSearchView.updatePadding(bottom = insets.bottom)
    }

    /**
     * Keeps the header just above the grid's first row and Other folder just below its last, each out
     * of sight once its row is scrolled far enough to be recycled. A grid scrolling sideways has no
     * rows to follow, so the header stays at the top and Other folder above the search pill.
     */
    private fun followGrid() = with(binding) {
        val layoutManager = directoriesGrid.layoutManager as LinearLayoutManager
        val isSideways = layoutManager.orientation == RecyclerView.HORIZONTAL
        val itemCount = layoutManager.itemCount
        val firstTile = layoutManager.findViewByPosition(0)
        directoriesHeader.translationY = when {
            isSideways || itemCount == 0 -> 0f
            firstTile == null -> -directoriesHeader.height.toFloat()
            else -> {
                val rowTop = layoutManager.getDecoratedTop(firstTile) - firstTile.marginTop
                (rowTop - directoriesGrid.paddingTop).toFloat()
            }
        }

        val lastTile = layoutManager.findViewByPosition(itemCount - 1)
        directoriesOtherFolder.translationY = when {
            // kept out of sight until the folders arrive, rather than shown under the title first
            directoriesGrid.adapter == null -> directoriesGrid.height
            isSideways -> directoriesGrid.height - directoriesGrid.paddingBottom
            itemCount == 0 -> directoriesGrid.paddingTop
            lastTile == null -> directoriesGrid.height
            else -> layoutManager.getDecoratedBottom(lastTile) + lastTile.marginBottom
        }.toFloat()
    }
}
