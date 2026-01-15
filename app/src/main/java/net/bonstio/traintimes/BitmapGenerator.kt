package net.bonstio.traintimes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat

object BitmapGenerator {

    fun textAsBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        textColor: Int,
        fontResId: Int? = null,
        maxWidth: Int? = null,
        maxLines: Int = 1,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Bitmap? {
        if (text.isEmpty()) return null

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            context.resources.displayMetrics
        )
        paint.color = textColor
        
        if (fontResId != null) {
            try {
                val typeface = ResourcesCompat.getFont(context, fontResId)
                paint.typeface = typeface
            } catch (e: Exception) {
                paint.typeface = Typeface.DEFAULT
            }
        }

        // Calculate width
        val desiredWidth = paint.measureText(text).toInt()
        val width = if (maxWidth != null) Math.min(desiredWidth, maxWidth) else desiredWidth
        
        if (width <= 0) return null

        // Create Layout
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)

        val layout = builder.build()
        val height = layout.height

        if (height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layout.draw(canvas)

        return bitmap
    }

    fun generateFadeGradient(context: Context, color: Int, isTop: Boolean): Bitmap? {
        val width = 1
        val height = (24 * context.resources.displayMetrics.density).toInt() // 24dp
        
        if (height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)
        
        val transparent = Color.argb(0, r, g, b)
        val solid = Color.argb(a, r, g, b)
        
        val startColor = if (isTop) solid else transparent
        val endColor = if (isTop) transparent else solid
        
        val paint = Paint()
        val shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}
