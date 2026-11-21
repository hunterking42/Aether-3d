package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.ai.AssistantPersona
import com.example.ai.CoreMode
import kotlin.math.abs
import kotlin.math.sin

class FloatingOrbOverlay(
    private val context: Context,
    private val onOrbTapped: () -> Unit,
    private val onStopOverlay: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rootLayout: LinearLayout? = null
    private var orbView: HolographicOrbView? = null
    private var bubbleLayout: LinearLayout? = null
    private var statusTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing || !Settings.canDrawOverlays(context)) return

        val density = context.resources.displayMetrics.density
        val orbSizePx = (62f * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (18f * density).toInt()
            y = (220f * density).toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val orb = HolographicOrbView(context).apply {
            layoutParams = LinearLayout.LayoutParams(orbSizePx, orbSizePx)
        }

        val bubbleBg = GradientDrawable().apply {
            setColor(0xE00C1322.toInt())
            cornerRadius = 18f * density
            setStroke((1f * density).toInt(), 0x6600E5FF)
        }

        val statusTv = TextView(context).apply {
            setTextColor(0xFFF0F6FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxWidth = (210f * density).toInt()
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            text = "Aether Ready"
        }

        val closeBtn = TextView(context).apply {
            setTextColor(0xFF9FB3C8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text = "  ×"
            setPadding((6f * density).toInt(), 0, (4f * density).toInt(), 0)
            setOnClickListener { onStopOverlay() }
        }

        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bubbleBg
            setPadding(
                (12f * density).toInt(),
                (8f * density).toInt(),
                (10f * density).toInt(),
                (8f * density).toInt()
            )
            visibility = View.GONE
            addView(statusTv)
            addView(closeBtn)
            setOnClickListener {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((8f * density).toInt(), 1)
        }

        container.addView(orb)
        container.addView(spacer)
        container.addView(bubble)

        // Draggable touch handler on orb (tap = voice, long-press = open app, drag = move)
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var downTime = 0L

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val heldMs = System.currentTimeMillis() - downTime
                    if (!moved) {
                        if (heldMs > 550L) {
                            context.startActivity(
                                Intent(context, MainActivity::class.java).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            )
                        } else {
                            onOrbTapped()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(container, params)
            rootLayout = container
            orbView = orb
            bubbleLayout = bubble
            statusTextView = statusTv
            layoutParams = params
            isShowing = true
        }
    }

    fun updateState(
        coreMode: CoreMode,
        persona: AssistantPersona,
        message: String,
        showBubble: Boolean
    ) {
        if (!isShowing) return
        mainHandler.post {
            orbView?.setVisualState(coreMode, persona)
            statusTextView?.text = message
            bubbleLayout?.visibility = if (showBubble || coreMode != CoreMode.IDLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            orbView?.stopAnimation()
            rootLayout?.let { runCatching { windowManager.removeView(it) } }
            rootLayout = null
            orbView = null
            bubbleLayout = null
            statusTextView = null
            layoutParams = null
            isShowing = false
        }
    }

    private class HolographicOrbView(context: Context) : View(context) {
        private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.CYAN
        }
        private val portraitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var accentColor: Int = 0xFF00E5FF.toInt()
        private var currentPersona: AssistantPersona = AssistantPersona.JARVIS
        private var cachedBitmapPersona: AssistantPersona? = null
        private var cachedShaderRadius = 0f
        private var animating = false
        private var phase = 0f

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!animating) return
                phase += 0.16f
                invalidate()
                postDelayed(this, 45L)
            }
        }

        fun setVisualState(mode: CoreMode, persona: AssistantPersona) {
            currentPersona = persona
            accentColor = when (mode) {
                CoreMode.ERROR -> 0xFFFF5252.toInt()
                CoreMode.THINKING, CoreMode.OBSERVING_SCREEN -> 0xFFFFB300.toInt()
                CoreMode.MULTITASKING -> 0xFF00E676.toInt()
                else -> when (persona) {
                    AssistantPersona.JARVIS -> 0xFF00E5FF.toInt()
                    AssistantPersona.MAX -> 0xFFFFB300.toInt()
                    AssistantPersona.MAYRA -> 0xFFB388FF.toInt()
                    AssistantPersona.STEALTH -> 0xFF00E676.toInt()
                }
            }
            val shouldAnimate = mode != CoreMode.IDLE
            if (shouldAnimate && !animating) {
                animating = true
                post(frameRunnable)
            } else if (!shouldAnimate && animating) {
                animating = false
                removeCallbacks(frameRunnable)
            }
            invalidate()
        }

        fun stopAnimation() {
            animating = false
            removeCallbacks(frameRunnable)
        }

        private fun ensurePortraitShader(cx: Float, cy: Float, innerRadius: Float) {
            val diameter = (innerRadius * 2f).toInt().coerceAtLeast(16)
            if (cachedBitmapPersona == currentPersona && kotlin.math.abs(cachedShaderRadius - innerRadius) < 1f && portraitPaint.shader != null) {
                return
            }
            val resId = com.example.ui.components.avatarDrawableFor(currentPersona)
            val raw = android.graphics.BitmapFactory.decodeResource(resources, resId) ?: return
            val scaled = android.graphics.Bitmap.createScaledBitmap(raw, diameter, diameter, true)
            if (scaled !== raw) raw.recycle()
            val shader = android.graphics.BitmapShader(
                scaled,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
            val matrix = android.graphics.Matrix().apply {
                setTranslate(cx - innerRadius, cy - innerRadius)
            }
            shader.setLocalMatrix(matrix)
            portraitPaint.shader = shader
            cachedBitmapPersona = currentPersona
            cachedShaderRadius = innerRadius
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val baseR = minOf(width, height) * 0.44f
            val pulse = if (animating) 1f + 0.07f * sin(phase) else 1f

            // Outer glowing ring
            outerRingPaint.color = accentColor
            outerRingPaint.alpha = if (animating) 245 else 200
            canvas.drawCircle(cx, cy, baseR * pulse, outerRingPaint)

            // Realistic persona portrait inside orb
            val innerR = baseR * 0.85f
            ensurePortraitShader(cx, cy, innerR)
            if (portraitPaint.shader != null) {
                canvas.drawCircle(cx, cy, innerR, portraitPaint)
            }
        }
    }
}
