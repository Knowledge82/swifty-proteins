package dev.knowledge82.swiftyproteins.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import dev.knowledge82.swiftyproteins.R

/**
 * The picture of one element label ("C", "Cl") for the 3D scene (document 19): the app's console font, white letters
 * with a dark outline (readable on a white hydrogen and on a black background alike), transparent around them.
 * 96 x 64 pixels, 3:2 like the quad it is shown on (AtomLabels.width / height): 24 KB per element.
 */
object LabelPicture {

    private const val WIDTH = 96
    private const val HEIGHT = 64

    fun draw(context: Context, element: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val font = runCatching { ResourcesCompat.getFont(context, R.font.share_tech_mono_regular) }.getOrNull() ?: Typeface.MONOSPACE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = HEIGHT * 0.78f
            textAlign = Paint.Align.CENTER
        }
        // Two letters must fit the width.
        val textWidth = paint.measureText(element)
        if (textWidth > WIDTH * 0.86f) paint.textSize = paint.textSize * WIDTH * 0.86f / textWidth
        val baseline = HEIGHT / 2f - (paint.descent() + paint.ascent()) / 2f
        // The outline first (a thick stroke), then the letters on top.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = HEIGHT * 0.12f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = OUTLINE
        canvas.drawText(element, WIDTH / 2f, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = INK
        canvas.drawText(element, WIDTH / 2f, baseline, paint)
        return bitmap
    }

    private const val INK = 0xFFE6DCC3.toInt()      // SwiftyColors.Bone
    private const val OUTLINE = 0xFF050806.toInt()  // SwiftyColors.Abyss
}
