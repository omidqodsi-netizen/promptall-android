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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.ui.PromptViewModel
import ir.promptall.app.ui.theme.PromptAllTheme
import kotlinx.coroutines.delay

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
                val vm: PromptViewModel = viewModel()
                PromptAllApp(vm)
            }
        }
    }
}

private data class Tab(
    val title: String,
    val icon: @Composable (Modifier) -> Unit,
)

@Composable
private fun PromptAllApp(vm: PromptViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val state by vm.state
    val saved by vm.favorites.collectAsStateWithLifecycle()
    val homeGridState = rememberLazyStaggeredGridState()
    val searchGridState = rememberLazyStaggeredGridState()
    val favoriteGridState = rememberLazyStaggeredGridState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.checkForNewPrompts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = listOf(
        Tab("خانه") { Icon(Icons.Default.Home, null, it) },
        Tab("جست‌وجو") { Icon(Icons.Default.Search, null, it) },
        Tab("علاقه‌مندی") { Icon(Icons.Default.Favorite, null, it) },
        Tab("تنظیمات") { Icon(Icons.Default.Settings, null, it) },
    )

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        when (selected) {
            0 -> Feed(
                title = "آخرین پرامپت‌ها",
                items = state.home.items,
                favoriteIds = state.favoriteIds,
                loading = state.home.loading,
                refreshing = state.home.refreshing,
                loadingMore = state.home.loadingMore,
                error = state.home.error,
                gridState = homeGridState,
                onRetry = vm::refreshHome,
                onRefresh = vm::refreshHome,
                onLoadMore = vm::loadMoreHome,
                onFavorite = vm::toggleFavorite,
                newPromptCount = state.newPromptCount,
                onShowNewPrompts = vm::showNewPrompts,
            )

            1 -> Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                    placeholder = { Text("جست‌وجو در عنوان و متن پرامپت") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
                Feed(
                    title = if (state.query.isBlank()) {
                        "پرامپت موردنظر را پیدا کنید"
                    } else {
                        "نتایج جست‌وجو"
                    },
                    items = state.search.items,
                    favoriteIds = state.favoriteIds,
                    loading = state.search.loading,
                    refreshing = false,
                    loadingMore = state.search.loadingMore,
                    error = state.search.error,
                    gridState = searchGridState,
                    onRetry = vm::retrySearch,
                    onRefresh = null,
                    onLoadMore = vm::loadMoreSearch,
                    onFavorite = vm::toggleFavorite,
                    showTopPadding = false,
                )
            }

            2 -> Feed(
                title = "علاقه‌مندی‌ها",
                items = saved.map(Favorite::toPrompt),
                favoriteIds = saved.map { it.id }.toSet(),
                loading = false,
                refreshing = false,
                loadingMore = false,
                error = null,
                gridState = favoriteGridState,
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
            onSelected = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    title: String,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    error: String?,
    gridState: LazyStaggeredGridState,
    onRetry: () -> Unit,
    onRefresh: (() -> Unit)?,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
    emptyText: String = "پرامپتی برای نمایش وجود ندارد.",
    showTopPadding: Boolean = true,
    newPromptCount: Int = 0,
    onShowNewPrompts: () -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        FeedContent(
            title = title,
            items = items,
            favoriteIds = favoriteIds,
            loading = loading,
            loadingMore = loadingMore,
            error = error,
            gridState = gridState,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onFavorite = onFavorite,
            emptyText = emptyText,
            showTopPadding = showTopPadding,
            newPromptCount = newPromptCount,
            onShowNewPrompts = onShowNewPrompts,
        )
    }

    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun FeedContent(
    title: String,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    gridState: LazyStaggeredGridState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
    emptyText: String,
    showTopPadding: Boolean,
    newPromptCount: Int,
    onShowNewPrompts: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().then(
            if (showTopPadding) Modifier.statusBarsPadding() else Modifier
        )
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 9.dp),
            textAlign = TextAlign.Right,
            color = Color(0xFFF4F5FA),
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (newPromptCount > 0) {
            NewPromptsBanner(newPromptCount, onShowNewPrompts)
        }

        when {
            loading -> PromptSkeletonGrid(
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            error != null && items.isEmpty() -> ErrorState(
                message = error,
                retry = onRetry,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            items.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyText, color = Color(0xFF9A9DA8))
            }

            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 104.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalItemSpacing = 7.dp,
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
                if (loadingMore) {
                    item {
                        PromptSkeletonCard(ratio = 0.9f)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPromptsBanner(count: Int, onClick: () -> Unit) {
    val countLabel = if (count >= 20) "۲۰+" else count.toPersianDigits()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFF262A3B),
        contentColor = Color(0xFFC1C7FF),
        border = BorderStroke(1.dp, Color(0x557F8CFF)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = 6.dp,
                alignment = Alignment.CenterHorizontally,
            ),
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
            Text(
                "$countLabel پرامپت جدید؛ برای نمایش بزنید",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
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

    val ratio = (item.image.width.toFloat() / item.image.height.coerceAtLeast(1))
        .coerceIn(0.55f, 1.5f)

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = item.image.url,
            contentDescription = item.title,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )

        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0x16000000),
                        0.62f to Color.Transparent,
                        1f to Color(0xB8000000),
                    ),
                )
            )
        )

        Box(
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(2.dp)
                .size(42.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (favorite) "حذف از علاقه‌مندی" else "افزودن به علاقه‌مندی",
                    onClick = onFavorite,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).background(Color(0xA617181E), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (favorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (favorite) Color(0xFFFF5B73) else Color.White,
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(7.dp)
                .height(29.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE8212330))
                .clickable(role = Role.Button) {
                    copyPrompt(context, item.promptText)
                    copied = true
                }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = Color(0xFFBCC4FF),
            )
            Text(
                text = if (copied) "کپی شد" else "کپی پرامپت",
                color = Color(0xFFCDD2FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PromptSkeletonGrid(modifier: Modifier = Modifier) {
    val ratios = remember {
        listOf(0.72f, 0.92f, 0.62f, 1.18f, 0.84f, 0.7f, 1.05f, 0.78f)
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalItemSpacing = 7.dp,
        userScrollEnabled = false,
    ) {
        items(ratios.size) { index ->
            PromptSkeletonCard(ratios[index])
        }
    }
}

@Composable
private fun PromptSkeletonCard(ratio: Float) {
    val transition = rememberInfiniteTransition(label = "prompt-skeleton")
    val progress by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "prompt-skeleton-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF171920),
            Color(0xFF292C36),
            Color(0xFF171920),
        ),
        start = Offset(progress - 260f, 0f),
        end = Offset(progress, 620f),
    )
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(17.dp))
            .background(brush)
    )
}

@Composable
private fun FloatingBottomBar(
    tabs: List<Tab>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.88f)
            .widthIn(max = 390.dp)
            .navigationBarsPadding()
            .padding(bottom = 7.dp)
            .shadow(16.dp, RoundedCornerShape(21.dp)),
        shape = RoundedCornerShape(21.dp),
        color = Color(0xE8181A22),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0x382E3341)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) Color(0xFF34394F) else Color.Transparent
                        )
                        .clickable(
                            role = Role.Tab,
                            onClickLabel = tab.title,
                        ) { onSelected(index) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tab.icon(
                        Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        fontSize = 9.sp,
                        color = if (isSelected) {
                            Color(0xFFE9EBFF)
                        } else {
                            Color(0xFFA5A8B2)
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(start = 32.dp, end = 32.dp, bottom = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry) { Text("تلاش دوباره") }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(
            Icons.Default.Info,
            null,
            modifier = Modifier.size(54.dp),
            tint = Color(0xFF9CA7FF),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "promptAll",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF4F5FA),
        )
        Text("منبع پرامپت‌های آماده", color = Color(0xFF9A9DA8))
        Spacer(Modifier.height(42.dp))
        InfoRow("طراح", "سیدامید قدسی‌زاده")
        InfoRow("نسخه اپلیکیشن", BuildConfig.VERSION_NAME)
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://promptall.ir/promptapp/"),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        ) {
            Text("صفحه دانلود اپلیکیشن")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            value,
            color = Color(0xFFF0F1F6),
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, color = Color(0xFF9A9DA8))
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
    .joinToString(separator = "")
