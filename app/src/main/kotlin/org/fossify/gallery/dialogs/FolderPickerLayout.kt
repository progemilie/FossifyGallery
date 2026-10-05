package org.fossify.gallery.dialogs

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * the title, and what a search finding nothing says - is carried along with the grid's first row, so
 * it starts on the page and scrolls away above the screen with the tiles.
 */
class FolderPickerLayout(private val binding: DialogDirectoryPickerBinding) {
    private val topBarPadding = binding.directoriesTopBar.paddingTop

    // the bars and the header change height with the insets, the keyboard, and a search finding nothing
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
        listOf(binding.directoriesTopBar, binding.directoriesHeader, binding.folderSearchView).forEach {
            it.addOnLayoutChangeListener(heightWatcher)
        }

        // every frame rather than on scroll, as a new list or a jump to the top moves the first row too
        binding.directoriesGrid.viewTreeObserver.addOnPreDrawListener {
            followGrid()
            true
        }
    }

    private fun applyInsets(insets: Insets) = with(binding) {
        root.setPadding(insets.left, 0, insets.right, 0)
        directoriesTopBar.updatePadding(top = topBarPadding + insets.top)
        folderSearchView.updatePadding(bottom = insets.bottom)
    }

    // the header's own height is read before its padding moves, which is what it is measured with
    private fun keepGridClear() = with(binding) {
        val barHeight = directoriesTopBar.height
        val headerContent = directoriesHeader.height - directoriesHeader.paddingTop
        directoriesHeader.updatePadding(top = barHeight)
        directoriesGrid.updatePadding(top = barHeight + headerContent, bottom = folderSearchView.height)
    }

    /**
     * Keeps the header just above the grid's first row, and out of sight once that row is scrolled far
     * enough to be recycled. A grid scrolling sideways has no first row above it, and leaves it be.
     */
    private fun followGrid() = with(binding) {
        val layoutManager = directoriesGrid.layoutManager as LinearLayoutManager
        val firstTile = layoutManager.findViewByPosition(0)
        directoriesHeader.translationY = when {
            layoutManager.orientation == RecyclerView.HORIZONTAL || layoutManager.itemCount == 0 -> 0f
            firstTile == null -> -directoriesHeader.height.toFloat()
            else -> {
                val rowTop = layoutManager.getDecoratedTop(firstTile) - firstTile.marginTop
                (rowTop - directoriesGrid.paddingTop).toFloat()
            }
        }
    }
}
