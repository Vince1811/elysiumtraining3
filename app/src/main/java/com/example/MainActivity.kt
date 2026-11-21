package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.ui.components.TopBarAccountButtonAndMenu
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val repository = (application as ElysiumApp).workoutRepository
        val preferences = com.example.data.PreferencesManager(applicationContext)
        
        // Auto-align and reschedule all active alarms on startup
        com.example.receivers.NotificationScheduler.rescheduleAllAlarms(
            applicationContext,
            repository,
            preferences
        )
        
        setContent {
            val themeName by preferences.themeNameFlow.collectAsState(initial = "Purple")
            MyApplicationTheme(themeName = themeName) {


                val userId by preferences.userIdFlow.collectAsState(initial = -1)
                val biometricLockOnStartup by preferences.biometricLockOnStartupFlow.collectAsState(initial = false)
                var userExistsState by remember(userId) { mutableStateOf<Boolean?>(null) }
                var activeUserObj by remember(userId) { mutableStateOf<com.example.data.User?>(null) }
                var unlockedForUserId by remember { mutableStateOf<Int?>(null) }
                var skipNextGateForLogin by remember { mutableStateOf(false) }
                var lockErrorMessage by remember { mutableStateOf("") }
                var isGatePromptActive by remember { mutableStateOf(false) }
                val appUiScope = rememberCoroutineScope()
                
                LaunchedEffect(userId) {
                    if (userId != -1) {
                        val dbUser = repository.getUserById(userId)
                        if (dbUser == null) {
                            preferences.logout()
                            activeUserObj = null
                            userExistsState = false
                            unlockedForUserId = null
                            skipNextGateForLogin = false
                        } else {
                            if (skipNextGateForLogin) {
                                unlockedForUserId = userId
                                skipNextGateForLogin = false
                            }
                            activeUserObj = dbUser
                            userExistsState = true
                        }
                    } else {
                        activeUserObj = null
                        userExistsState = false
                        if (!skipNextGateForLogin) {
                            unlockedForUserId = null
                        }
                    }
                }

                val savedUpdateSource by preferences.appUpdateSourceFlow.collectAsState(
                    initial = com.example.data.PreferencesManager.DEFAULT_GITHUB_UPDATE_URL
                )
                val savedGithubToken by preferences.githubUpdateTokenFlow.collectAsState(initial = "")
                val appUpdateAvailable by preferences.updateAvailableFlow.collectAsState(initial = false)
                val appUpdateLabel by preferences.updateAvailableLabelFlow.collectAsState(initial = "")
                val appUpdateMessage by preferences.updateAvailableMessageFlow.collectAsState(initial = "")
                val appUpdateUrl by preferences.updateAvailableUrlFlow.collectAsState(initial = "")
                val dismissedUpdateLabel by preferences.dismissedUpdateLabelFlow.collectAsState(initial = "")

                if (userId != -1 && userExistsState == true) {
                    LaunchedEffect(userId, savedUpdateSource, savedGithubToken) {
                        if (savedUpdateSource.isNotBlank()) {
                            val res = com.example.data.GitHubUpdateChecker.checkGitHubFilesForUpdate(
                                context = applicationContext,
                                rawSource = savedUpdateSource,
                                token = savedGithubToken
                            )
                            preferences.saveUpdateStatus(
                                available = res.isUpdateAvailable,
                                label = res.updateLabel,
                                message = res.statusMessage,
                                url = res.apkDownloadUrl.orEmpty()
                            )
                        }
                    }

                    LaunchedEffect(userId) {
                        com.example.data.CloudflareSyncService.appSyncScope.launch {
                            com.example.data.CloudflareSyncService(applicationContext)
                                .syncOnAppOpenOrReconnect(userId, repository, preferences)
                        }
                        com.example.data.CloudflareR2Service.r2SyncScope.launch {
                            com.example.data.CloudflareR2Service(applicationContext)
                                .syncAllPicturesOnStartup(
                                    userId = userId,
                                    repository = repository,
                                    preferencesManager = preferences,
                                    bundledGuides = ExerciseGuidesList.map { it.id to it.drawableResId }
                                )
                        }
                    }

                    DisposableEffect(userId, savedUpdateSource, savedGithubToken) {
                        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        val callback = object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    com.example.data.CloudflareSyncService(applicationContext)
                                        .syncOnAppOpenOrReconnect(userId, repository, preferences)
                                }
                                com.example.data.CloudflareR2Service.r2SyncScope.launch {
                                    com.example.data.CloudflareR2Service(applicationContext)
                                        .syncAllPicturesOnStartup(
                                            userId = userId,
                                            repository = repository,
                                            preferencesManager = preferences,
                                            bundledGuides = ExerciseGuidesList.map { it.id to it.drawableResId }
                                        )
                                }
                                if (savedUpdateSource.isNotBlank()) {
                                    appUiScope.launch {
                                        val res = com.example.data.GitHubUpdateChecker.checkGitHubFilesForUpdate(
                                            context = applicationContext,
                                            rawSource = savedUpdateSource,
                                            token = savedGithubToken
                                        )
                                        preferences.saveUpdateStatus(
                                            available = res.isUpdateAvailable,
                                            label = res.updateLabel,
                                            message = res.statusMessage,
                                            url = res.apkDownloadUrl.orEmpty()
                                        )
                                    }
                                }
                            }
                        }
                        try {
                            cm?.registerDefaultNetworkCallback(callback)
                        } catch (_: Exception) {
                        }
                        onDispose {
                            try {
                                cm?.unregisterNetworkCallback(callback)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                val requiresBiometricGate = userId != -1 &&
                    userExistsState == true &&
                    !skipNextGateForLogin &&
                    unlockedForUserId != userId &&
                    biometricLockOnStartup &&
                    activeUserObj?.biometricEnabled == true &&
                    com.example.data.BiometricAuthManager.checkBiometricAvailability(this@MainActivity).canAuthenticate

                if (requiresBiometricGate && activeUserObj != null) {
                    val gateUser = activeUserObj!!
                    LaunchedEffect(gateUser.id) {
                        if (!isGatePromptActive && unlockedForUserId != gateUser.id) {
                            isGatePromptActive = true
                            com.example.data.BiometricAuthManager.authenticateWithBiometric(
                                context = this@MainActivity,
                                targetUser = gateUser,
                                repository = repository,
                                preferencesManager = preferences,
                                scope = appUiScope,
                                title = "Unlock Elysium Training",
                                subtitle = "Verify biometric print for ${gateUser.username}",
                                onSuccess = {
                                    isGatePromptActive = false
                                    lockErrorMessage = ""
                                    unlockedForUserId = gateUser.id
                                },
                                onError = { err ->
                                    isGatePromptActive = false
                                    lockErrorMessage = err
                                },
                                onCancel = {
                                    isGatePromptActive = false
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(88.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "Biometric Lock",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Elysium Training Locked",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Verify fingerprint or device biometric for ${gateUser.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lockErrorMessage.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = lockErrorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    lockErrorMessage = ""
                                    isGatePromptActive = true
                                    com.example.data.BiometricAuthManager.authenticateWithBiometric(
                                        context = this@MainActivity,
                                        targetUser = gateUser,
                                        repository = repository,
                                        preferencesManager = preferences,
                                        scope = appUiScope,
                                        title = "Unlock Elysium Training",
                                        subtitle = "Verify biometric print for ${gateUser.username}",
                                        onSuccess = {
                                            isGatePromptActive = false
                                            lockErrorMessage = ""
                                            unlockedForUserId = gateUser.id
                                        },
                                        onError = { err ->
                                            isGatePromptActive = false
                                            lockErrorMessage = err
                                        },
                                        onCancel = {
                                            isGatePromptActive = false
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("startup_biometric_unlock_button")
                            ) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock with Biometrics 🔐")
                            }
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    appUiScope.launch {
                                        preferences.logout()
                                    }
                                }
                            ) {
                                Text("Use Password / Switch Account")
                            }
                        }
                    }
                } else if (userId != -1 && userExistsState == true) {

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val navigateWithState: (String) -> Unit = { route ->
                        if (currentRoute != route) {
                            try {
                                navController.navigate(route) {
                                    val startDestId = try { navController.graph.startDestinationId } catch (e: Exception) { -1 }
                                    if (startDestId != -1) {
                                        popUpTo(startDestId) {
                                            saveState = true
                                        }
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) {
                                try {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                } catch (ex: Exception) {
                                    // Ignore fallback failures silently
                                }
                            }
                        }
                    }

                    var showTopNavMenu by remember { mutableStateOf(false) }

                    val navItems = listOf(
                        Triple("workout", "Workout", Icons.Filled.FitnessCenter),
                        Triple("meals", "Meals", Icons.Filled.RestaurantMenu),
                        Triple("history", "History", Icons.Filled.DateRange),
                        Triple("progress_timeline", "Progress Photos", Icons.Filled.CameraAlt),
                        Triple("settings", "Settings", Icons.Filled.Settings)
                    )

                    val currentPageLabel = navItems.firstOrNull { it.first == currentRoute }?.second ?: "Menu"

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            CenterAlignedTopAppBar(
                                navigationIcon = {
                                    Box(modifier = Modifier.padding(start = 8.dp)) {
                                        FilledTonalButton(
                                            onClick = { showTopNavMenu = true },
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("top_nav_menu_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Menu,
                                                contentDescription = "Open Navigation Menu",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Menu",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showTopNavMenu,
                                            onDismissRequest = { showTopNavMenu = false },
                                            modifier = Modifier.widthIn(min = 220.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                navItems.forEach { (route, label, icon) ->
                                                    val isSelected = currentRoute == route
                                                    if (isSelected) {
                                                        Button(
                                                            onClick = {
                                                                showTopNavMenu = false
                                                                navigateWithState(route)
                                                            },
                                                            shape = RoundedCornerShape(12.dp),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 48.dp)
                                                                .testTag("top_menu_item_$route")
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = label,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (route == "settings" && appUpdateAvailable) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    color = MaterialTheme.colorScheme.errorContainer
                                                                ) {
                                                                    Text(
                                                                        text = "UPDATE",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        FilledTonalButton(
                                                            onClick = {
                                                                showTopNavMenu = false
                                                                navigateWithState(route)
                                                            },
                                                            shape = RoundedCornerShape(12.dp),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 48.dp)
                                                                .testTag("top_menu_item_$route")
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = label,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (route == "settings" && appUpdateAvailable) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    color = MaterialTheme.colorScheme.primary
                                                                ) {
                                                                    Text(
                                                                        text = "UPDATE",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                title = {
                                    TopBarAccountButtonAndMenu(
                                        userId = userId,
                                        repository = repository,
                                        preferences = preferences
                                    )
                                },
                                actions = {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Text(
                                            text = currentPageLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    ) { innerPadding ->
                        val activeContext = LocalContext.current
                        val showAppUpdateBanner = appUpdateAvailable &&
                            (appUpdateLabel.isBlank() || dismissedUpdateLabel != appUpdateLabel)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (showAppUpdateBanner) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("in_app_update_banner"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SystemUpdate,
                                            contentDescription = "App update available",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (appUpdateLabel.isNotBlank()) "Update Available: $appUpdateLabel" else "App Update Available",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (appUpdateMessage.isNotBlank()) {
                                                Text(
                                                    text = appUpdateMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        TextButton(
                                            onClick = {
                                                if (appUpdateUrl.isNotBlank()) {
                                                    try {
                                                        activeContext.startActivity(
                                                            android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse(appUpdateUrl)
                                                            )
                                                        )
                                                    } catch (_: Exception) {
                                                        navigateWithState("settings")
                                                    }
                                                } else {
                                                    navigateWithState("settings")
                                                }
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Update", fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(
                                            onClick = {
                                                appUiScope.launch {
                                                    preferences.dismissUpdateNotification(appUpdateLabel)
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Dismiss update notice",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            NavHost(
                                navController = navController,
                                startDestination = "workout",
                                modifier = Modifier.weight(1f)
                            ) {
                            composable("workout") { WorkoutScreen(repository, preferences, userId) }
                            composable("meals") { MealsScreen(repository, preferences, navController) }
                            composable("custom_meal_builder?mealId={mealId}") { backStackEntry -> 
                                val mealId = backStackEntry.arguments?.getString("mealId")?.toIntOrNull()
                                CustomMealBuilderScreen(repository, navController, mealId) 
                            }
                            composable("history") {
                                HistoryScreen(
                                    repository = repository,
                                    userId = userId,
                                    onOpenCloudPlatform = { navController.navigate("cloud_platform") }
                                )
                            }
                            composable("progress_timeline") {
                                ProgressTimelineScreen(
                                    repository = repository,
                                    userId = userId
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    repository = repository,
                                    preferencesManager = preferences,
                                    onOpenCloudPlatform = { navController.navigate("cloud_platform") }
                                )
                            }
                            composable("cloud_platform") {
                                CloudPlatformScreen(
                                    repository = repository,
                                    userId = userId,
                                    preferencesManager = preferences,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                        }
                    }
                } else if (userId == -1 || userExistsState == false) {
                    LoginScreen(repository, preferences) {
                        skipNextGateForLogin = true
                        isGatePromptActive = false
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

