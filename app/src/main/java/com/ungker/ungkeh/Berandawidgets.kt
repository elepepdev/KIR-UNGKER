package com.ungker.ungkeh

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.util.Log
import java.util.concurrent.TimeUnit
import android.app.usage.UsageStatsManager

fun getDistractionTimeToday(context: Context): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val usageMap = calcUsageFromEvents(context, cal.timeInMillis, System.currentTimeMillis())
    val totalMillis = usageMap.entries
        .filter { isDistractingApp(context, it.key) }
        .sumOf { it.value }
    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BerandaScreen(
    remainingTimeMillis: Long,
    progress: Float,
    blockedCount: Int,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onTambahWaktu: () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    val userName = remember {
        val name = sharedPref.getString("user_name", null)
        if (name != null) name else {
            val uid = sharedPref.getString("user_uid", "00000")
            "Fulan#$uid"
        }
    }

    var totalVerses by remember { mutableIntStateOf(sharedPref.getInt("total_verses", 0)) }
    var dailyVerses by remember {
        val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
        mutableIntStateOf(if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0)
    }
    var distractionTime  by remember { mutableStateOf("...") }
    var readingStreak    by remember { mutableIntStateOf(0) }

    // Animasi masuk untuk elemen-elemen dashboard
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        withContext(Dispatchers.IO) {
            val result = getDistractionTimeToday(context)
            val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
            val tv = sharedPref.getInt("total_verses", 0)
            val dv = if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0
            val rs = getReadingStreak(context)
            
            withContext(Dispatchers.Main) {
                distractionTime = result
                totalVerses     = tv
                dailyVerses     = dv
                readingStreak   = rs
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { -40 })
            ) {
                HeaderDashboard(userName = userName, isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode)
            }
        }
        
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 100)) + slideInVertically(initialOffsetY = { 40 })
            ) {
                Column {
                    CompatibilityCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    TimerCard(remainingTimeMillis, progress, onTambahWaktu)
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 200)) + slideInVertically(initialOffsetY = { 40 })
            ) {
                MotivasiCard()
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 300)) + slideInVertically(initialOffsetY = { 40 })
            ) {
                JadwalSholatCard()
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 400))
            ) {
                Column {
                    Text(
                        "Aktivitas Hari Ini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PhoneAndroid,
                            value = distractionTime,
                            label = "Akses Medsos & Game",
                            iconBg = if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFE3F2FD),
                            iconColor = if (isDarkMode) Color(0xFF90CAF9) else Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Block,
                            value = blockedCount.toString(),
                            label = "Aplikasi Dikunci",
                            iconBg = if (isDarkMode) Color(0xFF3B1F1F) else Color(0xFFFDE7E7),
                            iconColor = if (isDarkMode) Color(0xFFEF9A9A) else Color(0xFFD32F2F)
                        )
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 500)) + slideInVertically(initialOffsetY = { 40 })
            ) {
                AyatReadCard(totalVerses, dailyVerses, readingStreak)
            }
        }
    }
}

@Composable
fun HeaderDashboard(userName: String, isDarkMode: Boolean, onToggleDarkMode: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Assalamu'alaikum,", style = MaterialTheme.typography.bodyLarge, color = textSecC())
            Text("$userName! 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textPrimC())
        }
        IconButton(
            onClick = onToggleDarkMode,
            modifier = Modifier.background(cardBg(), CircleShape)
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Ganti Tema",
                tint = textPrimC()
            )
        }
    }
}

@Composable
fun TimerCard(remainingTimeMillis: Long, progress: Float, onTambahWaktu: () -> Unit) {
    val isLowCredit = progress < 0.2f
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLowCredit) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Sisa Waktu Akses", style = MaterialTheme.typography.labelMedium, color = textSecC())
                    val hours = TimeUnit.MILLISECONDS.toHours(remainingTimeMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTimeMillis) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTimeMillis) % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = textPrimC()
                    )
                }
                Button(onClick = onTambahWaktu, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = if (progress < 0.2f) Color(0xFFE53935) else Color(0xFF2E7D32),
                trackColor = trackBg()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Baca Quran untuk menambah waktu akses aplikasi hiburan", style = MaterialTheme.typography.bodySmall, color = textMutC())
        }
    }
}

@Composable
fun MotivasiCard() {
    val quotes = listOf(
        "\"Maka sesungguhnya bersama kesulitan ada kemudahan.\" (QS. Al-Insyirah: 5)",
        "\"Dan barangsiapa bertawakal kepada Allah, niscaya Allah akan mencukupkan (keperluan)nya.\" (QS. At-Talaq: 3)",
        "\"Sebaik-baik kalian adalah orang yang belajar Al-Qur'an dan mengajarkannya.\" (HR. Bukhari)",
        "\"Hati yang tenang lahir dari tilawah yang rutin.\""
    )
    val quote = remember { quotes.random() }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors = listOf(Color(0xFF2E7D32), Color(0xFF43A047))))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Motivasi Hari Ini", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = quote, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, value: String, label: String, iconBg: Color, iconColor: Color) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(40.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = textPrimC())
            Text(label, style = MaterialTheme.typography.labelSmall, color = textSecC(), lineHeight = 14.sp)
        }
    }
}

@Composable
fun CompatibilityCard() {
    val context = LocalContext.current
    var isBatteryIgnored by remember { mutableStateOf(CompatibilityUtils.isBatteryOptimizationIgnored(context)) }
    var isMiuiPopupDone by remember { mutableStateOf(CompatibilityUtils.isMiuiPopupPermissionGranted(context)) }
    var isMiuiLockDone by remember { mutableStateOf(CompatibilityUtils.isMiuiShowOnLockScreenGranted(context)) }
    var isFullScreenIntentAllowed by remember { mutableStateOf(CompatibilityUtils.canUseFullScreenIntent(context)) }
    var isExactAlarmAllowed by remember { mutableStateOf(CompatibilityUtils.canScheduleExactAlarms(context)) }
    var isOemManualDone by remember { mutableStateOf(CompatibilityUtils.isOemPermissionManualDone(context)) }
    
    val isXiaomi = remember { CompatibilityUtils.isXiaomi() }
    val isOppoRealme = remember { CompatibilityUtils.isOppoRealme() }
    val isVivo = remember { CompatibilityUtils.isVivo() }
    val isHuawei = remember { CompatibilityUtils.isHuawei() }
    val isAndroid14 = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE }
    val isAndroid12 = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            isBatteryIgnored = CompatibilityUtils.isBatteryOptimizationIgnored(context)
            isOemManualDone = CompatibilityUtils.isOemPermissionManualDone(context)
            isExactAlarmAllowed = CompatibilityUtils.canScheduleExactAlarms(context)
            if (isXiaomi) {
                isMiuiPopupDone = CompatibilityUtils.isMiuiPopupPermissionGranted(context)
                isMiuiLockDone = CompatibilityUtils.isMiuiShowOnLockScreenGranted(context)
            }
            if (isAndroid14) {
                isFullScreenIntentAllowed = CompatibilityUtils.canUseFullScreenIntent(context)
            }
        }
    }

    // PENTING: Logika "Sembunyikan" yang pintar
    val needsBatteryFix = !isBatteryIgnored
    val needsAndroid14Fix = isAndroid14 && !isFullScreenIntentAllowed
    val needsExactAlarmFix = isAndroid12 && !isExactAlarmAllowed
    val needsMiuiFix = isXiaomi && (!isMiuiPopupDone || !isMiuiLockDone)
    val needsOemReminder = (isOppoRealme || isVivo || isHuawei) && !isOemManualDone

    val shouldShow = needsBatteryFix || needsAndroid14Fix || needsExactAlarmFix || needsMiuiFix || needsOemReminder
    
    if (!shouldShow) return
    
    // Jika HP Samsung/Pixel (bukan Xiaomi/Oppo dsb) DAN semua fix wajib sudah aman -> SEMBUNYIKAN
    if (!isXiaomi && !isOppoRealme && !isVivo && !isHuawei && !needsBatteryFix && !needsAndroid14Fix && !needsExactAlarmFix) return

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (LocalIsDarkMode.current) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text("Optimasi Perangkat Terdeteksi", fontWeight = FontWeight.Bold, color = textPrimC())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Sistem HP Anda membatasi fitur lock screen otomatis. Mohon aktifkan izin berikut agar UNGKER bisa berfungsi 100%:",
                style = MaterialTheme.typography.bodySmall,
                color = textSecC()
            )
            Spacer(Modifier.height(16.dp))

            // Item 1: Battery (Universal)
            if (needsBatteryFix) {
                CompatibilityItem(
                    label = "Abaikan Optimasi Baterai",
                    isDone = false,
                    onClick = { CompatibilityUtils.requestIgnoreBatteryOptimization(context) }
                )
                Spacer(Modifier.height(12.dp))
            }

            // Item 2: Android 14 Full Screen Intent (Universal)
            if (needsAndroid14Fix) {
                CompatibilityItem(
                    label = "Izin Tampil di Layar Kunci (Android 14)",
                    isDone = false,
                    onClick = { CompatibilityUtils.openFullScreenIntentSettings(context) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Item 2.5: Exact Alarm (Krusial untuk Oppo/Realme)
            if (needsExactAlarmFix) {
                CompatibilityItem(
                    label = "Izin Penjadwalan Tepat (Wajib)",
                    isDone = false,
                    onClick = { CompatibilityUtils.openExactAlarmSettings(context) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Item 3: Xiaomi Specifics
            if (isXiaomi) {
                if (!isMiuiPopupDone) {
                    CompatibilityItem(
                        label = "Tampil Pop-up Latar Belakang",
                        isDone = false,
                        onClick = { CompatibilityUtils.openMiuiPopupPermission(context) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (!isMiuiLockDone) {
                    CompatibilityItem(
                        label = "Tampil di Kunci Layar",
                        isDone = false,
                        onClick = { CompatibilityUtils.openMiuiPopupPermission(context) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // Auto start (Gak bisa dicek, tampilkan saja sebagai info)
                CompatibilityItem(
                    label = "Izin Auto-start",
                    isDone = false, 
                    onClick = { CompatibilityUtils.openAutoStartSettings(context) }
                )
            } 
            
            // Item 4: Oppo/Realme/Vivo Specifics
            else if (isOppoRealme || isVivo || isHuawei) {
                CompatibilityItem(
                    label = "Izin Jendela Mengambang (Oppo/Realme/Vivo)",
                    isDone = false,
                    onClick = { CompatibilityUtils.openShowOnLockScreenSettings(context) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { 
                        CompatibilityUtils.setOemPermissionManualDone(context)
                        isOemManualDone = true
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sudah Saya Aktifkan ✅", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Setelah saklar UNGKER di pengaturan aktif, klik tombol di atas untuk menghilangkan peringatan ini.",
                    style = MaterialTheme.typography.labelSmall,
                    color = textMutC(),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CompatibilityItem(label: String, isDone: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDone) Color(0xFF2E7D32).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
            .clickable(enabled = !isDone) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isDone) "✅" else "🔘", fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (isDone) Color(0xFF2E7D32) else textPrimC())
        if (!isDone) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMutC(), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AyatReadCard(totalVerses: Int, dailyVerses: Int, readingStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).background(greenBgC(), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Progres Membaca", style = MaterialTheme.typography.labelMedium, color = textSecC())
                    Text("$dailyVerses Ayat Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimC())
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = textSecC())
                    Text("$totalVerses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
            if (readingStreak > 0) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = dividerC())
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Streak $readingStreak hari",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Spacer(Modifier.weight(1f))
                    // Dot indicator — tiap dot = 1 hari, max 7
                    val show = minOf(readingStreak, 7)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(show) {
                            Box(Modifier.size(8.dp).background(Color(0xFFE65100), RoundedCornerShape(2.dp)))
                        }
                        repeat((7 - show).coerceAtLeast(0)) {
                            Box(Modifier.size(8.dp).background(
                                if (LocalIsDarkMode.current) Color(0xFF334155) else Color(0xFFE0E0E0),
                                RoundedCornerShape(2.dp)
                            ))
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalSholatCard() {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    
    // State yang reaktif terhadap perubahan SharedPreferences
    var locationLabel by remember { mutableStateOf(sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta") }
    var usedLat by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lat", -6.2088f).toDouble()) }
    var usedLng by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lng", 106.8456f).toDouble()) }
    var usedTz  by remember { mutableIntStateOf(sharedPref.getInt("sholat_tz", 7)) }
    var isGpsMode by remember { mutableStateOf(sharedPref.getBoolean("sholat_gps_mode", false)) }
    
    var prayerTimes by remember { mutableStateOf<PrayerTimes?>(null) }
    var isLoadingGps by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember {
        val savedName = sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta"
        mutableStateOf(INDONESIA_CITIES.find { it.name == savedName } ?: INDONESIA_CITIES[10])
    }

    // Listener untuk memantau perubahan SharedPreferences (termasuk dari Onboarding)
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "sholat_lat" -> usedLat = sp.getFloat("sholat_lat", -6.2088f).toDouble()
                "sholat_lng" -> usedLng = sp.getFloat("sholat_lng", 106.8456f).toDouble()
                "sholat_tz" -> usedTz = sp.getInt("sholat_tz", 7)
                "sholat_city_name" -> locationLabel = sp.getString("sholat_city_name", "Jakarta") ?: "Jakarta"
                "sholat_gps_mode" -> isGpsMode = sp.getBoolean("sholat_gps_mode", false)
            }
        }
        sharedPref.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPref.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Hitung ulang jadwal sholat jika koordinat berubah
    LaunchedEffect(usedLat, usedLng, usedTz) {
        withContext(Dispatchers.Default) {
            val cal = Calendar.getInstance()
            val pt = hitungWaktuSholat(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), usedLat, usedLng, usedTz)
            withContext(Dispatchers.Main) { 
                prayerTimes = pt 
            }
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isLoadingGps = true
            getGpsLocation(context, onSuccess = { lat, lng, tz, label ->
                usedLat = lat; usedLng = lng; usedTz = tz; locationLabel = label
                isGpsMode = true; isLoadingGps = false
                sharedPref.edit(commit = true) {
                    putFloat("sholat_lat", lat.toFloat())
                    putFloat("sholat_lng", lng.toFloat())
                    putInt("sholat_tz", tz)
                    putString("sholat_city_name", label)
                    putBoolean("sholat_gps_mode", true)
                }
            }, onFail = { isLoadingGps = false; showCityPicker = true })
        } else { 
            showCityPicker = true 
        }
    }

    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("Pilih Kota", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih kota terdekat dengan lokasimu:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF757575))
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = !cityExpanded }) {
                        OutlinedTextField(
                            value = "${selectedCity.name} (${selectedCity.province})", onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                            INDONESIA_CITIES.forEach { city ->
                                DropdownMenuItem(text = { Text("${city.name} (${city.province})") }, onClick = {
                                    selectedCity = city; usedLat = city.lat; usedLng = city.lng; usedTz = city.tzOffset; locationLabel = city.name
                                    isGpsMode = false; cityExpanded = false; showCityPicker = false
                                    sharedPref.edit {putFloat("sholat_lat", city.lat.toFloat()).putFloat("sholat_lng", city.lng.toFloat())
                                        .putInt("sholat_tz", city.tzOffset).putString("sholat_city_name", city.name).putBoolean("sholat_gps_mode", false)}
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCityPicker = false }) { Text("Tutup") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Jadwal Sholat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimC())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isLoadingGps) "Mencari lokasi..." else locationLabel, style = MaterialTheme.typography.bodySmall, color = textSecC())
                        if (isGpsMode) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF2E7D32)) }
                    }
                }
                IconButton(
                    onClick = { locationPermLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    modifier = Modifier.size(32.dp).background(greenBgC(), CircleShape)
                ) {
                    if (isLoadingGps) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2E7D32))
                    else Icon(Icons.Default.Language, contentDescription = "Lokasi", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            prayerTimes?.let { pt ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SholatItem("Subuh", pt.subuh); SholatItem("Dzuhur", pt.dzuhur); SholatItem("Ashar", pt.ashar); SholatItem("Maghrib", pt.maghrib); SholatItem("Isya", pt.isya)
                }
            } ?: Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun SholatItem(name: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = textSecC())
        Spacer(modifier = Modifier.height(4.dp))
        Text(time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textPrimC())
    }
}