package com.bandary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bandary.presentation.navigation.Screen
import com.bandary.presentation.navigation.bottomNavItems
import com.bandary.presentation.ui.admin.AdminDashboardScreen
import com.bandary.presentation.ui.auth.AuthViewModel
import com.bandary.presentation.ui.feedback.FeedbackAdminScreen
import com.bandary.presentation.ui.feedback.FeedbackScreen
import com.bandary.presentation.ui.friends.FriendCalendarScreen
import com.bandary.presentation.ui.friends.FriendsScreen
import com.bandary.presentation.ui.importcal.ImportCalendarScreen
import com.bandary.presentation.ui.auth.LoginScreen
import com.bandary.presentation.ui.calendar.CalendarHubScreen
import com.bandary.presentation.ui.free.FreeEventsScreen
import com.bandary.presentation.ui.profile.ProfileScreen
import com.bandary.presentation.ui.settings.SettingsScreen
import com.bandary.presentation.ui.settings.SettingsViewModel
import com.bandary.presentation.ui.share.TicketShareScreen
import com.bandary.presentation.ui.shows.AddShowScreen
import com.bandary.presentation.ui.shows.ConcertDetailScreen
import com.bandary.presentation.ui.shows.ShowsScreen
import com.bandary.presentation.ui.stats.ArtistDetailScreen
import com.bandary.presentation.ui.stats.StatsScreen
import com.bandary.presentation.ui.theme.BandaryTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var firestoreService: com.bandary.data.remote.FirestoreService
    @javax.inject.Inject lateinit var settingsDataStore: com.bandary.data.local.SettingsDataStore
    @javax.inject.Inject lateinit var concertRepository: com.bandary.data.repository.ConcertRepository

    // 通知點擊帶進來的深連結目標（route）。onNewIntent 時更新，Compose 端 collect 後導航。
    private val deepLinkRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    // 多語言：套用使用者在「我的 → 語言」選的顯示語系（空＝跟隨系統）。
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.bandary.data.local.LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 啟動畫面要在 super.onCreate 之前掛上，否則來不及接管第一幀。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 更新 lastActiveAt（未登入不做事），讓後台能統計每日上線人數。
        lifecycleScope.launch {
            runCatching { firestoreService.touchLastActive() }
            // 補登記這台裝置的推播 token。onNewToken 只在 token 換新時才觸發，
            // 「安裝時還沒登入、後來才登入」那條路它完全不會被呼叫到，
            // 那個帳號就永遠收不到指定對象的公告。
            runCatching {
                // 用 Tasks.await（專案沒有 kotlinx-coroutines-play-services，
                // 不為了一行程式多拉一個相依）。已經在 IO 的 coroutine 裡，阻塞是安全的。
                // FirebaseMessaging.getInstance()/.token 在 25.1.0 已棄用，
                // 與 BandaryMessagingService.onNewToken 是同一組遷移（見那裡的說明）。
                @Suppress("DEPRECATION")
                val t = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.google.android.gms.tasks.Tasks.await(
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    )
                }
                firestoreService.registerPushToken(t)
            }
            // 沒登入的人在 users 裡沒有文件，後台完全看不到他們——
            // 於是「註冊轉換率」永遠是 100%，那個數字沒有意義。
            // 另外記一筆匿名安裝（只有隨機 id + 時間 + 版本），登入後就不再更新。
            runCatching {
                val version = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                }.getOrDefault("")
                firestoreService.touchGuestInstall(settingsDataStore.installId(), version)
            }
        }
        // 開機自動修復：本機讀不到的演唱會圖片（權限被收回 / 相簿搬移刪除原圖）
        // 若雲端有備份就悄悄補回來，不需要使用者手動按「從雲端還原」。
        lifecycleScope.launch {
            runCatching {
                val concerts = concertRepository.getAllConcerts().first()
                val repaired = firestoreService.repairLocalImages(concerts)
                repaired.forEachIndexed { i, c -> if (c !== concerts[i]) concertRepository.updateImagesOnly(c) }
            }
        }
        // 垃圾桶：清掉超過 7 天的軟刪除演唱會。
        lifecycleScope.launch {
            runCatching { concertRepository.purgeExpiredTrash() }
        }
        deepLinkRoute.value = routeFromIntent(intent)
        setContent {
            BandaryRoot(deepLinkRoute)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRoute.value = routeFromIntent(intent)
    }

    /**
     * 把通知 Intent 的 extra 轉成要導航的 route（沒有就 null）。
     *
     * 讀完就把 extra 從 intent 上清掉：Activity 的 intent 會一直留著，
     * 每次重建（例如轉螢幕）都會再跑一次 onCreate → 又導航回通知的目標頁，
     * 把使用者正在看的畫面踢掉。deep link 是一次性的，用過就該消耗掉。
     */
    private fun routeFromIntent(intent: android.content.Intent?): String? {
        val route = resolveRoute(intent)
        if (route != null) {
            intent?.removeExtra("editConcertId")
            intent?.removeExtra("concertId")
            intent?.removeExtra("navTarget")
        }
        return route
    }

    private fun resolveRoute(intent: android.content.Intent?): String? {
        // 別的 App 分享文字過來（主要是 Gmail 的訂票確認信）→ 開新增表單並解析。
        //
        // 標題（EXTRA_SUBJECT）也一起餵進去：信件主旨常常就是最完整的活動名稱
        //（「【購票成功】○○ 2026 巡迴演唱會 台北場」），而內文裡的名稱反而
        // 常被切成好幾段或夾在表格裡。放在第一行，解析器優先讀得到。
        if (intent?.action == android.content.Intent.ACTION_SEND &&
            intent.type?.startsWith("text/") == true
        ) {
            val subject = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT).orEmpty()
            val body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty()
            val merged = listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n")
            if (merged.isNotBlank()) {
                com.bandary.data.util.SharedTicketText.set(merged)
                // 消耗掉，否則轉螢幕重建會再分享一次。
                intent.removeExtra(android.content.Intent.EXTRA_TEXT)
                intent.removeExtra(android.content.Intent.EXTRA_SUBJECT)
                return Screen.AddShow.createRoute()
            }
        }
        // 補紀錄推播（E2-2）→ 直接開編輯頁。
        val editId = intent?.getStringExtra("editConcertId")
        if (!editId.isNullOrBlank()) return Screen.AddShow.createRoute(concertId = editId)
        val concertId = intent?.getStringExtra("concertId")
        if (!concertId.isNullOrBlank()) return Screen.ConcertDetail.createRoute(concertId)
        return when (intent?.getStringExtra("navTarget")) {
            "songs" -> Screen.Calendar.route      // 每日選歌提醒 → 日曆（可切選歌）
            "events" -> Screen.FreeEvents.route   // 週末免費活動 → 活動
            "stats" -> Screen.Stats.route         // 月結小回顧（E2-5）→ 統計頁
            "wrapped" -> Screen.Wrapped.createRoute(0)  // 年度回顧（E2-7）→ Wrapped story
            "friends" -> Screen.Friends.route     // 好友社交通知（E2-12）→ 好友頁
            "followed" -> Screen.FollowedArtists.route  // 追蹤藝人有新活動 → 追蹤中的藝人
            "wishlist" -> Screen.Shows.route      // 開賣提醒 → 我的演唱會（即將到來，未購票會標記）
            "pro" -> Screen.ProFeatures.route     // 試用快到期 → Pro 方案頁（看得到目前方案與取消入口）
            "announcements" -> Screen.Profile.route  // 官方通知推播 → 我的（收信匣在這頁）
            else -> null
        }
    }
}

/**
 * 沒有自訂起始分頁時的預設落點。
 *
 * 有紀錄 → 「我的演唱會」：這個 App 的核心價值是「我累積了什麼」，開場就該看到自己的東西。
 * 沒紀錄 → 「活動」：全新使用者的演唱會頁是空的，先給他看得到、可以一鍵加入的內容，
 *          才有機會產生第一筆紀錄。
 */
private fun defaultStartRoute(hasConcerts: Boolean): String =
    if (hasConcerts) Screen.Shows.route else Screen.FreeEvents.route

@Composable
private fun BandaryRoot(deepLinkRoute: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null)) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.state.collectAsState()
    val onboardingViewModel: com.bandary.presentation.ui.onboarding.OnboardingViewModel = hiltViewModel()
    val shouldShowOnboarding by onboardingViewModel.shouldShowOnboarding.collectAsState()

    // 依主題決定狀態列圖示/時鐘顏色：亮色模式用深色圖示（否則白字在淺底看不到）。
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = when (settingsState.themeMode) {
        com.bandary.data.local.ThemeMode.SYSTEM -> systemDark
        com.bandary.data.local.ThemeMode.LIGHT -> false
        com.bandary.data.local.ThemeMode.DARK -> true
    }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(useDark) {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !useDark
        }
    }

    BandaryTheme(
        themeMode = settingsState.themeMode,
        palette = settingsState.palette,
        customColorHex = settingsState.customColorHex
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (shouldShowOnboarding == true) {
                com.bandary.presentation.ui.onboarding.OnboardingScreen(
                    onStart = { route -> route?.let { deepLinkRoute.value = it } },
                    onFinish = {}
                )
            } else {
                NotificationPermissionRequester()
                // 起始分頁只在 NavHost 第一次組成時讀一次，所以要等「有沒有紀錄」讀出來再組，
                // 否則會先用 null 的判斷渲染錯的分頁，之後改不掉。讀取很快，畫面只會閃一下底色。
                val hasConcerts by settingsViewModel.hasAnyConcert.collectAsState()
                if (hasConcerts != null) {
                    BandaryNav(
                        deepLinkRoute,
                        startTab = settingsState.startTab,
                        hasConcerts = hasConcerts == true
                    )
                    // 彈出式通知（有新版 / 有沒看過的官方通知）。放在 NavHost 之後、
                    // 同一個 Surface 裡，所以不管使用者停在哪個分頁都會跳。
                    com.bandary.presentation.ui.notice.AppNoticeHost(settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionRequester() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result unused */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun BandaryNav(
    deepLinkRoute: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null),
    startTab: String? = null,
    hasConcerts: Boolean = false
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    // 通知深連結：有目標就導航過去，然後清掉避免重複觸發。
    val pendingRoute = deepLinkRoute.value
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute)
            deepLinkRoute.value = null
        }
    }

    val topLevelRoutes = bottomNavItems.map { it.screen.route }
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    // 造訪過的分頁堆疊（最近的在最後）：讓返回鍵回到「上一個停留的分頁」，而非固定回到我的演唱會。
    val tabHistory = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute in topLevelRoutes) {
            tabHistory.remove(currentRoute)
            tabHistory.add(currentRoute)
        }
    }
    // 返回鍵：在頂層分頁時**不要一按就跳出 App**。
    //   ① 有上一個停留的分頁 → 回上一個分頁
    //   ② 否則不在起始頁 → 先回起始頁
    //   ③ 已在起始頁、也沒有上一頁 → 兩秒內再按一次才離開（第一次只提示）
    val lastBackAt = remember { longArrayOf(0L) }
    // 要跟上面的 startDestination 用同一個判斷，否則返回鍵會把使用者送去一個不是起點的分頁。
    val homeRoute = startTab?.takeIf { r -> bottomNavItems.any { it.screen.route == r } }
        ?: defaultStartRoute(hasConcerts)
    // 用非 "context" 的名字捕捉 Activity：Kotlin 2.2 把 context 當軟關鍵字，
    // 在 lambda 裡直接用 `context as?` 會被誤判成 context 參數宣告。
    val hostActivity = LocalContext.current as? android.app.Activity
    androidx.activity.compose.BackHandler(enabled = currentRoute in topLevelRoutes) {
        when {
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)  // 移除目前分頁
                tabHistory.lastOrNull()?.let { prev ->
                    navController.navigate(prev) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            currentRoute != homeRoute -> {
                navController.navigate(homeRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt[0] < 2000) {
                    hostActivity?.moveTaskToBack(true)  // 退到背景，與一般返回離開一致，不砍掉狀態
                } else {
                    lastBackAt[0] = now
                    hostActivity?.let {
                        android.widget.Toast.makeText(it, "再按一次返回離開", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Don't consume system-bar insets here — each screen's own Scaffold/TopAppBar
        // handles the status bar (top) so the clock isn't overlapped. The bottom
        // NavigationBar applies the gesture-nav inset itself.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon
                                    else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(androidx.compose.ui.res.stringResource(item.labelRes), fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startTab?.takeIf { r -> bottomNavItems.any { it.screen.route == r } }
                ?: defaultStartRoute(hasConcerts),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Shows.route) {
                ShowsScreen(
                    onAddShow = { navController.navigate(Screen.AddShow.createRoute()) },
                    onScanPoster = { navController.navigate(Screen.AddShow.createRoute(scan = true)) },
                    onBulkImport = { navController.navigate(Screen.BulkImport.route) },
                    onShowDetail = { id ->
                        navController.navigate(Screen.ConcertDetail.createRoute(id))
                    },
                    onDuplicateConcert = { id ->
                        navController.navigate(Screen.AddShow.createRoute(duplicateId = id))
                    },
                    onEditConcert = { id ->
                        navController.navigate(Screen.AddShow.createRoute(concertId = id))
                    },
                    onImportCalendar = { navController.navigate(Screen.ImportCalendar.route) },
                    onOpenOverview = { navController.navigate(Screen.Overview.route) }
                )
            }

            composable(Screen.Trash.route) {
                com.bandary.presentation.ui.trash.TrashScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { id -> navController.navigate(Screen.ConcertDetail.createRoute(id)) }
                )
            }

            composable(Screen.Overview.route) {
                com.bandary.presentation.ui.shows.OverviewScreen(
                    onBack = { navController.popBackStack() },
                    onShowDetail = { id -> navController.navigate(Screen.ConcertDetail.createRoute(id)) }
                )
            }

            composable(Screen.Calendar.route) {
                CalendarHubScreen(
                    onShowDetail = { id ->
                        navController.navigate(Screen.ConcertDetail.createRoute(id))
                    },
                    onOpenFriends = { navController.navigate(Screen.Friends.route) },
                    onAddConcert = { date -> navController.navigate(Screen.AddShow.createRoute(date = date.toString())) },
                    onImportCalendar = { navController.navigate(Screen.ImportCalendar.route) }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    onOpenArtist = { name -> navController.navigate(Screen.ArtistDetail.createRoute(name)) },
                    onOpenVenue = { v -> navController.navigate(Screen.ShowFilter.createRoute("venue", v)) },
                    onOpenCity = { c -> navController.navigate(Screen.ShowFilter.createRoute("city", c)) },
                    onOpenPlatform = { p -> navController.navigate(Screen.ShowFilter.createRoute("platform", p)) },
                    onOpenFilter = { key, value -> navController.navigate(Screen.ShowFilter.createRoute(key, value)) },
                    onOpenWrapped = { year -> navController.navigate(Screen.Wrapped.createRoute(year)) },
                    onOpenFollowed = { navController.navigate(Screen.FollowedArtists.route) },
                    onOpenLeaderboard = { navController.navigate(Screen.Leaderboard.route) }
                )
            }

            composable(Screen.Leaderboard.route) {
                com.bandary.presentation.ui.leaderboard.LeaderboardScreen(
                    onBack = { navController.popBackStack() },
                    // 共同藝人可以直接點進藝人頁——那裡本來就有該藝人的粉絲排行。
                    onOpenArtist = { name -> navController.navigate(Screen.ArtistDetail.createRoute(name)) }
                )
            }

            composable(
                route = Screen.Wrapped.route,
                arguments = listOf(navArgument("year") { type = NavType.StringType })
            ) {
                com.bandary.presentation.ui.wrapped.WrappedScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ShowFilter.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("value") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val typeKey = backStackEntry.arguments?.getString("type")
                val value = backStackEntry.arguments?.getString("value")?.let { android.net.Uri.decode(it) } ?: ""
                com.bandary.presentation.ui.stats.ShowFilterScreen(
                    filterType = com.bandary.presentation.ui.stats.ShowFilterType.fromKey(typeKey),
                    value = value,
                    onBack = { navController.popBackStack() },
                    onShowDetail = { id -> navController.navigate(Screen.ConcertDetail.createRoute(id)) }
                )
            }

            composable(Screen.FreeEvents.route) {
                FreeEventsScreen(
                    onImportCalendar = { navController.navigate(Screen.ImportCalendar.route + "?target=free") },
                    onOpenFollowed = { navController.navigate(Screen.FollowedArtists.route) }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onSignInClick = { navController.navigate(Screen.Login.route) },
                    onOpenFeedback = { navController.navigate(Screen.Feedback.route) },
                    onOpenProFeatures = { navController.navigate(Screen.ProFeatures.route) },
                    onOpenBulkImport = { navController.navigate(Screen.BulkImport.route) },
                    onOpenTrash = { navController.navigate(Screen.Trash.route) },
                    onOpenFriends = { navController.navigate(Screen.Friends.route) },
                    onOpenImportCalendar = { navController.navigate(Screen.ImportCalendar.route) },
                    onOpenWishes = { navController.navigate(Screen.FeatureWishes.route) },
                    onOpenPromo = { navController.navigate(Screen.PromoShare.route) },
                    onOpenBrand = { navController.navigate(Screen.Brand.route) },
                    onOpenAdmin = { navController.navigate(Screen.AdminDashboard.route) }
                )
            }

            composable(Screen.FeatureWishes.route) {
                com.bandary.presentation.ui.wishes.FeatureWishScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.PromoShare.route) {
                com.bandary.presentation.ui.promo.PromoShareScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Brand.route) {
                // 點擊記錄走 SettingsViewModel（它本來就有 installId 與 FirestoreService），
                // 品牌頁本身不碰資料層，保持成一頁純畫面。
                val vm: com.bandary.presentation.ui.settings.SettingsViewModel = hiltViewModel()
                com.bandary.presentation.ui.brand.BrandScreen(
                    onBack = { navController.popBackStack() },
                    onLinkClick = { id -> vm.recordOutboundClick(id) }
                )
            }

            composable(Screen.BulkImport.route) {
                com.bandary.presentation.ui.importbulk.BulkImportScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ProFeatures.route) {
                com.bandary.presentation.ui.profile.ProFeaturesScreen(
                    onBack = { navController.popBackStack() },
                    onUnlock = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Settings.route) {
                val authViewModel: AuthViewModel = hiltViewModel()
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignInClick = { navController.navigate(Screen.Login.route) },
                    onSignOutClick = { authViewModel.logout() },
                    onOpenAdmin = { navController.navigate(Screen.AdminDashboard.route) },
                    onOpenFeedback = { navController.navigate(Screen.Feedback.route) },
                    onOpenFeedbackAdmin = { navController.navigate(Screen.FeedbackAdmin.route) }
                )
            }

            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFeedback = { navController.navigate(Screen.FeedbackAdmin.route) }
                )
            }

            composable(Screen.Feedback.route) {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.FeedbackAdmin.route) {
                FeedbackAdminScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.ImportCalendar.route + "?target={target}",
                arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "concert" })
            ) { backStackEntry ->
                val target = backStackEntry.arguments?.getString("target") ?: "concert"
                ImportCalendarScreen(
                    onBack = { navController.popBackStack() },
                    initialTarget = if (target == "free")
                        com.bandary.presentation.ui.importcal.ImportTarget.FREE
                    else com.bandary.presentation.ui.importcal.ImportTarget.CONCERT
                )
            }

            composable(Screen.Friends.route) {
                FriendsScreen(
                    onBack = { navController.popBackStack() },
                    onViewFriend = { uid, name ->
                        navController.navigate(Screen.FriendCalendar.createRoute(uid, name))
                    }
                )
            }

            composable(
                route = Screen.FriendCalendar.route,
                arguments = listOf(
                    navArgument("friendUid") { type = NavType.StringType },
                    navArgument("friendName") { type = NavType.StringType }
                )
            ) { entry ->
                val uid = entry.arguments?.getString("friendUid") ?: ""
                val name = entry.arguments?.getString("friendName")?.let { android.net.Uri.decode(it) } ?: ""
                FriendCalendarScreen(
                    friendUid = uid,
                    friendName = name,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedIn = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AddShow.route,
                arguments = listOf(
                    navArgument("concertId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("duplicateId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("date") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("scan") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    }
                )
            ) { entry ->
                val concertId = entry.arguments?.getString("concertId")
                val duplicateId = entry.arguments?.getString("duplicateId")
                val initialDate = entry.arguments?.getString("date")
                val autoScan = entry.arguments?.getString("scan") == "true"
                AddShowScreen(
                    onBack = { navController.popBackStack() },
                    existingConcertId = concertId,
                    duplicateFromId = duplicateId,
                    initialDate = initialDate,
                    autoScanPoster = autoScan,
                    onOpenTicketShare = { id ->
                        navController.navigate(Screen.TicketShare.createRoute(id))
                    },
                    onDuplicate = { id ->
                        navController.navigate(Screen.AddShow.createRoute(duplicateId = id))
                    },
                    onOpenPro = { navController.navigate(Screen.ProFeatures.route) }
                )
            }

            composable(
                route = Screen.TicketShare.route,
                arguments = listOf(navArgument("concertId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("concertId") ?: ""
                TicketShareScreen(
                    concertId = id,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ConcertDetail.route,
                arguments = listOf(navArgument("concertId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("concertId") ?: ""
                ConcertDetailScreen(
                    concertId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { cid -> navController.navigate(Screen.AddShow.createRoute(concertId = cid)) },
                    onShareTicket = { cid -> navController.navigate(Screen.TicketShare.createRoute(cid)) },
                    onDuplicate = { cid -> navController.navigate(Screen.AddShow.createRoute(duplicateId = cid)) },
                    onOpenArtist = { name -> navController.navigate(Screen.ArtistDetail.createRoute(name)) },
                    onOpenCompanion = { name -> navController.navigate(Screen.ShowFilter.createRoute("companion", name)) }
                )
            }

            composable(Screen.FollowedArtists.route) {
                com.bandary.presentation.ui.artists.FollowedArtistsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name -> navController.navigate(Screen.ArtistDetail.createRoute(name)) },
                    onOpenEvents = { navController.navigate(Screen.FreeEvents.route) }
                )
            }

            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
            ) { entry ->
                val name = entry.arguments?.getString("artistName")?.let { android.net.Uri.decode(it) } ?: ""
                ArtistDetailScreen(
                    artistName = name,
                    onBack = { navController.popBackStack() },
                    onShowDetail = { cid -> navController.navigate(Screen.ConcertDetail.createRoute(cid)) }
                )
            }
        }
    }
}
