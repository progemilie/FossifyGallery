package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * A bottom action button the landscape top row can squeeze out without touching the visibility the
 * viewer sets, so neither undoes the other. See BottomActionsPlacement.
 */
class BottomActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var wantedVisibility = visibility
        private set

    /** Keeps the button GONE whatever visibility the viewer sets. */
    var isSqueezedOut = false
        set(value) {
            field = value
            super.setVisibility(if (value) GONE else wantedVisibility)
        }

    override fun setVisibility(visibility: Int) {
        wantedVisibility = visibility
        super.setVisibility(if (isSqueezedOut) GONE else visibility)
    }
}
