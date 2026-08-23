package org.fossify.gallery.helpers

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import org.fossify.gallery.R

/**
 * The swap between the folder grid and the all media grid, which are two activities rather than two
 * pages of one screen: the one being left slides off the way the pill was tapped and the one
 * arriving comes in behind it.
 *
 * The platform's own transition rather than anything of ours. It is the only thing that can cover
 * the gap between the tap and the arriving screen's first frame - the media grid takes a good half
 * second to inflate and query - and it is what the toolbar buttons the pill replaced always used.
 */
fun Activity.startNavSwap(intent: Intent, fromLeft: Boolean) {
    val options = ActivityOptions.makeCustomAnimation(
        this,
        if (fromLeft) R.anim.nav_slide_in_left else R.anim.nav_slide_in_right,
        if (fromLeft) R.anim.nav_slide_out_right else R.anim.nav_slide_out_left,
    )

    startActivity(intent, options.toBundle())
    finish()
}
