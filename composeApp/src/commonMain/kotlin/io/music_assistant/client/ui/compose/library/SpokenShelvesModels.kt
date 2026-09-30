package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.api.Request
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class SpokenImage(
    val path: String,
    val provider: String,
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean = false,
    @SerialName("proxy_id") val proxyId: String? = null,
)

@Serializable
data class SpokenCard(
    val id: String,
    val provider: String = "library",
    val kind: String,
    val name: String,
    val version: String = "",
    val authors: List<String> = emptyList(),
    val description: String = "",
    val image: SpokenImage? = null,
    val count: Int = 1,
    @SerialName("content_kind") val contentKind: String = "",
    @SerialName("resume_position_ms") val resumePositionMs: Long = 0,
) {
    val identity: String get() = "$kind:$provider:$id"
}

@Serializable
data class SpokenCategory(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val icon: String = "book",
    val count: Int = 0,
)

@Serializable
data class SpokenSection(val id: String, val name: String, val items: List<SpokenCard> = emptyList())

@Serializable
data class SpokenPage(
    val categories: List<SpokenCategory> = emptyList(),
    val items: List<SpokenCard> = emptyList(),
    val sections: List<SpokenSection> = emptyList(),
    @SerialName("continue_listening") val continueListening: List<SpokenCard> = emptyList(),
    @SerialName("library_count") val libraryCount: Int = 0,
    @SerialName("work_count") val workCount: Int = 0,
    val total: Int = 0,
    val offset: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
    val title: String = "",
)

data class SpokenQuery(
    val category: String = "",
    val group: String = "",
    val search: String = "",
    val kind: String = "",
) {
    val isHome: Boolean get() = category.isEmpty() && group.isEmpty() && search.isEmpty() && kind.isEmpty()
    fun request(offset: Int = 0) = Request("voicehub/spoken/shelves", buildJsonObject {
        put("category", JsonPrimitive(category))
        put("group", JsonPrimitive(group))
        put("search", JsonPrimitive(search))
        put("kind", JsonPrimitive(kind))
        put("offset", JsonPrimitive(offset))
        put("limit", JsonPrimitive(24))
    })
}

internal fun appendSpokenPage(previous: SpokenPage, next: SpokenPage): SpokenPage =
    next.copy(items = (previous.items + next.items).distinctBy { it.identity })
