package io.music_assistant.client.services

import android.media.AudioDeviceInfo.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalMediaRouteTest {
    @Test fun callChannelRemovalCannotPauseMusic() {
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_SCO), setOf(TYPE_BLUETOOTH_A2DP)))
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_SCO), emptySet()))
    }
    @Test fun replacementMusicRoutePreventsFalsePause() {
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_A2DP), setOf(TYPE_BLUETOOTH_A2DP)))
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_A2DP), setOf(TYPE_USB_DEVICE)))
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_A2DP), setOf(TYPE_BLE_HEADSET)))
    }
    @Test fun realUnplugStillPauses() {
        assertTrue(shouldPauseAfterRouteRemoval(setOf(TYPE_BLUETOOTH_A2DP), setOf(TYPE_BUILTIN_SPEAKER)))
        assertTrue(shouldPauseAfterRouteRemoval(setOf(TYPE_WIRED_HEADPHONES), emptySet()))
        assertTrue(shouldPauseAfterRouteRemoval(setOf(TYPE_USB_HEADSET), emptySet()))
        assertTrue(shouldPauseAfterRouteRemoval(setOf(TYPE_BLE_SPEAKER), emptySet()))
    }
    @Test fun builtinRouteChangeIsNotAnUnplug() {
        assertFalse(shouldPauseAfterRouteRemoval(setOf(TYPE_BUILTIN_SPEAKER), emptySet()))
    }
}
