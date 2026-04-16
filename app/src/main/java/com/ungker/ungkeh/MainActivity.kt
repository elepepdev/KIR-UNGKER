package com.ungker.ungkeh

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Check onboarding
        val sp0 = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        if (!sp0.getBoolean("onboarding_done", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        // Ensure daily stats are reset
        CreditUtils.checkDailyReset(this)

        val serviceIntent = Intent(this, UngkerService::class.java)
        startForegroundService(serviceIntent)

        // Izin dasar tetap diperlukan di sini
        if (!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Cek izin usage stats di background agar tidak membebani UI startup
        lifecycleScope.launch(Dispatchers.Default) {
            if (!hasUsageStatsPermission()) {
                withContext(Dispatchers.Main) {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }

        setContent {
            val sharedPref = remember { getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
            var isDarkMode by remember { mutableStateOf(sharedPref.getBoolean("dark_mode", false)) }

            CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
                MaterialTheme(
                    colorScheme = if (isDarkMode) darkColorScheme(
                        background = Color(0xFF12100E),
                        surface = Color(0xFF1C1917),
                        onSurface = Color(0xFFFEF3C7),
                        onBackground = Color(0xFFFEF3C7),
                        primary = Color(0xFF52B788),
                        onPrimary = Color.Black
                    ) else lightColorScheme(
                        background = Color(0xFFFFF7ED),
                        surface = Color(0xFFFFFAF0),
                        onSurface = Color(0xFF451A03),
                        onBackground = Color(0xFF451A03),
                        primary = Color(0xFF2D6A4F),
                        onPrimary = Color.White
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = pageBg()
                    ) {
                        LayarUtama(
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = {
                                val newVal = !isDarkMode
                                isDarkMode = newVal
                                sharedPref.edit {putBoolean("dark_mode", newVal)}
                            }
                        )
                    }
                }
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LayarUtama(
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    viewModel: com.ungker.ungkeh.ui.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val remainingTimeMillis by viewModel.remainingCredit.collectAsState()

    val dark  = isDarkMode
    val navBg = cardBg()
    val bg    = pageBg()

    // Cache statis untuk data yang tidak reaktif
    var blockedCount by remember { mutableIntStateOf(0) }
    var hideNavBar   by remember { mutableStateOf(false) }
    var selectedTab  by remember { mutableIntStateOf(0) }

    val maxCreditLimit = 3_600_000L
    val READ_COOLDOWN_MS = 5L * 60 * 1000L
    val READ_CREDIT_MS   = 5L * 60 * 1000L

    LaunchedEffect(Unit) {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        blockedCount = sp.getStringSet("blocked_apps", emptySet())?.size ?: 0
    }

    Scaffold(
        containerColor = bg,
        bottomBar = {
            if (!hideNavBar) {
                NavigationBar(
                    containerColor = navBg,
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple("Beranda", Icons.Default.Home, 0),
                        Triple("Aplikasi", Icons.Default.Apps, 1),
                        Triple("Quran", Icons.AutoMirrored.Filled.MenuBook, 2),
                        Triple("Statistik", Icons.Default.BarChart, 3),
                        Triple("Profil", Icons.Default.Person, 4)
                    )

                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            label = { Text(label, fontSize = 10.sp) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = greenAccent(),
                                selectedTextColor = greenAccent(),
                                indicatorColor = greenAccent().copy(alpha = 0.1f),
                                unselectedIconColor = textSecC(),
                                unselectedTextColor = textSecC()
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Jika navbar disembunyikan, kita gunakan padding 0 di bawah agar konten menempel ke navigasi sistem
        val adjustedPadding = if (hideNavBar) {
            PaddingValues(
                start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = 0.dp
            )
        } else {
            innerPadding
        }

        Box(modifier = Modifier.padding(adjustedPadding)) {
            AnimatedContent(
                targetState  = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it / 3 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                            slideOutHorizontally { it / 3 } + fadeOut()
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> BerandaScreen(
                        remainingTimeMillis = remainingTimeMillis,
                        progress    = if (remainingTimeMillis > 0) remainingTimeMillis.toFloat() / maxCreditLimit.toFloat() else 0f,
                        blockedCount        = blockedCount,
                        isDarkMode          = dark,
                        onToggleDarkMode    = onToggleDarkMode,
                        onTambahWaktu = {
                            val sp      = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                            val lastRead = sp.getLong("last_read_tambah_time", 0L)
                            val now     = System.currentTimeMillis()
                            when {
                                remainingTimeMillis >= maxCreditLimit ->
                                    Toast.makeText(context, "Waktu sudah maksimal (1 Jam)!", Toast.LENGTH_SHORT).show()
                                now - lastRead < READ_COOLDOWN_MS -> {
                                    val sisaDetik = ((READ_COOLDOWN_MS - (now - lastRead)) / 1000).toInt()
                                    val mnt = sisaDetik / 60; val det = sisaDetik % 60
                                    Toast.makeText(context, "Cooldown aktif! Tunggu ${mnt}m ${det}d lagi", Toast.LENGTH_LONG).show()
                                }
                                else -> {
                                    val intentLock = Intent(context, LockActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        putExtra("credit_override_ms", READ_CREDIT_MS)
                                        putExtra("start_cooldown_key", "last_read_tambah_time")
                                    }
                                    context.startActivity(intentLock)
                                }
                            }
                        }
                    )
                    1 -> DaftarAplikasiScreen(isDarkMode = dark)
                    2 -> QuranScreen(onHideNavBar = { hideNavBar = it })
                    3 -> StatistikScreen()
                    4 -> ProfilScreen()
                    else -> Column(
                        modifier            = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { Text("Halaman Belum Tersedia", color = textPrimC()) }
                }
            }
        }
    }
}
