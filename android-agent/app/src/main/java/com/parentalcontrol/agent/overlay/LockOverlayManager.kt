package com.parentalcontrol.agent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService

class LockOverlayManager(private val context: Context) {
    private val windowManager by lazy { context.getSystemService<WindowManager>() }
    private var overlayView: View? = null

    fun show(reason: String = "Daily usage limit reached") {
        if (overlayView != null || windowManager == null) {
            return
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#E80B1E36"))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(48, 48, 48, 48)
                    addView(
                        TextView(context).apply {
                            text = "الجهاز مقفل"
                            setTextColor(Color.WHITE)
                            textSize = 28f
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = "Reason: $reason\nUse the admin dashboard to unlock this device."
                            setTextColor(Color.parseColor("#D7E5FF"))
                            textSize = 18f
                            gravity = Gravity.CENTER
                        },
                    )
                },
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(container, params)
        overlayView = container
    }

    fun hide() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
    }
}

