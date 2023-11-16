package com.johannes.llgplanv2.ui

/*
 *  Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

class TileDrawable(drawable: Drawable) : Drawable() {

    private val paint: Paint
    private val radius = 15.0F
    private val verticalPadding = 5F
    private val tileMode = Shader.TileMode.REPEAT

    init {
        paint = Paint().apply {
            shader = BitmapShader(getBitmap(drawable), tileMode, tileMode)
        }
    }

    override fun draw(canvas: Canvas) {
        // experimental
        val clipPath = Path()
        val rect = RectF(0F, verticalPadding,
            canvas.width.toFloat(),
            canvas.height.toFloat()-verticalPadding)
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)


        canvas.drawPaint(paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    private fun getBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        // experimental

        drawable.draw(c)
        return bmp
    }

}