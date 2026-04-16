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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
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
    OnboardingPage(
        "🛡️",
        "Selamat Datang di Ungker",
        "Ungker hadir untuk membantumu mengendalikan waktu layar dan fokus pada hal yang benar-benar penting.",
        Color(0xFF2E7D32)
    ),
    OnboardingPage(
        "📵",
        "Kunci Aplikasi Pengganggu",
        "Ungker akan mengunci otomatis aplikasi media sosial & game saat kamu sudah melewati batas waktu harian.",
        Color(0xFF1565C0)
    ),
    OnboardingPage(
        "📖",
        "Baca Qur'an, Buka Kunci",
        "Cara membuka kunci? Cukup baca beberapa ayat Al-Qur'an. Produktif dan berkah sekaligus.",
        Color(0xFF6A1B9A)
    ),
    OnboardingPage(
        "🕌",
        "Pengingat Waktu Sholat",
        "Layar akan terkunci otomatis saat waktu sholat tiba. Jangan sampai gadget melalaikanmu dari kewajiban.",
        Color(0xFF00695C)
    ),
)

// ── Composable utama ──────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onFinish: (String, Set<String>, Long, Double?, Double?, Int, String, String, String) -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var selectedTimeLimit by remember { mutableLongStateOf(60L) } // Default 1 Hour
    var gpsLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsTimezone by remember { mutableIntStateOf(7) } // Default UTC+7
    var gpsCityName by remember { mutableStateOf("Jakarta") } // Default city
    var userRole by remember { mutableStateOf("personal") }
    var parentPassword by remember { mutableStateOf("") }

    val totalSteps = pages.size + 8 // Intro(4) + Izin(1) + Accessibility(1) + Battery(1) + GPS(1) + PilihApp(1) + Waktu(1) + Role(1) + Password(1) = 12

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
            when {
                page < pages.size -> InstructionPage(
                    data = pages[page],
                    pageIndex = page,
                    totalPages = totalSteps,
                    onNext = { currentPage++ }
                )
                page == pages.size -> PermissionPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onNext = { currentPage++ }
                )
                page == pages.size + 1 -> AccessibilityPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onNext = { currentPage++ }
                )
                page == pages.size + 2 -> BatteryOptimizationPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onNext = { currentPage++ }
                )
                page == pages.size + 3 -> GpsLocationPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onLocationObtained = { lat, lng, tz, cityName ->
                        gpsLocation = Pair(lat, lng)
                        gpsTimezone = tz
                        gpsCityName = cityName
                        currentPage++
                    },
                    onNext = { currentPage++ }
                )
                page == pages.size + 4 -> AppSelectionOnboardingPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedApps = selectedApps,
                    onAppsSelected = { 
                        selectedApps = it
                        currentPage++
                    }
                )
                page == pages.size + 5 -> TimeLimitSelectionPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedLimit = selectedTimeLimit,
                    onLimitSelected = { selectedTimeLimit = it },
                    onNext = { currentPage++ }
                )
                page == pages.size + 6 -> UserRoleSelectionPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    selectedRole = userRole,
                    onRoleSelected = { 
                        userRole = it
                        if (it == "personal") {
                            currentPage += 2 // Skip password page
                        } else {
                            currentPage++
                        }
                    }
                )
                page == pages.size + 7 -> ParentPasswordPage(
                    pageIndex = page,
                    totalPages = totalSteps,
                    onPasswordSet = { 
                        parentPassword = it
                        currentPage++
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
            data.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            data.desc,
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
                "Lanjut →",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
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
            "Aktifkan Izin",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Semua izin berikut WAJIB diaktifkan agar Ungker bisa berjalan.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Izin 1: Tampil di atas aplikasi
        PermissionItem(
            emoji = "🪟",
            title = "Tampil di Atas Aplikasi",
            desc = "Agar layar kunci bisa muncul di atas aplikasi lain.",
            isGranted = hasOverlay,
            steps = "Pengaturan → Aplikasi Khusus → Tampil di atas aplikasi → Ungker → Izinkan",
            onGrant = {
                launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()))
            }
        )

        Spacer(Modifier.height(12.dp))

        // Izin 2: Akses Penggunaan
        PermissionItem(
            emoji = "📊",
            title = "Akses Data Penggunaan",
            desc = "Untuk mendeteksi aplikasi mana yang sedang aktif.",
            isGranted = hasUsage,
            steps = "Pengaturan → Privasi → Akses penggunaan → Ungker → Aktifkan",
            onGrant = {
                launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        Spacer(Modifier.height(12.dp))

        // Izin Tambahan Khusus Xiaomi: Pop-up window in background
        if (CompatibilityUtils.isXiaomi()) {
            PermissionItem(
                emoji = "🚀",
                title = "Xiaomi: Pop-up Latar Belakang",
                desc = "WAJIB agar layar kunci muncul otomatis di Xiaomi.",
                isGranted = false, // MIUI tidak lapor status izin ini ke API standar
                steps = "Pengaturan → Perizinan Lainnya → Tampilkan jendela pop-up saat berjalan di latar belakang → Selalu Izinkan",
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
                title = "Izin Notifikasi",
                desc = "Untuk menampilkan notifikasi layar kunci sholat.",
                isGranted = hasNotif,
                steps = "Tekan tombol lalu pilih 'Izinkan' pada dialog yang muncul.",
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
                if (allGranted) "Lanjut →" else "Wajib Mengaktifkan Semua Izin",
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
                    Text("Cara", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
                    Text("Buka Pengaturan →", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

    LaunchedEffect(Unit) {
        while (!hasAccessibility) {
            hasAccessibility = CompatibilityUtils.isAccessibilityServiceEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            "Aktifkan Aksesibilitas",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "WAJIB untuk layar kunci bekerja di semua HP (Oppo, Xiaomi, Vivo, dll)",
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
                    "📱 Mengapa perlu?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "HP Oppo, Xiaomi, Vivo, dll memblokir startActivity dari background service. " +
                    "Aksesibilitas adalah satu-satunya cara untuk menampilkan layar kunci otomatis.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "🔒 Privasi",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ungker TIDAK membaca, mengumpulkan, atau mengirim konten layarmu ke mana pun. " +
                    "Hanya mendeteksi nama aplikasi yang dibuka.",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { CompatibilityUtils.openAccessibilitySettings(context) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasAccessibility) Color(0xFF4ADE80) else Color(0xFF2E7D32)
            )
        ) {
            if (hasAccessibility) {
                Icon(Icons.Default.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Aksesibilitas Aktif ✓", fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text("Buka Pengaturan Aksesibilitas →", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (hasAccessibility) {
            TextButton(
                onClick = { onNext() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Lanjut →",
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
            "Matikan Optimasi Baterai",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "WAJIB agar Ungker tetap aktif di latar belakang dan tidak dimatikan oleh sistem.",
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
                    "💡 Mengapa ini wajib?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android secara agresif mematikan aplikasi yang berjalan lama untuk hemat baterai. " +
                    "Jika tidak dimatikan, Ungker mungkin gagal mengunci aplikasi saat kamu membutuhkannya.",
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
                Text("Sudah Diizinkan ✓", fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text("Matikan Optimasi Baterai →", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isIgnored) {
            TextButton(
                onClick = { onNext() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Lanjut →",
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
                errorMessage = "GPS Lambat / Lemah. Coba lagi atau gunakan lokasi default."
                Toast.makeText(context, "Pastikan GPS aktif & kamu di luar ruangan", Toast.LENGTH_LONG).show()
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
                            errorMessage = "Gagal memproses permintaan GPS"
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                        }
                }
            }.addOnFailureListener {
                isLoading = false
                errorMessage = "Gagal mengakses sensor GPS"
                timeoutHandler.removeCallbacks(timeoutRunnable)
            }
        } catch (e: SecurityException) {
            isLoading = false
            errorMessage = "Izin sensor ditolak"
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
            "Deteksi Lokasi",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            "Waktu sholat sangat bergantung pada posisimu saat ini agar akurasinya 100% pas.",
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
                    Text("📡 Sedang mencari sinyal GPS...", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Pastikan berada di tempat terbuka", color = Color(0xFF94A3B8), fontSize = 12.sp)
                } else if (currentLocation != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lokasi Berhasil Dikunci", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("📍 $currentCityName", color = Color(0xFFD1FAE5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Koordinat: ${String.format(Locale.US, "%.4f", currentLocation!!.first)}, ${String.format(Locale.US, "%.4f", currentLocation!!.second)}", color = Color(0xFFD1FAE5), fontSize = 12.sp)
                    Text("Zona Waktu: UTC+${currentTimezone}", color = Color(0xFFD1FAE5), fontSize = 12.sp)
                } else {
                    Text(
                        if (hasLocationPermission) "GPS belum aktif" else "Izin Lokasi Belum Ada",
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
                    Text(if (currentLocation != null) "Dapatkan Ulang" else "Dapatkan Lokasi Otomatis", fontWeight = FontWeight.Bold)
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
                    if (currentLocation != null) "Gunakan & Lanjut →" else "Gunakan Lokasi Jakarta (Manual) →",
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

                var categoryStr = "Lainnya"
                var isTarget = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (app.category) {
                        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> categoryStr = "Sosial Media"
                        android.content.pm.ApplicationInfo.CATEGORY_GAME -> categoryStr = "Game"
                        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> categoryStr = "Hiburan"
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
                    categoryStr = if (lowerPackage.contains("game") || lowerPackage.contains("mobile.legends") || lowerPackage.contains("pubg") || lowerPackage.contains("freefire")) "Game" else "Sosial Media"
                }

                AppInfo(name, packageName, categoryStr)
            }.distinctBy { it.packageName }.sortedBy { it.name }

            withContext(Dispatchers.Main) { 
                apps = appList
                isLoading = false 
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFF1565C0).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { Text("📱", fontSize = 40.sp) }

        Spacer(Modifier.height(24.dp))

        Text(
            "Pilih Aplikasi",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            "Pilih minimal 3 aplikasi yang ingin kamu batasi.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1565C0))
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
        }

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
            enabled = currentSelected.size >= 3,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1565C0),
                disabledContainerColor = Color(0xFF1E293B)
            )
        ) {
            Text(
                if (currentSelected.size >= 3) "Lanjut (${currentSelected.size} Terpilih) →" 
                else "Pilih Minimal 3 Aplikasi",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
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
        TimeOption(30L, "30 Menit", "OTW IQ diatas 100 boss"),
        TimeOption(45L, "45 Menit", "Sangat Direkomendasikan"),
        TimeOption(60L, "1 Jam", "Direkomendasikan"),
        TimeOption(90L, "1 Jam 30 Menit", "Ok lah"),
        TimeOption(120L, "2 Jam", "Lumayan")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            "Batas Waktu Harian",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            "Tentukan berapa lama kamu boleh menggunakan media sosial setiap harinya.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.weight(1f),
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
            Text("Lanjut →", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            "Siapa pengguna aplikasi ini?",
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
                    Text("Pribadi (Umum)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Penggunaan standar untuk diri sendiri.", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
                    Text("Orang Tua", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Mengawasi penggunaan gadget anak.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                RadioButton(
                    selected = selectedRole == "parent",
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00695C))
                )
            }
        }

        Spacer(Modifier.weight(1f))

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
            "Password Orang Tua",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))

        Text(
            "Password ini akan digunakan untuk membuka kunci aplikasi.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Jangan sampai dilihat anak-anak dan jangan lupa! 🤫",
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
            placeholder = { Text("Min. 4 karakter", color = Color(0xFF64748B)) },
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

        Spacer(Modifier.weight(1f))

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
                "Lanjut →",
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
            "Siapa namamu?",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Nama ini akan ditampilkan di profilmu.",
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
            placeholder = { Text("Contoh: Ahmad", color = Color(0xFF64748B)) },
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
                "Mulai Perjalanan ✨",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isValid) Color.White else Color(0xFF4A6B4A)
            )
        }
    }
}