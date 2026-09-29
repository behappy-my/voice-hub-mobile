package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.AudiobookCollection
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `library_items` request with `collapse_collections` returns a mixed list of
 * audiobooks and synthesized collections. An unmapped collection is dropped, and
 * every book inside it with it, since the server stops returning collapsed books
 * individually.
 */
class MediaCollectionMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private fun bookJson(itemId: String, name: String) = """
        {"item_id":"$itemId","provider":"library","name":"$name",
         "media_type":"audiobook","is_playable":true,
         "uri":"library://audiobook/$itemId"}
    """.trimIndent()

    private fun collectionJson(itemId: String, vararg books: String) = """
        {"item_id":"$itemId","provider":"library","name":"Discworld",
         "media_type":"collection","provider_mappings":[],
         "uri":"library://collection/$itemId",
         "items":[${books.joinToString(",")}]}
    """.trimIndent()

    private fun decode(json: String) = myJson.decodeFromString<ServerMediaItem>(json)

    @Test
    fun mapsCollectionWithItsMembers() {
        val item = factory.create(
            decode(collectionJson("audiobook___Discworld", bookJson("1", "Mort"), bookJson("2", "Guards!"))),
        )

        val collection = item as AudiobookCollection
        assertEquals(MediaType.COLLECTION, collection.mediaType)
        assertEquals(MediaType.AUDIOBOOK, collection.itemMediaType)
        assertEquals("Discworld", collection.name)
        assertEquals(listOf("Mort", "Guards!"), collection.items.map(Audiobook::name))
    }

    @Test
    fun keepsCollectionsAndSinglesInOneList() {
        val mixed = factory.createList(
            listOf(
                decode(collectionJson("audiobook___Discworld", bookJson("1", "Mort"))),
                decode(bookJson("9", "Standalone")),
            ),
        )

        assertEquals(2, mixed.size, "A collection must survive the list mapping alongside singles")
        assertTrue(mixed[0] is AudiobookCollection)
        assertTrue(mixed[1] is Audiobook)
    }

    @Test
    fun borrowsCoverFromFirstMember() {
        val bookWithImage = """
            {"item_id":"1","provider":"library","name":"Mort","media_type":"audiobook",
             "image":{"type":"thumb","path":"/mort.jpg","provider":"library"}}
        """.trimIndent()

        val collection = factory.create(
            decode(collectionJson("audiobook___Discworld", bookWithImage)),
        ) as AudiobookCollection

        val borrowed = collection.image(ImageType.THUMB)
        assertEquals("/mort.jpg", borrowed?.path, "A collection has no image of its own")
    }

    @Test
    fun dropsCollectionOfUnsupportedElementType() {
        val item = factory.create(
            decode(
                """
                {"item_id":"album___Greatest","provider":"library","name":"Greatest",
                 "media_type":"collection","items":[]}
                """.trimIndent(),
            ),
        )

        assertNull(item, "The server only resolves audiobook collections")
    }

    @Test
    fun collectionCannotBeFavorited() {
        val collection = factory.create(
            decode(collectionJson("audiobook___Discworld", bookJson("1", "Mort"))),
        ) as AudiobookCollection

        assertNotNull(collection.uri, "Precondition: the server does send a uri")
        assertFalse(collection.canBeFavorited, "There is no library row behind a collection uri")
    }
}
