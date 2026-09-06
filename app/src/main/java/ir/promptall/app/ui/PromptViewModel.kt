package ir.promptall.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.promptall.app.PromptAllApplication
import ir.promptall.app.data.local.CachedPrompt
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptCategory
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.data.remote.PromptPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeFeedMode {
    RANDOM,
    LATEST,
}

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
    val randomHome: FeedState = FeedState(),
    val latestHome: FeedState = FeedState(),
    val homeMode: HomeFeedMode = HomeFeedMode.RANDOM,
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
    private var randomTotalPages = 0
    private val randomPagesLoaded = linkedSetOf<Int>()

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
        val normalizedSlug = slug?.trim()?.takeIf { it.isNotEmpty() && it != "all" }
        if (state.value.selectedCategory == normalizedSlug) {
            randomizeHome()
            return
        }

        homeJob?.cancel()
        pendingFirstPage = null
        randomPagesLoaded.clear()
        randomTotalPages = 0
        state.value = state.value.copy(
            selectedCategory = normalizedSlug,
            newPromptCount = 0,
            homeMode = HomeFeedMode.RANDOM,
            randomHome = FeedState(),
            latestHome = FeedState(),
        )
        loadRandomHome(reset = true, userInitiated = false)
    }

    private fun loadCachedHome() = viewModelScope.launch {
        val cached = cacheDao.getAll().map { it.toPrompt() }
        if (state.value.selectedCategory != null) return@launch

        if (cached.isNotEmpty()) {
            val cachedLatest = cached.take(PAGE_SIZE)
            val cachedRandomPool = cached.drop(PAGE_SIZE).ifEmpty {
                cached.drop(LATEST_PREVIEW_COUNT).ifEmpty { cached }
            }
            state.value = state.value.copy(
                latestHome = FeedState(
                    items = cachedLatest,
                    loading = false,
                    page = if (cachedLatest.isEmpty()) 0 else 1,
                    hasMore = true,
                ),
                randomHome = FeedState(
                    items = cachedRandomPool.shuffled(),
                    loading = false,
                    page = 0,
                    hasMore = true,
                ),
            )
        }

        loadRandomHome(reset = true, userInitiated = false)
    }

    fun refreshHome() {
        pendingFirstPage = null
        state.value = state.value.copy(newPromptCount = 0)
        when (state.value.homeMode) {
            HomeFeedMode.RANDOM -> loadRandomHome(reset = true, userInitiated = true)
            HomeFeedMode.LATEST -> loadLatestHome(reset = true, userInitiated = true)
        }
    }

    fun randomizeHome() {
        pendingFirstPage = null
        state.value = state.value.copy(
            homeMode = HomeFeedMode.RANDOM,
            newPromptCount = 0,
        )
        loadRandomHome(reset = true, userInitiated = true)
    }

    fun showLatestPrompts() {
        state.value = state.value.copy(homeMode = HomeFeedMode.LATEST)
        if (state.value.latestHome.items.isEmpty()) {
            loadLatestHome(reset = true, userInitiated = false)
        }
    }

    fun showRandomPrompts() {
        state.value = state.value.copy(homeMode = HomeFeedMode.RANDOM)
        if (state.value.randomHome.items.isEmpty()) {
            loadRandomHome(reset = true, userInitiated = false)
        }
    }

    fun loadMoreHome() {
        when (state.value.homeMode) {
            HomeFeedMode.RANDOM -> loadMoreRandomHome()
            HomeFeedMode.LATEST -> loadLatestHome(reset = false, userInitiated = false)
        }
    }

    private fun loadRandomHome(reset: Boolean, userInitiated: Boolean) {
        if (!reset) {
            loadMoreRandomHome()
            return
        }

        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val oldRandom = state.value.randomHome
            val oldLatest = state.value.latestHome
            state.value = state.value.copy(
                randomHome = oldRandom.copy(
                    loading = oldRandom.items.isEmpty(),
                    refreshing = userInitiated && oldRandom.items.isNotEmpty(),
                    loadingMore = false,
                    error = null,
                ),
                latestHome = oldLatest.copy(
                    loading = oldLatest.items.isEmpty(),
                    error = null,
                ),
            )

            val categoryAtRequest = state.value.selectedCategory
            val firstPageResult = runCatching {
                app.api.prompts(page = 1, category = categoryAtRequest)
            }

            firstPageResult.onFailure {
                if (state.value.selectedCategory != categoryAtRequest) return@onFailure
                val currentRandom = state.value.randomHome
                val currentLatest = state.value.latestHome
                state.value = state.value.copy(
                    randomHome = currentRandom.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = if (currentRandom.items.isEmpty()) {
                            "دریافت پرامپت‌ها انجام نشد. اتصال اینترنت را بررسی کنید."
                        } else {
                            null
                        },
                    ),
                    latestHome = currentLatest.copy(loading = false),
                )
            }

            firstPageResult.onSuccess { firstPage ->
                if (state.value.selectedCategory != categoryAtRequest) return@onSuccess

                randomTotalPages = firstPage.totalPages.coerceAtLeast(1)
                randomPagesLoaded.clear()

                state.value = state.value.copy(
                    latestHome = FeedState(
                        items = firstPage.items,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
                        hasMore = firstPage.hasMore,
                        page = firstPage.page,
                    )
                )

                val randomItems = if (randomTotalPages <= 1) {
                    randomPagesLoaded += 1
                    firstPage.items
                        .drop(LATEST_PREVIEW_COUNT)
                        .ifEmpty { firstPage.items }
                        .shuffled()
                } else {
                    val pageNumbers = (2..randomTotalPages)
                        .shuffled()
                        .take(RANDOM_INITIAL_PAGE_COUNT)
                    val collected = mutableListOf<PromptDto>()

                    pageNumbers.forEach { pageNumber ->
                        val page = runCatching {
                            app.api.prompts(page = pageNumber, category = categoryAtRequest)
                        }.getOrNull()
                        if (page != null && state.value.selectedCategory == categoryAtRequest) {
                            randomPagesLoaded += pageNumber
                            collected += page.items
                        }
                    }

                    if (collected.isEmpty()) {
                        firstPage.items
                            .drop(LATEST_PREVIEW_COUNT)
                            .ifEmpty { firstPage.items }
                            .shuffled()
                    } else {
                        collected.distinctBy(PromptDto::id).shuffled()
                    }
                }

                if (state.value.selectedCategory != categoryAtRequest) return@onSuccess
                val current = state.value.randomHome
                val randomPageCapacity = (randomTotalPages - 1).coerceAtLeast(0)
                state.value = state.value.copy(
                    randomHome = current.copy(
                        items = randomItems,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
                        hasMore = randomTotalPages > 1 &&
                            randomPagesLoaded.size < randomPageCapacity,
                        page = randomPagesLoaded.size,
                    )
                )
                if (categoryAtRequest == null) cacheOfflineItems()
            }
        }
    }

    private fun loadMoreRandomHome() {
        val home = state.value.randomHome
        if (home.loading || home.refreshing || home.loadingMore || !home.hasMore) return

        if (randomTotalPages <= 1) {
            state.value = state.value.copy(randomHome = home.copy(hasMore = false))
            return
        }

        val remainingPages = (2..randomTotalPages).filterNot(randomPagesLoaded::contains)
        val nextPage = remainingPages.randomOrNull()
        if (nextPage == null) {
            state.value = state.value.copy(randomHome = home.copy(hasMore = false))
            return
        }

        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            state.value = state.value.copy(
                randomHome = state.value.randomHome.copy(loadingMore = true, error = null)
            )
            val categoryAtRequest = state.value.selectedCategory
            runCatching { app.api.prompts(nextPage, category = categoryAtRequest) }
                .onSuccess { page ->
                    if (state.value.selectedCategory != categoryAtRequest) return@onSuccess
                    randomPagesLoaded += nextPage
                    val current = state.value.randomHome
                    val merged = (current.items + page.items.shuffled()).distinctBy(PromptDto::id)
                    val randomPageCapacity = (randomTotalPages - 1).coerceAtLeast(0)
                    state.value = state.value.copy(
                        randomHome = current.copy(
                            items = merged,
                            loadingMore = false,
                            error = null,
                            hasMore = randomPagesLoaded.size < randomPageCapacity,
                            page = randomPagesLoaded.size,
                        )
                    )
                    if (categoryAtRequest == null) cacheOfflineItems()
                }
                .onFailure {
                    val current = state.value.randomHome
                    state.value = state.value.copy(
                        randomHome = current.copy(loadingMore = false)
                    )
                }
        }
    }

    private fun loadLatestHome(reset: Boolean, userInitiated: Boolean) {
        val latest = state.value.latestHome
        if (!reset && (latest.loading || latest.refreshing || latest.loadingMore || !latest.hasMore)) {
            return
        }

        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val old = state.value.latestHome
            val nextPage = if (reset) 1 else old.page + 1
            state.value = state.value.copy(
                latestHome = old.copy(
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
                    val current = state.value.latestHome
                    val merged = if (reset) {
                        page.items
                    } else {
                        (current.items + page.items).distinctBy(PromptDto::id)
                    }
                    state.value = state.value.copy(
                        latestHome = current.copy(
                            items = merged,
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            page = page.page,
                            hasMore = page.hasMore,
                            error = null,
                        )
                    )
                    if (categoryAtRequest == null) cacheOfflineItems()
                }
                .onFailure {
                    val current = state.value.latestHome
                    state.value = state.value.copy(
                        latestHome = current.copy(
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
        val latest = state.value.latestHome
        if (latest.items.isEmpty() || latest.refreshing || latest.loading) return
        lastNewPromptCheck = now

        viewModelScope.launch {
            val categoryAtRequest = state.value.selectedCategory
            runCatching { app.api.prompts(page = 1, category = categoryAtRequest) }
                .onSuccess { page ->
                    if (state.value.selectedCategory != categoryAtRequest) return@onSuccess
                    val visibleIds = state.value.latestHome.items
                        .take(PAGE_SIZE)
                        .mapTo(hashSetOf(), PromptDto::id)
                    val newItems = page.items.takeWhile { it.id !in visibleIds }
                    if (newItems.isNotEmpty()) {
                        pendingFirstPage = page
                        state.value = state.value.copy(newPromptCount = newItems.size)
                    } else if (state.value.latestHome.page == 1 && categoryAtRequest == null) {
                        cacheOfflineItems()
                    }
                }
        }
    }

    fun showNewPrompts() {
        val page = pendingFirstPage ?: return
        val current = state.value.latestHome
        val merged = (page.items + current.items).distinctBy(PromptDto::id)
        pendingFirstPage = null
        state.value = state.value.copy(
            latestHome = current.copy(
                items = merged,
                page = maxOf(1, current.page),
                hasMore = page.hasMore || current.hasMore,
            ),
            newPromptCount = 0,
        )
        if (state.value.selectedCategory == null) cacheOfflineItems()
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

    private fun cacheOfflineItems() {
        val snapshot = state.value
        val items = (
            snapshot.latestHome.items.take(PAGE_SIZE) + snapshot.randomHome.items
        ).distinctBy(PromptDto::id)
        cacheHomeItems(items)
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
        private const val LATEST_PREVIEW_COUNT = 8
        private const val RANDOM_INITIAL_PAGE_COUNT = 2
        private const val MAX_CACHED_PROMPTS = 100
    }
}
