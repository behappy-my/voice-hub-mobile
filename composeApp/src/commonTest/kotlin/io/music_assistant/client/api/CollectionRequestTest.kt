package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wire shape of `music/audiobooks/get_collection` against the server signature
 * `MediaControllerBase.get_collection(item_id)`, which takes the synthesized collection id
 * and no provider argument.
 */
class CollectionRequestTest {
    @Test
    fun getCollectionCarriesOnlyTheItemId() {
        val request = Request.Audiobook.getCollection("audiobook___Discworld")

        assertEquals("music/audiobooks/get_collection", request.command)
        assertEquals(JsonPrimitive("audiobook___Discworld"), request.args?.get("item_id"))
        assertEquals(setOf("item_id"), request.args?.keys)
    }

    @Test
    fun `audiobook library listing carries the collapse flag`() {
        assertEquals(
            JsonPrimitive(true),
            Request.Audiobook.listLibrary(collapseCollections = true).args?.get("collapse_collections"),
        )
        assertEquals(
            JsonPrimitive(false),
            Request.Audiobook.listLibrary().args?.get("collapse_collections"),
        )
    }
}
