package ir.promptall.app.ui

import android.app.Application
import android.net.Uri
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.promptall.app.BuildConfig
import ir.promptall.app.PromptAllApplication
import ir.promptall.app.data.GeminiImageSearchClient
import ir.promptall.app.data.ImageFingerprint
import ir.promptall.app.data.ImageLabelAnalyzer
import ir.promptall.app.data.local.CachedPrompt
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.ImageSearchAiRequest
import ir.promptall.app.data.remote.ImageSearchGeneratedPrompt
import ir.promptall.app.data.remote.ImageSearchItem
import ir.promptall.app.data.remote.ImageSearchLabel
import ir.promptall.app.data.remote.ImageSearchRequest
import ir.promptall.app.data.remote.PromptCategory
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.data.remote.PromptPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID

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



data class ImageSearchUiState(
    val statusLoaded: Boolean = false,
    val backendAvailable: Boolean = false,
    val enabled: Boolean = false,
    val dailyLimit: Int = 3,
    val used: Int = 0,
    val remaining: Int = 0,
    val indexCount: Int = 0,
    val queueCount: Int = 0,
    val ready: Boolean = false,
    val rebuildInProgress: Boolean = false,
    val rebuildTotal: Int = 0,
    val searching: Boolean = false,
    val previewUri: String? = null,
    val results: List<ImageSearchItem> = emptyList(),
    val error: String? = null,
    val aiAvailable: Boolean = false,
    val aiSearching: Boolean = false,
    val aiResults: List<ImageSearchItem> = emptyList(),
    val generatedPrompt: ImageSearchGeneratedPrompt? = null,
    val aiError: String? = null,
    val aiClientKey: String = "",
    val aiModels: List<String> = emptyList(),
    val aiVpnNote: String = "لطفاً فیلترشکن خود را روشن کنید.",
    val aiRetryMessage: String = "اگر نتایج دقیق نبود با هوش مصنوعی دوباره بررسی کنید.",
    val aiButtonLabel: String = "بررسی با هوش مصنوعی",
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
    val trending: FeedState = FeedState(hasMore = false),
    val categoryFeed: FeedState = FeedState(),
    val categoryFeedSlug: String? = null,
    val similarPrompts: FeedState = FeedState(hasMore = false),
    val imageSearch: ImageSearchUiState = ImageSearchUiState(),
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
    private var trendingJob: Job? = null
    private var categoryFeedJob: Job? = null
    private var similarPromptsJob: Job? = null
    private var similarPromptRequestId: Long? = null
    private var searchDebounceJob: Job? = null
    private var searchRequestJob: Job? = null
    private var imageSearchJob: Job? = null
    private var imageAiSearchJob: Job? = null
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
        loadTrending()
        loadCachedHome()
        refreshImageSearchStatus()
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

    fun refreshCategories() {
        loadCategories()
        loadTrending()
    }

    private fun loadTrending() {
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch {
            val old = state.value.trending
            state.value = state.value.copy(
                trending = old.copy(
                    loading = old.items.isEmpty(),
                    refreshing = old.items.isNotEmpty(),
                    error = null,
                )
            )
            runCatching {
                app.api.prompts(
                    page = 1,
                    perPage = TRENDING_PREVIEW_COUNT,
                    category = TRENDING_CATEGORY_SLUG,
                )
            }.onSuccess { page ->
                state.value = state.value.copy(
                    trending = FeedState(
                        items = page.items,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
                        hasMore = page.hasMore,
                        page = page.page,
                    )
                )
            }.onFailure {
                val current = state.value.trending
                state.value = state.value.copy(
                    trending = current.copy(
                        loading = false,
                        refreshing = false,
                        error = if (current.items.isEmpty()) {
                            "دریافت پرامپت‌های ترند انجام نشد."
                        } else {
                            null
                        },
                    )
                )
            }
        }
    }

    fun openCategory(slug: String) {
        val normalized = slug.trim()
        if (normalized.isEmpty()) return
        categoryFeedJob?.cancel()
        state.value = state.value.copy(
            categoryFeedSlug = normalized,
            categoryFeed = FeedState(),
        )
        loadCategoryFeed(reset = true, userInitiated = false)
    }

    fun closeCategory() {
        categoryFeedJob?.cancel()
        state.value = state.value.copy(
            categoryFeedSlug = null,
            categoryFeed = FeedState(),
        )
    }

    fun refreshCategory() {
        loadCategoryFeed(reset = true, userInitiated = true)
    }

    fun retryCategory() {
        loadCategoryFeed(reset = true, userInitiated = false)
    }

    fun loadMoreCategory() {
        loadCategoryFeed(reset = false, userInitiated = false)
    }

    private fun loadCategoryFeed(reset: Boolean, userInitiated: Boolean) {
        val slug = state.value.categoryFeedSlug ?: return
        val feed = state.value.categoryFeed
        if (!reset && (feed.loading || feed.refreshing || feed.loadingMore || !feed.hasMore)) return

        categoryFeedJob?.cancel()
        categoryFeedJob = viewModelScope.launch {
            val old = state.value.categoryFeed
            val nextPage = if (reset) 1 else old.page + 1
            state.value = state.value.copy(
                categoryFeed = old.copy(
                    items = if (reset && old.items.isEmpty()) emptyList() else old.items,
                    loading = reset && old.items.isEmpty(),
                    refreshing = reset && userInitiated && old.items.isNotEmpty(),
                    loadingMore = !reset,
                    error = null,
                )
            )

            runCatching { app.api.prompts(page = nextPage, category = slug) }
                .onSuccess { page ->
                    if (state.value.categoryFeedSlug != slug) return@onSuccess
                    val current = state.value.categoryFeed
                    state.value = state.value.copy(
                        categoryFeed = current.copy(
                            items = if (reset) {
                                page.items
                            } else {
                                (current.items + page.items).distinctBy(PromptDto::id)
                            },
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            error = null,
                            hasMore = page.hasMore,
                            page = page.page,
                        )
                    )
                }
                .onFailure {
                    if (state.value.categoryFeedSlug != slug) return@onFailure
                    val current = state.value.categoryFeed
                    state.value = state.value.copy(
                        categoryFeed = current.copy(
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            error = if (current.items.isEmpty()) {
                                "دریافت پرامپت‌های این دسته انجام نشد. اتصال اینترنت را بررسی کنید."
                            } else {
                                null
                            },
                        )
                    )
                }
        }
    }


    fun loadSimilarPrompts(item: PromptDto, sourceCategory: String?) {
        similarPromptsJob?.cancel()
        val promptId = item.id
        similarPromptRequestId = promptId
        state.value = state.value.copy(
            similarPrompts = FeedState(loading = true, hasMore = false),
        )

        similarPromptsJob = viewModelScope.launch {
            val normalizedCategory = sourceCategory
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "all" }
            val searchQuery = buildSimilarSearchQuery(item.title)

            val primary = runCatching {
                when {
                    normalizedCategory != null -> app.api.prompts(
                        page = 1,
                        perPage = SIMILAR_PROMPTS_FETCH_COUNT,
                        category = normalizedCategory,
                    )
                    searchQuery.isNotBlank() -> app.api.prompts(
                        page = 1,
                        perPage = SIMILAR_PROMPTS_FETCH_COUNT,
                        search = searchQuery,
                    )
                    else -> app.api.prompts(
                        page = 1,
                        perPage = SIMILAR_PROMPTS_FETCH_COUNT,
                    )
                }
            }.getOrNull()

            val candidates = buildList {
                primary?.items?.let(::addAll)
                addAll(localSimilarCandidates())
            }
                .asSequence()
                .filter { it.id != promptId }
                .distinctBy(PromptDto::id)
                .sortedByDescending { similarityScore(item, it) }
                .take(SIMILAR_PROMPTS_DISPLAY_COUNT)
                .toList()

            if (similarPromptRequestId != promptId) return@launch
            state.value = state.value.copy(
                similarPrompts = FeedState(
                    items = candidates,
                    loading = false,
                    error = if (candidates.isEmpty() && primary == null) {
                        "دریافت پرامپت‌های مشابه انجام نشد."
                    } else {
                        null
                    },
                    hasMore = false,
                    page = if (candidates.isEmpty()) 0 else 1,
                )
            )
        }
    }

    fun clearSimilarPrompts() {
        similarPromptRequestId = null
        similarPromptsJob?.cancel()
        state.value = state.value.copy(
            similarPrompts = FeedState(hasMore = false),
        )
    }

    private fun localSimilarCandidates(): List<PromptDto> {
        val snapshot = state.value
        return buildList {
            addAll(snapshot.latestHome.items)
            addAll(snapshot.randomHome.items)
            addAll(snapshot.categoryFeed.items)
            addAll(snapshot.trending.items)
            addAll(snapshot.search.items)
        }.distinctBy(PromptDto::id)
    }

    private fun buildSimilarSearchQuery(title: String): String {
        val stopWords = setOf(
            "پرامپت", "آماده", "ساخت", "عکس", "تصویر", "هوش", "مصنوعی",
            "prompt", "image", "photo", "create", "with", "for", "the", "and",
        )
        return title
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it.lowercase() !in stopWords }
            .take(3)
            .joinToString(" ")
    }

    private fun similarityScore(source: PromptDto, candidate: PromptDto): Int {
        fun tokens(value: String): Set<String> = value
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }
            .toSet()

        val sourceTitle = tokens(source.title)
        val candidateTitle = tokens(candidate.title)
        val sourcePrompt = tokens(source.promptText).take(40).toSet()
        val candidatePrompt = tokens(candidate.promptText).take(40).toSet()

        return (sourceTitle intersect candidateTitle).size * 5 +
            (sourceTitle intersect candidatePrompt).size * 2 +
            (sourcePrompt intersect candidateTitle).size * 2 +
            (sourcePrompt intersect candidatePrompt).size
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

    private fun installationId(): String {
        val prefs = getApplication<Application>().getSharedPreferences(
            "promptall_image_search",
            Context.MODE_PRIVATE,
        )
        val current = prefs.getString("installation_id", null)
        if (!current.isNullOrBlank()) return current
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", created).apply()
        return created
    }

    fun refreshImageSearchStatus() {
        viewModelScope.launch {
            val previous = state.value.imageSearch
            runCatching { app.api.imageSearchStatus(installationId()) }
                .onSuccess { response ->
                    val current = state.value.imageSearch
                    state.value = state.value.copy(
                        imageSearch = current.copy(
                            statusLoaded = true,
                            backendAvailable = true,
                            enabled = response.enabled && BuildConfig.VERSION_CODE >= response.minAppVersion,
                            dailyLimit = response.dailyLimit,
                            used = response.used,
                            remaining = response.remaining,
                            indexCount = response.indexCount,
                            queueCount = response.queueCount,
                            ready = response.ready,
                            rebuildInProgress = response.rebuildInProgress,
                            rebuildTotal = response.rebuildTotal,
                            aiAvailable = response.aiFallbackEnabled && response.aiClientDirect && response.aiClientKey.isNotBlank(),
                            aiClientKey = response.aiClientKey,
                            aiModels = response.aiModels,
                            aiVpnNote = response.aiVpnNote.ifBlank { current.aiVpnNote },
                            aiRetryMessage = response.aiRetryMessage.ifBlank { current.aiRetryMessage },
                            aiButtonLabel = response.aiButtonLabel.ifBlank { current.aiButtonLabel },
                            error = if (response.enabled && BuildConfig.VERSION_CODE < response.minAppVersion) {
                                "برای استفاده از جستجو با تصویر، برنامه را بروزرسانی کنید."
                            } else if (response.enabled && !response.ready) {
                                "ایندکس جستجوی تصویر هنوز آماده نشده است."
                            } else if (current.searching || current.results.isNotEmpty()) current.error else null,
                        )
                    )
                }
                .onFailure {
                    val current = state.value.imageSearch
                    state.value = state.value.copy(
                        imageSearch = current.copy(
                            statusLoaded = true,
                            backendAvailable = false,
                            enabled = false,
                            error = if (current.searching || current.results.isNotEmpty()) current.error else null,
                        )
                    )
                }
        }
    }

    fun searchByImage(uri: Uri) {
        imageSearchJob?.cancel()
        imageSearchJob = viewModelScope.launch {
            var current = state.value.imageSearch

            // A shared image can arrive immediately after app launch, before the normal
            // status request finishes. Resolve backend/feature/quota first so a disabled
            // service never wastes CPU on local image analysis.
            if (!current.statusLoaded) {
                val status = runCatching { app.api.imageSearchStatus(installationId()) }.getOrNull()
                if (status == null) {
                    state.value = state.value.copy(
                        imageSearch = current.copy(
                            statusLoaded = true,
                            backendAvailable = false,
                            enabled = false,
                            previewUri = uri.toString(),
                            error = "سرویس جستجو با تصویر فعلاً در دسترس نیست.",
                        )
                    )
                    return@launch
                }
                current = current.copy(
                    statusLoaded = true,
                    backendAvailable = true,
                    enabled = status.enabled && BuildConfig.VERSION_CODE >= status.minAppVersion,
                    dailyLimit = status.dailyLimit,
                    used = status.used,
                    remaining = status.remaining,
                    indexCount = status.indexCount,
                    queueCount = status.queueCount,
                    ready = status.ready,
                    rebuildInProgress = status.rebuildInProgress,
                    rebuildTotal = status.rebuildTotal,
                    aiAvailable = status.aiFallbackEnabled && status.aiClientDirect && status.aiClientKey.isNotBlank(),
                    aiClientKey = status.aiClientKey,
                    aiModels = status.aiModels,
                    aiVpnNote = status.aiVpnNote.ifBlank { current.aiVpnNote },
                    aiRetryMessage = status.aiRetryMessage.ifBlank { current.aiRetryMessage },
                    aiButtonLabel = status.aiButtonLabel.ifBlank { current.aiButtonLabel },
                    previewUri = uri.toString(),
                    error = null,
                )
                state.value = state.value.copy(imageSearch = current)
            }

            if (current.backendAvailable && !current.enabled) {
                state.value = state.value.copy(
                    imageSearch = current.copy(
                        previewUri = uri.toString(),
                        error = "جستجو با تصویر فعلاً از سمت سرور فعال نشده است.",
                    )
                )
                return@launch
            }
            if (current.backendAvailable && current.remaining <= 0) {
                state.value = state.value.copy(
                    imageSearch = current.copy(
                        previewUri = uri.toString(),
                        error = "سهمیه جستجوی تصویری امروز تمام شده است. فردا دوباره امتحان کنید.",
                    )
                )
                return@launch
            }
            state.value = state.value.copy(
                imageSearch = current.copy(
                    searching = true,
                    previewUri = uri.toString(),
                    results = emptyList(),
                    aiSearching = false,
                    aiResults = emptyList(),
                    generatedPrompt = null,
                    aiError = null,
                    error = null,
                )
            )
            try {
                val fingerprint = withContext(Dispatchers.Default) {
                    ImageFingerprint.fromUri(getApplication(), uri)
                }
                val localLabels = runCatching {
                    ImageLabelAnalyzer.fromUri(getApplication(), uri)
                }.getOrDefault(emptyList())
                val response = app.api.imageSearch(
                    ImageSearchRequest(
                        installationId = installationId(),
                        clientVersion = BuildConfig.VERSION_CODE,
                        phash = fingerprint.phash,
                        dhash = fingerprint.dhash,
                        ahash = fingerprint.ahash,
                        hist = fingerprint.hist,
                        labels = localLabels.map {
                            ImageSearchLabel(text = it.text, confidence = it.confidence)
                        },
                        clientType = "web",
                    )
                )
                state.value = state.value.copy(
                    imageSearch = state.value.imageSearch.copy(
                        statusLoaded = true,
                        backendAvailable = true,
                        enabled = true,
                        dailyLimit = response.dailyLimit,
                        used = response.used,
                        remaining = response.remaining,
                        searching = false,
                        results = response.items,
                        error = if (response.items.isEmpty()) {
                            "پرامپت مشابهی در آرشیو پیدا نشد."
                        } else null,
                    )
                )
            } catch (e: HttpException) {
                val quotaReached = e.code() == 429
                val message = when (e.code()) {
                    429 -> "سهمیه جستجوی تصویری امروز تمام شده است. فردا دوباره امتحان کنید."
                    426 -> "برای استفاده از این قابلیت، برنامه را بروزرسانی کنید."
                    503 -> "جستجو با تصویر فعلاً در دسترس نیست."
                    else -> "جستجوی تصویر انجام نشد. کمی بعد دوباره امتحان کنید."
                }
                state.value = state.value.copy(
                    imageSearch = state.value.imageSearch.copy(
                        searching = false,
                        remaining = if (quotaReached) 0 else state.value.imageSearch.remaining,
                        error = message,
                    )
                )
            } catch (e: Throwable) {
                state.value = state.value.copy(
                    imageSearch = state.value.imageSearch.copy(
                        searching = false,
                        error = "خواندن یا جستجوی تصویر انجام نشد. یک تصویر دیگر امتحان کنید.",
                    )
                )
            }
        }
    }

    fun searchImageWithAi() {
        val snapshot = state.value.imageSearch
        val uriText = snapshot.previewUri ?: return
        if (!snapshot.aiAvailable || snapshot.aiClientKey.isBlank()) {
            state.value = state.value.copy(
                imageSearch = snapshot.copy(aiError = "بررسی هوش مصنوعی از سمت سایت فعال نشده است.")
            )
            return
        }

        imageAiSearchJob?.cancel()
        imageAiSearchJob = viewModelScope.launch {
            state.value = state.value.copy(
                imageSearch = state.value.imageSearch.copy(
                    aiSearching = true,
                    aiResults = emptyList(),
                    generatedPrompt = null,
                    aiError = null,
                )
            )
            try {
                val uri = Uri.parse(uriText)
                val current = state.value.imageSearch
                val analysis = GeminiImageSearchClient.analyze(
                    context = getApplication(),
                    uri = uri,
                    apiKey = current.aiClientKey,
                    models = current.aiModels,
                    vpnNote = current.aiVpnNote,
                )
                val response = app.api.imageSearchAiFallback(
                    ImageSearchAiRequest(
                        installationId = installationId(),
                        clientVersion = BuildConfig.VERSION_CODE,
                        analysisPayload = analysis,
                    )
                )

                val verified = if (response.items.isNotEmpty()) {
                    GeminiImageSearchClient.verify(
                        context = getApplication(),
                        uri = uri,
                        candidates = response.items,
                        apiKey = current.aiClientKey,
                        models = current.aiModels,
                        vpnNote = current.aiVpnNote,
                    )
                } else emptyList()
                val byId = response.items.associateBy { it.id }
                val verifiedItems = verified.mapNotNull { match ->
                    byId[match.id]?.copy(
                        score = match.confidence,
                        similarityPercent = (match.confidence * 100.0).toInt().coerceIn(0, 100),
                        matchType = "ai",
                    )
                }

                state.value = state.value.copy(
                    imageSearch = state.value.imageSearch.copy(
                        aiSearching = false,
                        aiResults = verifiedItems,
                        generatedPrompt = response.generatedPrompt,
                        aiError = if (verifiedItems.isEmpty()) {
                            "هوش مصنوعی نتیجه مطمئنی در دیتابیس پیدا نکرد؛ می‌توانید پرامپت همین تصویر را بسازید."
                        } else null,
                    )
                )
            } catch (error: Throwable) {
                val note = state.value.imageSearch.aiVpnNote
                state.value = state.value.copy(
                    imageSearch = state.value.imageSearch.copy(
                        aiSearching = false,
                        aiError = error.message?.takeIf { it.isNotBlank() }
                            ?: "$note اتصال به هوش مصنوعی برقرار نشد.",
                    )
                )
            }
        }
    }

    fun clearImageSearch() {
        imageSearchJob?.cancel()
        imageAiSearchJob?.cancel()
        state.value = state.value.copy(
            imageSearch = state.value.imageSearch.copy(
                searching = false,
                previewUri = null,
                results = emptyList(),
                aiSearching = false,
                aiResults = emptyList(),
                generatedPrompt = null,
                aiError = null,
                error = null,
            )
        )
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
        private const val TRENDING_PREVIEW_COUNT = 10
        private const val SIMILAR_PROMPTS_FETCH_COUNT = 18
        private const val SIMILAR_PROMPTS_DISPLAY_COUNT = 8
        const val TRENDING_CATEGORY_SLUG = "trending-prompts"
    }
}
