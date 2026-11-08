package com.padmapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot

/**
 * Listens to gamepad buttons and turns them into taps / swipes on the screen.
 * Also shows a small "edit panel" on top of the game so you can assign buttons.
 */
class PadService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PadService? = null
    }

    private val wm: WindowManager
        get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val handler = Handler(Looper.getMainLooper())

    // The game (app) that is currently in front.
    private var currentPkg = ""

    // Edit mode state.
    private var editing = false
    private var waitingKey = false
    private var pendingKey = -1

    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var status: TextView? = null
    private var addButton: Button? = null
    private var capture: FrameLayout? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopEdit()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg != currentPkg) {
            currentPkg = pkg
            if (editing && !waitingKey && capture == null) refreshStatus()
        }
    }

    // ------------------------------------------------------------ key handling

    private fun isPad(e: KeyEvent): Boolean =
        KeyEvent.isGamepadButton(e.keyCode) ||
            (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isPad(event)) return false
        val down = event.action == KeyEvent.ACTION_DOWN

        if (editing) {
            if (waitingKey) {
                if (down && event.repeatCount == 0) {
                    pendingKey = event.keyCode
                    waitingKey = false
                    showCapture()
                }
                return true // swallow, so the game does not react while assigning
            }
            return false
        }

        if (!Prefs.enabled(this) || currentPkg.isEmpty()) return false
        val m = Prefs.load(this, currentPkg).firstOrNull { it.keyCode == event.keyCode }
            ?: return false
        if (down && event.repeatCount == 0) perform(m)
        return true
    }

    private fun perform(m: Mapping) {
        val path = Path()
        path.moveTo(m.x, m.y)
        if (m.swipe) path.lineTo(m.x2, m.y2)
        val duration = if (m.swipe) 350L else 60L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---------------------------------------------------------------- edit mode

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    fun startEdit() {
        if (editing) return
        editing = true
        waitingKey = false
        pendingKey = -1

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.argb(215, 20, 20, 20))
        box.setPadding(dp(10), dp(6), dp(10), dp(6))

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f

        val add = Button(this)
        add.text = "+ Add"
        add.textSize = 12f
        add.setOnClickListener { onAddClicked() }

        val clear = Button(this)
        clear.text = "Clear game"
        clear.textSize = 12f
        clear.setOnClickListener {
            if (currentPkg.isNotEmpty()) Prefs.clear(this, currentPkg)
            refreshStatus()
        }

        val done = Button(this)
        done.text = "Done"
        done.textSize = 12f
        done.setOnClickListener { stopEdit() }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(add)
        row.addView(clear)
        row.addView(done)

        box.addView(tv)
        box.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(8)

        try {
            wm.addView(box, lp)
        } catch (e: Exception) {
            editing = false
            toast("Could not show the edit panel: ${e.message}")
            return
        }

        panel = box
        panelParams = lp
        status = tv
        addButton = add
        refreshStatus()
    }

    fun stopEdit() {
        editing = false
        waitingKey = false
        pendingKey = -1
        removeCapture()
        panel?.let {
            try {
                wm.removeView(it)
            } catch (ignored: Exception) {
            }
        }
        panel = null
        panelParams = null
        status = null
        addButton = null
    }

    private fun refreshStatus() {
        val n = if (currentPkg.isEmpty()) 0 else Prefs.load(this, currentPkg).size
        status?.text = if (currentPkg.isEmpty()) {
            "Open your game first"
        } else {
            "Game: $currentPkg\n$n button(s) mapped"
        }
    }

    private fun onAddClicked() {
        if (waitingKey || capture != null) {
            cancelStep()
            return
        }
        if (currentPkg.isEmpty()) {
            toast("Open your game first, then press + Add")
            return
        }
        waitingKey = true
        addButton?.text = "Cancel"
        status?.text = "Press a controller button now..."
    }

    private fun cancelStep() {
        waitingKey = false
        pendingKey = -1
        removeCapture()
        addButton?.text = "+ Add"
        refreshStatus()
    }

    private fun showCapture() {
        status?.text = "Touch (or drag) where ${Prefs.keyName(pendingKey)} should act"

        val layer = FrameLayout(this)
        layer.setBackgroundColor(Color.argb(90, 0, 0, 0))

        var sx = 0f
        var sy = 0f
        layer.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val ex = e.rawX
                    val ey = e.rawY
                    handler.post { finishCapture(sx, sy, ex, ey) }
                }
                MotionEvent.ACTION_CANCEL -> handler.post { cancelStep() }
            }
            true
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            wm.addView(layer, lp)
        } catch (e: Exception) {
            toast("Could not show the touch layer: ${e.message}")
            cancelStep()
            return
        }
        capture = layer
        addButton?.text = "Cancel"
        bringPanelToFront()
    }

    /** The touch layer covers the screen, so re-add the panel above it (keeps Cancel tappable). */
    private fun bringPanelToFront() {
        val p = panel ?: return
        val lp = panelParams ?: return
        try {
            wm.removeView(p)
            wm.addView(p, lp)
        } catch (ignored: Exception) {
        }
    }

    private fun finishCapture(x1: Float, y1: Float, x2: Float, y2: Float) {
        val key = pendingKey
        if (key < 0 || currentPkg.isEmpty()) {
            cancelStep()
            return
        }
        val swipe = hypot(x2 - x1, y2 - y1) > dp(24)
        val list = Prefs.load(this, currentPkg).filter { it.keyCode != key }.toMutableList()
        list.add(Mapping(key, x1, y1, x2, y2, swipe))
        Prefs.save(this, currentPkg, list)

        removeCapture()
        waitingKey = false
        pendingKey = -1
        addButton?.text = "+ Add"
        status?.text = "Saved: ${Prefs.keyName(key)} -> ${if (swipe) "swipe" else "tap"}"
        handler.postDelayed({ if (editing && capture == null && !waitingKey) refreshStatus() }, 1800L)
    }

    private fun removeCapture() {
        capture?.let {
            try {
                wm.removeView(it)
            } catch (ignored: Exception) {
            }
        }
        capture = null
    }
}
