package own.moderpach.extinguish.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "ExtinguishAccessibilityService"

/**
 * Intercepts hardware volume key events system-wide using
 * [AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS].
 *
 * Unlike [own.moderpach.extinguish.service.hosts.VolumeKeyEventWindowHost], which has to hold a
 * focusable overlay window to receive key events (and therefore can steal window/input focus,
 * interfering with the keyboard/IME or gesture navigation of whatever app is in front), an
 * AccessibilityService with this flag is handed key events directly by the system's input
 * pipeline before they reach any app or window. It never takes focus, never shows any UI, and
 * therefore can't interfere with the IME or nav gestures the way the Window method can.
 *
 * The service itself is a thin, mostly-static bridge: the actual "should I intercept right now"
 * state and the callback to invoke live in the companion object, and are driven by
 * [own.moderpach.extinguish.service.hosts.VolumeKeyEventAccessibilityHost]. This mirrors the
 * shape of the Window/Shell hosts so [own.moderpach.extinguish.service.ExtinguishService] can
 * treat all three listening methods uniformly.
 */
class ExtinguishAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Whether the system has actually connected/bound this service. Reflects real-world
         * state, not just "the user flipped the Settings switch" - the switch can be on while
         * the process backing it hasn't started yet (or has died).
         */
        @JvmStatic
        val isConnected = MutableStateFlow(false)

        // Whether volume keys should currently be consumed. When false, onKeyEvent returns
        // false for volume keys too, so they fall through to the normal system behavior -
        // this is the equivalent of VolumeKeyEventWindowHost detaching its overlay on sleep().
        @Volatile
        private var isAwake = false

        @Volatile
        private var onVolumeKeyEvent: (() -> Unit)? = null

        @JvmStatic
        fun setAwake(awake: Boolean) {
            isAwake = awake
        }

        @JvmStatic
        fun setOnVolumeKeyEvent(listener: (() -> Unit)?) {
            onVolumeKeyEvent = listener
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            serviceInfo = serviceInfo?.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }
        isConnected.value = true
    }

    /**
     * Called by the system for every hardware key event, before any window sees it. Returning
     * `true` consumes the event outright (no volume change, no other app/IME sees it);
     * returning `false` lets it continue through the normal pipeline untouched.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKeyCode = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKeyCode && isAwake) {
            // Consume DOWN (and any repeats) as well as UP so the system never adjusts the
            // volume, and only fire the callback once, on UP.
            if (event.action == KeyEvent.ACTION_UP) {
                onVolumeKeyEvent?.invoke()
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    // This service only cares about key events, not accessibility (content/window) events, so
    // there is nothing to do here - we still have to override it, it's abstract.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        isConnected.value = false
    }
}
