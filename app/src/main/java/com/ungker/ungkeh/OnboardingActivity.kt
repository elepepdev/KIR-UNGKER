package com.ungker.ungkeh

import java.util.Locale
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val sp = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)

        // Jika sudah pernah onboarding, langsung ke MainActivity
        if (sp.getBoolean("onboarding_done", false)) {
            goToMain(); return
        }

        setContent {
            MaterialTheme {
                OnboardingScreen(
                    onFinish = { name, selectedApps, timeLimitMinutes, lat, lng, tz, role, password, cityName ->
                        // Generate UID jika belum ada
                        val uid = sp.getString("user_uid", null) ?: run {
                            val g = (100000..999999).random().toString()
                            sp.edit { putString("user_uid", g) }; g
                        }
                        
                        // Save GPS location if obtained, otherwise use defaults
                        val finalLat = lat ?: (-6.2088) // Default Jakarta
                        val finalLng = lng ?: 106.8456
                        val finalTz = tz ?: 7
                        val finalCity = cityName ?: "Jakarta (Default)"
                        
                        // Set Deep Friction berdasarkan role:
                        // - Personal: OFF secara default (tidak perlu friction, hanya self-control)
                        // - Parent: ON secara default (orang tua mengontrol anak)
                        val defaultDeepFriction = role == "parent"
                        
                        sp.edit {
                            putString("user_name", name.trim().ifBlank { "Fulan#$uid" })
                            putStringSet("blocked_apps", selectedApps)
                            putLong("social_media_limit_minutes", timeLimitMinutes)
                            putFloat("sholat_lat", finalLat.toFloat())
                            putFloat("sholat_lng", finalLng.toFloat())
                            putInt("sholat_tz", finalTz)
                            putString("sholat_city_name", finalCity)
                            putBoolean("sholat_gps_mode", lat != null) // Set TRUE jika GPS berhasil didapat
                            putString("user_role", role)
                            putString("parent_password", password)
                            putBoolean("feature_deep_friction_enabled", defaultDeepFriction)
                            
                            putBoolean("onboarding_done", true)
                        }
                        goToMain()
                    }
                )
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ── Data halaman onboarding ───────────────────────────────────────────────────

internal data class OnboardingPage(
    val emoji: String,
    val title: String,
    val desc: String,
    val accent: Color
)

internal val pages = listOf(
    OnboardingPage("🛡️", "welcome_title", "welcome_desc", Color(0xFF2E7D32)),
    OnboardingPage("📵", "lock_app_title", "lock_app_desc", Color(0xFF1565C0)),
    OnboardingPage("📖", "read_quran_title", "read_quran_desc", Color(0xFF6A1B9A)),
    OnboardingPage("🕌", "prayer_title", "prayer_desc", Color(0xFF00695C)),
)

// ── Composable utama ──────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onFinish: (String, Set<String>, Long, Double?, Double?, Int, String, String, String) -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf("en") }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var selectedTimeLimit by remember { mutableLongStateOf(60L) } // Default 1 Hour
    var gpsLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsTimezone by remember { mutableIntStateOf(7) } // Default UTC+7
    var gpsCityName by remember { mutableStateOf("Jakarta") } // Default city
    var userRole by remember { mutableStateOf("personal") }
    var parentPassword by remember { mutableStateOf("") }

    val totalSteps = 5 // Page 0: Intro+Lang, Page 1: All Permissions, Page 2: App Selection, Page 3: Time Limit, Page 4: Role+Name

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it / 2 } + fadeOut()
                )
            },
            label = "OnboardingPage"
        ) { page ->
            when (page) {
                0 -> IntroWithLanguagePage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { lang ->
                        selectedLanguage = lang
                        LocaleManager.setLanguage(lang)
                    },
                    onNext = { currentPage++ }
                )
                1 -> AllPermissionsPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onNext = { currentPage++ },
                    onLocationObtained = { lat, lng, tz, cityName ->
                        gpsLocation = Pair(lat, lng)
                        gpsTimezone = tz
                        gpsCityName = cityName
                    }
                )
                2 -> AppSelectionOnboardingPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedApps = selectedApps,
                    onAppsSelected = {
                        selectedApps = it
                        currentPage++
                    }
                )
                3 -> TimeLimitSelectionPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedLimit = selectedTimeLimit,
                    onLimitSelected = { selectedTimeLimit = it },
                    onNext = { currentPage++ }
                )
                4 -> RolePasswordNamePage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    userRole = userRole,
                    parentPassword = parentPassword,
                    onRoleChanged = { userRole = it },
                    onPasswordChanged = { parentPassword = it },
                    onFinish = { name ->
                        onFinish(name, selectedApps, selectedTimeLimit, gpsLocation?.first, gpsLocation?.second, gpsTimezone, userRole, parentPassword, gpsCityName)
                    }
                )
                else -> NameInputPage(onFinish = { name ->
                    onFinish(name, selectedApps, selectedTimeLimit, gpsLocation?.first, gpsLocation?.second, gpsTimezone, userRole, parentPassword, gpsCityName)
                })
            }
        }
    }
}

// ── Halaman instruksi ─────────────────────────────────────────────────────────

@Composable
internal fun InstructionPage(
    data: OnboardingPage,
    pageIndex: Int,
    totalPages: Int,
    onNext: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "emojiScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Emoji besar dengan glow
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    data.accent.copy(alpha = 0.15f),
                    RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                data.emoji,
                fontSize = (56 * scale).sp
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            LocaleManager.L(data.title),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            LocaleManager.L(data.desc),
            fontSize = 15.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(56.dp))

        // Dot indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) data.accent else Color(0xFF334155)
                        )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = data.accent)
        ) {
            Text(
                LocaleManager.L("onboarding_next"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

// ── Halaman Intro + Language (Gabungan) ────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IntroWithLanguagePage(
    pageIndex: Int,
    totalPages: Int,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            if (page < pages.size) {
                IntroSlide(
                    data = pages[page],
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LanguageSelectionSlide(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { i ->
                val isActive = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Color(0xFF4ADE80) else Color(0xFF334155)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (pagerState.currentPage < pages.size) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onNext()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80))
        ) {
            Text(
                LocaleManager.L("onboarding_next"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun IntroSlide(
    data: OnboardingPage,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "emojiScale"
    )

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    data.accent.copy(alpha = 0.15f),
                    RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                data.emoji,
                fontSize = (56 * scale).sp
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            LocaleManager.L(data.title),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            LocaleManager.L(data.desc),
            fontSize = 15.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun LanguageSelectionSlide(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌐", fontSize = 64.sp)

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("select_language"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            LocaleManager.L("onboarding_lang_title"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        LanguageOptionCard(
            flag = "🇺🇸",
            language = LocaleManager.L("onboarding_lang_english"),
            isSelected = selectedLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )

        Spacer(Modifier.height(16.dp))

        LanguageOptionCard(
            flag = "🇮🇩",
            language = LocaleManager.L("onboarding_lang_indonesian"),
            isSelected = selectedLanguage == "id",
            onClick = { onLanguageSelected("id") }
        )
    }
}

// ── Halaman Pemilihan Bahasa ───────────────────────────────────────────

@Composable
fun LanguageSelectionPage(
    pageIndex: Int,
    totalPages: Int,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌐", fontSize = 64.sp)
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            LocaleManager.L("select_language"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            LocaleManager.L("onboarding_lang_title"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))
        
        // English Option
        LanguageOptionCard(
            flag = "🇺🇸",
            language = LocaleManager.L("onboarding_lang_english"),
            isSelected = true,
            onClick = { onLanguageSelected("en") }
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Indonesian Option
        LanguageOptionCard(
            flag = "🇮🇩",
            language = LocaleManager.L("onboarding_lang_indonesian"),
            isSelected = false,
            onClick = { onLanguageSelected("id") }
        )
        
        Spacer(Modifier.height(40.dp))
        
        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF4ADE80) else Color(0xFF334155))
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    flag: String,
    language: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF4ADE80) else Color(0xFF334155)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flag, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                language,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ── Halaman izin ─────────────────────────────────────────────────────────────

@Composable
fun PermissionPage(pageIndex: Int, totalPages: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    fun refreshPermissions(): Boolean {
        val overlay = Settings.canDrawOverlays(context)
        val usage = CompatibilityUtils.hasUsageStatsPermission(context)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        return overlay && usage && notif
    }

    var allGranted by remember { mutableStateOf(refreshPermissions()) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasUsage by remember { mutableStateOf(CompatibilityUtils.hasUsageStatsPermission(context)) }
    var hasNotif by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    )}

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            hasOverlay = Settings.canDrawOverlays(context)
            hasUsage = CompatibilityUtils.hasUsageStatsPermission(context)
            hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            allGranted = hasOverlay && hasUsage && hasNotif
        }
    }

    // Refresh status setiap kali user kembali dari Settings
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasOverlay = Settings.canDrawOverlays(context)
        hasUsage = CompatibilityUtils.hasUsageStatsPermission(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else hasNotif = true
        allGranted = hasOverlay && hasUsage && hasNotif
    }

    val scrollState = rememberScrollState()
    
    var showOverlayDisclosure by remember { mutableStateOf(false) }
    var showUsageDisclosure by remember { mutableStateOf(false) }

    if (showOverlayDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("perm_overlay_title"),
            description = LocaleManager.L("perm_overlay_desc"),
            onDismiss = { showOverlayDisclosure = false },
            onConfirm = {
                showOverlayDisclosure = false
                launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()))
            }
        )
    }

    if (showUsageDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("perm_usage_title"),
            description = LocaleManager.L("perm_usage_desc"),
            onDismiss = { showUsageDisclosure = false },
            onConfirm = {
                showUsageDisclosure = false
                launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Header
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFB45309).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🔐", fontSize = 48.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("perm_enable_btn"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            LocaleManager.L("perm_warning"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Izin 1: Tampil di atas aplikasi
        PermissionItem(
            emoji = "🪟",
            title = LocaleManager.L("perm_guide_overlay_title"),
            desc = LocaleManager.L("perm_guide_overlay_desc"),
            isGranted = hasOverlay,
            steps = LocaleManager.L("perm_guide_overlay_steps"),
            onGrant = { showOverlayDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        // Izin 2: Akses Penggunaan
        PermissionItem(
            emoji = "📊",
            title = LocaleManager.L("perm_guide_usage_title"),
            desc = LocaleManager.L("perm_guide_usage_desc"),
            isGranted = hasUsage,
            steps = LocaleManager.L("perm_guide_usage_steps"),
            onGrant = { showUsageDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        // Izin Tambahan Khusus Xiaomi: Pop-up window in background
        if (CompatibilityUtils.isXiaomi()) {
            PermissionItem(
                emoji = "🚀",
                title = LocaleManager.L("perm_guide_xiaomi_title"),
                desc = LocaleManager.L("perm_guide_xiaomi_desc"),
                isGranted = false, // MIUI tidak lapor status izin ini ke API standar
                steps = LocaleManager.L("perm_guide_xiaomi_steps"),
                onGrant = {
                    CompatibilityUtils.openMiuiPopupPermission(context)
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        // Izin 3: Notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem(
                emoji = "🔔",
                title = LocaleManager.L("perm_guide_notif_title"),
                desc = LocaleManager.L("perm_guide_notif_desc"),
                isGranted = hasNotif,
                steps = LocaleManager.L("perm_guide_notif_steps"),
                onGrant = {
                    launcher.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))

        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFFB45309) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = allGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) Color(0xFF2E7D32) else Color(0xFF334155),
                disabledContainerColor = Color(0xFF1E293B)
            )
        ) {
            Text(
                if (allGranted) LocaleManager.L("onboarding_next") else LocaleManager.L("perm_btn_must_enable"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (allGranted) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun PermissionItem(
    emoji: String,
    title: String,
    desc: String,
    isGranted: Boolean,
    steps: String,
    onGrant: () -> Unit
) {
    var expanded by remember { mutableStateOf(!isGranted) }

    // Auto-collapse saat izin diberikan
    LaunchedEffect(isGranted) { if (isGranted) expanded = false }

    val borderColor = if (isGranted) Color(0xFF4ADE80) else Color(0xFF334155)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(desc, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            if (isGranted) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(22.dp))
            } else {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(LocaleManager.L("onboarding_how_to"), color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }

        AnimatedVisibility(visible = expanded && !isGranted) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "📋 $steps",
                    fontSize = 12.sp,
                    color = Color(0xFFFBBF24),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                ) {
                    Text(LocaleManager.L("onboarding_go_to_settings"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Halaman Accessibility Service ───────────────────────────────────────────────

@Composable
fun AccessibilityPage(pageIndex: Int, totalPages: Int, onNext: () -> Unit) {
    val context = LocalContext.current

    var hasAccessibility by remember { mutableStateOf(CompatibilityUtils.isAccessibilityServiceEnabled(context)) }
    var showDisclosure by remember { mutableStateOf(false) }

    if (showDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("permission_overlay_title"),
            description = LocaleManager.L("permission_overlay_desc"),
            onDismiss = { showDisclosure = false },
            onConfirm = {
                showDisclosure = false
                CompatibilityUtils.openAccessibilitySettings(context)
            }
        )
    }

    LaunchedEffect(Unit) {
        while (!hasAccessibility) {
            hasAccessibility = CompatibilityUtils.isAccessibilityServiceEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🔧", fontSize = 48.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("accessibility_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            LocaleManager.L("accessibility_subtitle"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    LocaleManager.L("accessibility_why_title"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    LocaleManager.L("accessibility_why_desc"),
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    LocaleManager.L("accessibility_privacy_title"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    LocaleManager.L("accessibility_privacy_desc"),
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { if (!hasAccessibility) showDisclosure = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasAccessibility) Color(0xFF4ADE80) else Color(0xFF2E7D32)
            )
        ) {
            if (hasAccessibility) {
                Icon(Icons.Default.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(LocaleManager.L("accessibility_enabled"), fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text(LocaleManager.L("accessibility_disabled"), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (hasAccessibility) {
            TextButton(
                onClick = { onNext() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    LocaleManager.L("permission_next"),
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF2E7D32) else Color(0xFF334155))
                )
            }
        }
    }
}

// ── Halaman Battery Optimization ────────────────────────────────────────────────

@Composable
fun BatteryOptimizationPage(pageIndex: Int, totalPages: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var isIgnored by remember { mutableStateOf(CompatibilityUtils.isBatteryOptimizationIgnored(context)) }

    LaunchedEffect(Unit) {
        while (!isIgnored) {
            isIgnored = CompatibilityUtils.isBatteryOptimizationIgnored(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFD32F2F).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🔋", fontSize = 48.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("battery_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            LocaleManager.L("battery_subtitle"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    LocaleManager.L("battery_why_title"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    LocaleManager.L("battery_why_desc"),
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { CompatibilityUtils.requestIgnoreBatteryOptimization(context) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isIgnored) Color(0xFF4ADE80) else Color(0xFFD32F2F)
            )
        ) {
            if (isIgnored) {
                Icon(Icons.Default.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(LocaleManager.L("battery_enabled"), fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text(LocaleManager.L("battery_disabled"), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isIgnored) {
            TextButton(
                onClick = { onNext() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    LocaleManager.L("permission_next"),
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFFD32F2F) else Color(0xFF334155))
                )
            }
        }
    }
}

// ── Halaman Semua Izin (Gabungan) ───────────────────────────────────────────

@Composable
fun AllPermissionsPage(
    pageIndex: Int,
    totalPages: Int,
    onNext: () -> Unit,
    onLocationObtained: (Double, Double, Int, String) -> Unit
) {
    val context = LocalContext.current

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasUsage by remember { mutableStateOf(CompatibilityUtils.hasUsageStatsPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(CompatibilityUtils.isAccessibilityServiceEnabled(context)) }
    var isBatteryIgnored by remember { mutableStateOf(CompatibilityUtils.isBatteryOptimizationIgnored(context)) }
    var hasLocationPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }
    var hasNotif by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    ) }

    val allGranted = hasOverlay && hasUsage && hasAccessibility && isBatteryIgnored && hasLocationPermission

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasOverlay = Settings.canDrawOverlays(context)
        hasUsage = CompatibilityUtils.hasUsageStatsPermission(context)
        hasAccessibility = CompatibilityUtils.isAccessibilityServiceEnabled(context)
        isBatteryIgnored = CompatibilityUtils.isBatteryOptimizationIgnored(context)
        hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            hasOverlay = Settings.canDrawOverlays(context)
            hasUsage = CompatibilityUtils.hasUsageStatsPermission(context)
            hasAccessibility = CompatibilityUtils.isAccessibilityServiceEnabled(context)
            isBatteryIgnored = CompatibilityUtils.isBatteryOptimizationIgnored(context)
            hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotif = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    var showOverlayDisclosure by remember { mutableStateOf(false) }
    var showUsageDisclosure by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showOverlayDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("perm_overlay_title"),
            description = LocaleManager.L("perm_overlay_desc"),
            onDismiss = { showOverlayDisclosure = false },
            onConfirm = {
                showOverlayDisclosure = false
                launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
            }
        )
    }

    if (showUsageDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("perm_usage_title"),
            description = LocaleManager.L("perm_usage_desc"),
            onDismiss = { showUsageDisclosure = false },
            onConfirm = {
                showUsageDisclosure = false
                launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
    }

    if (showAccessibilityDisclosure) {
        PermissionDisclosureDialog(
            title = LocaleManager.L("permission_overlay_title"),
            description = LocaleManager.L("permission_overlay_desc"),
            onDismiss = { showAccessibilityDisclosure = false },
            onConfirm = {
                showAccessibilityDisclosure = false
                CompatibilityUtils.openAccessibilitySettings(context)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFB45309).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🔐", fontSize = 48.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("perm_enable_btn"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            LocaleManager.L("perm_warning"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        PermissionItem(
            emoji = "🪟",
            title = LocaleManager.L("perm_guide_overlay_title"),
            desc = LocaleManager.L("perm_guide_overlay_desc"),
            isGranted = hasOverlay,
            steps = LocaleManager.L("perm_guide_overlay_steps"),
            onGrant = { showOverlayDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            emoji = "📊",
            title = LocaleManager.L("perm_guide_usage_title"),
            desc = LocaleManager.L("perm_guide_usage_desc"),
            isGranted = hasUsage,
            steps = LocaleManager.L("perm_guide_usage_steps"),
            onGrant = { showUsageDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            emoji = "🔧",
            title = LocaleManager.L("accessibility_title"),
            desc = LocaleManager.L("accessibility_subtitle"),
            isGranted = hasAccessibility,
            steps = LocaleManager.L("perm_guide_accessibility_steps"),
            onGrant = { showAccessibilityDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            emoji = "🔋",
            title = LocaleManager.L("battery_title"),
            desc = LocaleManager.L("battery_subtitle"),
            isGranted = isBatteryIgnored,
            steps = LocaleManager.L("perm_guide_battery_steps"),
            onGrant = { CompatibilityUtils.requestIgnoreBatteryOptimization(context) }
        )

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            emoji = "📍",
            title = LocaleManager.L("gps_title"),
            desc = LocaleManager.L("gps_subtitle"),
            isGranted = hasLocationPermission,
            steps = LocaleManager.L("perm_guide_location_steps"),
            onGrant = {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        )

        if (CompatibilityUtils.isXiaomi()) {
            Spacer(Modifier.height(12.dp))
            PermissionItem(
                emoji = "🚀",
                title = LocaleManager.L("perm_guide_xiaomi_title"),
                desc = LocaleManager.L("perm_guide_xiaomi_desc"),
                isGranted = false,
                steps = LocaleManager.L("perm_guide_xiaomi_steps"),
                onGrant = { CompatibilityUtils.openMiuiPopupPermission(context) }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(12.dp))
            PermissionItem(
                emoji = "🔔",
                title = LocaleManager.L("perm_guide_notif_title"),
                desc = LocaleManager.L("perm_guide_notif_desc"),
                isGranted = hasNotif,
                steps = LocaleManager.L("perm_guide_notif_steps"),
                onGrant = {
                    launcher.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFFB45309) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = allGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) Color(0xFF2E7D32) else Color(0xFF334155),
                disabledContainerColor = Color(0xFF1E293B)
            )
        ) {
            Text(
                if (allGranted) LocaleManager.L("onboarding_next") else LocaleManager.L("perm_btn_must_enable"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (allGranted) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

// ── Halaman Lokasi GPS ─────────────────────────────────────────────────────────

@Composable
fun GpsLocationPage(
    pageIndex: Int,
    totalPages: Int,
    onLocationObtained: (Double, Double, Int, String) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentTimezone by remember { mutableIntStateOf(7) }
    var currentCityName by remember { mutableStateOf("Jakarta") } // Default Jakarta
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Animation for pulsing circle (Sonar effect)
    val infiniteTransition = rememberInfiniteTransition(label = "sonar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha"
    )
    
    // Check if location permission is granted
    fun checkPermission() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    checkPermission()
    
    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            // Start getting location automatically after permission granted
            errorMessage = null
            currentLocation = null
            // Will be called via LaunchedEffect below
        }
    }
    
    // Get current location using FusedLocationProviderClient
    fun getCurrentLocation() {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        
        isLoading = true
        errorMessage = null
        
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        // Timeout handler (15 seconds)
        val timeoutHandler = android.os.Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (isLoading && currentLocation == null) {
                isLoading = false
                errorMessage = LocaleManager.L("gps_error_slow")
                Toast.makeText(context, LocaleManager.L("gps_toast_warning"), Toast.LENGTH_LONG).show()
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000L)
        
        try {
            // 1. Try last location first (fastest)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && (System.currentTimeMillis() - location.time < 600_000)) {
                    // Use last location if fresh (less than 10 mins old)
                    currentLocation = Pair(location.latitude, location.longitude)
                    currentTimezone = ((location.longitude + 7.5) / 15).toInt().coerceIn(-12, 14).let { if (it == 0) 7 else it }
                    currentCityName = findNearestCity(location.latitude, location.longitude)
                    isLoading = false
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                } else {
                    // 2. Request fresh location
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                        .setWaitForAccurateLocation(false)
                        .setMaxUpdates(1)
                        .build()
                    
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { loc ->
                                currentLocation = Pair(loc.latitude, loc.longitude)
                                currentTimezone = ((loc.longitude + 7.5) / 15).toInt().coerceIn(-12, 14).let { if (it == 0) 7 else it }
                                currentCityName = findNearestCity(loc.latitude, loc.longitude)
                            }
                            isLoading = false
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            fusedLocationClient.removeLocationUpdates(this)
                        }
                    }
                    
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                        .addOnFailureListener { 
                            isLoading = false
                            errorMessage = LocaleManager.L("gps_error_process")
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                        }
                }
            }.addOnFailureListener {
                isLoading = false
                errorMessage = LocaleManager.L("gps_error_sensor")
                timeoutHandler.removeCallbacks(timeoutRunnable)
            }
        } catch (e: SecurityException) {
            isLoading = false
            errorMessage = LocaleManager.L("gps_error_permission")
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
    }
    
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && currentLocation == null) {
            getCurrentLocation()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visual GPS Area
        Box(
            modifier = Modifier
                .size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                // Sonar pulse animation
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFF4ADE80).copy(alpha = pulseAlpha), CircleShape)
                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                )
            }
            
            val boxColor = when {
                currentLocation != null -> Color(0xFF4ADE80)
                isLoading -> Color(0xFFFBBF24)
                else -> Color(0xFF94A3B8)
            }
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(boxColor.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, boxColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (currentLocation != null) "✅" else "📍",
                    fontSize = 48.sp,
                    modifier = Modifier.scale(if (isLoading) pulseScale.coerceIn(1f, 1.1f) else 1f)
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            LocaleManager.L("gps_title"),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            LocaleManager.L("gps_subtitle"),
            fontSize = 15.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(Modifier.height(32.dp))
        
        // Dynamic Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (currentLocation != null) Color(0xFF064E3B) else Color(0xFF1E293B)
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (currentLocation != null) Color(0xFF059669) else Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    Text(LocaleManager.L("gps_loading"), color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(LocaleManager.L("gps_loading_desc"), color = Color(0xFF94A3B8), fontSize = 12.sp)
                } else if (currentLocation != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(LocaleManager.L("gps_success"), color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("📍 $currentCityName", color = Color(0xFFD1FAE5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(LocaleManager.LF("gps_coord_format", currentLocation!!.first, currentLocation!!.second), color = Color(0xFFD1FAE5), fontSize = 12.sp)
                    Text(LocaleManager.LF("gps_tz_format", currentTimezone), color = Color(0xFFD1FAE5), fontSize = 12.sp)
                } else {
                    Text(
                        if (hasLocationPermission) LocaleManager.L("gps_no_signal") else LocaleManager.L("gps_no_permission"),
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(modifier = Modifier.size(if (isActive) 24.dp else 10.dp, 10.dp).clip(CircleShape).background(if (isActive) Color(0xFF4ADE80) else Color(0xFF334155)))
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { getCurrentLocation() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentLocation != null) Color(0xFF4ADE80) else Color(0xFF00695C),
                    contentColor = Color.White
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Icon(if (currentLocation != null) Icons.Default.Check else Icons.Default.LocationOn, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (currentLocation != null) LocaleManager.L("gps_button_get_again") else LocaleManager.L("gps_button_get"), fontWeight = FontWeight.Bold)
                }
            }
            
            TextButton(
                onClick = { 
                    val cityName = if (currentLocation != null) findNearestCity(currentLocation!!.first, currentLocation!!.second) else "Jakarta (Default)"
                    onLocationObtained(currentLocation?.first ?: -6.2088, currentLocation?.second ?: 106.8456, currentTimezone, cityName) 
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (currentLocation != null) LocaleManager.L("gps_button_use") else LocaleManager.L("gps_button_use_default"),
                    color = if (currentLocation != null) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Halaman Pilih Aplikasi ───────────────────────────────────────────────────

@Composable
fun AppSelectionOnboardingPage(
    pageIndex: Int,
    totalPages: Int,
    selectedApps: Set<String>,
    onAppsSelected: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentSelected = remember { mutableStateListOf<String>().apply { addAll(selectedApps) } }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val app = resolveInfo.activityInfo.applicationInfo
                val name = resolveInfo.loadLabel(pm).toString()
                val packageName = app.packageName
                
                if (packageName == context.packageName) return@mapNotNull null

                var categoryStr = LocaleManager.L("category_others")
                var isTarget = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (app.category) {
                        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> categoryStr = LocaleManager.L("category_social")
                        android.content.pm.ApplicationInfo.CATEGORY_GAME -> categoryStr = LocaleManager.L("category_game")
                        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> categoryStr = LocaleManager.L("category_entertainment")
                        else -> {}
                    }
                }
                val lowerPackage = packageName.lowercase()
                if (lowerPackage.contains("instagram") || lowerPackage.contains("tiktok") ||
                    lowerPackage.contains("youtube") || lowerPackage.contains("facebook") ||
                    lowerPackage.contains("whatsapp") || lowerPackage.contains("twitter") ||
                    lowerPackage.contains("telegram") || lowerPackage.contains("discord") ||
                    lowerPackage.contains("snapchat") || lowerPackage.contains("linkedin") ||
                    lowerPackage.contains("game") || lowerPackage.contains("mobile.legends") ||
                    lowerPackage.contains("pubg") || lowerPackage.contains("freefire")) {
                    categoryStr = if (lowerPackage.contains("game") || lowerPackage.contains("mobile.legends") || lowerPackage.contains("pubg") || lowerPackage.contains("freefire")) LocaleManager.L("category_game") else LocaleManager.L("category_social")
                }

                if (categoryStr == LocaleManager.L("category_others")) return@mapNotNull null

                AppInfo(name, packageName, categoryStr)
            }.distinctBy { it.packageName }.sortedBy { it.name }

            withContext(Dispatchers.Main) { 
                apps = appList
                isLoading = false 
            }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1565C0).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("📱", fontSize = 40.sp) }

                Spacer(Modifier.height(24.dp))

                Text(
                    LocaleManager.L("app_select_title"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    LocaleManager.L("app_select_desc"),
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1565C0))
                }
            }
        } else {
            items(apps) { app ->
                val isSelected = currentSelected.contains(app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                        .border(1.dp, if (isSelected) Color(0xFF1565C0) else Color(0xFF334155), RoundedCornerShape(12.dp))
                        .clickable {
                            if (isSelected) currentSelected.remove(app.packageName)
                            else currentSelected.add(app.packageName)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Simplified
                    Box(Modifier.size(32.dp).background(Color(0xFF334155), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(app.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(app.category, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1565C0))
                    )
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))

                // Dot indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalPages) { i ->
                        val isActive = i == pageIndex
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 24.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF1565C0) else Color(0xFF334155))
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onAppsSelected(currentSelected.toSet()) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = currentSelected.size >= 1,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1565C0),
                        disabledContainerColor = Color(0xFF1E293B)
                    )
                ) {
                    Text(
                        if (currentSelected.size >= 1) LocaleManager.LF("app_btn_continue_selected", currentSelected.size)
                        else LocaleManager.L("app_btn_must_select"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ── Halaman Standar Waktu ────────────────────────────────────────────────────

@Composable
fun TimeLimitSelectionPage(
    pageIndex: Int,
    totalPages: Int,
    selectedLimit: Long,
    onLimitSelected: (Long) -> Unit,
    onNext: () -> Unit
) {
    val options = listOf(
        TimeOption(30L, LocaleManager.L("time_30min"), LocaleManager.L("time_30min_desc")),
        TimeOption(45L, LocaleManager.L("time_45min"), LocaleManager.L("time_45min_desc")),
        TimeOption(60L, LocaleManager.L("time_60min"), LocaleManager.L("time_60min_desc")),
        TimeOption(90L, LocaleManager.L("time_90min"), LocaleManager.L("time_90min_desc")),
        TimeOption(120L, LocaleManager.L("time_120min"), LocaleManager.L("time_120min_desc"))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFF6A1B9A).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { Text("⏳", fontSize = 40.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("time_limit_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            LocaleManager.L("time_limit_desc"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { option ->
                val isSelected = selectedLimit == option.minutes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF6A1B9A).copy(alpha = 0.2f) else Color(0xFF1E293B))
                        .border(1.dp, if (isSelected) Color(0xFF6A1B9A) else Color(0xFF334155), RoundedCornerShape(16.dp))
                        .clickable {
                            onLimitSelected(option.minutes)
                            onNext()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(option.desc, color = if (isSelected) Color(0xFFE1BEE7) else Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6A1B9A))
                    )
                }
            }
        }

        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF6A1B9A) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onLimitSelected(selectedLimit)
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
        ) {
            Text(LocaleManager.L("onboarding_next"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

data class TimeOption(val minutes: Long, val label: String, val desc: String)

// ── Halaman Pilih Peran ──────────────────────────────────────────────────────

@Composable
fun UserRoleSelectionPage(
    pageIndex: Int,
    totalPages: Int,
    selectedRole: String,
    onRoleSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFF00695C).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { Text("👤", fontSize = 40.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("role_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(32.dp))

        // Role 1: Personal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selectedRole == "personal") Color(0xFF00695C).copy(alpha = 0.2f) else Color(0xFF1E293B))
                .border(1.dp, if (selectedRole == "personal") Color(0xFF00695C) else Color(0xFF334155), RoundedCornerShape(16.dp))
                .clickable { onRoleSelected("personal") }
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocaleManager.L("onboarding_role_personal"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(LocaleManager.L("onboarding_role_personal_desc"), color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                RadioButton(
                    selected = selectedRole == "personal",
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00695C))
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Role 2: Parent
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selectedRole == "parent") Color(0xFF00695C).copy(alpha = 0.2f) else Color(0xFF1E293B))
                .border(1.dp, if (selectedRole == "parent") Color(0xFF00695C) else Color(0xFF334155), RoundedCornerShape(16.dp))
                .clickable { onRoleSelected("parent") }
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocaleManager.L("onboarding_role_parent"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(LocaleManager.L("onboarding_role_parent_desc"), color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                RadioButton(
                    selected = selectedRole == "parent",
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00695C))
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF00695C) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ── Halaman Password Orang Tua ────────────────────────────────────────────────

@Composable
fun ParentPasswordPage(
    pageIndex: Int,
    totalPages: Int,
    onPasswordSet: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val isValid = password.length >= 4

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFB45309).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🔐", fontSize = 40.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("parent_password_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))

        Text(
            LocaleManager.L("parent_password_desc"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            LocaleManager.L("parent_password_warning"),
            fontSize = 13.sp,
            color = Color(0xFFFBBF24),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= 10) password = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(LocaleManager.L("onboarding_password_min"), color = Color(0xFF64748B)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isValid) { keyboard?.hide(); onPasswordSet(password) } }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFBBF24),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFFBBF24),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
            )
        )

        Spacer(Modifier.height(32.dp))

        // Dot indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFFFBBF24) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { keyboard?.hide(); onPasswordSet(password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB45309),
                disabledContainerColor = Color(0xFF1E293B)
            )
        ) {
            Text(
                LocaleManager.L("onboarding_next"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isValid) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

// ── Halaman input nama ────────────────────────────────────────────────────────

@Composable
fun NameInputPage(onFinish: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val isValid = name.trim().length >= 2

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("👤", fontSize = 48.sp)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            LocaleManager.L("name_title"),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            LocaleManager.L("name_desc"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) name = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(LocaleManager.L("onboarding_name_example"), color = Color(0xFF64748B)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isValid) { keyboard?.hide(); onFinish(name) } }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4ADE80),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF4ADE80),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
            )
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { keyboard?.hide(); onFinish(name) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                disabledContainerColor = Color(0xFF1E3A1E)
            )
        ) {
            Text(
                LocaleManager.L("start_btn"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isValid) Color.White else Color(0xFF4A6B4A)
            )
        }
    }
}

@Composable
fun PermissionDisclosureDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = LocaleManager.L("permission_disclosure_title"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4ADE80),
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(LocaleManager.L("permission_disclosure_cancel"), color = Color(0xFF64748B))
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(LocaleManager.L("permission_disclosure_agree"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Halaman Role + Password + Name (Gabungan) ───────────────────────────────

@Composable
fun RolePasswordNamePage(
    pageIndex: Int,
    totalPages: Int,
    userRole: String,
    parentPassword: String,
    onRoleChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onFinish: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var currentRole by remember { mutableStateOf(userRole) }
    var currentPassword by remember { mutableStateOf(parentPassword) }
    var showPasswordError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val isValid = name.trim().length >= 2

    val isParent = currentRole == "parent"
    val isPasswordValid = !isParent || currentPassword.length >= 4

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF6A1B9A).copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("👤", fontSize = 48.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("role_title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            LocaleManager.L("role_personal_desc"),
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoleCard(
                emoji = "🙂",
                title = LocaleManager.L("role_personal_title"),
                desc = LocaleManager.L("role_personal_desc"),
                isSelected = currentRole == "personal",
                onClick = {
                    currentRole = "personal"
                    onRoleChanged("personal")
                },
                modifier = Modifier.weight(1f)
            )

            RoleCard(
                emoji = "👨‍👩‍👧",
                title = LocaleManager.L("role_parent_title"),
                desc = LocaleManager.L("role_parent_desc"),
                isSelected = currentRole == "parent",
                onClick = {
                    currentRole = "parent"
                    onRoleChanged("parent")
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (isParent) {
            Spacer(Modifier.height(24.dp))

            Text(
                LocaleManager.L("password_title"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = currentPassword,
                onValueChange = {
                    currentPassword = it
                    showPasswordError = false
                    onPasswordChanged(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(LocaleManager.L("password_enter"), color = textMutC()) },
                singleLine = true,
                isError = showPasswordError,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Password
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4ADE80),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (showPasswordError) {
                Text(
                    LocaleManager.L("password_mismatch"),
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            LocaleManager.L("name_title"),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(LocaleManager.L("name_placeholder"), color = textMutC()) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4ADE80),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            LocaleManager.L("onboarding_name_example"),
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalPages) { i ->
                val isActive = i == pageIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF6A1B9A) else Color(0xFF334155))
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isParent && currentPassword.length < 4) {
                    showPasswordError = true
                } else {
                    onFinish(name)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = isValid && isPasswordValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6A1B9A),
                disabledContainerColor = Color(0xFF334155)
            )
        ) {
            Text(
                LocaleManager.L("onboarding_next"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isValid && isPasswordValid) Color.White else Color(0xFF94A3B8)
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RoleCard(
    emoji: String,
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF6A1B9A) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF4ADE80) else Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}