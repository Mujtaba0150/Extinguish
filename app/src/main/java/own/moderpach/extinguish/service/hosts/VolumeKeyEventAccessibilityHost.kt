package own.moderpach.extinguish.service.hosts

import android.util.Log
import own.moderpach.extinguish.service.ExtinguishAccessibilityService

private const val TAG = "VolumeKeyEventAccessibilityHost"

/**
 * Drives [ExtinguishAccessibilityService], which does the actual key-event interception. The
 * service is a system-managed singleton (Android creates/destroys it based on the user's
 * Accessibility settings, not on our command), so this host doesn't own a window or any other
 * resource of its own - it just registers the callback and awake/asleep state that the service
 * reads.
 *
 * Because the underlying interception happens via an AccessibilityService rather than a
 * focusable overlay, this method - unlike [VolumeKeyEventWindowHost] - never takes window/input
 * focus, so it can't interfere with the keyboard, IME, or gesture navigation.
 */
class VolumeKeyEventAccessibilityHost(
    var onKeyEvent: () -> Unit
) {

    private val listener: () -> Unit = { onKeyEvent() }

    fun create() {
        ExtinguishAccessibilityService.setOnVolumeKeyEvent(listener)
    }

    fun sleep() {
        Log.d(TAG, "sleep: ")
        ExtinguishAccessibilityService.setAwake(false)
    }

    fun wake() {
        Log.d(TAG, "wake: ")
        ExtinguishAccessibilityService.setAwake(true)
    }

    fun destroy() {
        ExtinguishAccessibilityService.setAwake(false)
        ExtinguishAccessibilityService.setOnVolumeKeyEvent(null)
    }
}
