package own.moderpach.extinguish.service.hosts

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
import androidx.lifecycle.LifecycleOwner
import own.moderpach.extinguish.util.ext.addFlags

private const val TAG = "VolumeKeyEventWindowHost"

class VolumeKeyEventWindowHost(
    private val owner: Context,
    var onKeyEvent: () -> Unit
) {

    val windowManager = owner.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val mLayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.RGBA_8888
        gravity = Gravity.END or Gravity.BOTTOM
        width = 1
        height = 1

        addFlags(FLAG_NOT_TOUCH_MODAL)
        addFlags(FLAG_SPLIT_TOUCH)
    }

    private val listener = View.OnKeyListener { _, keyCode, event ->
        val isVolumeKeyCode = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKeyCode) {
            // Consume DOWN (and any repeats) as well as UP so the system never sees an
            // unhandled volume key event and never adjusts the volume. Previously only UP
            // was consumed (and everything else - i.e. every other key on the device,
            // including ones meant for the keyboard/IME - was consumed by the `true` in the
            // fallback branch below), which was backwards: it let the volume change through
            // and swallowed unrelated key input instead.
            if (event.action == KeyEvent.ACTION_UP) {
                Log.d(TAG, "get key event $keyCode")
                onKeyEvent()
            }
            return@OnKeyListener true
        }
        false
    }

    val mView = View(owner).apply {
        setOnKeyListener(listener)
    }

    fun create() {
        if (!mView.isAttachedToWindow)
            windowManager.addView(mView, mLayoutParams)
    }

    fun sleep() {
        // Actually detach the window rather than just hiding the child view. This window is
        // focusable (it has to be, to receive key events at all), so as long as it stays
        // attached it keeps stealing input/IME focus from whatever app is in the foreground -
        // merely setting the view invisible does not release that focus. Removing it entirely
        // while we don't need to intercept anything stops that interference.
        if (mView.isAttachedToWindow) windowManager.removeView(mView)
    }

    fun wake() {
        if (!mView.isAttachedToWindow) windowManager.addView(mView, mLayoutParams)
    }

    fun destroy() {
        if (mView.isAttachedToWindow)
            windowManager.removeView(mView)
    }
}