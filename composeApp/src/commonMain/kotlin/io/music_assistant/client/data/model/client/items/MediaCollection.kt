package io.music_assistant.client.data.model.client.items

import androidx.compose.ui.graphics.vector.ImageVector
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

sealed class MediaCollection : AppMediaItem() {
    abstract val items: List<AppMediaItem>

    // The server encodes the collected items' type in the item id, not in a field.
    abstract val itemMediaType: MediaType

    abstract val defaultIcon: ImageVector

    final override val mediaType: MediaType = MediaType.COLLECTION
    override val providerMappings: List<ProviderMapping>? = null
    override val metadata: Metadata? = null
    override val favorite: Boolean? = null

    companion object {
        private const val ITEM_ID_SEPARATOR = "___"

        fun itemMediaTypeOf(itemId: String): MediaType? =
            MediaType.fromServer(itemId.substringBefore(ITEM_ID_SEPARATOR, missingDelimiterValue = ""))
    }
}
