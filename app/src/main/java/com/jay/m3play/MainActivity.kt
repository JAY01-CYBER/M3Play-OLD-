/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalWindowInfo
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import dev.seaglass.compose.LocalSeaGlassStageState
import dev.seaglass.compose.LocalSeaGlassTierOverride
import dev.seaglass.compose.SeaGlassPillNavBar
import dev.seaglass.compose.SeaGlassTopAppBar
import dev.seaglass.compose.SeaGlassIconButton
import dev.seaglass.compose.SeaGlassContentRole
import dev.seaglass.compose.rememberSeaGlassStageState
import dev.seaglass.compose.seaGlassBackdrop
import dev.seaglass.compose.toSeaGlassMaterials
import dev.seaglass.core.SeaGlassDeviceTier
import dev.seaglass.compose.SeaGlassGlyph
import dev.seaglass.core.SeaGlassMaterial
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.asDrawable
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.jay.innertube.YouTube
import com.jay.innertube.models.SongItem
import com.jay.innertube.models.WatchEndpoint
import com.jay.m3play.constants.AppBarHeight
import com.jay.m3play.constants.DarkModeKey
import com.jay.m3play.constants.DefaultOpenTabKey
import com.jay.m3play.constants.DisableScreenshotKey
import com.jay.m3play.constants.DynamicThemeKey
import com.jay.m3play.constants.MiniPlayerHeight
import com.jay.m3play.constants.NavigationBarAnimationSpec
import com.jay.m3play.constants.NavigationBarHeight
import com.jay.m3play.constants.PauseSearchHistoryKey
import com.jay.m3play.constants.PlayerBackgroundStyle
import com.jay.m3play.constants.PlayerBackgroundStyleKey
import com.jay.m3play.constants.PureBlackKey
import com.jay.m3play.constants.SearchSource
import com.jay.m3play.constants.SearchSourceKey
import com.jay.m3play.constants.SlimNavBarKey
import com.jay.m3play.constants.StopMusicOnTaskClearKey
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.db.entities.SearchHistory
import com.jay.m3play.extensions.toEnum
import com.jay.m3play.models.toMediaMetadata
import com.jay.m3play.playback.DownloadUtil
import com.jay.m3play.playback.MusicService
import com.jay.m3play.playback.MusicService.MusicBinder
import com.jay.m3play.playback.PlayerConnection
import com.jay.m3play.playback.queues.YouTubeQueue
import com.jay.m3play.ui.component.AvatarPreferenceManager
import com.jay.m3play.ui.component.AvatarSelection
import com.jay.m3play.ui.component.BottomSheetMenu
import com.jay.m3play.ui.component.IconButton
import com.jay.m3play.ui.component.LocalMenuState
import com.jay.m3play.ui.component.LocaleManager
import com.jay.m3play.ui.utils.AppMotion
import com.jay.m3play.ui.component.SwitchPreference
import com.jay.m3play.ui.component.TopSearch
import com.jay.m3play.ui.component.rememberBottomSheetState
import com.jay.m3play.ui.component.shimmer.ShimmerTheme
import com.jay.m3play.ui.menu.YouTubeSongMenu
import com.jay.m3play.ui.player.BottomSheetPlayer
import com.jay.m3play.ui.screens.Screens
import com.jay.m3play.ui.screens.navigationBuilder
import com.jay.m3play.ui.screens.search.LocalSearchScreen
import com.jay.m3play.ui.screens.search.OnlineSearchScreen
import com.jay.m3play.ui.screens.settings.DarkMode
import com.jay.m3play.ui.screens.settings.NavigationTab
import com.jay.m3play.ui.theme.ColorSaver
import com.jay.m3play.ui.theme.DefaultThemeColor
import com.jay.m3play.ui.theme.OpenTuneTheme
import com.jay.m3play.ui.theme.extractThemeColor
import com.jay.m3play.ui.utils.appBarScrollBehavior
import com.jay.m3play.ui.utils.backToMain
import com.jay.m3play.ui.utils.resetHeightOffset
import com.jay.m3play.utils.CrashGuard
import com.jay.m3play.utils.SyncUtils
import com.jay.m3play.utils.Updater
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get
import com.jay.m3play.utils.rememberEnumPreference
import com.jay.m3play.utils.rememberPreference
import com.jay.m3play.utils.reportException
import com.jay.m3play.viewmodels.NewReleaseViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service is MusicBinder) {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)

    /** Shows the stack trace of the previous crash (saved by App) so it can be copied and shared. */
    private fun showLastCrashIfAny() {
        val crashFile = filesDir.resolve(App.CRASH_LOG_FILE)
        if (!crashFile.exists()) return
        val trace = runCatching { crashFile.readText() }.getOrNull()
        runCatching { crashFile.delete() }
        if (trace.isNullOrBlank()) return
        runCatching {
            android.app.AlertDialog.Builder(this)
                .setTitle("M3 Play crashed last time")
                .setMessage(trace.take(3500))
                .setPositiveButton("Copy") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            startService(Intent(this, MusicService::class.java))
        } catch (e: Exception) {
            // e.g. background-start restrictions; binding below still creates the service
            e.printStackTrace()
        }
        try {
            bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        try {
            unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dataStore.get(StopMusicOnTaskClearKey, false) && playerConnection?.isPlaying?.value == true && isFinishing) {
            stopService(Intent(this, MusicService::class.java))
            unbindService(serviceConnection)
            playerConnection = null
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.getInstance(newBase).applyLocaleToContext(newBase))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLastCrashIfAny()
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Keep Android's gesture-navigation area visually continuous with the app surface.
        // The short horizontal gesture handle itself is owned by Android and remains visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        intent?.let { handlevideoIdIntent(it) }

        setContent {
            LaunchedEffect(Unit) {
                if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                    Updater.getLatestVersionName().onSuccess { latestVersionName = it }
                }
            }

            var showFullscreenLyrics by rememberSaveable { mutableStateOf(false) }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }
            LaunchedEffect(useDarkTheme) { setSystemBarAppearance(useDarkTheme) }
            
            var themeColor by rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(DefaultThemeColor) }

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    playerConnection.service.currentMediaMetadata.collectLatest { song ->
                        themeColor = if (song != null) {
                            withContext(Dispatchers.IO) {
                                val result = imageLoader.execute(
                                    ImageRequest.Builder(this@MainActivity)
                                        .data(song.thumbnailUrl)
                                        .size(128)
                                        .allowHardware(false)
                                        .build()
                                )
                                (result.image?.asDrawable(resources) as? BitmapDrawable)?.bitmap?.extractThemeColor() ?: DefaultThemeColor
                            }
                        } else {
                            DefaultThemeColor
                        }
                    }
                }
            }

            OpenTuneTheme(darkTheme = useDarkTheme, pureBlack = pureBlack, themeColor = themeColor) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // SeaGlass' documented two-layer model: record a real backdrop first,
                    // then draw the app's foreground surfaces against that shared GPU stage.
                    val seaGlassStage = rememberSeaGlassStageState()
                    // This release deliberately opts into the full GPU stage for a controlled
                    // device test. CrashGuard enables it once for this version, then remembers
                    // a native failure and falls back on subsequent launches.
                    val gpuBackdropEnabled = CrashGuard.gpuBackdropEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    val stageArtworkUrl = playerConnection
                        ?.mediaMetadata
                        ?.collectAsStateWithLifecycle(initialValue = null)
                        ?.value
                        ?.thumbnailUrl
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (gpuBackdropEnabled) Modifier.seaGlassBackdrop(seaGlassStage) else Modifier)
                    ) {
                        // SeaGlass repository's two-layer contract: this independently rendered
                        // scene is recorded once; foreground glass surfaces sample the shared stage.
                        // The active track artwork gives the stage real, changing visual content
                        // without trying to record the foreground UI into itself.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (gpuBackdropEnabled && !stageArtworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = stageArtworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.30f)
                                        .blur(42.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.045f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.025f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()

                    val navigationItems = remember { Screens.MainScreens }
                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val defaultOpenTab = remember { dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME) }
                    val tabOpenedFromShortcut = remember {
                        when (intent?.action) {
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            ACTION_EXPLORE -> NavigationTab.EXPLORE
                            else -> null
                        }
                    }

                    val topLevelScreens = listOf(Screens.Home.route, Screens.Explore.route, Screens.Library.route, "settings")
                    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
                    var active by rememberSaveable { mutableStateOf(false) }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                            if (dataStore[PauseSearchHistoryKey] != true) {
                                database.query { insert(SearchHistory(query = it)) }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember { mutableStateOf(intent?.action == ACTION_SEARCH) }

                    val shouldShowSearchBar = remember(active, navBackStackEntry) {
                        active || navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith("search/") == true
                    }

                    val shouldShowNavigationBar = remember(navBackStackEntry, active) {
                        navBackStackEntry?.destination?.route == null ||
                        navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } && !active
                    }

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = "",
                    )

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = bottomInset + (if (shouldShowNavigationBar) NavigationBarHeight else 0.dp) + MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                    val playerAwareWindowInsets = remember(bottomInset, shouldShowNavigationBar, playerBottomSheetState.isDismissed) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar) bottom += NavigationBarHeight
                        if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                        // Extra breathing room keeps the final Home feed row clear of the floating controls.
                        bottom += 24.dp
                        windowsInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                    }

                    appBarScrollBehavior(canScroll = {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    })

                    val searchBarScrollBehavior = appBarScrollBehavior(canScroll = {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    })
                    val topAppBarScrollBehavior = appBarScrollBehavior(canScroll = {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    })

                    LaunchedEffect(navBackStackEntry) {
                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query") ?: ""
                            val searchQuery = withContext(Dispatchers.IO) {
                                if (rawQuery.contains("%")) rawQuery else URLDecoder.decode(rawQuery, "UTF-8")
                            }
                            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }
                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()
                    }
                    
                    LaunchedEffect(active) {
                        if (active) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(playerConnection) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState) {
                        val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                    mediaItem != null && playerBottomSheetState.isDismissed
                                ) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        player.addListener(listener)
                        onDispose { player.removeListener(listener) }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar = !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
                    }

                    val coroutineScope = rememberCoroutineScope()
                    // State used by the SeaGlass navigation's Explore double-tap behavior.
                    var lastTapTime by rememberSaveable { mutableLongStateOf(0L) }
                    var lastTappedIcon by rememberSaveable { mutableStateOf<Int?>(null) }
                    var navigateToExplore by remember { mutableStateOf(false) }
                    var sharedSong: SongItem? by remember { mutableStateOf(null) }
                    
                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return@Consumer
                            when (val path = uri.pathSegments.firstOrNull()) {
                                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                                    if (playlistId.startsWith("OLAK5uy_")) {
                                        coroutineScope.launch {
                                            YouTube.albumSongs(playlistId).onSuccess { songs ->
                                                songs.firstOrNull()?.album?.id?.let { browseId -> navController.navigate("album/$browseId") }
                                            }.onFailure { reportException(it) }
                                        }
                                    } else {
                                        navController.navigate("online_playlist/$playlistId")
                                    }
                                }
                                "browse" -> uri.lastPathSegment?.let { browseId -> navController.navigate("album/$browseId") }
                                "channel", "c" -> uri.lastPathSegment?.let { artistId -> navController.navigate("artist/$artistId") }
                                else -> when {
                                    path == "watch" -> uri.getQueryParameter("v")
                                    uri.host == "youtu.be" -> path
                                    else -> null
                                }?.let { videoId ->
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            YouTube.queue(listOf(videoId))
                                        }.onSuccess {
                                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = it.firstOrNull()?.id), it.firstOrNull()?.toMediaMetadata()))
                                        }.onFailure { reportException(it) }
                                    }
                                }
                            }
                        }
                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    val currentTitle = remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Explore.route -> R.string.explore
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }
                    val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                    val insetBg = if (playerBottomSheetState.progress > 0f) Color.Transparent else MaterialTheme.colorScheme.surface

                    // AGSL_FULL is SeaGlass' Android 13+ RenderNode/RuntimeShader GPU tier.
                    // ExitInfoReporter automatically falls back to LIGHTWEIGHT_TINT after a new
                    // native process crash, without disabling ordinary SeaGlass surfaces.
                    LaunchedEffect(gpuBackdropEnabled) {
                        if (gpuBackdropEnabled) CrashGuard.markGpuSessionStarted(this@MainActivity)
                    }
                    CompositionLocalProvider(
                        LocalSeaGlassTierOverride provides if (gpuBackdropEnabled) SeaGlassDeviceTier.AGSL_FULL else SeaGlassDeviceTier.LIGHTWEIGHT_TINT,
                        LocalSeaGlassStageState provides if (gpuBackdropEnabled) seaGlassStage else null,
                        LocalDatabase provides database,
                        LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                    ) {
                        Scaffold(
                            topBar = {},
                            bottomBar = {
                                Box {
                                    BottomSheetPlayer(state = playerBottomSheetState, navController = navController, onOpenFullscreenLyrics = { showFullscreenLyrics = true }, modifier = Modifier.fillMaxSize())

                                    val configuration = LocalConfiguration.current
                                    val isTabletLandscape = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value } >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                    val shouldShowBottomNav = true

                                    if (shouldShowBottomNav) {
                                        val selectedNavIndex = navigationItems.indexOfFirst { screen ->
                                            navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                        }.coerceAtLeast(0)
                                        // Derive the navigation glass from the active Material 3
                                        // scheme so dynamic/theme palettes stay visually consistent.
                                        val navGlassMaterial = MaterialTheme.colorScheme
                                            .toSeaGlassMaterials()
                                            .elevatedGlass
                                            .copy(cornerRadiusDp = 30f)
                                        SeaGlassPillNavBar(
                                            items = navigationItems.map { screen ->
                                                val glyph = when (screen.titleId) {
                                                    R.string.home -> SeaGlassGlyph.Home
                                                    R.string.explore -> SeaGlassGlyph.Search
                                                    R.string.filter_library -> SeaGlassGlyph.Layers
                                                    else -> null
                                                }
                                                glyph to stringResource(screen.titleId)
                                            },
                                            selectedIndex = selectedNavIndex,
                                            onSelect = { index ->
                                                val screen = navigationItems.getOrNull(index) ?: return@SeaGlassPillNavBar
                                                val isSelected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                                val currentTapTime = System.currentTimeMillis()
                                                val timeSinceLastTap = currentTapTime - lastTapTime
                                                val isDoubleTap = screen.titleId == R.string.explore && lastTappedIcon == R.string.explore && timeSinceLastTap < 300L
                                                lastTapTime = currentTapTime
                                                lastTappedIcon = screen.titleId

                                                if (screen.titleId == R.string.explore) {
                                                    if (isDoubleTap) {
                                                        onActiveChange(true)
                                                        navigateToExplore = false
                                                    } else {
                                                        navigateToExplore = true
                                                        coroutineScope.launch {
                                                            delay(300L)
                                                            if (navigateToExplore) {
                                                                try { navigateToScreen(navController, screen) } catch (e: Exception) {
                                                                    Timber.tag("Navigation").e(e, "%s", "Error navigating to screen")
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else if (isSelected) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    coroutineScope.launch {
                                                        try { searchBarScrollBehavior.state.resetHeightOffset() } catch (e: Exception) {
                                                            Timber.tag("ScrollBehavior").e(e, "%s", "Error resetting scroll")
                                                        }
                                                    }
                                                } else {
                                                    try { navigateToScreen(navController, screen) } catch (e: Exception) {
                                                        Timber.tag("Navigation").e(e, "%s", "Error navigating to screen")
                                                    }
                                                }
                                            },
                                            stageState = if (gpuBackdropEnabled) seaGlassStage else null,
                                            material = navGlassMaterial,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset {
                                                    if (navigationBarHeight == 0.dp) {
                                                        IntOffset(x = 0, y = (bottomInset + NavigationBarHeight).roundToPx())
                                                    } else {
                                                        val slideOffset = (bottomInset + NavigationBarHeight) * playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                        val hideOffset = (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                        IntOffset(x = 0, y = (slideOffset + hideOffset).roundToPx())
                                                    }
                                                }
                                                .padding(start = 16.dp, end = 16.dp, bottom = bottomInsetDp + 8.dp)
                                        )
                                        Box(modifier = Modifier.background(insetBg).fillMaxWidth().align(Alignment.BottomCenter).height(bottomInsetDp))
                                    } else {
                                        Box(modifier = Modifier.background(insetBg).fillMaxWidth().align(Alignment.BottomCenter).height(bottomInsetDp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().nestedScroll(searchBarScrollBehavior.nestedScrollConnection).background(MaterialTheme.colorScheme.surface)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) { NavigationTab.HOME -> Screens.Home; NavigationTab.EXPLORE -> Screens.Explore; NavigationTab.LIBRARY -> Screens.Library }.route,
                                // Match ArchiveTune's NavHost transitions: top-level
                                // destinations fade only; nested destinations use a
                                // half-width horizontal parallax with a soft fade.
                                enterTransition = {
                                    if (initialState.destination.route in topLevelScreens &&
                                        targetState.destination.route in topLevelScreens
                                    ) {
                                        fadeIn(tween(250))
                                    } else {
                                        fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                    }
                                },
                                exitTransition = {
                                    if (initialState.destination.route in topLevelScreens &&
                                        targetState.destination.route in topLevelScreens
                                    ) {
                                        fadeOut(tween(200))
                                    } else {
                                        fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                    }
                                },
                                popEnterTransition = {
                                    if ((initialState.destination.route in topLevelScreens ||
                                            initialState.destination.route?.startsWith("search/") == true) &&
                                        targetState.destination.route in topLevelScreens
                                    ) {
                                        fadeIn(tween(250))
                                    } else {
                                        fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                    }
                                },
                                popExitTransition = {
                                    // Match ArchiveTune: during a pop/back gesture the
                                    // outgoing page fades away without an extra slide.
                                    // Sliding both entries caused the old gesture animation
                                    // to peek through while the app's own back button looked fine.
                                    fadeOut(tween(200))
                                },
                                modifier = Modifier
                                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                                    .nestedScroll(if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } || navBackStackEntry?.destination?.route?.startsWith("search/") == true) searchBarScrollBehavior.nestedScrollConnection else topAppBarScrollBehavior.nestedScrollConnection)
                            ) {
                                navigationBuilder(navController, topAppBarScrollBehavior, latestVersionName)
                            }
                        }


                        // Floating SeaGlass chrome, rendered as a sibling over app content.
                        if (shouldShowTopBar) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxSize()
                                    .zIndex(3f)
                            ) {
                                val playerBackground by rememberEnumPreference(key = PlayerBackgroundStyleKey, defaultValue = PlayerBackgroundStyle.DEFAULT)
                                if (shouldShowTopBar) {
                                    val topBarGlassMaterial = MaterialTheme.colorScheme
                                        .toSeaGlassMaterials()
                                        .elevatedGlass
                                    SeaGlassTopAppBar(
                                        title = stringResource(R.string.app_name),
                                        stageState = if (gpuBackdropEnabled) seaGlassStage else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding(),
                                        navigationGlyph = null,
                                        onNavigationClick = null,
                                        material = topBarGlassMaterial,
                                        actions = {
                                            val context = LocalContext.current
                                            val viewModel: NewReleaseViewModel = hiltViewModel()
                                            val hasNewReleases by viewModel.hasNewReleases.collectAsStateWithLifecycle()

                                            Box(modifier = Modifier.size(44.dp)) {
                                                SeaGlassIconButton(
                                                    glyph = SeaGlassGlyph.Bell,
                                                    onClick = {
                                                        try {
                                                            viewModel.markNewReleasesAsSeen()
                                                            navController.navigate("new_release")
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            Toast.makeText(context, R.string.navigation_error, Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    stageState = if (gpuBackdropEnabled) seaGlassStage else null,
                                                    size = 40.dp,
                                                    material = topBarGlassMaterial
                                                )
                                                if (hasNewReleases) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(top = 3.dp, end = 3.dp)
                                                            .size(9.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                                                    )
                                                }
                                            }
                                            SeaGlassIconButton(
                                                glyph = SeaGlassGlyph.Search,
                                                onClick = { onActiveChange(true) },
                                                stageState = if (gpuBackdropEnabled) seaGlassStage else null,
                                                size = 40.dp,
                                                material = topBarGlassMaterial
                                            )
                                            ProfileIconWithUpdateBadge(
                                                currentVersion = BuildConfig.VERSION_NAME,
                                                onProfileClick = {
                                                    try {
                                                        navController.navigate("settings")
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        Toast.makeText(context, R.string.navigation_error, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    )
                                }

                                val isSearchRoute = navBackStackEntry?.destination?.route?.startsWith("search/") == true

                                if (active || isSearchRoute) {
                                    TopSearch(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        active = active,
                                        onActiveChange = onActiveChange,
                                        placeholder = { Text(text = stringResource(when (searchSource) { SearchSource.LOCAL -> R.string.search_library; SearchSource.ONLINE -> R.string.search_yt_music })) },
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        active -> onActiveChange(false)
                                                        !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> navController.navigateUp()
                                                        else -> onActiveChange(true)
                                                    }
                                                },
                                                onLongClick = {
                                                    when {
                                                        active -> {}
                                                        !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> navController.backToMain()
                                                        else -> {}
                                                    }
                                                },
                                            ) {
                                                Icon(painterResource(if (active || !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) R.drawable.arrow_back else R.drawable.search), contentDescription = null)
                                            }
                                        },
                                        trailingIcon = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (active) {
                                                    if (query.text.isNotEmpty()) {
                                                        IconButton(onClick = { onQueryChange(TextFieldValue("")) }) {
                                                            Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                                                        }
                                                    }
                                                    IconButton(onClick = { searchSource = if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE }) {
                                                        Icon(painter = painterResource(when (searchSource) { SearchSource.LOCAL -> R.drawable.library_music; SearchSource.ONLINE -> R.drawable.language }), contentDescription = stringResource(when (searchSource) { SearchSource.LOCAL -> R.string.search_online; SearchSource.ONLINE -> R.string.search_library }))
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.focusRequester(searchBarFocusRequester).align(Alignment.TopCenter).fillMaxWidth(),
                                        focusRequester = searchBarFocusRequester
                                    ) {
                                        Crossfade(targetState = searchSource, label = "search_content_transition", modifier = Modifier.fillMaxSize().padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp).navigationBarsPadding()) { currentSearchSource ->
                                            when (currentSearchSource) {
                                                SearchSource.LOCAL -> LocalSearchScreen(query = query.text, navController = navController, onDismiss = { onActiveChange(false) }, pureBlack = pureBlack)
                                                SearchSource.ONLINE -> OnlineSearchScreen(
                                                    query = query.text,
                                                    onQueryChange = onQueryChange,
                                                    navController = navController,
                                                    onSearch = { searchQuery ->
                                                        try {
                                                            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
                                                            if (dataStore[PauseSearchHistoryKey] != true) database.query { insert(SearchHistory(query = searchQuery)) }
                                                        } catch (e: Exception) { Timber.tag("SearchNavigation").e(e, "%s", "Error navigating to search: ${e.message}") }
                                                    },
                                                    onDismiss = { onActiveChange(false) },
                                                )
                                            }
                                        }
                                    }
                                }
                            
                            }
                        }


                        AnimatedVisibility(
                            visible = showFullscreenLyrics,
                            modifier = Modifier.fillMaxSize(),
                            enter = AppMotion.sheetEnter(),
                            exit = AppMotion.sheetExit()
                        ) {
                            com.jay.m3play.ui.screens.LyricsScreen(
                                onDismiss = { showFullscreenLyrics = false }
                            )
                        }

                        BottomSheetMenu(state = LocalMenuState.current, modifier = Modifier.align(Alignment.BottomCenter))

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(onDismissRequest = { sharedSong = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                                    Surface(modifier = Modifier.padding(24.dp), shape = RoundedCornerShape(16.dp), color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            YouTubeSongMenu(song = song, navController = navController, onDismiss = { sharedSong = null })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                            openSearchImmediately = false
                        }
                    }
                }
            }
        }
    }

    private fun navigateToScreen(navController: NavHostController, screen: Screens) {
        navController.navigate(screen.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
    }

    private fun handlevideoIdIntent(intent: Intent) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        when {
            uri.pathSegments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
            else -> null
        }?.let { videoId ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { YouTube.queue(listOf(videoId)) }.onSuccess {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = it.firstOrNull()?.id), it.firstOrNull()?.toMediaMetadata()))
                }.onFailure { reportException(it) }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply { isAppearanceLightStatusBars = !isDark; isAppearanceLightNavigationBars = !isDark }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        // Let the app's own themed surface draw behind the gesture area. A fixed
        // warm tint here caused a visible color mismatch with dynamic Material themes.
        window.navigationBarColor = Color.Transparent.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    companion object { const val ACTION_SEARCH = "com.jay.m3play.action.SEARCH"; const val ACTION_EXPLORE = "com.jay.m3play.action.EXPLORE"; const val ACTION_LIBRARY = "com.jay.m3play.action.LIBRARY" }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }

@Composable
fun NotificationPermissionPreference() {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }

    val checkNotificationPermission = remember {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        permissionGranted = isGranted
        if (!isGranted) Timber.tag("NotificationPermission").d("%s", "Permiso de notificaciones denegado")
    }

    LaunchedEffect(Unit) { permissionGranted = checkNotificationPermission() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionGranted = checkNotificationPermission() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SwitchPreference(
        title = { Text(stringResource(R.string.notification)) },
        icon = { Icon(painter = painterResource(id = if (permissionGranted) R.drawable.notification_on else R.drawable.notification_off), contentDescription = stringResource(if (permissionGranted) R.string.notifications_enabled else R.string.notifications_disabled)) },
        checked = permissionGranted,
        onCheckedChange = { checked ->
            when {
                checked && !permissionGranted -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotificationSettings(context)
                }
                !checked && permissionGranted -> openNotificationSettings(context)
            }
        }
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
    }
    try { context.startActivity(intent) } catch (e: ActivityNotFoundException) {
        Timber.tag("NotificationSettings").e(e, "%s", "No se pudo abrir configuración de notificaciones")
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

suspend fun checkForUpdates(): String? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/JAY01-CYBER/M3-Play/releases/latest")
        val connection = url.openConnection()
        connection.connect()
        val json = connection.getInputStream().bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)
        return@withContext jsonObject.getString("tag_name")
    } catch (e: Exception) { e.printStackTrace(); return@withContext null }
}

fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val remote = remoteVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val current = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrNull(i) ?: 0
        val c = current.getOrNull(i) ?: 0
        if (r > c) return true
        if (r < c) return false
    }
    return false
}

@Composable
fun ProfileIconWithUpdateBadge(currentVersion: String, onProfileClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val avatarManager = remember { AvatarPreferenceManager(context) }
    val currentSelection by avatarManager.getAvatarSelection.collectAsStateWithLifecycle(initialValue = AvatarSelection.Default)
    var showUpdateBadge by remember { mutableStateOf(false) }
    val updatedOnClick = rememberUpdatedState(onProfileClick)

    val infiniteTransition = rememberInfiniteTransition(label = "badge_animation")
    val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "scale")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.6f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "alpha")

    LaunchedEffect(currentVersion) {
        try {
            val latestVersion = withContext(Dispatchers.IO) { checkForUpdates() }
            showUpdateBadge = latestVersion?.let { isNewerVersion(it, currentVersion) } ?: false
        } catch (e: Exception) { Timber.tag("ProfileIcon").e("Error checking for updates: ${e.message}") }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).clip(CircleShape).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { try { updatedOnClick.value() } catch (e: Exception) { e.printStackTrace() } }) {
        Box(contentAlignment = Alignment.Center) {
            when (currentSelection) {
                is AvatarSelection.Custom -> AsyncImage(model = ImageRequest.Builder(context).data((currentSelection as AvatarSelection.Custom).uri.toUri()).crossfade(true).error(R.drawable.person).placeholder(R.drawable.person).build(), contentDescription = "Avatar personalizado", modifier = modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                is AvatarSelection.DiceBear -> AsyncImage(model = ImageRequest.Builder(context).data((currentSelection as AvatarSelection.DiceBear).url).crossfade(true).error(R.drawable.person).placeholder(R.drawable.person).build(), contentDescription = "Avatar DiceBear", modifier = modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else -> Icon(painter = painterResource(R.drawable.person), contentDescription = "Avatar predeterminado", modifier = modifier)
            }
        }
        if (showUpdateBadge) {
            Box(modifier = Modifier.align(Alignment.Center).size(28.dp)) {
                Box(modifier = Modifier.fillMaxSize().scale(scale).background(brush = Brush.radialGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f * alpha), Color.Transparent)), shape = CircleShape))
                Box(modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = CircleShape))
                Icon(painter = painterResource(R.drawable.update), contentDescription = "Actualización disponible", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).align(Alignment.Center).scale(scale).alpha(alpha))
            }
        }
    }
}
