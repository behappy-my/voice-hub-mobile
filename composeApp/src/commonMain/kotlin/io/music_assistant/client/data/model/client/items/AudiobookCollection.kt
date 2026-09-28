package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.common.icons.BookshelfIcon

data class AudiobookCollection(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val sortName: String? = null,
    override val uri: String?,
    override val items: List<Audiobook>,
) : MediaCollection() {
    override val itemMediaType: MediaType = MediaType.AUDIOBOOK
    override val defaultIcon = BookshelfIcon

    // A collection has no image of its own; borrow the first member's cover.
    override val images: Map<ImageType, ImageInfo> = items.firstOrNull()?.images.orEmpty()
}
