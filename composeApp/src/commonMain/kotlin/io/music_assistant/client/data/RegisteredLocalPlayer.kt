package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.Player

/** Visibility is a UI preference, not evidence that a Sendspin connection is gone. */
internal fun registeredLocalPlayer(players: List<Player>, localId: String): Player? =
    players.find { it.id == localId && it.isAvailable }
