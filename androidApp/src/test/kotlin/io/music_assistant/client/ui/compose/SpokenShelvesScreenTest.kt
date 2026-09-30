package io.music_assistant.client.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.ui.compose.library.*
import io.music_assistant.client.data.model.client.MediaType
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SpokenShelvesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun categoryGroupAndNativeBookNavigation() {
        val api = FakeServiceClient()
        val category = SpokenCategory("literature", "文学小说", count = 2)
        val group = SpokenCard("edition:三国演义", kind = "group", name = "三国演义", count = 2, description = "两个录音版本")
        val book = SpokenCard("1564", kind = "book", name = "三国演义", version = "第一版")
        api.spokenShelvesHandler = { request ->
            when {
                request.args!!["group"]!!.jsonPrimitive.content.isNotEmpty() -> SpokenPage(categories = listOf(category), items = listOf(book), title = "三国演义", total = 1)
                request.args!!["category"]!!.jsonPrimitive.content.isNotEmpty() -> SpokenPage(categories = listOf(category), items = listOf(group), total = 1)
                else -> SpokenPage(categories = listOf(category), sections = listOf(SpokenSection("literature", "文学小说", listOf(group))))
            }
        }
        var opened: Triple<String, MediaType, String>? = null
        val vm = SpokenShelvesViewModel(api)
        compose.setContent {
            MaterialTheme {
                SpokenShelvesScreen(vm, PaddingValues(0.dp), {}, {},
                    { id, mediaType, provider -> opened = Triple(id, mediaType, provider) })
            }
        }
        compose.onNodeWithText("📖 文学小说 2").performClick()
        compose.onNodeWithText("2 个分卷 / 版本").assertIsDisplayed()
        compose.onNodeWithText("三国演义").performClick()
        compose.onNodeWithText("第一版").performClick()
        compose.runOnIdle { assertEquals(Triple("1564", MediaType.AUDIOBOOK, "library"), opened) }
    }

    @Test
    fun unsupportedServerKeepsExplicitOriginalListEscape() {
        var originalList = false
        val vm = SpokenShelvesViewModel(FakeServiceClient())
        compose.setContent { MaterialTheme {
            SpokenShelvesScreen(vm, PaddingValues(0.dp), {}, { originalList = true }, { _, _, _ -> })
        } }
        compose.onNodeWithText("暂时无法读取书架，请重试，或使用原始列表。").assertIsDisplayed()
        compose.onNodeWithText("列表").performClick()
        compose.runOnIdle { assertEquals(true, originalList) }
    }
}
