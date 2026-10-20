package io.music_assistant.client.services

import android.media.AudioDeviceInfo

/** SCO is a call channel; its disappearance is not the loss of the music route. */
internal fun shouldPauseAfterRouteRemoval(removed: Set<Int>, remaining: Set<Int>): Boolean =
    removed.any(::isExternalMediaRoute) && remaining.none(::isExternalMediaRoute)

internal fun isExternalMediaRoute(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    -> true
    else -> false
}
