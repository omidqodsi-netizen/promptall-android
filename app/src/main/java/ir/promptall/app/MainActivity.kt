package ir.promptall.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptCategory
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.ui.HomeFeedMode
import ir.promptall.app.ui.PromptViewModel
import ir.promptall.app.ui.theme.PromptAllTheme
import kotlinx.coroutines.delay

private val Purple = Color(0xFFA85CFF)
private val PurpleSoft = Color(0xFFC084FF)
private val MutedText = Color(0xFF98999F)
private val CardBorder = Color(0xFF282A30)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContent {
            PromptAllTheme {
                PromptAllApp(viewModel())
            }
        }
    }
}

private data class Tab(
    val title: String,
    val icon: @Composable (Modifier, Color) -> Unit,
)

@Composable
private fun PromptAllApp(vm: PromptViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val displayPreferences = remember {
        context.getSharedPreferences("promptall_display", Context.MODE_PRIVATE)
    }
    var galleryMode by rememberSaveable {
        mutableStateOf(displayPreferences.getBoolean("home_gallery_mode", false))
    }
    val state by vm.state
    val saved by vm.favorites.collectAsStateWithLifecycle()
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val favoriteListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val homeFeed = if (state.homeMode == HomeFeedMode.RANDOM) {
        state.randomHome
    } else {
        state.latestHome
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.homeMode) {
        if (homeFeed.items.isNotEmpty()) homeListState.scrollToItem(0)
    }

    LaunchedEffect(state.categoryFeedSlug) {
        if (state.categoryFeedSlug != null) categoryListState.scrollToItem(0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.checkForNewPrompts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = listOf(
        Tab("دسته‌بندی") { modifier, color ->
            Icon(Icons.Default.Category, null, modifier, tint = color)
        },
        Tab("علاقه‌مندی‌ها") { modifier, color ->
            Icon(Icons.Default.FavoriteBorder, null, modifier, tint = color)
        },
        Tab("جست‌وجو") { modifier, color ->
            Icon(Icons.Default.Search, null, modifier, tint = color)
        },
        Tab("خانه") { modifier, color ->
            Icon(Icons.Default.Home, null, modifier, tint = color)
        },
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF111118), Color(0xFF050608)),
                center = Offset(950f, 80f),
                radius = 1_350f,
            )
        )
    ) {
        if (showAbout) {
            BackHandler { showAbout = false }
            AboutScreen(onBack = { showAbout = false })
        } else {
            if (selected == 3 && state.categoryFeedSlug != null) {
                BackHandler { vm.closeCategory() }
            }

            when (selected) {
                0 -> FeedScreen(
                    title = "پرامپت‌های آماده",
                    subtitle = "برای ساخت تصاویر با هوش مصنوعی",
                    items = homeFeed.items,
                    favoriteIds = state.favoriteIds,
                    loading = homeFeed.loading,
                    refreshing = homeFeed.refreshing,
                    loadingMore = homeFeed.loadingMore,
                    error = homeFeed.error,
                    listState = homeListState,
                    onRetry = vm::refreshHome,
                    onRefresh = vm::refreshHome,
                    onLoadMore = vm::loadMoreHome,
                    onFavorite = vm::toggleFavorite,
                    onSearchClick = { selected = 1 },
                    newPromptCount = state.newPromptCount,
                    onShowNewPrompts = vm::showNewPrompts,
                    showLatestSection = true,
                    latestItems = state.latestHome.items.take(8),
                    latestLoading = state.latestHome.loading,
                    homeMode = state.homeMode,
                    onShowLatest = vm::showLatestPrompts,
                    onShowRandom = vm::showRandomPrompts,
                    categories = state.categories,
                    categoriesLoading = state.categoriesLoading,
                    selectedCategory = state.selectedCategory,
                    onCategorySelected = vm::selectCategory,
                    galleryMode = galleryMode,
                    onGalleryModeToggle = {
                        galleryMode = !galleryMode
                        displayPreferences.edit()
                            .putBoolean("home_gallery_mode", galleryMode)
                            .apply()
                    },
                )

                1 -> SearchScreen(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    items = state.search.items,
                    favoriteIds = state.favoriteIds,
                    loading = state.search.loading,
                    loadingMore = state.search.loadingMore,
                    error = state.search.error,
                    listState = searchListState,
                    onRetry = vm::retrySearch,
                    onLoadMore = vm::loadMoreSearch,
                    onFavorite = vm::toggleFavorite,
                )

                2 -> FeedScreen(
                    title = "علاقه‌مندی‌ها",
                    subtitle = "پرامپت‌هایی که برای بعد ذخیره کرده‌اید",
                    items = saved.map(Favorite::toPrompt),
                    favoriteIds = saved.map { it.id }.toSet(),
                    loading = false,
                    refreshing = false,
                    loadingMore = false,
                    error = null,
                    listState = favoriteListState,
                    onRetry = {},
                    onRefresh = null,
                    onLoadMore = {},
                    onFavorite = vm::toggleFavorite,
                    emptyText = "هنوز پرامپتی ذخیره نکرده‌اید.",
                )

                else -> {
                    val activeSlug = state.categoryFeedSlug
                    if (activeSlug != null) {
                        val activeCategory = state.categories.firstOrNull { it.slug == activeSlug }
                        FeedScreen(
                            title = activeCategory?.name ?: if (activeSlug == PromptViewModel.TRENDING_CATEGORY_SLUG) {
                                "ترند روز"
                            } else {
                                "پرامپت‌های دسته"
                            },
                            subtitle = "پرامپت‌های این دسته",
                            items = state.categoryFeed.items,
                            favoriteIds = state.favoriteIds,
                            loading = state.categoryFeed.loading,
                            refreshing = state.categoryFeed.refreshing,
                            loadingMore = state.categoryFeed.loadingMore,
                            error = state.categoryFeed.error,
                            listState = categoryListState,
                            onRetry = vm::retryCategory,
                            onRefresh = vm::refreshCategory,
                            onLoadMore = vm::loadMoreCategory,
                            onFavorite = vm::toggleFavorite,
                            onBackClick = vm::closeCategory,
                        )
                    } else {
                        CategoriesScreen(
                            categories = state.categories,
                            categoriesLoading = state.categoriesLoading,
                            trendingItems = state.trending.items,
                            trendingLoading = state.trending.loading,
                            trendingError = state.trending.error,
                            refreshing = state.categoriesLoading || state.trending.refreshing,
                            onRefresh = vm::refreshCategories,
                            onCategoryClick = vm::openCategory,
                            onTrendingClick = { vm.openCategory(PromptViewModel.TRENDING_CATEGORY_SLUG) },
                            onInfoClick = { showAbout = true },
                        )
                    }
                }
            }
        }

        if (!showAbout) {
            FloatingBottomBar(
                tabs = tabs,
                selected = selected,
                onSelected = { index ->
                    vm.closeCategory()
                    selected = when (index) {
                        0 -> 3
                        1 -> 2
                        2 -> 1
                        else -> 0
                    }
                },
                onCenterClick = {
                    vm.closeCategory()
                    selected = 0
                    vm.randomizeHome()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedScreen(
    title: String,
    subtitle: String,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    error: String?,
    listState: LazyListState,
    onRetry: () -> Unit,
    onRefresh: (() -> Unit)?,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    emptyText: String = "پرامپتی برای نمایش وجود ندارد.",
    newPromptCount: Int = 0,
    onShowNewPrompts: () -> Unit = {},
    showLatestSection: Boolean = false,
    latestItems: List<PromptDto> = emptyList(),
    latestLoading: Boolean = false,
    homeMode: HomeFeedMode = HomeFeedMode.RANDOM,
    onShowLatest: () -> Unit = {},
    onShowRandom: () -> Unit = {},
    categories: List<PromptCategory> = emptyList(),
    categoriesLoading: Boolean = false,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {},
    galleryMode: Boolean = false,
    onGalleryModeToggle: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        FeedContent(
            title = title,
            subtitle = subtitle,
            items = items,
            favoriteIds = favoriteIds,
            loading = loading,
            loadingMore = loadingMore,
            error = error,
            listState = listState,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onFavorite = onFavorite,
            onSearchClick = onSearchClick,
            emptyText = emptyText,
            newPromptCount = newPromptCount,
            onShowNewPrompts = onShowNewPrompts,
            showLatestSection = showLatestSection,
            latestItems = latestItems,
            latestLoading = latestLoading,
            homeMode = homeMode,
            onShowLatest = onShowLatest,
            onShowRandom = onShowRandom,
            categories = categories,
            categoriesLoading = categoriesLoading,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            galleryMode = galleryMode,
            onGalleryModeToggle = onGalleryModeToggle,
            onBackClick = onBackClick,
        )
    }
    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun FeedContent(
    title: String,
    subtitle: String,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    listState: LazyListState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
    onSearchClick: (() -> Unit)?,
    emptyText: String,
    newPromptCount: Int,
    onShowNewPrompts: () -> Unit,
    showLatestSection: Boolean,
    latestItems: List<PromptDto>,
    latestLoading: Boolean,
    homeMode: HomeFeedMode,
    onShowLatest: () -> Unit,
    onShowRandom: () -> Unit,
    categories: List<PromptCategory>,
    categoriesLoading: Boolean,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    galleryMode: Boolean,
    onGalleryModeToggle: (() -> Unit)?,
    onBackClick: (() -> Unit)?,
) {
    val galleryState = rememberLazyStaggeredGridState()
    val feedHasScrolled = if (galleryMode && onGalleryModeToggle != null) {
        galleryState.canScrollBackward
    } else {
        listState.canScrollBackward
    }
    // The latest strip should get out of the way in both RANDOM and LATEST modes
    // as soon as the main feed starts moving. It comes back when the feed is at top.
    val latestSectionVisible = showLatestSection && !feedHasScrolled

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(
            title = title,
            subtitle = subtitle,
            onSearchClick = onSearchClick,
            galleryMode = galleryMode,
            onGalleryModeToggle = onGalleryModeToggle,
            onBackClick = onBackClick,
        )
        if (onSearchClick != null) {
            CategoryBar(
                categories = categories,
                loading = categoriesLoading,
                selectedCategory = selectedCategory,
                onSelected = onCategorySelected,
            )
        }
        if (newPromptCount > 0) {
            NewPromptsBanner(newPromptCount, onShowNewPrompts)
        }
        AnimatedVisibility(
            visible = latestSectionVisible,
            enter = fadeIn(animationSpec = tween(190)) +
                expandVertically(
                    animationSpec = tween(280),
                    expandFrom = Alignment.Top,
                ) +
                slideInVertically(
                    animationSpec = tween(280),
                    initialOffsetY = { -it / 7 },
                ),
            exit = fadeOut(animationSpec = tween(170)) +
                shrinkVertically(
                    animationSpec = tween(260),
                    shrinkTowards = Alignment.Top,
                ) +
                slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { -it / 6 },
                ),
        ) {
            Column {
                LatestPromptsSection(
                    latestItems = latestItems,
                    loading = latestLoading,
                    homeMode = homeMode,
                    onShowLatest = onShowLatest,
                    onShowRandom = onShowRandom,
                )
                HomeFeedModeLabel(homeMode)
            }
        }

        when {
            loading -> PromptSkeletonList(Modifier.weight(1f))
            error != null && items.isEmpty() -> ErrorState(
                error, onRetry, Modifier.fillMaxWidth().weight(1f)
            )
            items.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 110.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyText, color = MutedText, fontSize = 13.sp)
            }
            else -> {
                if (galleryMode && onGalleryModeToggle != null) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = galleryState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = 2.dp,
                            bottom = 118.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                    ) {
                        staggeredItems(
                            items = items,
                            key = { it.id },
                        ) { item ->
                            val index = items.indexOf(item)
                            GalleryPromptCard(
                                item = item,
                                favorite = item.id in favoriteIds,
                                onFavorite = { onFavorite(item) },
                            )
                            if (index == (items.lastIndex - 5).coerceAtLeast(0)) {
                                LaunchedEffect(items.size) { onLoadMore() }
                            }
                        }
                        if (loadingMore) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                PromptSkeletonCard()
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 4.dp,
                            bottom = 118.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            PromptCard(
                                item = item,
                                favorite = item.id in favoriteIds,
                                onFavorite = { onFavorite(item) },
                            )
                            if (index == (items.lastIndex - 3).coerceAtLeast(0)) {
                                LaunchedEffect(items.size) { onLoadMore() }
                            }
                        }
                        if (loadingMore) item { PromptSkeletonCard() }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestPromptsSection(
    latestItems: List<PromptDto>,
    loading: Boolean,
    homeMode: HomeFeedMode,
    onShowLatest: () -> Unit,
    onShowRandom: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = if (homeMode == HomeFeedMode.RANDOM) onShowLatest else onShowRandom,
            shape = RoundedCornerShape(50),
            color = Color(0xFF171125),
            contentColor = PurpleSoft,
            border = BorderStroke(1.dp, Color(0xFF432A64)),
        ) {
            Text(
                text = if (homeMode == HomeFeedMode.RANDOM) "نمایش بیشتر" else "نمایش تصادفی",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "آخرین پرامپت‌ها",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Right,
            )
            Text(
                "جدیدترین موارد منتشرشده در سایت",
                color = MutedText,
                fontSize = 10.sp,
                textAlign = TextAlign.Right,
            )
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        reverseLayout = true,
    ) {
        if (loading && latestItems.isEmpty()) {
            items(4) {
                LatestPromptSkeleton()
            }
        } else {
            items(latestItems, key = { it.id }) { item ->
                LatestPromptCard(item)
            }
        }
    }
}

@Composable
private fun LatestPromptCard(item: PromptDto) {
    val context = LocalContext.current
    var copied by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_100)
            copied = false
        }
    }

    Surface(
        onClick = {
            copyPrompt(context, item.promptText)
            copied = true
        },
        modifier = Modifier.width(132.dp).height(158.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101116),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(104.dp)) {
                AsyncImage(
                    model = item.image.url,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xC915111F),
                    contentColor = PurpleSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null,
                            Modifier.size(13.dp),
                        )
                        Text(if (copied) "کپی شد" else "کپی", fontSize = 9.sp)
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.title,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LatestPromptSkeleton() {
    val transition = rememberInfiniteTransition(label = "latest-prompt-skeleton")
    val progress by transition.animateFloat(
        initialValue = -260f,
        targetValue = 520f,
        animationSpec = infiniteRepeatable(
            tween(1_050, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "latest-prompt-skeleton-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF111216), Color(0xFF25272E), Color(0xFF111216)),
        start = Offset(progress - 180f, 0f),
        end = Offset(progress, 260f),
    )
    Box(
        Modifier.width(132.dp).height(158.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
    )
}

@Composable
private fun HomeFeedModeLabel(homeMode: HomeFeedMode) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (homeMode == HomeFeedMode.RANDOM) "پیشنهادهای تصادفی" else "همهٔ آخرین پرامپت‌ها",
                color = Color(0xFFE2E2E7),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
            )
            Text(
                if (homeMode == HomeFeedMode.RANDOM) {
                    "با هر بار باز شدن اپ، ترکیب تازه‌ای می‌بینید"
                } else {
                    "مرتب‌شده از جدیدترین به قدیمی‌تر"
                },
                color = MutedText,
                fontSize = 10.sp,
                textAlign = TextAlign.Right,
            )
        }
    }
}

@Composable
private fun CategoryBar(
    categories: List<PromptCategory>,
    loading: Boolean,
    selectedCategory: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        reverseLayout = true,
    ) {
        item(key = "all") {
            CategoryChip(
                text = "همه",
                selected = selectedCategory == null,
                onClick = { onSelected(null) },
            )
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                text = category.name,
                selected = selectedCategory == category.slug,
                onClick = { onSelected(category.slug) },
            )
        }
        if (loading && categories.isEmpty()) {
            items(3) {
                Box(
                    Modifier.width(86.dp).height(39.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xFF17191D))
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = if (selected) Color(0xFF24172F) else Color(0xFF111317),
        contentColor = if (selected) PurpleSoft else Color(0xFFB0B1B7),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF9B54E8) else Color(0xFF303238),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesScreen(
    categories: List<PromptCategory>,
    categoriesLoading: Boolean,
    trendingItems: List<PromptDto>,
    trendingLoading: Boolean,
    trendingError: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onTrendingClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(
            title = "دسته‌بندی پرامپت‌ها",
            subtitle = "موضوع موردنظرتان را سریع پیدا کنید",
            onSearchClick = null,
            onInfoClick = onInfoClick,
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 118.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "trending-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onTrendingClick,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF171125),
                            contentColor = PurpleSoft,
                            border = BorderStroke(1.dp, Color(0xFF432A64)),
                        ) {
                            Text(
                                "همه ترندها",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "ترند روز",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Right,
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = PurpleSoft,
                                )
                            }
                            Text(
                                "پرامپت‌هایی که این روزها بیشتر مورد توجه‌اند",
                                color = MutedText,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Right,
                            )
                        }
                    }
                }

                item(key = "trending-row") {
                    when {
                        trendingLoading && trendingItems.isEmpty() -> {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                reverseLayout = true,
                            ) {
                                items(4) { LatestPromptSkeleton() }
                            }
                        }
                        trendingItems.isNotEmpty() -> {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                reverseLayout = true,
                            ) {
                                items(trendingItems, key = { it.id }) { item ->
                                    LatestPromptCard(item)
                                }
                            }
                        }
                        trendingError != null -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF101116),
                                border = BorderStroke(1.dp, CardBorder),
                            ) {
                                Text(
                                    "ترند روز فعلاً دریافت نشد؛ صفحه را به پایین بکشید و دوباره تلاش کنید.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Right,
                                )
                            }
                        }
                    }
                }

                item(key = "category-title") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            "همه دسته‌بندی‌ها",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Right,
                        )
                        Text(
                            "برای دیدن پرامپت‌های هر موضوع روی آن بزنید",
                            color = MutedText,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Right,
                        )
                    }
                }

                if (categoriesLoading && categories.isEmpty()) {
                    items(4, key = { "category-skeleton-$it" }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CategorySkeletonCard(Modifier.weight(1f))
                            CategorySkeletonCard(Modifier.weight(1f))
                        }
                    }
                } else if (categories.isEmpty()) {
                    item(key = "category-empty") {
                        Text(
                            "دسته‌بندی‌ای برای نمایش دریافت نشد.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MutedText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(categories.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { category ->
                                CategoryGridCard(
                                    category = category,
                                    onClick = { onCategoryClick(category.slug) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    category: PromptCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTrending = category.slug == PromptViewModel.TRENDING_CATEGORY_SLUG ||
        category.name.contains("ترند", ignoreCase = true)
    val isVideo = category.name.contains("ویدئو", ignoreCase = true) ||
        category.name.contains("video", ignoreCase = true) ||
        category.slug.contains("video", ignoreCase = true)

    Surface(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101116),
        border = BorderStroke(
            1.dp,
            if (isTrending || isVideo) Color(0xFF432A64) else CardBorder,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isTrending || isVideo) Color(0xFF21162D) else Color(0xFF18191E),
                    contentColor = if (isTrending || isVideo) PurpleSoft else Color(0xFFB5B6BC),
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            when {
                                isTrending -> Icons.Default.LocalFireDepartment
                                isVideo -> Icons.Default.Videocam
                                else -> Icons.Default.Category
                            },
                            null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (category.count > 0) {
                    Text(
                        "${category.count.toPersianDigits()} پرامپت",
                        color = MutedText,
                        fontSize = 9.sp,
                    )
                }
            }
            Text(
                category.name,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategorySkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "category-skeleton")
    val progress by transition.animateFloat(
        initialValue = -220f,
        targetValue = 480f,
        animationSpec = infiniteRepeatable(
            tween(1_050, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "category-skeleton-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF111216), Color(0xFF25272E), Color(0xFF111216)),
        start = Offset(progress - 180f, 0f),
        end = Offset(progress, 220f),
    )
    Box(
        modifier.height(108.dp).clip(RoundedCornerShape(20.dp)).background(brush)
    )
}

@Composable
private fun AppHeader(
    title: String,
    subtitle: String,
    onSearchClick: (() -> Unit)?,
    galleryMode: Boolean = false,
    onGalleryModeToggle: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 15.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            HeaderCircleButton(
                onClick = onBackClick,
                contentDescription = "بازگشت",
            ) {
                Icon(Icons.Default.ArrowBack, null, Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(Modifier.width(9.dp))
        }
        if (onInfoClick != null) {
            HeaderCircleButton(
                onClick = onInfoClick,
                contentDescription = "اطلاعات برنامه",
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(Modifier.width(9.dp))
        }
        if (onSearchClick != null) {
            HeaderCircleButton(
                onClick = onSearchClick,
                contentDescription = "جست‌وجو",
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(25.dp), tint = Color.White)
            }
            if (onGalleryModeToggle != null) {
                Spacer(Modifier.width(9.dp))
                HeaderCircleButton(
                    onClick = onGalleryModeToggle,
                    contentDescription = if (galleryMode) "نمای کارت‌ها" else "نمای گالری",
                ) {
                    Icon(
                        if (galleryMode) Icons.Default.ViewAgenda else Icons.Default.GridView,
                        null,
                        Modifier.size(24.dp),
                        tint = if (galleryMode) PurpleSoft else Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                title,
                color = Color.White,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Right,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = MutedText,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Right,
            )
        }
    }
}

@Composable
private fun HeaderCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = Color(0xFF17191D),
        border = BorderStroke(1.dp, Color(0xFF24262B)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.semantics {
                    this.contentDescription = contentDescription
                },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    listState: LazyListState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader("جست‌وجوی پرامپت", "عنوان یا متن دلخواهتان را بنویسید", null)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            placeholder = { Text("مثلاً پرتره سینمایی...", color = Color(0xFF777980)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = PurpleSoft) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = Color(0xFF101116),
                unfocusedContainerColor = Color(0xFF101116),
            ),
        )

        when {
            query.isBlank() -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 110.dp),
                contentAlignment = Alignment.Center,
            ) { Text("برای شروع جست‌وجو چیزی بنویسید", color = MutedText) }
            loading -> PromptSkeletonList(Modifier.weight(1f))
            error != null && items.isEmpty() -> ErrorState(
                error, onRetry, Modifier.fillMaxWidth().weight(1f)
            )
            items.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 110.dp),
                contentAlignment = Alignment.Center,
            ) { Text("نتیجه‌ای پیدا نشد.", color = MutedText) }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 118.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    PromptCard(
                        item = item,
                        favorite = item.id in favoriteIds,
                        onFavorite = { onFavorite(item) },
                    )
                    if (index == (items.lastIndex - 3).coerceAtLeast(0)) {
                        LaunchedEffect(items.size) { onLoadMore() }
                    }
                }
                if (loadingMore) item { PromptSkeletonCard() }
            }
        }
    }
}

@Composable
private fun PromptCard(
    item: PromptDto,
    favorite: Boolean,
    onFavorite: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_250)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xD90D0E12),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.43f).fillMaxHeight()) {
                AsyncImage(
                    model = item.image.url,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.TopStart).padding(10.dp).size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xA31B1C1F))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (favorite) {
                                "حذف از علاقه‌مندی"
                            } else {
                                "افزودن به علاقه‌مندی"
                            },
                            onClick = onFavorite,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        Modifier.size(21.dp),
                        tint = if (favorite) Color(0xFFFF5872) else Color.White,
                    )
                }
            }

            Column(
                Modifier.weight(0.57f).fillMaxHeight().padding(
                    start = 15.dp, end = 11.dp, top = 11.dp, bottom = 11.dp
                )
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "پرامپت آماده",
                        color = PurpleSoft,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Right,
                    )
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF808188),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    item.title,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.promptText,
                    modifier = Modifier.fillMaxWidth(),
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Left,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = {
                        copyPrompt(context, item.promptText)
                        copied = true
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF171125),
                    contentColor = PurpleSoft,
                    border = BorderStroke(1.dp, Color(0xFF432A64)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null,
                            Modifier.size(17.dp),
                        )
                        Text(
                            if (copied) "کپی شد" else "کپی پرامپت",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryPromptCard(
    item: PromptDto,
    favorite: Boolean,
    onFavorite: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(item.id) { mutableStateOf(false) }
    val imageRatio = remember(item.image.width, item.image.height) {
        if (item.image.width > 0 && item.image.height > 0) {
            (item.image.width.toFloat() / item.image.height.toFloat()).coerceIn(0.52f, 1.7f)
        } else {
            0.78f
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_250)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(imageRatio),
        shape = RoundedCornerShape(19.dp),
        color = Color(0xFF111216),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.image.url,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxWidth().height(78.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xC9000000))
                    )
                )
            )
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xA81A1B1F))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (favorite) {
                            "حذف از علاقه‌مندی"
                        } else {
                            "افزودن به علاقه‌مندی"
                        },
                        onClick = onFavorite,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null,
                    Modifier.size(19.dp),
                    tint = if (favorite) Color(0xFFFF5872) else Color.White,
                )
            }
            Surface(
                onClick = {
                    copyPrompt(context, item.promptText)
                    copied = true
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(9.dp),
                shape = RoundedCornerShape(50),
                color = Color(0xD91A1428),
                contentColor = PurpleSoft,
                border = BorderStroke(1.dp, Color(0xFF563878)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        null,
                        Modifier.size(15.dp),
                    )
                    Text(
                        if (copied) "کپی شد" else "کپی پرامپت",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPromptsBanner(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFF171125),
        contentColor = PurpleSoft,
        border = BorderStroke(1.dp, Color(0xFF432A64)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "${count.toPersianDigits()} پرامپت جدید؛ برای نمایش بزنید",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PromptSkeletonList(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(5) { PromptSkeletonCard() }
    }
}

@Composable
private fun PromptSkeletonCard() {
    val transition = rememberInfiniteTransition(label = "prompt-skeleton")
    val progress by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            tween(1_150, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "prompt-skeleton-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF111216), Color(0xFF25272E), Color(0xFF111216)),
        start = Offset(progress - 260f, 0f),
        end = Offset(progress, 420f),
    )
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp)).background(brush)
    )
}

@Composable
private fun FloatingBottomBar(
    tabs: List<Tab>,
    selected: Int,
    onSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(0.94f).widthIn(max = 450.dp)
            .navigationBarsPadding().padding(bottom = 7.dp).height(82.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp).align(Alignment.BottomCenter)
                .shadow(20.dp, RoundedCornerShape(27.dp)),
            shape = RoundedCornerShape(27.dp),
            color = Color(0xF0111217),
            border = BorderStroke(1.dp, Color(0xFF282A31)),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab(tabs[0], selected == 3) { onSelected(0) }
                BottomTab(tabs[1], selected == 2) { onSelected(1) }
                Spacer(Modifier.width(74.dp))
                BottomTab(tabs[2], selected == 1) { onSelected(2) }
                BottomTab(tabs[3], selected == 0) { onSelected(3) }
            }
        }

        Surface(
            onClick = onCenterClick,
            modifier = Modifier.size(70.dp).align(Alignment.TopCenter)
                .shadow(22.dp, CircleShape),
            shape = CircleShape,
            color = Purple,
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFFBE86FF)),
        ) {
            Box(
                Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFFB866FF), Color(0xFF8042E7)))
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Refresh, "تازه‌سازی پرامپت‌ها", Modifier.size(31.dp))
            }
        }
    }
}

@Composable
private fun RowScope.BottomTab(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) PurpleSoft else Color(0xFF8B8D94)
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClickLabel = tab.title, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tab.icon(Modifier.size(23.dp), color)
        Spacer(Modifier.height(3.dp))
        Text(
            tab.title,
            color = color,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = 32.dp, end = 32.dp, bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, textAlign = TextAlign.Center, color = Color(0xFFD5D6DB))
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry) { Text("تلاش دوباره") }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(
            title = "اطلاعات برنامه",
            subtitle = "درباره PromptAll",
            onSearchClick = null,
            onBackClick = onBack,
        )
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                color = Color(0xFF171125),
                border = BorderStroke(1.dp, Color(0xFF432A64)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, null, Modifier.size(36.dp), tint = PurpleSoft)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "promptAll",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text("منبع پرامپت‌های آماده", color = MutedText)
            Spacer(Modifier.height(38.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101116),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    InfoRow("طراح", "سیدامید قدسی‌زاده")
                    InfoRow("نسخه اپلیکیشن", BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, color = MutedText)
    }
}

private fun copyPrompt(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("promptAll prompt", text))
}

private fun Favorite.toPrompt() = PromptDto(
    id = id,
    title = title,
    promptText = promptText,
    image = PromptImage(imageUrl, imageWidth, imageHeight),
)

private fun Int.toPersianDigits(): String = toString()
    .map { char -> if (char in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[char - '0'] else char }
    .joinToString("")
