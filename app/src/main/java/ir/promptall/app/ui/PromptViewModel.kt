package ir.promptall.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.promptall.app.PromptAllApplication
import ir.promptall.app.data.local.CachedPrompt
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptCategory
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.data.remote.PromptPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedState(
    val items: List<PromptDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 0,
)

data class PromptUiState(
    val home: FeedState = FeedState(),
    val search: FeedState = FeedState(hasMore = false),
    val query: String = "",
    val favoriteIds: Set<Long> = emptySet(),
    val newPromptCount: Int = 0,
    val categories: List<PromptCategory> = emptyList(),
    val categoriesLoading: Boolean = false,
    val selectedCategory: String? = null,
)

class PromptViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PromptAllApplication
    private val favoriteDao = app.database.favorites()
    private val cacheDao = app.database.promptCache()

    var state = mutableStateOf(PromptUiState())
        private set

    val favorites = favoriteDao.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private var homeJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var searchRequestJob: Job? = null
    private var pendingFirstPage: PromptPage? = null
    private var lastNewPromptCheck = 0L

    init {
        viewModelScope.launch {
            favorites.collect { saved ->
                state.value = state.value.copy(favoriteIds = saved.map { it.id }.toSet())
            }
        }
        loadCategories()
        loadCachedHome()
    }

    private fun loadCategories() = viewModelScope.launch {
        state.value = state.value.copy(categoriesLoading = true)
        runCatching { app.api.categories() }
            .onSuccess { response ->
                state.value = state.value.copy(
                    categories = response.items,
                    categoriesLoading = false,
                )
            }
            .onFailure {
                state.value = state.value.copy(categoriesLoading = false)
            }
    }

    fun selectCategory(slug: String?) {
        if (state.value.selectedCategory == slug) return
        pendingFirstPage = null
        state.value = state.value.copy(
            selectedCategory = slug,
            newPromptCount = 0,
            home = FeedState(),
        )
        loadHome(reset = true, userInitiated = false)
    }

    private fun loadCachedHome() = viewModelScope.launch {
        val cached = cacheDao.getAll().map { it.toPrompt() }
        if (state.value.selectedCategory != null) return@launch
        if (cached.isEmpty()) {
            loadHome(reset = true, userInitiated = false)
        } else {
            val cachedPage = ((cached.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
            state.value = state.value.copy(
                home = state.value.home.copy(
                    items = cached,
                    loading = false,
                    page = cachedPage,
                    hasMore = true,
                )
            )
            checkForNewPrompts(force = true)
        }
    }

    fun refreshHome() {
        pendingFirstPage = null
        state.value = state.value.copy(newPromptCount = 0)
        loadHome(reset = true, userInitiated = true)
    }

    fun loadMoreHome() {
        val home = state.value.home
        if (!home.loading && !home.refreshing && !home.loadingMore && home.hasMore) {
            loadHome(reset = false, userInitiated = false)
        }
    }

    private fun loadHome(reset: Boolean, userInitiated: Boolean) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val old = state.value.home
            val nextPage = if (reset) 1 else old.page + 1
            state.value = state.value.copy(
                home = old.copy(
                    loading = reset && old.items.isEmpty(),
                    refreshing = reset && userInitiated && old.items.isNotEmpty(),
                    loadingMore = !reset,
                    error = null,
                )
            )

            val categoryAtRequest = state.value.selectedCategory
            runCatching { app.api.prompts(nextPage, category = categoryAtRequest) }
                .onSuccess { page ->
                    if (state.value.selectedCategory != categoryAtRequest) return@onSuccess
                    val current = state.value.home
                    val merged = if (reset) {
                        page.items
                    } else {
                        (current.items + page.items).distinctBy(PromptDto::id)
                    }
                    state.value = state.value.copy(
                        home = current.copy(
                            items = merged,
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            page = page.page,
                            hasMore = page.hasMore,
                            error = null,
                        )
                    )
                    if (categoryAtRequest == null) cacheHomeItems(merged)
                }
                .onFailure {
                    val current = state.value.home
                    state.value = state.value.copy(
                        home = current.copy(
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            error = if (current.items.isEmpty()) {
                                "دریافت پرامپت‌ها انجام نشد. اتصال اینترنت را بررسی کنید."
                            } else {
                                null
                            },
                        )
                    )
                }
        }
    }

    fun checkForNewPrompts(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNewPromptCheck < NEW_PROMPT_CHECK_INTERVAL_MS) return
        if (state.value.home.items.isEmpty() || state.value.home.refreshing) return
        lastNewPromptCheck = now

        viewModelScope.launch {
            val categoryAtRequest = state.value.selectedCategory
            runCatching { app.api.prompts(page = 1, category = categoryAtRequest) }
                .onSuccess { page ->
                    if (state.value.selectedCategory != categoryAtRequest) return@onSuccess
                    val visibleIds = state.value.home.items.mapTo(hashSetOf(), PromptDto::id)
                    val newItems = page.items.takeWhile { it.id !in visibleIds }
                    if (newItems.isNotEmpty()) {
                        pendingFirstPage = page
                        state.value = state.value.copy(newPromptCount = newItems.size)
                    } else if (state.value.home.page == 1) {
                        if (categoryAtRequest == null) cacheHomeItems(state.value.home.items)
                    }
                }
        }
    }

    fun showNewPrompts() {
        val page = pendingFirstPage ?: return
        val current = state.value.home
        val merged = (page.items + current.items).distinctBy(PromptDto::id)
        pendingFirstPage = null
        state.value = state.value.copy(
            home = current.copy(
                items = merged,
                page = maxOf(1, current.page),
                hasMore = page.hasMore || current.hasMore,
            ),
            newPromptCount = 0,
        )
        if (state.value.selectedCategory == null) cacheHomeItems(merged)
    }

    fun setQuery(value: String) {
        state.value = state.value.copy(query = value)
        searchDebounceJob?.cancel()
        searchRequestJob?.cancel()

        if (value.isBlank()) {
            state.value = state.value.copy(search = FeedState(hasMore = false))
            return
        }

        searchDebounceJob = viewModelScope.launch {
            delay(350)
            loadSearch(reset = true)
        }
    }

    fun retrySearch() {
        if (state.value.query.isNotBlank()) loadSearch(reset = true)
    }

    fun loadMoreSearch() {
        val search = state.value.search
        if (
            state.value.query.isNotBlank() &&
            !search.loading &&
            !search.loadingMore &&
            search.hasMore
        ) {
            loadSearch(reset = false)
        }
    }

    private fun loadSearch(reset: Boolean) {
        searchRequestJob?.cancel()
        searchRequestJob = viewModelScope.launch {
            val queryAtRequest = state.value.query.trim()
            if (queryAtRequest.isBlank()) return@launch

            val old = state.value.search
            val nextPage = if (reset) 1 else old.page + 1
            state.value = state.value.copy(
                search = old.copy(
                    items = if (reset) emptyList() else old.items,
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                )
            )

            runCatching { app.api.prompts(nextPage, search = queryAtRequest) }
                .onSuccess { page ->
                    if (state.value.query.trim() != queryAtRequest) return@onSuccess
                    val current = state.value.search
                    state.value = state.value.copy(
                        search = current.copy(
                            items = if (reset) {
                                page.items
                            } else {
                                (current.items + page.items).distinctBy(PromptDto::id)
                            },
                            loading = false,
                            loadingMore = false,
                            page = page.page,
                            hasMore = page.hasMore,
                            error = null,
                        )
                    )
                }
                .onFailure {
                    if (state.value.query.trim() != queryAtRequest) return@onFailure
                    state.value = state.value.copy(
                        search = state.value.search.copy(
                            loading = false,
                            loadingMore = false,
                            error = "جست‌وجو انجام نشد. اتصال اینترنت را بررسی کنید.",
                        )
                    )
                }
        }
    }

    fun toggleFavorite(item: PromptDto) = viewModelScope.launch {
        if (favoriteDao.contains(item.id)) {
            favoriteDao.remove(item.id)
        } else {
            favoriteDao.save(
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

    private fun cacheHomeItems(items: List<PromptDto>) = viewModelScope.launch {
        cacheDao.replaceAll(
            items.take(MAX_CACHED_PROMPTS).mapIndexed { index, item -> item.toCache(index) }
        )
    }

    private fun PromptDto.toCache(position: Int) = CachedPrompt(
        id = id,
        title = title,
        promptText = promptText,
        imageUrl = image.url,
        imageWidth = image.width,
        imageHeight = image.height,
        position = position,
    )

    private fun CachedPrompt.toPrompt() = PromptDto(
        id = id,
        title = title,
        promptText = promptText,
        image = PromptImage(imageUrl, imageWidth, imageHeight),
    )

    companion object {
        private const val NEW_PROMPT_CHECK_INTERVAL_MS = 60_000L
        private const val PAGE_SIZE = 20
        private const val MAX_CACHED_PROMPTS = 100
    }
}
