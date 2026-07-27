package ir.promptall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.promptall.app.PromptAllApplication
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedState(
    val items: List<PromptDto> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 0,
    val query: String = "",
    val favoriteIds: Set<Long> = emptySet(),
)

class PromptViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PromptAllApplication
    private val dao = app.database.favorites()
    var state = androidx.compose.runtime.mutableStateOf(FeedState())
        private set
    val favorites = dao.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            favorites.collect { saved ->
                state.value = state.value.copy(favoriteIds = saved.map { it.id }.toSet())
            }
        }
        refresh()
    }

    fun refresh() = load(reset = true)
    fun loadMore() {
        val s = state.value
        if (!s.loading && !s.loadingMore && s.hasMore) load(reset = false)
    }

    fun setQuery(value: String) {
        state.value = state.value.copy(query = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(reset = true)
        }
    }

    private fun load(reset: Boolean) = viewModelScope.launch {
        val old = state.value
        val nextPage = if (reset) 1 else old.page + 1
        state.value = old.copy(
            loading = reset,
            loadingMore = !reset,
            error = null,
            items = if (reset) emptyList() else old.items,
        )
        runCatching {
            app.api.prompts(nextPage, search = state.value.query.trim().ifBlank { null })
        }.onSuccess { page ->
            val current = state.value
            state.value = current.copy(
                items = if (reset) page.items else current.items + page.items,
                loading = false,
                loadingMore = false,
                page = page.page,
                hasMore = page.hasMore,
            )
        }.onFailure {
            state.value = state.value.copy(
                loading = false,
                loadingMore = false,
                error = "دریافت پرامپت‌ها انجام نشد. اتصال اینترنت را بررسی کنید.",
            )
        }
    }

    fun toggleFavorite(item: PromptDto) = viewModelScope.launch {
        if (dao.contains(item.id)) dao.remove(item.id)
        else dao.save(
            Favorite(
                id = item.id,
                title = item.title,
                promptText = item.promptText,
                imageUrl = item.image.url,
                imageWidth = item.image.width,
                imageHeight = item.image.height,
            )
        )
    }
}
