package pl.mateuszteteruk.camerasamples

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.widget.FrameLayout

class AreaCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val areaPaint: Paint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }
    private var currentX = 200F
    private var currentY = 200F
    private val mTutorialColor = Color.parseColor("#D20E0F02")

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun update(x: Float, y: Float) {
        currentX = x
        currentY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(mTutorialColor)
        if (currentX >= 0 && currentY >= 0) {
            canvas.drawCircle(currentX, currentY, RADIUS, areaPaint)
        }
    }

    companion object {

        private const val RADIUS = 200f
    }
}
