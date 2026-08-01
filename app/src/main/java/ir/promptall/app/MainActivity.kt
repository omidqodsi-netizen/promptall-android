package ir.promptall.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.checkForNewPrompts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = listOf(
        Tab("تنظیمات") { modifier, color ->
            Icon(Icons.Default.Settings, null, modifier, tint = color)
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
        when (selected) {
            0 -> FeedScreen(
                title = "پرامپت‌های آماده",
                subtitle = "برای ساخت تصاویر با هوش مصنوعی",
                items = state.home.items,
                favoriteIds = state.favoriteIds,
                loading = state.home.loading,
                refreshing = state.home.refreshing,
                loadingMore = state.home.loadingMore,
                error = state.home.error,
                listState = homeListState,
                onRetry = vm::refreshHome,
                onRefresh = vm::refreshHome,
                onLoadMore = vm::loadMoreHome,
                onFavorite = vm::toggleFavorite,
                onSearchClick = { selected = 1 },
                newPromptCount = state.newPromptCount,
                onShowNewPrompts = vm::showNewPrompts,
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

            else -> AboutScreen()
        }

        FloatingBottomBar(
            tabs = tabs,
            selected = selected,
            onSelected = { index ->
                selected = when (index) {
                    0 -> 3
                    1 -> 2
                    2 -> 1
                    else -> 0
                }
            },
            onCenterClick = {
                selected = 0
                vm.refreshHome()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
    categories: List<PromptCategory> = emptyList(),
    categoriesLoading: Boolean = false,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {},
    galleryMode: Boolean = false,
    onGalleryModeToggle: (() -> Unit)? = null,
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
            categories = categories,
            categoriesLoading = categoriesLoading,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            galleryMode = galleryMode,
            onGalleryModeToggle = onGalleryModeToggle,
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
    categories: List<PromptCategory>,
    categoriesLoading: Boolean,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    galleryMode: Boolean,
    onGalleryModeToggle: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(
            title = title,
            subtitle = subtitle,
            onSearchClick = onSearchClick,
            galleryMode = galleryMode,
            onGalleryModeToggle = onGalleryModeToggle,
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
                    val galleryState = rememberLazyStaggeredGridState()
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

@Composable
private fun AppHeader(
    title: String,
    subtitle: String,
    onSearchClick: (() -> Unit)?,
    galleryMode: Boolean = false,
    onGalleryModeToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 15.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun AboutScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            start = 24.dp, end = 24.dp, bottom = 118.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
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
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://cafebazaar.ir/app/ir.promptall.app"),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("مشاهده برنامه در بازار")
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
