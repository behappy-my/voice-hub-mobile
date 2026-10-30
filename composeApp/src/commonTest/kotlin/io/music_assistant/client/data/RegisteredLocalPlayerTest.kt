package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerDataFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegisteredLocalPlayerTest {
    @Test
    fun hiddenPhoneCanResumeAfterServerRefresh() {
        val phone = PlayerDataFixtures.playerData().player.copy(id = "phone", isListed = false, isAvailable = true)
        assertEquals(phone, registeredLocalPlayer(listOf(phone), "phone"))
    }

    @Test
    fun listedButDisconnectedPhoneIsNotReady() {
        val phone = PlayerDataFixtures.playerData().player.copy(id = "phone", isListed = true, isAvailable = false)
        assertNull(registeredLocalPlayer(listOf(phone), "phone"))
    }

    @Test
    fun AnotherAvailableSpeakerDoesNotMakePhoneReady() {
        val other = PlayerDataFixtures.playerData().player.copy(id = "other", isAvailable = true)
        assertNull(registeredLocalPlayer(listOf(other), "phone"))
    }
}
