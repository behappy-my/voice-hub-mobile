package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SpokenShelvesTest {
    @Test
    fun stableSchema65ResponseKeepsSectionsArtworkAndNativeIds() {
        val page = myJson.decodeFromString<SpokenPage>(FIXTURE)
        assertEquals(3194, page.libraryCount)
        assertEquals(858, page.workCount)
        assertEquals(listOf("children", "literature", "history", "commentary"), page.sections.map { it.id })
        assertEquals("16", page.items.first().id)
        assertEquals("library", page.items.first().provider)
        assertEquals("book", page.items.first().kind)
        assertNotNull(page.items.first().image?.proxyId)
        assertFalse(page.items.first().image!!.remotelyAccessible)
        assertEquals(10, page.categories.size)
    }

    @Test
    fun pagingDoesNotConfusePodcastAndBookWithSameLibraryId() {
        val book = SpokenCard("16", kind = "book", name = "围城")
        val podcast = SpokenCard("16", kind = "podcast", name = "童话")
        val group = SpokenCard("edition:三国演义", kind = "group", name = "三国演义")
        val merged = appendSpokenPage(SpokenPage(items = listOf(book)),
            SpokenPage(items = listOf(book, podcast, group), offset = 24, hasMore = true))
        assertEquals(listOf(book, podcast, group), merged.items)
        assertEquals(24, merged.offset)
        assertTrue(merged.hasMore)
    }

    @Test
    fun selectingCategoryDiscardsLateHomeResponseAndGroupBackRestoresFilter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = object : StubServiceClient() {
                override suspend fun sendRequest(request: Request): Result<Answer> {
                    val category = request.args!!["category"]!!.jsonPrimitive.content
                    if (category.isEmpty()) withContext(NonCancellable) { delay(1_000) }
                    val page = SpokenPage(title = category, items = listOf(SpokenCard("1", kind = "book", name = category)))
                    return Result.success(Answer(buildJsonObject { put("result", myJson.encodeToJsonElement(page)) }))
                }
            }
            val vm = SpokenShelvesViewModel(api)
            vm.start(); runCurrent()
            vm.category("literature"); runCurrent()
            assertEquals("literature", vm.state.value.page?.title)
            advanceTimeBy(1_000); runCurrent()
            assertEquals("literature", vm.state.value.page?.title, "late home reply must not overwrite category")
            vm.group(SpokenCard("collection:名著", kind = "group", name = "名著")); runCurrent()
            assertEquals("collection:名著", vm.state.value.query.group)
            assertTrue(vm.back()); runCurrent()
            assertEquals("literature", vm.state.value.query.category)
            assertEquals("", vm.state.value.query.group)
            assertTrue(vm.back()); runCurrent()
            advanceTimeBy(1_000); runCurrent()
            assertTrue(vm.state.value.query.isHome)
            assertFalse(vm.back())
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun readFailureIsExplicitAndOffersRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail = true
            val api = object : StubServiceClient() {
                override suspend fun sendRequest(request: Request): Result<Answer> =
                    if (fail) Result.failure(IllegalStateException("offline"))
                    else Result.success(Answer(buildJsonObject { put("result", buildJsonObject {}) }))
            }
            val vm = SpokenShelvesViewModel(api)
            vm.start(); runCurrent()
            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.page)
            fail = false; vm.refresh(); runCurrent()
            assertNull(vm.state.value.error)
            assertNotNull(vm.state.value.page)
        } finally { Dispatchers.resetMain() }
    }

    companion object {
        // Recorded NAS schema-65 response; unknown metadata stays forwards compatible.
        private val FIXTURE = """{"categories": [{"id": "children", "name": "儿童故事", "subtitle": "童话、寓言与睡前故事", "icon": "children", "count": 37}, {"id": "literature", "name": "文学小说", "subtitle": "在声音里，读一本好书", "icon": "book", "count": 566}, {"id": "history", "name": "历史人文", "subtitle": "往事、人物与时代", "icon": "history", "count": 18}, {"id": "mystery", "name": "悬疑推理", "subtitle": "谜题与环环相扣的故事", "icon": "mystery", "count": 27}, {"id": "scifi", "name": "科幻想象", "subtitle": "走向更远的世界", "icon": "space", "count": 20}, {"id": "science", "name": "科普知识", "subtitle": "听懂世界的另一面", "icon": "science", "count": 17}, {"id": "english", "name": "英语听读", "subtitle": "故事与语言一起听", "icon": "language", "count": 5}, {"id": "storytelling", "name": "评书曲艺", "subtitle": "一张嘴，说尽人间事", "icon": "mic", "count": 213}, {"id": "commentary", "name": "精读讲书", "subtitle": "听解读，发现下一本书", "icon": "notes", "count": 11}, {"id": "life", "name": "知识生活", "subtitle": "日常、成长与实用知识", "icon": "coffee", "count": 57}], "library_count": 3194, "work_count": 858, "items": [{"id": "16", "provider": "library", "kind": "book", "name": "围城", "version": "", "authors": ["钱锺书"], "narrators": [], "description": "同源简介", "image": {"type": "thumb", "path": "douban-cover:https%3A%2F%2Fimg3.doubanio.com%2Fview%2Fsubject%2Fl%2Fpublic%2Fs11276847.jpg", "provider": "voicehub_spoken", "remotely_accessible": false, "proxy_id": "ce09971c53e528e88229c4b7c741f165a142478357ef8790ff0fde1bb71db5c1"}, "categories": ["literature"], "count": 1, "content_kind": "有声书", "chapter_count": 0, "duration": 0, "resume_position_ms": 0, "fully_played": false, "quality": 8}], "total": 858, "offset": 0, "has_more": true, "continue_listening": [{"id": "798", "provider": "library", "kind": "book", "name": "得到每天听本书2019全年 · 10月", "version": "", "authors": [], "narrators": [], "description": "同源简介", "image": null, "categories": ["commentary"], "count": 1, "content_kind": "精读讲书", "chapter_count": 31, "duration": 48619, "resume_position_ms": 20000, "fully_played": false, "quality": 0}, {"id": "129", "provider": "library", "kind": "book", "name": "新岳飞传", "version": "刘兰芳", "authors": [], "narrators": ["刘兰芳"], "description": "同源简介", "image": null, "categories": ["storytelling"], "count": 1, "content_kind": "有声书", "chapter_count": 0, "duration": 0, "resume_position_ms": 3131000, "fully_played": false, "quality": 0}, {"id": "112", "provider": "library", "kind": "book", "name": "哪吒闹海", "version": "袁阔成", "authors": [], "narrators": ["袁阔成"], "description": "同源简介", "image": null, "categories": ["storytelling"], "count": 1, "content_kind": "有声书", "chapter_count": 6, "duration": 10327, "resume_position_ms": 3460000, "fully_played": false, "quality": 0}], "sections": [{"id": "children", "name": "儿童故事", "items": [{"id": "17", "provider": "library", "kind": "podcast", "name": "lipstar的儿童科普智慧故事", "version": "", "authors": [], "narrators": [], "description": "同源简介", "image": {"type": "thumb", "path": "https://fdfs.xmcdn.com/group4/M09/43/DB/wKgDs1QqyYzjZLyuAA03Kndzkak473.jpg", "provider": "itunes_podcasts", "remotely_accessible": true, "proxy_id": "1db0c2b0dbabc786bbc1def50a30eca4d97aaaa728932a1e7d01fdff24c9ba74"}, "categories": ["children", "science"], "count": 1, "content_kind": "播客节目", "chapter_count": 0, "duration": 0, "resume_position_ms": 0, "fully_played": false, "quality": 7}]}, {"id": "literature", "name": "文学小说", "items": [{"id": "16", "provider": "library", "kind": "book", "name": "围城", "version": "", "authors": ["钱锺书"], "narrators": [], "description": "同源简介", "image": {"type": "thumb", "path": "douban-cover:https%3A%2F%2Fimg3.doubanio.com%2Fview%2Fsubject%2Fl%2Fpublic%2Fs11276847.jpg", "provider": "voicehub_spoken", "remotely_accessible": false, "proxy_id": "ce09971c53e528e88229c4b7c741f165a142478357ef8790ff0fde1bb71db5c1"}, "categories": ["literature"], "count": 1, "content_kind": "有声书", "chapter_count": 0, "duration": 0, "resume_position_ms": 0, "fully_played": false, "quality": 8}]}, {"id": "history", "name": "历史人文", "items": [{"id": "3", "provider": "library", "kind": "podcast", "name": "上下五千年——畅游5000年中国历史|睡前故事", "version": "", "authors": [], "narrators": [], "description": "同源简介", "image": {"type": "thumb", "path": "https://fdfs.xmcdn.com/storages/b1fa-audiofreehighqps/19/3E/GKwRIJIJrHYkAASMFAKrs-co.jpeg", "provider": "itunes_podcasts", "remotely_accessible": true, "proxy_id": "9a63319ea19927d74dc1ae6c79004a4f7207938e677ecd430bc9255d67ae4d5a"}, "categories": ["children", "history"], "count": 1, "content_kind": "播客节目", "chapter_count": 0, "duration": 0, "resume_position_ms": 0, "fully_played": false, "quality": 7}]}, {"id": "commentary", "name": "精读讲书", "items": [{"id": "collection:世界名著精读100本合集   每天二十分钟读一本名著   收藏慢慢看", "provider": "library", "kind": "group", "name": "世界名著精读100本合集   每天二十分钟读一本名著   收藏慢慢看", "version": "", "authors": [], "narrators": [], "description": "同源简介", "image": {"type": "thumb", "path": "https://books.google.com/books/content?id=PysOoAEACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api", "provider": "url", "remotely_accessible": true, "proxy_id": "02af08eb7fac837547bdb1a4e484e1406f106c7a9151309b0eaeb6033c0f94ab"}, "categories": ["commentary"], "count": 93, "content_kind": "精读讲书", "chapter_count": 0, "duration": 0, "resume_position_ms": 0, "fully_played": false, "quality": 6, "group_type": "合集"}]}]}"""
    }
}
