package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * The extended details under the viewer's filename: the fields packed onto as few lines as they
 * fit on, a dot between them.
 *
 * Where the lines break is worked out here rather than left to the TextView, because the only
 * place a TextView is able to break this text is at the spaces around a dot - which strands the
 * dot at the end of one line or the start of the next, pointing at nothing. Breaking it here puts
 * a dot only ever between two fields sharing a line.
 */
class DetailsFlowText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private companion object {
        const val SEPARATOR = "  ·  "
    }

    private var fields = emptyList<String>()

    /** The width the text was last packed for, so re-measuring at that width repacks nothing. */
    private var packedFor = -1

    fun setFields(fields: List<String>) {
        if (fields == this.fields) {
            return
        }

        this.fields = fields
        packedFor = -1
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        if (available != packedFor) {
            packedFor = available
            // before super, so this same pass measures the height the packed lines need
            text = packIntoLines(available)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun packIntoLines(available: Int): String {
        if (fields.isEmpty() || available <= 0) {
            return fields.joinToString(SEPARATOR)
        }

        val separatorWidth = paint.measureText(SEPARATOR)
        val packed = StringBuilder()
        var lineWidth = 0f
        fields.forEachIndexed { index, field ->
            val fieldWidth = paint.measureText(field)
            when {
                index == 0 -> Unit
                lineWidth + separatorWidth + fieldWidth <= available -> {
                    packed.append(SEPARATOR)
                    lineWidth += separatorWidth
                }

                else -> {
                    packed.append('\n')
                    lineWidth = 0f
                }
            }

            packed.append(field)
            // a field too wide to fit on a line of its own wraps inside itself, and where that
            // leaves it is not worth working out - the next field starts a fresh line
            lineWidth = if (fieldWidth > available) available.toFloat() else lineWidth + fieldWidth
        }

        return packed.toString()
    }
}
