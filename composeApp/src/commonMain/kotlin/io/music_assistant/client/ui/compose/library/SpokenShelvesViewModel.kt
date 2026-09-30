package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ServiceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpokenShelvesViewModel(private val api: ServiceClient) : ViewModel() {
    data class State(
        val query: SpokenQuery = SpokenQuery(),
        val page: SpokenPage? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private var generation = 0
    private val parents = mutableListOf<SpokenQuery>()

    fun start() { if (_state.value.page == null && !_state.value.loading) load() }
    fun refresh() = load()
    fun category(id: String) { parents.clear(); select(SpokenQuery(category = id)) }
    fun kind(kind: String) { parents.clear(); select(_state.value.query.copy(kind = kind, group = "")) }
    fun search(value: String) {
        parents.clear()
        _state.update { it.copy(query = it.query.copy(search = value, group = "")) }
        load(debounce = true)
    }
    fun group(card: SpokenCard) {
        parents.add(_state.value.query)
        select(_state.value.query.copy(group = card.id, search = ""))
    }
    fun back(): Boolean {
        if (parents.isNotEmpty()) { select(parents.removeAt(parents.lastIndex)); return true }
        if (!_state.value.query.isHome) { select(SpokenQuery()); return true }
        return false
    }
    private fun select(query: SpokenQuery) {
        _state.update { it.copy(query = query) }
        load()
    }
    fun more() {
        val s = _state.value
        if (s.loading || s.page?.hasMore != true) return
        load(offset = s.page.offset + 24)
    }
    fun image(card: SpokenCard): String? = card.image?.let {
        api.resolveImageUrl(it.path, it.provider, it.remotelyAccessible, it.proxyId)
    }
    private fun load(offset: Int = 0, debounce: Boolean = false) {
        requestJob?.cancel()
        val requestGeneration = ++generation
        val query = _state.value.query
        val previous = _state.value.page
        _state.update { it.copy(loading = true, error = null, page = if (offset == 0) null else previous) }
        requestJob = viewModelScope.launch {
            try {
                if (debounce) delay(350)
                val answer = api.sendRequest(query.request(offset)).getOrThrow()
                val page = answer.resultAs<SpokenPage>() ?: error("书架响应格式不兼容")
                if (generation == requestGeneration) {
                    _state.update { it.copy(loading = false, page = if (offset > 0 && previous != null) appendSpokenPage(previous, page) else page) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == requestGeneration) _state.update {
                    it.copy(loading = false, error = "暂时无法读取书架，请重试，或使用原始列表。")
                }
            }
        }
    }
}
