package ro.mehene.app.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import ro.mehene.app.R

class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val ratio: Float

    init {
        val values = context.obtainStyledAttributes(attrs, R.styleable.RatioFrameLayout)
        val width = values.getFloat(R.styleable.RatioFrameLayout_ratioWidth, 16f)
        val height = values.getFloat(R.styleable.RatioFrameLayout_ratioHeight, 9f)
        values.recycle()
        ratio = if (width > 0f && height > 0f) width / height else 16f / 9f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / ratio).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
