package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * A button of the viewer's bottom action bar. Up in the landscape layout's top row there may not be
 * room for all of the buttons the viewer shows, and the ones squeezed out are kept away here rather
 * than by the viewer: it goes on saying which buttons apply to the file on screen, the row says which
 * of those fit, and neither undoes the other. See BottomActionsPlacement.
 */
class BottomActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** The visibility the viewer asked for, whether or not there is room for it. */
    var wantedVisibility = visibility
        private set

    /** Whether the top row has no room for this one, which keeps it away whatever the viewer asks. */
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
