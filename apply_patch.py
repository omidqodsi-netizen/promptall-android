from pathlib import Path

ROOT = Path(__file__).resolve().parent


def patch(path: str, old: str, new: str, count: int = 1):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Pattern not found in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


# ---------------------------------------------------------------------------
# Gradle / version / official Bazaar billing client
# ---------------------------------------------------------------------------
patch(
    "settings.gradle.kts",
    '        mavenCentral()\n    }\n}\nrootProject.name',
    '        mavenCentral()\n        maven("https://jitpack.io")\n    }\n}\nrootProject.name',
)
patch(
    "app/build.gradle.kts",
    '        versionCode = 30901\n        versionName = "3.9.1"',
    '        versionCode = 31000\n        versionName = "3.10.0"',
)
patch(
    "app/build.gradle.kts",
    '    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")\n',
    '    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")\n\n'
    '    // Cafe Bazaar official In-App Billing client.\n'
    '    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")\n',
)

# ---------------------------------------------------------------------------
# Manifest: Bazaar billing + camera FileProvider + old Android download support
# ---------------------------------------------------------------------------
patch(
    "app/src/main/AndroidManifest.xml",
    '    <uses-permission android:name="android.permission.INTERNET" />',
    '    <uses-permission android:name="android.permission.INTERNET" />\n'
    '    <uses-permission android:name="com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR" />\n'
    '    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />',
)
patch(
    "app/src/main/AndroidManifest.xml",
    '        <package android:name="ai.x.grok" />',
    '        <package android:name="ai.x.grok" />\n'
    '        <package android:name="com.farsitel.bazaar" />',
)
patch(
    "app/src/main/AndroidManifest.xml",
    '        <activity\n            android:name=".MainActivity"',
    '        <provider\n'
    '            android:name="androidx.core.content.FileProvider"\n'
    '            android:authorities="${applicationId}.fileprovider"\n'
    '            android:exported="false"\n'
    '            android:grantUriPermissions="true">\n'
    '            <meta-data\n'
    '                android:name="android.support.FILE_PROVIDER_PATHS"\n'
    '                android:resource="@xml/file_paths" />\n'
    '        </provider>\n\n'
    '        <activity\n            android:name=".MainActivity"',
)

# ---------------------------------------------------------------------------
# Application: share one Retrofit client between public Prompt API and AI API
# ---------------------------------------------------------------------------
p = ROOT / "app/src/main/java/ir/promptall/app/PromptAllApplication.kt"
t = p.read_text(encoding="utf-8")
if "import ir.promptall.app.data.remote.AiImageApi" not in t:
    t = t.replace(
        "import ir.promptall.app.data.remote.PromptApi",
        "import ir.promptall.app.data.remote.PromptApi\nimport ir.promptall.app.data.remote.AiImageApi",
        1,
    )
old = '''    val api: PromptApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        Retrofit.Builder()
            .baseUrl("https://promptall.ir/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PromptApi::class.java)
    }
'''
new = '''    private val retrofit by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        Retrofit.Builder()
            .baseUrl("https://promptall.ir/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: PromptApi by lazy { retrofit.create(PromptApi::class.java) }
    val aiImageApi: AiImageApi by lazy { retrofit.create(AiImageApi::class.java) }
'''
if new not in t:
    if old not in t:
        raise SystemExit("PromptAllApplication API block not found")
    t = t.replace(old, new, 1)
p.write_text(t, encoding="utf-8")

# ---------------------------------------------------------------------------
# Main UI integration
# ---------------------------------------------------------------------------
p = ROOT / "app/src/main/java/ir/promptall/app/MainActivity.kt"
t = p.read_text(encoding="utf-8")

if "import androidx.compose.material.icons.filled.Person\n" not in t:
    t = t.replace(
        "import androidx.compose.material.icons.filled.OpenInNew\n",
        "import androidx.compose.material.icons.filled.OpenInNew\nimport androidx.compose.material.icons.filled.Person\n",
        1,
    )
if "import ir.promptall.app.ai.AiGenerateScreen\n" not in t:
    t = t.replace(
        "import ir.promptall.app.ui.HomeFeedMode\n",
        "import ir.promptall.app.ai.AiGenerateScreen\n"
        "import ir.promptall.app.ai.AiImageViewModel\n"
        "import ir.promptall.app.ai.AiProfileScreen\n"
        "import ir.promptall.app.ui.HomeFeedMode\n",
        1,
    )

if "var showAiProfile by rememberSaveable" not in t:
    t = t.replace(
        "    var showAbout by rememberSaveable { mutableStateOf(false) }\n",
        "    var showAbout by rememberSaveable { mutableStateOf(false) }\n"
        "    var showAiProfile by rememberSaveable { mutableStateOf(false) }\n"
        "    var aiGeneratePrompt by remember { mutableStateOf<PromptDto?>(null) }\n",
        1,
    )
if "val aiVm: AiImageViewModel = viewModel()" not in t:
    t = t.replace(
        "    val context = LocalContext.current\n",
        "    val context = LocalContext.current\n"
        "    val aiVm: AiImageViewModel = viewModel()\n",
        1,
    )

# Incoming image search must close AI sub-screens as well.
if "        showAiProfile = false\n        aiGeneratePrompt = null\n" not in t:
    t = t.replace(
        "        showAbout = false\n        detailPrompt = null\n",
        "        showAbout = false\n"
        "        showAiProfile = false\n"
        "        aiGeneratePrompt = null\n"
        "        detailPrompt = null\n",
        1,
    )

old_route = '''        if (showAbout) {
            BackHandler { showAbout = false }
            AboutScreen(onBack = { showAbout = false })
        } else if (detailPrompt != null) {
'''
new_route = '''        if (aiGeneratePrompt != null) {
            val prompt = requireNotNull(aiGeneratePrompt)
            BackHandler { aiGeneratePrompt = null }
            AiGenerateScreen(
                prompt = prompt,
                vm = aiVm,
                onBack = { aiGeneratePrompt = null },
                onOpenProfile = { aiGeneratePrompt = null; showAiProfile = true },
            )
        } else if (showAiProfile) {
            BackHandler { showAiProfile = false }
            AiProfileScreen(vm = aiVm, onBack = { showAiProfile = false })
        } else if (showAbout) {
            BackHandler { showAbout = false }
            AboutScreen(onBack = { showAbout = false })
        } else if (detailPrompt != null) {
'''
if new_route not in t:
    if old_route not in t:
        raise SystemExit("Main routing block not found")
    t = t.replace(old_route, new_route, 1)

if "                onGenerate = { aiGeneratePrompt = it },\n" not in t:
    t = t.replace(
        "                onSimilarClick = openSimilarDetail,\n            )",
        "                onSimilarClick = openSimilarDetail,\n"
        "                onGenerate = { aiGeneratePrompt = it },\n"
        "            )",
        1,
    )

# Put profile access on the home header without changing the 4-tab bottom nav.
old_home = '''                    onGalleryModeToggle = {
                        galleryMode = !galleryMode
                        displayPreferences.edit()
                            .putBoolean("home_gallery_mode", galleryMode)
                            .apply()
                    },
                )
'''
new_home = '''                    onGalleryModeToggle = {
                        galleryMode = !galleryMode
                        displayPreferences.edit()
                            .putBoolean("home_gallery_mode", galleryMode)
                            .apply()
                    },
                    onProfileClick = { showAiProfile = true },
                )
'''
if new_home not in t:
    if old_home not in t:
        raise SystemExit("Home FeedScreen call not found")
    t = t.replace(old_home, new_home, 1)

# FeedScreen -> FeedContent -> AppHeader propagation.
if "    onProfileClick: (() -> Unit)? = null,\n" not in t:
    t = t.replace(
        "    onBackClick: (() -> Unit)? = null,\n) {",
        "    onBackClick: (() -> Unit)? = null,\n"
        "    onProfileClick: (() -> Unit)? = null,\n"
        ") {",
        1,
    )
if "            onProfileClick = onProfileClick,\n" not in t:
    t = t.replace(
        "            onBackClick = onBackClick,\n        )",
        "            onBackClick = onBackClick,\n"
        "            onProfileClick = onProfileClick,\n"
        "        )",
        1,
    )
if t.count("    onProfileClick: (() -> Unit)?,\n") == 0:
    t = t.replace(
        "    onBackClick: (() -> Unit)?,\n) {",
        "    onBackClick: (() -> Unit)?,\n"
        "    onProfileClick: (() -> Unit)?,\n"
        ") {",
        1,
    )
if t.count("            onProfileClick = onProfileClick,\n") < 2:
    t = t.replace(
        "            onBackClick = onBackClick,\n        )\n        if (onSearchClick != null)",
        "            onBackClick = onBackClick,\n"
        "            onProfileClick = onProfileClick,\n"
        "        )\n"
        "        if (onSearchClick != null)",
        1,
    )

# AppHeader parameter + visual icon.
if "    onProfileClick: (() -> Unit)? = null,\n" not in t[t.find("private fun AppHeader"):t.find("private fun AppHeader")+1200]:
    t = t.replace(
        "    onInfoClick: (() -> Unit)? = null,\n) {",
        "    onInfoClick: (() -> Unit)? = null,\n"
        "    onProfileClick: (() -> Unit)? = null,\n"
        ") {",
        1,
    )
info_block = '''        if (onInfoClick != null) {
            HeaderCircleButton(
                onClick = onInfoClick,
                contentDescription = "اطلاعات برنامه",
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(9.dp))
        }
'''
profile_block = info_block + '''        if (onProfileClick != null) {
            HeaderCircleButton(
                onClick = onProfileClick,
                contentDescription = "پروفایل و تصاویر ساخته‌شده",
                accent = true,
            ) {
                Icon(Icons.Default.Person, null, Modifier.size(22.dp), tint = PurpleSoft)
            }
            Spacer(Modifier.width(9.dp))
        }
'''
if "contentDescription = \"پروفایل و تصاویر ساخته‌شده\"" not in t:
    if info_block not in t:
        raise SystemExit("AppHeader info block not found")
    t = t.replace(info_block, profile_block, 1)

# AI full-screen routes must not leave the regular bottom navigation floating over them.
if "if (!showAbout && !showAiProfile && aiGeneratePrompt == null && detailPrompt == null)" not in t:
    t = t.replace(
        "if (!showAbout && detailPrompt == null) {",
        "if (!showAbout && !showAiProfile && aiGeneratePrompt == null && detailPrompt == null) {",
        1,
    )

# Prompt detail gets one clear native CTA; no web checkout is ever opened.
if "    onGenerate: (PromptDto) -> Unit,\n" not in t:
    t = t.replace(
        "    onSimilarClick: (PromptDto) -> Unit,\n) {",
        "    onSimilarClick: (PromptDto) -> Unit,\n"
        "    onGenerate: (PromptDto) -> Unit,\n"
        ") {",
        1,
    )

marker = '''        item(key = "prompt-${item.id}") {
'''
cta = '''        if (!isVideoPrompt) {
            item(key = "generate-${item.id}") {
                Surface(
                    onClick = { onGenerate(item) },
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 5.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF21152F),
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF70479A)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFF14251A)) {
                            Text(
                                "پرداخت امن بازار",
                                color = Color(0xFF87D99E),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("با چهره خودت بساز", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("عکس خودت را انتخاب کن؛ همین پرامپت خودکار اجرا می‌شود", color = MutedText, fontSize = 9.sp, textAlign = TextAlign.Right)
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = Color(0xFF7C3AED)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                        }
                    }
                }
            }
        }

''' + marker
if 'item(key = "generate-${item.id}")' not in t:
    if marker not in t:
        raise SystemExit("Prompt detail prompt marker not found")
    t = t.replace(marker, cta, 1)

p.write_text(t, encoding="utf-8")

# ---------------------------------------------------------------------------
# GitHub Actions release artifact names
# ---------------------------------------------------------------------------
p = ROOT / ".github/workflows/build-apk.yml"
t = p.read_text(encoding="utf-8")
t = t.replace("promptAll-v3.0.1-bazaar.apk", "promptAll-v3.10.0-bazaar.apk")
t = t.replace("promptAll-v3.0.1-bazaar.aab", "promptAll-v3.10.0-bazaar.aab")
t = t.replace("mapping-v3.0.1.txt", "mapping-v3.10.0.txt")
t = t.replace("promptAll-v3.0.1-bazaar-release", "promptAll-v3.10.0-bazaar-release")
p.write_text(t, encoding="utf-8")

print("PromptAll Android v3.10.0 AI/Bazaar patch applied successfully.")
