package com.dame.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppUsageStat(val name: String, val packageName: String, val usageMs: Long)

/** Hitung usage time per package dari events dalam rentang [start, end] secara akurat */
fun calcUsageFromEvents(context: Context, start: Long, end: Long): Map<String, Long> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val result = mutableMapOf<String, Long>()
    try {
        val events = usm.queryEvents(start, end)
        val event = android.app.usage.UsageEvents.Event()
        val resumeMap = mutableMapOf<String, Long>() // packageName -> last RESUME time
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ->
                    resumeMap[event.packageName] = event.timeStamp
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumeTime = resumeMap.remove(event.packageName)
                    if (resumeTime != null) {
                        val duration = event.timeStamp - resumeTime
                        if (duration > 0) result[event.packageName] = (result[event.packageName] ?: 0L) + duration
                    }
                }
            }
        }
        // Tutup sesi yang masih terbuka (app masih di foreground saat end)
        resumeMap.forEach { (pkg, resumeTime) ->
            val duration = end - resumeTime
            if (duration > 0) result[pkg] = (result[pkg] ?: 0L) + duration
        }
    } catch (_: Exception) {}
    return result
}

fun isDistractingApp(context: Context, packageName: String): Boolean {
    val pm = context.packageManager
    val pkg = packageName.lowercase()
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                        info.category == ApplicationInfo.CATEGORY_GAME ||
                        info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp")
                    .any { pkg.contains(it) }
    } catch (_: Exception) { false }
}

/** Ambil total distraction time hari ini dalam ms */
fun getDistractionTodayMs(context: Context): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val usageMap = calcUsageFromEvents(context, cal.timeInMillis, System.currentTimeMillis())
    return usageMap.entries.filter { isDistractingApp(context, it.key) }.sumOf { it.value }
}

/** Ambil total distraction time 7 hari terakhir (index 0 = hari ini) */
fun getWeeklyDistractionMs(context: Context): LongArray {
    val result = LongArray(7)
    val dayMs = 86_400_000L
    val now = System.currentTimeMillis()
    // Midnight hari ini
    val todayMidnight = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    for (dayOffset in 0..6) {
        val start = todayMidnight - dayOffset * dayMs
        val end   = if (dayOffset == 0) now else start + dayMs
        val usageMap = calcUsageFromEvents(context, start, end)
        result[dayOffset] = usageMap.entries.filter { isDistractingApp(context, it.key) }.sumOf { it.value }
    }
    return result
}

/** Top 3 app distraction hari ini */
fun getTopDistractingApps(context: Context): List<AppUsageStat> {
    val pm  = context.packageManager
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
    }
    val usageMap = calcUsageFromEvents(context, cal.timeInMillis, System.currentTimeMillis())
    return usageMap.entries
        .filter { it.value > 0 && isDistractingApp(context, it.key) }
        .mapNotNull { (pkg, ms) ->
            try { AppUsageStat(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg, ms) }
            catch (_: Exception) { null }
        }
        .sortedByDescending { it.usageMs }
        .take(3)
}

fun Long.toHhMm(): String {
    val h = TimeUnit.MILLISECONDS.toHours(this)
    val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
}

/**
 * Hitung streak membaca Quran berturut-turut (hari).
 * Dihitung berdasarkan sesi 'read' dari Mushaf atau 'voice' dari LockActivity.
 */
fun getReadingStreak(context: Context): Int {
    val sp       = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    val sessions = sp.getStringSet("reading_sessions", emptySet()) ?: emptySet()
    val fmt      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Ambil tanggal unik dari sesi pembacaan (baik read maupun voice)
    val readDates = sessions
        .filter { it.endsWith("_read") || it.endsWith("_voice") }
        .mapNotNull { it.split("_").getOrNull(0) }
        .toSet()

    var streak = 0
    val cal = Calendar.getInstance()

    // Cek mulai dari hari ini mundur ke belakang
    for (offset in 0..365) {
        val dateStr = fmt.format(cal.time)
        if (dateStr in readDates) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Jika hari ini belum baca, jangan langsung putus streak-nya, cek hari kemarin
            if (offset == 0) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                continue
            }
            break
        }
    }
    return streak
}

/**
 * Hitung streak detox medsos berturut-turut (hari).
 * Satu hari "lulus" jika penggunaan medsos < [limitMs] milidetik.
 * Menggunakan data 7 hari dari getWeeklyDistractionMs dan
 * no_brainwash_streak dari SharedPrefs sebagai cache.
 */
fun getDetoxStreak(context: Context, limitMs: Long = 3L * 3_600_000L): Int {
    val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    // Pakai nilai yang sudah dihitung Profile.kt jika tersedia (hemat CPU)
    return sp.getInt("no_brainwash_streak", 0)
}

// ── Composable streak card ────────────────────────────────────────────────────

@Composable
fun StreakCard(
    icon       : ImageVector,
    streak     : Int,
    label      : String,
    sublabel   : String,
    accentColor: Color,
    modifier   : Modifier = Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lingkaran ikon + angka streak
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Text(
                        "$streak",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color      = accentColor
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    label,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = textPrimC()
                )
                Text(
                    sublabel,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = textSecC(),
                    lineHeight = 16.sp
                )
                // Mini flame bar — tiap titik = 1 hari, max 7
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val show = minOf(streak, 7)
                    repeat(show) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(accentColor, RoundedCornerShape(2.dp))
                        )
                    }
                    // Titik abu sisa (max 7)
                    repeat((7 - show).coerceAtLeast(0)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (LocalIsDarkMode.current) Color(0xFF334155)
                                    else Color(0xFFE0E0E0),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYAR STATISTIK
// ══════════════════════════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun StatistikScreen() {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

    // ── State ──────────────────────────────────────────────────────────────
    var todayMs      by remember { mutableLongStateOf(0L) }
    var weeklyMs     by remember { mutableStateOf(LongArray(7)) }
    var topApps      by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var focusMs      by remember { mutableLongStateOf(0L) }
    var detoxStreak  by remember { mutableIntStateOf(0) }
    var isLoading    by remember { mutableStateOf(true) }

    // Baca SharedPrefs dengan reset harian
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tMs  = getDistractionTodayMs(context)
            val wMs  = getWeeklyDistractionMs(context)
            val apps = getTopDistractingApps(context)
            val lastFocusDate = sp.getString("last_focus_date", "") ?: ""
            val fMs  = if (lastFocusDate == todayStr) sp.getLong("focus_time_today_ms", 0L) else 0L
            val ds   = getDetoxStreak(context)
            withContext(Dispatchers.Main) {
                todayMs      = tMs;  weeklyMs = wMs;  topApps = apps
                focusMs      = fMs;  detoxStreak = ds;  isLoading = false
            }
        }
    }

    // ── BATAS HARIAN: 3 jam = normal, 3-5 jam = cukup, >5 jam = kelewat batas ─
    val normalLimitMs  = 3L * 3_600_000L   // 3 jam
    val warnLimitMs    = 5L * 3_600_000L   // 5 jam (batas kelewat)
    val statusLabel    = when {
        todayMs < normalLimitMs -> "WAJAR"
        todayMs < warnLimitMs   -> "CUKUP"
        else                    -> "KELEWAT BATAS"
    }
    val statusColor = when (statusLabel) {
        "WAJAR"         -> Color(0xFF43A047)
        "CUKUP"         -> Color(0xFFFFA000)
        else            -> Color(0xFFE53935)
    }
    val sisaMs = maxOf(0L, normalLimitMs - todayMs)

    // ── Renungan: estimasi juz jika waktu dipakai baca Qur'an ─────────────
    // 1 juz ≈ 35 menit = 2_100_000 ms
    val estimatedJuz = (todayMs / 2_100_000L).toInt()

    // Urutan hari untuk bar chart (index 6=hari paling lama lalu, 0=hari ini)
    val dayLabels = remember {
        val cal = Calendar.getInstance()
        val dayNames = arrayOf("Min","Sen","Sel","Rab","Kam","Jum","Sab")
        Array(7) { i ->
            val c = Calendar.getInstance()
            c.timeInMillis = cal.timeInMillis - (6 - i) * 86_400_000L
            dayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }
    val weekReversed = remember(weeklyMs) { weeklyMs.reversedArray() }
    val maxWeekMs    = remember(weeklyMs) { weeklyMs.maxOrNull()?.takeIf { it > 0 } ?: 1L }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg()),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── HEADER ──────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg())
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp)
            ) {
                Text("Statistik",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimC())
                Text("Pantau perkembangan fokusmu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecC())
            }
        }

        // ── CARD: BATASAN HARIAN ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("Batasan Harian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = textPrimC())
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tersisa ", style = MaterialTheme.typography.bodySmall, color = textSecC())
                                Text(sisaMs.toHhMm(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold, color = statusColor)
                                Text(" sebelum kelewat batas",
                                    style = MaterialTheme.typography.bodySmall, color = textSecC())
                            }
                        }
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Timer, null, tint = statusColor, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TERPAKAI: ${todayMs.toHhMm()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = textPrimC())
                        Text("BATAS: 3J 00M",
                            style = MaterialTheme.typography.labelSmall, color = textMutC())
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(14.dp)
                            .clip(RoundedCornerShape(7.dp)).background(trackBg())
                    ) {
                        val greenFrac = (todayMs.toFloat() / normalLimitMs).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(greenFrac).fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF43A047), Color(0xFFFFA000)))))
                        if (todayMs > normalLimitMs) {
                            val overFrac  = ((todayMs - normalLimitMs).toFloat() / (warnLimitMs - normalLimitMs)).coerceIn(0f, 1f)
                            val startFrac = normalLimitMs.toFloat() / warnLimitMs
                            Box(Modifier.fillMaxWidth(startFrac + overFrac * (1f - startFrac))
                                .fillMaxHeight().clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.85f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WAJAR", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        Text("CUKUP", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        Text("KELEWAT BATAS", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    }
                }
            }
        }

        // ── CARD: STREAK DETOX MEDSOS ────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            StreakCard(
                icon        = Icons.Default.Security,
                streak      = detoxStreak,
                label       = "Streak Detox Medsos",
                sublabel    = "Hari berturut-turut medsos & game < 3 jam",
                accentColor = Color(0xFF2E7D32),
                modifier    = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        // ── ROW: TOTAL AYAT + WAKTU FOKUS ────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg()),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(greenBgC()), contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.MenuBook, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.height(12.dp))
                        val dailyV = run {
                            val lastDate = sp.getString("last_verse_date", "") ?: ""
                            val today2   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            if (lastDate == today2) sp.getInt("daily_verses", 0) else 0
                        }
                        Text("$dailyV", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = textPrimC())
                        Text("Total Ayat Dibaca", style = MaterialTheme.typography.bodySmall,
                            color = textSecC())
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg()),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (LocalIsDarkMode.current) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Timer, null, tint = if (LocalIsDarkMode.current) Color(0xFF60A5FA) else Color(0xFF1976D2), modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.height(12.dp))
                        val fH = TimeUnit.MILLISECONDS.toHours(focusMs)
                        val fM = TimeUnit.MILLISECONDS.toMinutes(focusMs) % 60
                        val focusLabel = if (fH > 0) "${fH}j ${fM}m" else "${fM}m"
                        Text(focusLabel, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = textPrimC())
                        Text("Waktu Fokus", style = MaterialTheme.typography.bodySmall,
                            color = textSecC())
                    }
                }
            }
        }

        // ── CARD: GRAFIK BATANG 7 HARI ───────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Hiburan & Sosmed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = textPrimC())
                    Text("Durasi (jam) 7 hari terakhir",
                        style = MaterialTheme.typography.bodySmall, color = textSecC())
                    Spacer(Modifier.height(20.dp))
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFF05252), strokeWidth = 2.dp)
                        }
                    } else {
                        val barInactive = if (LocalIsDarkMode.current)
                            Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        else
                            Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)))
                        Row(Modifier.fillMaxWidth().height(140.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weekReversed.forEachIndexed { idx, ms ->
                                val isToday = idx == 6
                                val frac = (ms.toFloat() / maxWeekMs).coerceIn(0f, 1f)
                                val barH = if (ms == 0L) 8.dp else 8.dp + (100.dp - 8.dp) * frac
                                val barColor = if (isToday || ms == maxWeekMs)
                                    Brush.verticalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFF05252)))
                                else barInactive
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(Modifier.width(26.dp).height(barH)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(barColor))
                                    Spacer(Modifier.height(6.dp))
                                    Text(dayLabels.getOrElse(idx) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isToday) Color(0xFFF05252) else textMutC(),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── CARD: WAKTU LAYAR TERTINGGI ──────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg()),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = textPrimC(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Waktu Layar Tertinggi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = textPrimC())
                    }
                    Spacer(Modifier.height(16.dp))
                    if (isLoading) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height(20.dp)
                                .clip(RoundedCornerShape(4.dp)).background(trackBg()))
                            Spacer(Modifier.height(16.dp))
                        }
                    } else if (topApps.isEmpty()) {
                        Text("Tidak ada data penggunaan hari ini.",
                            style = MaterialTheme.typography.bodySmall, color = textMutC())
                    } else {
                        val maxMs = topApps.maxOf { it.usageMs }
                        val appColors = listOf(Color(0xFFEC4899), Color(0xFF60A5FA), Color(0xFFEF4444))
                        topApps.forEachIndexed { idx, app ->
                            val frac  = (app.usageMs.toFloat() / maxMs).coerceIn(0f, 1f)
                            val color = appColors.getOrElse(idx) { Color(0xFF6B7280) }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, color = textPrimC())
                                Text(app.usageMs.toHhMm(), style = MaterialTheme.typography.bodyMedium,
                                    color = textSecC())
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(trackBg())
                            ) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(color))
                            }
                            if (idx < topApps.size - 1) Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // ── CARD: RENUNGAN WAKTU ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5), Color(0xFF7C3AED))))
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(8.dp))
                        Text("Renungan Waktu", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Seandainya Waktu Layarku Kupakai Untuk Membaca Qur'an",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 26.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.15f)).padding(16.dp)
                    ) {
                        Column {
                            Text("Aku bakal dapat", style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$estimatedJuz", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("juz", style = MaterialTheme.typography.titleLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 6.dp))
                            }
                            if (todayMs > 0) {
                                val totalMin = TimeUnit.MILLISECONDS.toMinutes(todayMs)
                                Text("dari $totalMin menit waktu layarmu hari ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f))
                                Text("(1 juz ≈ 35 menit membaca)", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp))
                            } else {
                                Text("Belum ada data penggunaan hari ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}