package ir.promptall.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import ir.promptall.app.data.local.Favorite
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.ui.PromptViewModel
import ir.promptall.app.ui.theme.PromptAllTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PromptAllTheme {
                val vm: PromptViewModel = viewModel()
                PromptAllApp(vm)
            }
        }
    }
}

private data class Tab(val title: String, val icon: @Composable () -> Unit)

@Composable
private fun PromptAllApp(vm: PromptViewModel) {
    var selected by remember { mutableIntStateOf(0) }
    val state by vm.state
    val saved by vm.favorites.collectAsStateWithLifecycle()
    val tabs = listOf(
        Tab("خانه") { Icon(Icons.Default.Home, null, Modifier.size(21.dp)) },
        Tab("جست‌وجو") { Icon(Icons.Default.Search, null, Modifier.size(21.dp)) },
        Tab("علاقه‌مندی") { Icon(Icons.Default.Favorite, null, Modifier.size(21.dp)) },
        Tab("تنظیمات") { Icon(Icons.Default.Settings, null, Modifier.size(21.dp)) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    containerColor = Color(0xF5181A21),
                    tonalElevation = 8.dp,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = tab.icon,
                            label = { Text(tab.title, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFF34394F),
                                selectedIconColor = Color(0xFFB6BEFF),
                                selectedTextColor = Color(0xFFE9EBFF),
                                unselectedIconColor = Color(0xFF8A8E9A),
                                unselectedTextColor = Color(0xFF8A8E9A),
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (selected) {
                0 -> Feed(
                    title = "جدیدترین پرامپت‌ها",
                    items = state.items,
                    favoriteIds = state.favoriteIds,
                    loading = state.loading,
                    loadingMore = state.loadingMore,
                    error = state.error,
                    onRetry = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onFavorite = vm::toggleFavorite,
                )
                1 -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("جست‌وجو در عنوان و متن پرامپت") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Feed(
                        title = if (state.query.isBlank()) "پرامپت موردنظر را پیدا کنید" else "نتایج جست‌وجو",
                        items = state.items,
                        favoriteIds = state.favoriteIds,
                        loading = state.loading,
                        loadingMore = state.loadingMore,
                        error = state.error,
                        onRetry = vm::refresh,
                        onLoadMore = vm::loadMore,
                        onFavorite = vm::toggleFavorite,
                        showTopPadding = false,
                    )
                }
                2 -> Feed(
                    title = "علاقه‌مندی‌ها",
                    items = saved.map(Favorite::toPrompt),
                    favoriteIds = saved.map { it.id }.toSet(),
                    loading = false,
                    loadingMore = false,
                    error = null,
                    onRetry = {},
                    onLoadMore = {},
                    onFavorite = vm::toggleFavorite,
                    emptyText = "هنوز پرامپتی ذخیره نکرده‌اید.",
                )
                else -> AboutScreen()
            }
        }
    }
}

@Composable
private fun Feed(
    title: String,
    items: List<PromptDto>,
    favoriteIds: Set<Long>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFavorite: (PromptDto) -> Unit,
    emptyText: String = "پرامپتی برای نمایش وجود ندارد.",
    showTopPadding: Boolean = true,
) {
    Column(Modifier.fillMaxSize().then(if (showTopPadding) Modifier.statusBarsPadding() else Modifier)) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            textAlign = TextAlign.Right,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && items.isEmpty() -> ErrorState(error, onRetry)
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = Color(0xFF9A9DA8))
            }
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    PromptCard(item, item.id in favoriteIds) { onFavorite(item) }
                    if (index >= items.lastIndex - 4) {
                        LaunchedEffect(items.size) { onLoadMore() }
                    }
                }
                if (loadingMore) item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptCard(item: PromptDto, favorite: Boolean, onFavorite: () -> Unit) {
    val context = LocalContext.current
    val ratio = (item.image.width.toFloat() / item.image.height.coerceAtLeast(1))
        .coerceIn(0.55f, 1.5f)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
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
                    listOf(Color.Transparent, Color.Transparent, Color(0xE6000000))
                )
            )
        )
        IconButton(
            onClick = onFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(36.dp)
                .background(Color(0xB314151A), CircleShape),
        ) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                modifier = Modifier.size(20.dp),
                tint = if (favorite) Color(0xFFFF5B73) else Color.White,
            )
        }
        Button(
            onClick = { copyPrompt(context, item.promptText) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).height(38.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
            border = BorderStroke(1.dp, Color(0x557C8CFF)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xF0212330),
                contentColor = Color(0xFFB8C0FF),
            ),
        ) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            Text("  کپی پرامپت", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
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
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(Icons.Default.Info, null, modifier = Modifier.height(54.dp), tint = Color(0xFF9CA7FF))
        Spacer(Modifier.height(18.dp))
        Text("promptAll", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("منبع پرامپت‌های آماده", color = Color(0xFF9A9DA8))
        Spacer(Modifier.height(42.dp))
        InfoRow("طراح", "سیدامید قدسی‌زاده")
        InfoRow("نسخه اپلیکیشن", BuildConfig.VERSION_NAME)
        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://promptall.ir/promptapp/")))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        ) { Text("صفحه دانلود اپلیکیشن") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(value, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color(0xFF9A9DA8))
    }
}

private fun copyPrompt(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("promptAll prompt", text))
    Toast.makeText(context, "پرامپت کپی شد", Toast.LENGTH_SHORT).show()
}

private fun Favorite.toPrompt() = PromptDto(
    id = id,
    title = title,
    promptText = promptText,
    image = PromptImage(imageUrl, imageWidth, imageHeight),
)
