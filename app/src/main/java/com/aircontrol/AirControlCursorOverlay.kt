package com.aircontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

object AirControlCursorOverlay {

    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context) {
        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) return@post
            if (cursorView != null) return@post

            windowManager = context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

            val density = context.resources.displayMetrics.density
            val size = (28 * density).toInt()

            val cursor = object : View(context) {

                private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.FILL
                }

                private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3 * density
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)

                    val center = size / 2f
                    canvas.drawCircle(center, center, 9 * density, fill)
                    canvas.drawCircle(center, center, 9 * density, border)
                }
            }

            val overlayType = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams = WindowManager.LayoutParams(
                size,
                size,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                windowManager?.addView(cursor, layoutParams)
                cursorView = cursor
                params = layoutParams
            } catch (e: Exception) {
                cursorView = null
                params = null
            }
        }
    }

    fun updatePosition(context: Context, x: Float, y: Float) {
        mainHandler.post {
            val wm = windowManager ?: return@post
            val view = cursorView ?: return@post
            val layout = params ?: return@post

            val density = context.resources.displayMetrics.density
            val size = (28 * density).toInt()

            val width = context.resources.displayMetrics.widthPixels
            val height = context.resources.displayMetrics.heightPixels

            layout.x = (x * width - size / 2)
                .toInt().coerceIn(0, width - size)

            layout.y = (y * height - size / 2)
                .toInt().coerceIn(0, height - size)

            try {
                wm.updateViewLayout(view, layout)
            } catch (_: Exception) {
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                cursorView?.let {
                    windowManager?.removeView(it)
                }
            } catch (_: Exception) {
            }

            cursorView = null
            params = null
            windowManager = null
        }
    }
}
