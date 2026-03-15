package com.dame.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: String
)

// ── Dark Mode state — disimpan di level Activity, diteruskan sebagai parameter ──
// Menggunakan compositionLocalOf yang sudah tersedia via import androidx.compose.runtime.*
val LocalIsDarkMode = compositionLocalOf { false }

// ══════════════════════════════════════════════════════════════════════════════
// TOKEN WARNA DARK/LIGHT — satu sumber kebenaran untuk seluruh UI
// Semua composable mengambil warna dari sini, bukan hardcode Color(0xFF...)
// ══════════════════════════════════════════════════════════════════════════════
object DarkTheme {
    // Background layers
    val bgPage      = Color(0xFF0F172A)   // background halaman
    val bgCard      = Color(0xFF1E293B)   // card utama
    val bgCardAlt   = Color(0xFF263348)   // card secondary / icon background
    val bgInput     = Color(0xFF1E293B)   // text field background
    val bgTrack     = Color(0xFF334155)   // progress bar track

    // Teks
    val textPrimary = Color(0xFFF1F5F9)   // heading, nilai penting
    val textSecond  = Color(0xFF94A3B8)   // subtext, label
    val textMuted   = Color(0xFF64748B)   // placeholder, hint

    // Warna aksen (tetap sama di dark/light)
    val green       = Color(0xFF4ADE80)
    val greenDark   = Color(0xFF2E7D32)   // dipakai teks & border
    val greenBg     = Color(0xFF14532D)   // background pill hijau gelap
    val amber       = Color(0xFFFBBF24)
    val red         = Color(0xFFEF4444)

    // Border
    val border      = Color(0xFF334155)
    val divider     = Color(0xFF1E293B)

    // Canvas / arc
    val arcTrack    = Color(0xFF334155)
    val sunCircle   = Color(0xFF1E293B)
    val horizon     = Color(0xFF334155)
}

object LightTheme {
    val bgPage      = Color(0xFFF8F9FA)
    val bgCard      = Color.White
    val bgCardAlt   = Color(0xFFF0F0F0)
    val bgInput     = Color.White
    val bgTrack     = Color(0xFFF3F4F6)

    val textPrimary = Color(0xFF1A1A1A)
    val textSecond  = Color(0xFF6B7280)
    val textMuted   = Color(0xFF9CA3AF)

    val green       = Color(0xFF2E7D32)
    val greenDark   = Color(0xFF2E7D32)
    val greenBg     = Color(0xFFE8F5E9)
    val amber       = Color(0xFFFFA000)
    val red         = Color(0xFFE53935)

    val border      = Color(0xFFE0E0E0)
    val divider     = Color(0xFFEEEEEE)

    val arcTrack    = Color(0xFFEEEEEE)
    val sunCircle   = Color.White
    val horizon     = Color(0xFFE8E8E8)
}

/** Shortcut: ambil token warna sesuai mode saat ini */
@Composable
fun cardBg()      = if (LocalIsDarkMode.current) DarkTheme.bgCard      else LightTheme.bgCard
@Composable
fun cardAltBg()   = if (LocalIsDarkMode.current) DarkTheme.bgCardAlt   else LightTheme.bgCardAlt
@Composable
fun pageBg()      = if (LocalIsDarkMode.current) DarkTheme.bgPage      else LightTheme.bgPage
@Composable
fun textPrimC()   = if (LocalIsDarkMode.current) DarkTheme.textPrimary else LightTheme.textPrimary
@Composable
fun textSecC()    = if (LocalIsDarkMode.current) DarkTheme.textSecond  else LightTheme.textSecond
@Composable
fun textMutC()    = if (LocalIsDarkMode.current) DarkTheme.textMuted   else LightTheme.textMuted
@Composable
fun trackBg()     = if (LocalIsDarkMode.current) DarkTheme.bgTrack     else LightTheme.bgTrack
@Composable
fun greenAccent() = if (LocalIsDarkMode.current) DarkTheme.green       else LightTheme.green
@Composable
fun greenBgC()    = if (LocalIsDarkMode.current) DarkTheme.greenBg     else LightTheme.greenBg
@Composable
fun dividerC()    = if (LocalIsDarkMode.current) DarkTheme.divider     else LightTheme.divider
@Composable
fun borderC()     = if (LocalIsDarkMode.current) DarkTheme.border      else LightTheme.border

// ══════════════════════════════════════════════════════════════════════════════
// HISAB WAKTU SHOLAT — METODE KEMENAG RI
// Referensi: "Ilmu Falak Praktis" Kemenag RI
// Fajr: 20°, Isha: 18°, Ihtiyat: +2 mnt, Asar: mazhab Syafi'i
// ══════════════════════════════════════════════════════════════════════════════

data class PrayerTimes(
    val subuh: String,
    val syuruq: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String,
    val syuruqDecimal: Double,   // untuk arc matahari
    val maghribDecimal: Double,  // untuk arc matahari
    val nowDecimal: Double,      // jam sekarang dalam desimal
)

data class CityLocation(val name: String, val lat: Double, val lng: Double, val tzOffset: Int, val province: String)

/** 43 kota besar Indonesia dengan koordinat akurat */
val INDONESIA_CITIES: List<CityLocation> = listOf(
    CityLocation("Banda Aceh",      5.5483,  95.3238, 7, "Aceh"),
    CityLocation("Medan",           3.5952,  98.6722, 7, "Sumatera Utara"),
    CityLocation("Padang",         -0.9471, 100.4172, 7, "Sumatera Barat"),
    CityLocation("Pekanbaru",       0.5335, 101.4474, 7, "Riau"),
    CityLocation("Batam",           1.0746, 104.0305, 7, "Kepulauan Riau"),
    CityLocation("Jambi",          -1.6102, 103.6131, 7, "Jambi"),
    CityLocation("Palembang",      -2.9761, 104.7754, 7, "Sumatera Selatan"),
    CityLocation("Bengkulu",       -3.7928, 102.2608, 7, "Bengkulu"),
    CityLocation("Bandar Lampung", -5.3971, 105.2668, 7, "Lampung"),
    CityLocation("Pangkalpinang",  -2.1337, 106.1164, 7, "Bangka Belitung"),
    CityLocation("Jakarta",        -6.2088, 106.8456, 7, "DKI Jakarta"),
    CityLocation("Bogor",          -6.5971, 106.8060, 7, "Jawa Barat"),
    CityLocation("Bandung",        -6.9175, 107.6191, 7, "Jawa Barat"),
    CityLocation("Bekasi",         -6.2383, 106.9756, 7, "Jawa Barat"),
    CityLocation("Depok",          -6.4025, 106.7942, 7, "Jawa Barat"),
    CityLocation("Tangerang",      -6.1783, 106.6319, 7, "Banten"),
    CityLocation("Serang",         -6.1204, 106.1503, 7, "Banten"),
    CityLocation("Semarang",       -6.9932, 110.4203, 7, "Jawa Tengah"),
    CityLocation("Solo",           -7.5642, 110.8317, 7, "Jawa Tengah"),
    CityLocation("Yogyakarta",     -7.7956, 110.3695, 7, "DI Yogyakarta"),
    CityLocation("Surabaya",       -7.2575, 112.7521, 7, "Jawa Timur"),
    CityLocation("Malang",         -7.9654, 112.6326, 7, "Jawa Timur"),
    CityLocation("Kediri",         -7.8480, 112.0178, 7, "Jawa Timur"),
    CityLocation("Denpasar",       -8.6705, 115.2126, 8, "Bali"),
    CityLocation("Mataram",        -8.5833, 116.1167, 8, "NTB"),
    CityLocation("Kupang",        -10.1789, 123.6070, 8, "NTT"),
    CityLocation("Pontianak",       0.0263, 109.3425, 7, "Kalimantan Barat"),
    CityLocation("Palangkaraya",   -2.2136, 113.9108, 7, "Kalimantan Tengah"),
    CityLocation("Banjarmasin",    -3.3194, 114.5908, 8, "Kalimantan Selatan"),
    CityLocation("Samarinda",      -0.5022, 117.1536, 8, "Kalimantan Timur"),
    CityLocation("Balikpapan",     -1.2654, 116.8312, 8, "Kalimantan Timur"),
    CityLocation("Tarakan",         3.3013, 117.5783, 8, "Kalimantan Utara"),
    CityLocation("Makassar",       -5.1477, 119.4327, 8, "Sulawesi Selatan"),
    CityLocation("Parepare",       -4.0135, 119.6298, 8, "Sulawesi Selatan"),
    CityLocation("Kendari",        -3.9985, 122.5127, 8, "Sulawesi Tenggara"),
    CityLocation("Palu",           -0.8917, 119.8707, 8, "Sulawesi Tengah"),
    CityLocation("Gorontalo",       0.5435, 123.0600, 8, "Gorontalo"),
    CityLocation("Manado",          1.4748, 124.8421, 8, "Sulawesi Utara"),
    CityLocation("Ternate",         0.7833, 127.3667, 9, "Maluku Utara"),
    CityLocation("Ambon",          -3.6954, 128.1814, 9, "Maluku"),
    CityLocation("Sorong",         -0.8833, 131.2500, 9, "Papua Barat"),
    CityLocation("Manokwari",      -0.8615, 134.0650, 9, "Papua Barat Daya"),
    CityLocation("Jayapura",       -2.5337, 140.7181, 9, "Papua"),
)

/**
 * Menghitung waktu sholat sesuai metode Kemenag RI.
 * @param year, month, day  — tanggal Masehi
 * @param lat, lng           — koordinat desimal (negatif = S/W)
 * @param tz                 — offset zona waktu dari UTC (WIB=7, WITA=8, WIT=9)
 * @param fajrAngle          — sudut Subuh (20° sesuai Kemenag)
 * @param ishaAngle          — sudut Isya  (18° sesuai Kemenag)
 * @param ihtiyat            — ihtiyat/kehati-hatian dalam menit (default 2)
 */
fun hitungWaktuSholat(
    year: Int, month: Int, day: Int,
    lat: Double, lng: Double, tz: Int,
    fajrAngle: Double = 20.0, ishaAngle: Double = 18.0, ihtiyat: Int = 2
): PrayerTimes {
    // ── 1. Julian Day Number ─────────────────────────────────────────────────
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 12 else month
    val A = y / 100; val B = 2 - A + A / 4
    val JD = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toInt() + day + B - 1524.5

    // ── 2. Julian Century ────────────────────────────────────────────────────
    val T = (JD - 2451545.0) / 36525.0

    // ── 3. Posisi Matahari ───────────────────────────────────────────────────
    val L0  = ((280.46646 + 36000.76983 * T + 0.0003032 * T * T) % 360 + 360) % 360
    val Msr = ((357.52911 + 35999.05029 * T - 0.0001537 * T * T) % 360).toRadians()
    val C   = (1.914602 - 0.004817*T - 0.000014*T*T) * sin(Msr) +
            (0.019993 - 0.000101*T) * sin(2*Msr) + 0.000289 * sin(3*Msr)
    val sunLon  = L0 + C
    val omega   = (125.04 - 1934.136 * T).toRadians()
    val lam     = (sunLon - 0.00569 - 0.00478 * sin(omega)).toRadians()

    // ── 4. Deklinasi & Equation of Time ─────────────────────────────────────
    val eps0 = 23 + 26.0/60 + 21.448/3600 - (46.8150/3600)*T - (0.00059/3600)*T*T + (0.001813/3600)*T*T*T
    val eps  = (eps0 + 0.00256 * cos(omega)).toRadians()
    val dec  = asin(sin(eps) * sin(lam))   // deklinasi matahari (radian)

    val yy   = tan(eps / 2).pow(2)
    val L0r  = L0.toRadians()
    val e    = 0.016708634 - 0.000042037 * T
    val EqT  = 4 * Math.toDegrees(
        yy*sin(2*L0r) - 2*e*sin(Msr) + 4*e*yy*sin(Msr)*cos(2*L0r) -
                0.5*yy*yy*sin(4*L0r) - 1.25*e*e*sin(2*Msr)
    )  // dalam menit

    // ── 5. Transit (Dhuhur) ─────────────────────────────────────────────────
    val transit = 12.0 + tz - lng / 15.0 - EqT / 60.0

    val latR = lat.toRadians()

    fun hourAngle(elevDeg: Double): Double {
        val cosHA = (sin(elevDeg.toRadians()) - sin(latR)*sin(dec)) / (cos(latR)*cos(dec))
        return Math.toDegrees(acos(cosHA.coerceIn(-1.0, 1.0))) / 15.0
    }

    // ── 6. Waktu-waktu sholat ────────────────────────────────────────────────
    val tSubuh   = transit - hourAngle(-fajrAngle)
    val tSyuruq  = transit - hourAngle(-0.8333)
    val tDzuhur  = transit
    val asrElev  = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(latR - dec)))))
    val tAshar   = transit + hourAngle(asrElev)
    val tMaghrib = transit + hourAngle(-0.8333)
    val tIsya    = transit + hourAngle(-ishaAngle)

    // ── 7. Format ke HH:MM dengan ihtiyat ───────────────────────────────────
    fun Double.toHHMM(addIhtiyat: Boolean = true): String {
        val total = this + if (addIhtiyat) ihtiyat / 60.0 else 0.0
        val h = total.toInt() % 24
        var mn = ((total % 1.0) * 60.0).let { kotlin.math.round(it).toInt() }
        val hFinal = if (mn == 60) { mn = 0; (h + 1) % 24 } else h
        return "%02d:%02d".format(hFinal, mn)
    }

    // Jam sekarang dalam desimal lokal
    val cal = Calendar.getInstance()
    val nowDecimal = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE)/60.0 + cal.get(Calendar.SECOND)/3600.0

    return PrayerTimes(
        subuh   = tSubuh.toHHMM(),
        syuruq  = tSyuruq.toHHMM(false),
        dzuhur  = tDzuhur.toHHMM(),
        ashar   = tAshar.toHHMM(),
        maghrib = tMaghrib.toHHMM(),
        isya    = tIsya.toHHMM(),
        syuruqDecimal  = tSyuruq + ihtiyat/60.0,
        maghribDecimal = tMaghrib + ihtiyat/60.0,
        nowDecimal     = nowDecimal,
    )
}

private fun Double.toRadians(): Double = this * PI / 180.0

/** Tentukan sholat berikutnya berdasarkan waktu sekarang */
fun getNextPrayer(pt: PrayerTimes): Pair<String, String> {
    fun toDouble(hhmm: String): Double {
        val parts = hhmm.split(":"); return parts[0].toDouble() + parts[1].toDouble() / 60.0
    }
    val now = pt.nowDecimal
    val prayers = listOf(
        "Subuh" to toDouble(pt.subuh), "Dzuhur" to toDouble(pt.dzuhur),
        "Ashar" to toDouble(pt.ashar), "Maghrib" to toDouble(pt.maghrib),
        "Isya" to toDouble(pt.isya),
    )
    val next = prayers.firstOrNull { it.second > now } ?: prayers.first()
    val diffMin = ((next.second - now + 24) % 24 * 60).toInt()
    val label = if (diffMin < 60) "Menuju ${next.first}" else "Menuju ${next.first}"
    return next.first to label
}

// ══════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, UngkerService::class.java)
        startForegroundService(serviceIntent)

        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        if (stats.isEmpty()) {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        setContent {
            // isDarkMode di-hoist langsung di sini — setContent adalah @Composable context
            val sharedPref = remember {
                getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
            }
            var isDarkMode by remember {
                mutableStateOf(sharedPref.getBoolean("dark_mode", false))
            }

            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    background   = Color(0xFF0F172A),
                    surface      = Color(0xFF1E293B),
                    onSurface    = Color(0xFFF1F5F9),
                    onBackground = Color(0xFFF1F5F9),
                    primary      = Color(0xFF4ADE80),
                    onPrimary    = Color(0xFF052E16),
                )
            } else {
                lightColorScheme()
            }

            CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8F9FA)
                    ) {
                        LayarUtama(
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = {
                                val newVal = !isDarkMode
                                isDarkMode = newVal
                                sharedPref.edit().putBoolean("dark_mode", newVal).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menghitung total waktu penggunaan untuk aplikasi yang tergolong Hiburan, Sosial Media, atau Game hari ini.
 */
fun getDistractionTimeToday(context: Context): String {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = context.packageManager
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    var totalMillis = 0L

    stats?.forEach { usageStats ->
        try {
            val appInfo = pm.getApplicationInfo(usageStats.packageName, 0)
            val packageName = usageStats.packageName.lowercase()

            var isDistraction = false

            // Cek Kategori Sistem (Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO) {
                    isDistraction = true
                }
            }

            // Cek Manual untuk aplikasi populer (Fallback)
            if (!isDistraction) {
                val targets = listOf("instagram", "tiktok", "youtube", "facebook", "twitter", "game", "mobile.legends", "whatsapp")
                if (targets.any { packageName.contains(it) }) {
                    isDistraction = true
                }
            }

            if (isDistraction) {
                totalMillis += usageStats.totalTimeInForeground
            }
        } catch (e: Exception) { }
    }

    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}

@Composable
fun LayarUtama(isDarkMode: Boolean = false, onToggleDarkMode: () -> Unit = {}) {
    val context = LocalContext.current
    val dark  = isDarkMode
    val navBg = if (dark) Color(0xFF1E293B) else Color.White
    val bg    = if (dark) Color(0xFF0F172A) else Color(0xFFF8F9FA)
    var selectedTab by remember { mutableIntStateOf(0) }
    var remainingTimeMillis by remember { mutableLongStateOf(0L) }
    var blockedCount by remember { mutableIntStateOf(0) }

    val maxCreditLimit = 3600000L // 1 Jam
    val READ_COOLDOWN_MS = 5L * 60 * 1000L  // 5 menit cooldown untuk tambah waktu
    val READ_CREDIT_MS   = 5L * 60 * 1000L  // kredit tetap 5 menit per sesi baca

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                val credit = sharedPref.getLong("remaining_credit", 0L)
                val count = sharedPref.getStringSet("blocked_apps", emptySet())?.size ?: 0
                withContext(Dispatchers.Main) {
                    remainingTimeMillis = credit
                    blockedCount = count
                }
            }
            delay(500)
        }
    }

    Scaffold(
        containerColor = bg,
        bottomBar = {
            NavigationBar(containerColor = navBg, tonalElevation = 8.dp) {
                val items = listOf("🏠", "📱", "📖", "📊", "👤")
                items.forEachIndexed { index, icon ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 24.sp)
                                if (selectedTab == index) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(Color(0xFF2E7D32), CircleShape)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (androidx.compose.animation.slideInHorizontally { it / 3 } +
                                androidx.compose.animation.fadeIn()).togetherWith(
                            androidx.compose.animation.slideOutHorizontally { -it / 3 } +
                                    androidx.compose.animation.fadeOut()
                        )
                    } else {
                        (androidx.compose.animation.slideInHorizontally { -it / 3 } +
                                androidx.compose.animation.fadeIn()).togetherWith(
                            androidx.compose.animation.slideOutHorizontally { it / 3 } +
                                    androidx.compose.animation.fadeOut()
                        )
                    }.using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> BerandaScreen(
                        remainingTimeMillis = remainingTimeMillis,
                        progress = if (remainingTimeMillis > 0) remainingTimeMillis.toFloat() / maxCreditLimit.toFloat() else 0f,
                        blockedCount = blockedCount,
                        isDarkMode = dark,
                        onToggleDarkMode = onToggleDarkMode,
                        onTambahWaktu = {
                            val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                            val lastRead = sp.getLong("last_read_tambah_time", 0L)
                            val now = System.currentTimeMillis()
                            when {
                                remainingTimeMillis >= maxCreditLimit ->
                                    Toast.makeText(context, "Waktu sudah maksimal (1 Jam)!", Toast.LENGTH_SHORT).show()
                                now - lastRead < READ_COOLDOWN_MS -> {
                                    val sisaDetik = ((READ_COOLDOWN_MS - (now - lastRead)) / 1000).toInt()
                                    val mnt = sisaDetik / 60; val det = sisaDetik % 60
                                    Toast.makeText(context,
                                        "Cooldown aktif! Tunggu ${mnt}m ${det}d lagi",
                                        Toast.LENGTH_LONG).show()
                                }
                                else -> {
                                    // Buka LockActivity dengan mode "read_only" — kredit tetap 5 menit
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
                    2 -> QuranScreen()
                    3 -> StatistikScreen(isDarkMode = dark)
                    else -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { Text("Halaman Belum Tersedia") }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// DATA JUZ AL-QUR'AN (Standar Mushaf Rasm Usmani 604 halaman)
// ─────────────────────────────────────────────────────────────────────────────

/** Halaman pertama setiap juz (index 0 = Juz 1, dst.) */
val JUZ_PAGE_START = intArrayOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
    202, 222, 242, 262, 282, 302, 322, 342, 362, 382,
    402, 422, 442, 462, 482, 502, 522, 542, 562, 582
)

/**
 * Surah ID yang menjadi penanda awal juz di daftar surat.
 * Kunci = surah ID, nilai = nomor juz yang mulai dari sekitar surah ini.
 * Catatan: beberapa juz dimulai di tengah surah — di sini dipakai surah terdekat sebagai pembatas visual.
 */
val SURAH_JUZ_START: Map<Int, Int> = mapOf(
    1 to 1,  2 to 2,  3 to 4,  4 to 5,  5 to 7,
    6 to 8,  7 to 9,  8 to 10, 9 to 11, 11 to 12,
    12 to 13, 15 to 14, 17 to 15, 18 to 16, 21 to 17,
    23 to 18, 25 to 19, 27 to 20, 29 to 21, 33 to 22,
    36 to 23, 39 to 24, 41 to 25, 46 to 26, 51 to 27,
    58 to 28, 67 to 29, 78 to 30
)

/** Kembalikan nomor juz (1-30) berdasarkan ID surah. */
fun getJuzOfSurah(surahId: Int): Int {
    var juz = 1
    for ((sid, j) in SURAH_JUZ_START.entries.sortedBy { it.key }) {
        if (surahId >= sid) juz = j else break
    }
    return juz
}

/** Kembalikan nomor juz (1-30) berdasarkan nomor halaman (1-604). */
fun getJuzOfPage(page: Int): Int {
    var juz = 1
    for (i in JUZ_PAGE_START.indices) {
        if (page >= JUZ_PAGE_START[i]) juz = i + 1 else break
    }
    return juz
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuranScreen() {
    val context = LocalContext.current
    val dbHelper = remember(context) { QuranDatabaseHelper(context) }

    var surahList     by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var selectedSurah by remember { mutableStateOf<Chapter?>(null) }
    var selectedPage  by remember { mutableIntStateOf(-1) }
    var quranTab      by remember { mutableIntStateOf(0) }   // 0=Surat 1=Halaman 2=Juz
    var isLoading     by remember { mutableStateOf(true) }

    // ── State pencarian ──────────────────────────────────────────────────────
    var surahSearchQuery by remember { mutableStateOf("") }
    var pageSearchQuery  by remember { mutableStateOf("") }
    var juzSearchQuery   by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = try { dbHelper.getAllChapters() }
            catch (e: Exception) { android.util.Log.e("QuranScreen", "getAllChapters gagal: ${e.message}", e); emptyList() }
            withContext(Dispatchers.Main) { surahList = list; isLoading = false }
        }
    }

    // Filter surah: nama Arab, nama latin, nomor, atau "juz X"
    val filteredSurahList = remember(surahList, surahSearchQuery) {
        if (surahSearchQuery.isBlank()) surahList
        else {
            val q = surahSearchQuery.trim().lowercase()
            val juzNum = Regex("^(?:juz|jz)\\s*(\\d+)$").find(q)?.groupValues?.get(1)?.toIntOrNull()
            if (juzNum != null && juzNum in 1..30) {
                surahList.filter { getJuzOfSurah(it.id) == juzNum }
            } else {
                surahList.filter { chapter ->
                    chapter.name.contains(q, ignoreCase = true) ||
                            chapter.nameLatin.contains(q, ignoreCase = true) ||
                            chapter.id.toString() == q
                }
            }
        }
    }

    // Filter halaman: nomor atau "juz X"
    val filteredPages = remember(pageSearchQuery) {
        val q = pageSearchQuery.trim().lowercase()
        when {
            q.isBlank() -> (1..604).toList()
            else -> {
                val juzNum = Regex("^(?:juz|jz)\\s*(\\d+)$").find(q)?.groupValues?.get(1)?.toIntOrNull()
                if (juzNum != null && juzNum in 1..30) {
                    val start = JUZ_PAGE_START[juzNum - 1]
                    val end   = if (juzNum < 30) JUZ_PAGE_START[juzNum] - 1 else 604
                    (start..end).toList()
                } else {
                    (1..604).filter { it.toString().startsWith(q) }
                }
            }
        }
    }

    // Filter tab Juz
    val filteredJuzList = remember(juzSearchQuery) {
        val q = juzSearchQuery.trim()
        if (q.isBlank()) (1..30).toList()
        else { val num = q.toIntOrNull(); if (num != null) (1..30).filter { it == num } else emptyList() }
    }

    when {
        // ── Loading ──────────────────────────────────────────────────────────
        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Memuat Mushaf...", color = Color(0xFF757575))
            }
        }

        // ── Detail Surah ──────────────────────────────────────────────────────
        selectedSurah != null -> {
            val surah = selectedSurah!!
            val verses = remember(surah.id) {
                surah.content.split(Regex("\\[\\d+\\]")).filter { it.isNotBlank() }.map { it.trim() }
            }
            val juzSurah = remember(surah.id) { getJuzOfSurah(surah.id) }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedSurah = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(surah.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (surah.nameLatin.isNotBlank())
                            Text(surah.nameLatin, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                        Text("${verses.size} ayat  •  Juz $juzSurah", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                    }
                }
                HorizontalDivider(color = Color(0xFFEEEEEE))
                if (verses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Konten surah tidak tersedia.", color = Color(0xFF757575))
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(verses.size, key = { it }) { index ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = verses[index],
                                    style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 48.sp),
                                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(color = Color(0xFFE8F5E9), shape = CircleShape) {
                                    Text(text = "Ayat ${index + 1}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }
        }

        // ── Detail Halaman ────────────────────────────────────────────────────
        selectedPage != -1 -> {
            val page = selectedPage
            var pageVerses    by remember { mutableStateOf<List<Verse>>(emptyList()) }
            var isPageLoading by remember { mutableStateOf(true) }
            var pageError     by remember { mutableStateOf(false) }

            LaunchedEffect(page) {
                isPageLoading = true; pageError = false
                withContext(Dispatchers.IO) {
                    val list = try { dbHelper.getVersesByPage(page) }
                    catch (e: Exception) { android.util.Log.e("QuranScreen", "error: ${e.message}", e); null }
                    withContext(Dispatchers.Main) {
                        if (list == null) pageError = true else pageVerses = list
                        isPageLoading = false
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedPage = -1 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Halaman $page", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Juz ${getJuzOfPage(page)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { if (page > 1) selectedPage = page - 1 }, enabled = page > 1) {
                        Text("◀", fontSize = 18.sp, color = if (page > 1) Color(0xFF2E7D32) else Color.LightGray)
                    }
                    IconButton(onClick = { if (page < 604) selectedPage = page + 1 }, enabled = page < 604) {
                        Text("▶", fontSize = 18.sp, color = if (page < 604) Color(0xFF2E7D32) else Color.LightGray)
                    }
                }
                HorizontalDivider(color = Color(0xFFEEEEEE))
                when {
                    isPageLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Memuat halaman $page...", color = Color(0xFF757575), fontSize = 13.sp)
                        }
                    }
                    pageError -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Gagal memuat halaman $page.", color = Color(0xFF757575), textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp))
                    }
                    pageVerses.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada ayat ditemukan untuk halaman $page.", color = Color(0xFF757575))
                    }
                    else -> LazyColumn(modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(pageVerses, key = { "${it.surahId}-${it.ayahId}" }) { verse ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = verse.text,
                                    style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 50.sp),
                                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(color = Color(0xFFE8F5E9), shape = CircleShape) {
                                    Text(text = "${verse.surahName} : ${verse.ayahId}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }
        }

        // ── Layar Utama (Surat / Halaman / Juz) ──────────────────────────────
        else -> Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Al-Qur'anul Karim", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))

            // 3 tab: Surat | Halaman | Juz
            ScrollableTabRow(
                selectedTabIndex = quranTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF2E7D32),
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.then(TabRowDefaults.tabIndicatorOffset(tabPositions[quranTab])),
                        color = Color(0xFF2E7D32)
                    )
                }
            ) {
                Tab(selected = quranTab == 0, onClick = { quranTab = 0 }) { Text("Surat", modifier = Modifier.padding(16.dp)) }
                Tab(selected = quranTab == 1, onClick = { quranTab = 1 }) { Text("Halaman", modifier = Modifier.padding(16.dp)) }
                Tab(selected = quranTab == 2, onClick = { quranTab = 2 }) { Text("Juz", modifier = Modifier.padding(16.dp)) }
            }

            when (quranTab) {

                // ── Tab Surat ────────────────────────────────────────────────
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = surahSearchQuery, onValueChange = { surahSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("Cari surat, nomor, atau 'Juz 5'", color = textMutC()) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSecC()) },
                        trailingIcon = { if (surahSearchQuery.isNotEmpty()) IconButton(onClick = { surahSearchQuery = "" }) { Text("✕", color = textSecC(), fontSize = 14.sp) } },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2E7D32),
                            unfocusedBorderColor = borderC(),
                            focusedContainerColor = cardBg(),
                            unfocusedContainerColor = cardBg(),
                            focusedTextColor = textPrimC(),
                            unfocusedTextColor = textPrimC()
                        )
                    )
                    when {
                        surahList.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Gagal memuat daftar surat.", color = Color(0xFF757575)) }
                        filteredSurahList.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 36.sp); Spacer(modifier = Modifier.height(12.dp))
                                Text("\"$surahSearchQuery\" tidak ditemukan", color = Color(0xFF9E9E9E), fontSize = 14.sp, textAlign = TextAlign.Center)
                            }
                        }
                        else -> {
                            if (surahSearchQuery.isNotBlank())
                                Text("${filteredSurahList.size} surat ditemukan", fontSize = 12.sp, color = Color(0xFF9E9E9E),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            val showSeparator = surahSearchQuery.isBlank()
                            LazyColumn(modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
                            ) {
                                filteredSurahList.forEach { surah ->
                                    // Tampilkan pembatas juz sebelum surah yang menjadi awal juz baru
                                    if (showSeparator && SURAH_JUZ_START.containsKey(surah.id)) {
                                        val juzNum = SURAH_JUZ_START[surah.id]!!
                                        item(key = "juz_sep_$juzNum") { JuzSeparator(juzNum) }
                                    }
                                    item(key = "surah_${surah.id}") {
                                        ChapterItem(surah) { selectedSurah = surah }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Tab Halaman ──────────────────────────────────────────────
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = pageSearchQuery, onValueChange = { pageSearchQuery = it.take(10) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("Cari halaman (1–604) atau 'Juz 5'", color = textMutC()) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSecC()) },
                        trailingIcon = { if (pageSearchQuery.isNotEmpty()) IconButton(onClick = { pageSearchQuery = "" }) { Text("✕", color = textSecC(), fontSize = 14.sp) } },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2E7D32),
                            unfocusedBorderColor = borderC(),
                            focusedContainerColor = cardBg(),
                            unfocusedContainerColor = cardBg(),
                            focusedTextColor = textPrimC(),
                            unfocusedTextColor = textPrimC()
                        )
                    )
                    when {
                        pageSearchQuery.isNotBlank() && filteredPages.size == 1 -> {
                            val exactPage = filteredPages.first()
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📖", fontSize = 40.sp); Spacer(modifier = Modifier.height(12.dp))
                                    Text("Halaman $exactPage", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Juz ${getJuzOfPage(exactPage)}", fontSize = 13.sp, color = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { selectedPage = exactPage },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(12.dp)) { Text("Buka Halaman $exactPage", color = Color.White) }
                                }
                            }
                        }
                        filteredPages.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 36.sp); Spacer(modifier = Modifier.height(12.dp))
                                Text("\"$pageSearchQuery\" tidak ditemukan\n(halaman 1–604 atau 'Juz 1–30')",
                                    color = Color(0xFF9E9E9E), fontSize = 14.sp, textAlign = TextAlign.Center)
                            }
                        }
                        else -> {
                            if (pageSearchQuery.isNotBlank())
                                Text("${filteredPages.size} halaman ditemukan", fontSize = 12.sp, color = Color(0xFF9E9E9E),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            val showSeparator = pageSearchQuery.isBlank()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                var lastJuzInGrid = -1
                                filteredPages.forEach { pageNum ->
                                    val pageJuz = getJuzOfPage(pageNum)
                                    if (showSeparator && pageJuz != lastJuzInGrid && JUZ_PAGE_START.contains(pageNum)) {
                                        lastJuzInGrid = pageJuz
                                        item(key = "juz_page_sep_$pageJuz", span = { GridItemSpan(4) }) {
                                            JuzSeparator(pageJuz)
                                        }
                                    }
                                    item(key = "page_$pageNum") {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { selectedPage = pageNum },
                                            colors = CardDefaults.cardColors(containerColor = cardBg()),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, borderC())
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Hal", fontSize = 10.sp, color = Color.Gray)
                                                    Text(pageNum.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Tab Juz ──────────────────────────────────────────────────
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = juzSearchQuery,
                        onValueChange = { juzSearchQuery = it.filter { c -> c.isDigit() }.take(2) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("Cari nomor juz (1–30)", color = textMutC()) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSecC()) },
                        trailingIcon = { if (juzSearchQuery.isNotEmpty()) IconButton(onClick = { juzSearchQuery = "" }) { Text("✕", color = textSecC(), fontSize = 14.sp) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2E7D32),
                            unfocusedBorderColor = borderC(),
                            focusedContainerColor = cardBg(),
                            unfocusedContainerColor = cardBg(),
                            focusedTextColor = textPrimC(),
                            unfocusedTextColor = textPrimC()
                        )
                    )
                    when {
                        filteredJuzList.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 36.sp); Spacer(modifier = Modifier.height(12.dp))
                                Text("Juz \"$juzSearchQuery\" tidak ditemukan (1–30)", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                            }
                        }
                        filteredJuzList.size == 1 -> {
                            val juzNum = filteredJuzList.first()
                            val startPage = JUZ_PAGE_START[juzNum - 1]
                            val endPage = if (juzNum < 30) JUZ_PAGE_START[juzNum] - 1 else 604
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📖", fontSize = 48.sp); Spacer(modifier = Modifier.height(8.dp))
                                    Text("Juz $juzNum", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    Text("Halaman $startPage – $endPage", color = Color(0xFF757575), fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(onClick = { selectedPage = startPage },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)) {
                                        Text("Buka Juz $juzNum", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        else -> LazyColumn(modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredJuzList, key = { it }) { juzNum ->
                                JuzCard(juzNum = juzNum, onOpenPage = { selectedPage = it })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TabRowDefaults.tabIndicatorOffset(position: TabPosition): Modifier {return Modifier.tabIndicatorOffset(position)}

// ── Composable helpers ─────────────────────────────────────────────────────────

/** Pembatas visual juz — muncul di antara item surat/halaman */
@Composable
fun JuzSeparator(juzNum: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDDDDD))
        Surface(
            color = Color(0xFF2E7D32),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = "  Juz $juzNum  ",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDDDDD))
    }
}

/** Card satu juz di tab Juz */
@Composable
fun JuzCard(juzNum: Int, onOpenPage: (Int) -> Unit) {
    val startPage = JUZ_PAGE_START[juzNum - 1]
    val endPage   = if (juzNum < 30) JUZ_PAGE_START[juzNum] - 1 else 604
    val pageCount = endPage - startPage + 1

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenPage(startPage) },
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(juzNum.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Juz $juzNum", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimC())
                Text("Hal. $startPage – $endPage  •  $pageCount halaman",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
            }
            Text("▶", fontSize = 16.sp, color = Color(0xFF2E7D32))
        }
    }
}


@Composable
fun ChapterItem(chapter: Chapter, onClick: () -> Unit) {
    val verseCount = remember(chapter.id) {
        chapter.content.split(Regex("\\[\\d+\\]")).count { it.isNotBlank() }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(greenBgC(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(chapter.id.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(chapter.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = textPrimC())
                Text("$verseCount ayat", style = MaterialTheme.typography.bodySmall,
                    color = textSecC())
            }
            Text("📖", fontSize = 18.sp)
        }
    }
}

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

    var totalVerses by remember { mutableIntStateOf(sharedPref.getInt("total_verses", 0)) }
    var dailyVerses by remember {
        // Cek apakah data daily masih dari hari ini
        val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
        mutableIntStateOf(if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0)
    }
    var distractionTime by remember { mutableStateOf("...") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val result = getDistractionTimeToday(context)
            val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
            val tv = sharedPref.getInt("total_verses", 0)
            val dv = if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0
            withContext(Dispatchers.Main) {
                distractionTime = result
                totalVerses     = tv
                dailyVerses     = dv
            }
        }
    }
    // Poll ulang setiap 10 detik agar langsung update setelah sesi ngaji selesai
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            withContext(Dispatchers.IO) {
                val lastDate = sharedPref.getString("last_verse_date", "") ?: ""
                val tv = sharedPref.getInt("total_verses", 0)
                val dv = if (lastDate == todayStr) sharedPref.getInt("daily_verses", 0) else 0
                withContext(Dispatchers.Main) {
                    totalVerses = tv
                    dailyVerses = dv
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            HeaderDashboard(isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode)
            Spacer(modifier = Modifier.height(24.dp))
            TimerCard(remainingTimeMillis, progress, onTambahWaktu)
            Spacer(modifier = Modifier.height(24.dp))
            MotivasiCard()
            Spacer(modifier = Modifier.height(24.dp))
            // ── Widget Jadwal Sholat ──────────────────────────────────────
            JadwalSholatCard()
            Spacer(modifier = Modifier.height(24.dp))
            // ─────────────────────────────────────────────────────────────
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
                    icon = "📱",
                    value = distractionTime,
                    label = "Akses Medsos & Game",
                    iconBg = if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🚫",
                    value = blockedCount.toString(),
                    label = "Aplikasi Dikunci",
                    iconBg = if (isDarkMode) Color(0xFF3B1F1F) else Color(0xFFFDE7E7)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AyatReadCard(totalVerses, dailyVerses)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WIDGET JADWAL SHOLAT
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalSholatCard() {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

    // ── State lokasi ─────────────────────────────────────────────────────────
    var locationLabel by remember { mutableStateOf(sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta") }
    var prayerTimes   by remember { mutableStateOf<PrayerTimes?>(null) }
    var isLoadingGps  by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var cityExpanded  by remember { mutableStateOf(false) }
    var selectedCity  by remember {
        val savedName = sharedPref.getString("sholat_city_name", "Jakarta") ?: "Jakarta"
        mutableStateOf(INDONESIA_CITIES.find { it.name == savedName } ?: INDONESIA_CITIES[10])
    }
    // Latitude/longitude yang dipakai (bisa dari GPS atau pilihan kota)
    var usedLat by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lat", selectedCity.lat.toFloat()).toDouble()) }
    var usedLng by remember { mutableDoubleStateOf(sharedPref.getFloat("sholat_lng", selectedCity.lng.toFloat()).toDouble()) }
    var usedTz  by remember { mutableIntStateOf(sharedPref.getInt("sholat_tz", selectedCity.tzOffset)) }
    var isGpsMode by remember { mutableStateOf(sharedPref.getBoolean("sholat_gps_mode", false)) }

    // ── Launcher izin lokasi ─────────────────────────────────────────────────
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isLoadingGps = true
            getGpsLocation(context,
                onSuccess = { lat, lng, tz, label ->
                    usedLat = lat; usedLng = lng; usedTz = tz; locationLabel = label
                    isGpsMode = true; isLoadingGps = false
                    sharedPref.edit()
                        .putFloat("sholat_lat", lat.toFloat()).putFloat("sholat_lng", lng.toFloat())
                        .putInt("sholat_tz", tz).putString("sholat_city_name", label)
                        .putBoolean("sholat_gps_mode", true).apply()
                },
                onFail = {
                    isLoadingGps = false
                    Toast.makeText(context, "GPS tidak tersedia, pilih kota manual", Toast.LENGTH_SHORT).show()
                    showCityPicker = true
                }
            )
        } else {
            Toast.makeText(context, "Izin lokasi ditolak, pilih kota manual", Toast.LENGTH_SHORT).show()
            showCityPicker = true
        }
    }

    // ── Hitung waktu sholat ──────────────────────────────────────────────────
    LaunchedEffect(usedLat, usedLng, usedTz) {
        withContext(Dispatchers.Default) {
            val cal = Calendar.getInstance()
            val pt = hitungWaktuSholat(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                usedLat, usedLng, usedTz
            )
            withContext(Dispatchers.Main) { prayerTimes = pt }
        }
    }

    // ── Dialog pilih kota ────────────────────────────────────────────────────
    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("Pilih Kota", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih kota terdekat dengan lokasimu:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF757575))
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = cityExpanded,
                        onExpandedChange = { cityExpanded = !cityExpanded }
                    ) {
                        OutlinedTextField(
                            value = "${selectedCity.name} (${selectedCity.province})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2E7D32),
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = cityExpanded,
                            onDismissRequest = { cityExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            INDONESIA_CITIES.forEach { city ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(city.name, fontWeight = FontWeight.Medium)
                                            Text(city.province, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                        }
                                    },
                                    onClick = {
                                        selectedCity = city; cityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        usedLat = selectedCity.lat; usedLng = selectedCity.lng
                        usedTz = selectedCity.tzOffset; locationLabel = selectedCity.name
                        isGpsMode = false; showCityPicker = false
                        sharedPref.edit()
                            .putFloat("sholat_lat", selectedCity.lat.toFloat())
                            .putFloat("sholat_lng", selectedCity.lng.toFloat())
                            .putInt("sholat_tz", selectedCity.tzOffset)
                            .putString("sholat_city_name", selectedCity.name)
                            .putBoolean("sholat_gps_mode", false).apply()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Gunakan Kota Ini") }
            },
            dismissButton = {
                TextButton(onClick = { showCityPicker = false }) { Text("Batal") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Tanya izin GPS saat pertama kali ─────────────────────────────────────
    LaunchedEffect(Unit) {
        val hasAsked = sharedPref.getBoolean("sholat_asked_gps", false)
        if (!hasAsked) {
            sharedPref.edit().putBoolean("sholat_asked_gps", true).apply()
            // Tunda sedikit agar UI sudah tampil
            delay(600)
            val hasFinePermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasFinePermission) {
                isLoadingGps = true
                getGpsLocation(context,
                    onSuccess = { lat, lng, tz, label ->
                        usedLat = lat; usedLng = lng; usedTz = tz; locationLabel = label
                        isGpsMode = true; isLoadingGps = false
                        sharedPref.edit()
                            .putFloat("sholat_lat", lat.toFloat()).putFloat("sholat_lng", lng.toFloat())
                            .putInt("sholat_tz", tz).putString("sholat_city_name", label)
                            .putBoolean("sholat_gps_mode", true).apply()
                    },
                    onFail = { isLoadingGps = false }
                )
            } else {
                locationPermLauncher.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    // ── UI Card ──────────────────────────────────────────────────────────────
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header: judul + badge sholat berikutnya + lokasi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Jadwal Sholat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showCityPicker = true }
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            if (isGpsMode) "$locationLabel (GPS)" else locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecC()
                        )
                    }
                }

                val pt = prayerTimes
                if (pt != null) {
                    val (_, nextLabel) = getNextPrayer(pt)
                    Surface(
                        color = greenBgC(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🕌", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                nextLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (dark) DarkTheme.green else LightTheme.greenDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isLoadingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF2E7D32),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Arc Matahari
            if (prayerTimes != null) {
                SunArcWidget(pt = prayerTimes!!)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Baris waktu sholat
            if (prayerTimes != null) {
                PrayerTimesRow(pt = prayerTimes!!)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: tombol GPS & ganti kota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isGpsMode) {
                    TextButton(
                        onClick = {
                            locationPermLauncher.launch(arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gunakan GPS", color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = { showCityPicker = true }) {
                    Text("Ganti Kota", color = textSecC(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Arc perjalanan matahari dari syuruq ke maghrib */
@Composable
fun SunArcWidget(pt: PrayerTimes) {
    val dark = LocalIsDarkMode.current
    val syuruq   = pt.syuruqDecimal
    val maghrib  = pt.maghribDecimal
    val now      = pt.nowDecimal
    val progress = ((now - syuruq) / (maghrib - syuruq)).coerceIn(0.0, 1.0).toFloat()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        val canvasW    = maxWidth
        val arcPad     = 20.dp
        val arcDiam    = canvasW - arcPad * 2f
        val arcRadius  = arcDiam / 2f
        val centerX    = canvasW / 2f
        val centerY    = arcRadius
        val labelH     = 44.dp
        val canvasH    = arcRadius + labelH

        val sunAngleDeg   = 180.0 + 180.0 * progress
        val sunAngleRad   = Math.toRadians(sunAngleDeg)
        val sunX: Dp      = centerX + arcRadius * cos(sunAngleRad).toFloat()
        val sunY: Dp      = centerY + arcRadius * sin(sunAngleRad).toFloat()

        val circleRadius = 16.dp
        val emojiSize    = 20.sp
        val emojiHalfW   = 12.dp
        val emojiHalfH   = 11.dp

        val arcTrackColor  = if (dark) DarkTheme.arcTrack  else LightTheme.arcTrack
        val horizonColor   = if (dark) DarkTheme.horizon   else LightTheme.horizon
        val sunCircleColor = if (dark) DarkTheme.sunCircle else LightTheme.sunCircle
        val labelColor     = if (dark) DarkTheme.textSecond else Color(0xFF555555)

        Box(modifier = Modifier.fillMaxWidth().height(canvasH)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w   = size.width
                val padPx      = arcPad.toPx()
                val diamPx     = w - padPx * 2f
                val radiusPx   = diamPx / 2f
                val cxPx       = w / 2f
                val cyPx       = radiusPx
                val arcTopLeft = Offset(cxPx - radiusPx, cyPx - radiusPx)
                val arcSizePx  = Size(diamPx, diamPx)

                drawLine(
                    color = horizonColor,
                    start = Offset(0f, cyPx), end = Offset(w, cyPx),
                    strokeWidth = 1.dp.toPx()
                )
                drawArc(
                    color = arcTrackColor, startAngle = 180f, sweepAngle = 180f,
                    useCenter = false, topLeft = arcTopLeft, size = arcSizePx,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                if (progress > 0.01f) {
                    drawArc(
                        color = Color(0xFFFFA726), startAngle = 180f,
                        sweepAngle = 180f * progress, useCenter = false,
                        topLeft = arcTopLeft, size = arcSizePx,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                val sxPx = cxPx + radiusPx * cos(sunAngleRad).toFloat()
                val syPx = cyPx + radiusPx * sin(sunAngleRad).toFloat()
                drawCircle(color = sunCircleColor, radius = circleRadius.toPx(), center = Offset(sxPx, syPx))
                drawCircle(
                    color = Color(0xFFFFA726), radius = circleRadius.toPx(),
                    center = Offset(sxPx, syPx), style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Text(text = "☀️", fontSize = emojiSize,
                modifier = Modifier.absoluteOffset(x = sunX - emojiHalfW, y = sunY - emojiHalfH))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .absoluteOffset(y = arcRadius + 16.dp)
                    .padding(horizontal = arcPad - 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌅", fontSize = 18.sp)
                    Text(pt.syuruq, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = labelColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌇", fontSize = 18.sp)
                    Text(pt.maghrib, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = labelColor)
                }
            }
        }
    }
}

/** Baris 5 waktu sholat utama dengan highlight sholat aktif */
@Composable
fun PrayerTimesRow(pt: PrayerTimes) {
    val dark = LocalIsDarkMode.current
    fun toDouble(hhmm: String): Double {
        val p = hhmm.split(":"); return p[0].toDouble() + p[1].toDouble() / 60.0
    }
    val now = pt.nowDecimal
    val prayers = listOf(
        "Subuh" to pt.subuh, "Dzuhur" to pt.dzuhur,
        "Ashar" to pt.ashar, "Maghrib" to pt.maghrib, "Isya" to pt.isya
    )
    val nextIdx = prayers.indexOfFirst { toDouble(it.second) > now }.let { if (it < 0) 0 else it }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        prayers.forEachIndexed { idx, (name, time) ->
            val isNext = idx == nextIdx
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNext) Color(0xFF2E7D32) else textMutC(),
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isNext) {
                    Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(20.dp)) {
                        Text(time,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.size(5.dp).background(Color(0xFF2E7D32), CircleShape))
                } else {
                    Text(time,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimC())
                }
            }
        }
    }
}

/** Ambil lokasi GPS dari LocationManager */
fun getGpsLocation(
    context: Context,
    onSuccess: (lat: Double, lng: Double, tz: Int, label: String) -> Unit,
    onFail: () -> Unit
) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) { onFail(); return }

        // Tentukan timezone dari longitude secara otomatis (untuk Indonesia)
        fun tzFromLng(lng: Double): Int = when {
            lng < 115.0 -> 7   // WIB
            lng < 135.0 -> 8   // WITA
            else        -> 9   // WIT
        }

        // Coba last known location dulu (sangat cepat, tidak perlu tunggu)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var bestLocation: Location? = null
        for (p in providers) {
            if (lm.isProviderEnabled(p)) {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }
        }
        if (bestLocation != null) {
            val tz = tzFromLng(bestLocation.longitude)
            val label = findNearestCity(bestLocation.latitude, bestLocation.longitude)
            onSuccess(bestLocation.latitude, bestLocation.longitude, tz, label)
            return
        }

        // Fallback: minta update aktif
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { onFail(); return }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                lm.removeUpdates(this)   // hapus langsung setelah dapat 1 lokasi
                val tz    = tzFromLng(loc.longitude)
                val label = findNearestCity(loc.latitude, loc.longitude)
                onSuccess(loc.latitude, loc.longitude, tz, label)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) { onFail() }
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener)
    } catch (e: SecurityException) { onFail() }
    catch (e: Exception)           { onFail() }
}

/** Cari nama kota terdekat dari koordinat GPS */
fun findNearestCity(lat: Double, lng: Double): String {
    var nearest = INDONESIA_CITIES[10]  // default Jakarta
    var minDist = Double.MAX_VALUE
    for (city in INDONESIA_CITIES) {
        val d = sqrt((lat - city.lat).pow(2) + (lng - city.lng).pow(2))
        if (d < minDist) { minDist = d; nearest = city }
    }
    return nearest.name
}

// ══════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════════
// HELPER: DATA STATISTIK
// ══════════════════════════════════════════════════════════════════════════════

data class AppUsageStat(val name: String, val packageName: String, val usageMs: Long)

/** Ambil total distraction time hari ini dalam ms */
fun getDistractionTodayMs(context: Context): Long {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val cal = Calendar.getInstance()
        .apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
    var total = 0L
    stats?.forEach { s ->
        try {
            val info = pm.getApplicationInfo(s.packageName, 0)
            val pkg  = s.packageName.lowercase()
            val isDistraction = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                            info.category == ApplicationInfo.CATEGORY_GAME  ||
                            info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                    listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp","x.com","twitter")
                        .any { pkg.contains(it) }
            if (isDistraction) total += s.totalTimeInForeground
        } catch (e: Exception) { }
    }
    return total
}

/** Ambil distraction time per hari selama 7 hari terakhir (dalam ms), index 0=hari ini */
fun getWeeklyDistractionMs(context: Context): LongArray {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val result = LongArray(7)
    val now = System.currentTimeMillis()
    val DAY = 86_400_000L
    for (dayOffset in 0..6) {
        val end   = now - dayOffset * DAY
        val start = end - DAY
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        var total = 0L
        stats?.forEach { s ->
            try {
                val info = pm.getApplicationInfo(s.packageName, 0)
                val pkg  = s.packageName.lowercase()
                val isD  = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                                info.category == ApplicationInfo.CATEGORY_GAME  ||
                                info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                        listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp")
                            .any { pkg.contains(it) }
                if (isD) total += s.totalTimeInForeground
            } catch (e: Exception) { }
        }
        result[dayOffset] = total
    }
    return result
}

/** Top 3 app distraction hari ini */
fun getTopDistractingApps(context: Context): List<AppUsageStat> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val cal = Calendar.getInstance()
        .apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
    val list  = mutableListOf<AppUsageStat>()
    stats?.forEach { s ->
        try {
            val info = pm.getApplicationInfo(s.packageName, 0)
            val pkg  = s.packageName.lowercase()
            val isD  = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                            info.category == ApplicationInfo.CATEGORY_GAME  ||
                            info.category == ApplicationInfo.CATEGORY_VIDEO)) ||
                    listOf("instagram","tiktok","youtube","facebook","twitter","game","mobile.legends","whatsapp")
                        .any { pkg.contains(it) }
            if (isD && s.totalTimeInForeground > 0) {
                val name = pm.getApplicationLabel(info).toString()
                list.add(AppUsageStat(name, s.packageName, s.totalTimeInForeground))
            }
        } catch (e: Exception) { }
    }
    return list.sortedByDescending { it.usageMs }.take(3)
}

fun Long.toHhMm(): String {
    val h = TimeUnit.MILLISECONDS.toHours(this)
    val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYAR STATISTIK
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StatistikScreen(isDarkMode: Boolean = false) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }

    // ── State ──────────────────────────────────────────────────────────────
    var todayMs    by remember { mutableLongStateOf(0L) }
    var weeklyMs   by remember { mutableStateOf(LongArray(7)) }
    var topApps    by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var totalVerse by remember { mutableIntStateOf(0) }
    var focusMs    by remember { mutableLongStateOf(0L) }
    var isLoading  by remember { mutableStateOf(true) }

    // Baca SharedPrefs dengan reset harian
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tMs  = getDistractionTodayMs(context)
            val wMs  = getWeeklyDistractionMs(context)
            val apps = getTopDistractingApps(context)
            val lastFocusDate = sp.getString("last_focus_date", "") ?: ""
            val fMs  = if (lastFocusDate == todayStr) sp.getLong("focus_time_today_ms", 0L) else 0L
            val tv   = sp.getInt("total_verses", 0)
            withContext(Dispatchers.Main) {
                todayMs   = tMs;  weeklyMs = wMs;  topApps = apps
                focusMs   = fMs;  totalVerse = tv;  isLoading = false
            }
        }
    }

    // ── BATAS HARIAN: 3 jam = normal, 3-5 jam = cukup, >5 jam = kelewat batas ─
    val normalLimitMs  = 3L * 3_600_000L   // 3 jam
    val warnLimitMs    = 5L * 3_600_000L   // 5 jam (batas kelewat)
    val progressNormal = (todayMs.toFloat() / normalLimitMs).coerceIn(0f, 1f)
    val progressWarn   = ((todayMs - normalLimitMs).toFloat() / (warnLimitMs - normalLimitMs)).coerceIn(0f, 1f)
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
            c.timeInMillis = cal.timeInMillis - i * 86_400_000L
            dayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
        }.reversedArray()
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
                        ) { Text("⏱", fontSize = 18.sp) }
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
                        ) { Text("📖", fontSize = 20.sp) }
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
                        ) { Text("⏱", fontSize = 20.sp) }
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
                        Text("📱", fontSize = 18.sp)
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

// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun HeaderDashboard(isDarkMode: Boolean = false, onToggleDarkMode: () -> Unit = {}) {
    val dark     = isDarkMode
    val textPrim = if (dark) Color(0xFFF1F5F9) else Color(0xFF1A1A1A)
    val textSub  = if (dark) Color(0xFF94A3B8) else Color(0xFF757575)
    val iconBg   = if (dark) Color(0xFF1E293B) else Color.White
    val iconTint = if (dark) Color(0xFFCBD5E1) else Color(0xFF333333)

    var showSettingsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("UNGKER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textPrim)
            Text("Penjaga anak muda untuk mengaji.",
                style = MaterialTheme.typography.bodyMedium,
                color = textSub)
        }
        Row {
            IconButton(onClick = { },
                modifier = Modifier.background(iconBg, CircleShape)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = iconTint)
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Settings dengan dropdown
            Box {
                IconButton(onClick = { showSettingsMenu = !showSettingsMenu },
                    modifier = Modifier.background(iconBg, CircleShape)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = iconTint)
                }
                DropdownMenu(
                    expanded = showSettingsMenu,
                    onDismissRequest = { showSettingsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Gunakan Text emoji — tidak bergantung pada material-icons-extended
                                Text(
                                    if (isDarkMode) "☀️" else "🌙",
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (isDarkMode) "Mode Terang" else "Mode Gelap",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onToggleDarkMode()
                            showSettingsMenu = false
                        }
                    )
                }
            }
        }
    }
}

// Thread-safe icon cache menggunakan ConcurrentHashMap
private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun DaftarAplikasiScreen(isDarkMode: Boolean = false) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    val blockedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPref.getStringSet("blocked_apps", emptySet()) ?: emptySet())
        }
    }

    var selectedDuration by remember {
        mutableIntStateOf(sharedPref.getInt("lock_duration_minutes", 5))
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val app = resolveInfo.activityInfo.applicationInfo
                val name = resolveInfo.loadLabel(pm).toString()
                val packageName = app.packageName

                var categoryStr = "Lainnya"
                var isTarget = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (app.category) {
                        ApplicationInfo.CATEGORY_SOCIAL -> { categoryStr = "Sosial Media"; isTarget = true }
                        ApplicationInfo.CATEGORY_GAME -> { categoryStr = "Game"; isTarget = true }
                        ApplicationInfo.CATEGORY_VIDEO -> { categoryStr = "Hiburan"; isTarget = true }
                    }
                }

                // Extra check for common distractors
                val lowerPackage = packageName.lowercase()
                if (lowerPackage.contains("instagram") || lowerPackage.contains("tiktok") ||
                    lowerPackage.contains("youtube") || lowerPackage.contains("facebook") ||
                    lowerPackage.contains("twitter") || lowerPackage.contains("game") ||
                    lowerPackage.contains("mobile.legends")) {
                    isTarget = true
                    if (categoryStr == "Lainnya") {
                        categoryStr = if (lowerPackage.contains("game")) "Game" else "Hiburan"
                    }
                }

                if (isTarget) {
                    AppInfo(name, packageName, categoryStr)
                } else null
            }.distinctBy { it.packageName }.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                apps = appList
                isLoading = false
            }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val isAllSelected = filteredApps.isNotEmpty() && filteredApps.all { blockedApps.contains(it.packageName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Daftar Aplikasi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimC()
        )
        Text(
            "Pilih aplikasi dan durasi sesi kunci",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecC()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF00C853)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛡️", fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${blockedApps.size} Aplikasi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Masuk daftar blokir",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }

                TextButton(
                    onClick = {
                        if (isAllSelected) {
                            filteredApps.forEach { blockedApps.remove(it.packageName) }
                        } else {
                            filteredApps.forEach { if (!blockedApps.contains(it.packageName)) blockedApps.add(it.packageName) }
                        }
                        sharedPref.edit().putStringSet("blocked_apps", blockedApps.toSet()).apply()
                    }
                ) {
                    Text(
                        if (isAllSelected) "Batal Semua" else "Pilih Semua",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Durasi Kunci Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🕒", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Durasi Kunci", fontWeight = FontWeight.Bold, color = textPrimC())
        }
        Spacer(modifier = Modifier.height(12.dp))
        val durations = listOf(5, 10, 15, 20)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(durations) { duration ->
                val isSelected = selectedDuration == duration
                Surface(
                    onClick = {
                        selectedDuration = duration
                        sharedPref.edit().putInt("lock_duration_minutes", duration).apply()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF00C853) else cardBg(),
                    border = if (isSelected) null else BorderStroke(1.dp, borderC()),
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$duration Mnt",
                            color = if (isSelected) Color.White else textSecC(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari aplikasi...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00C853),
                unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00C853))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tunggu sebentar, ya...", color = textSecC())
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        isBlocked = blockedApps.contains(app.packageName),
                        onToggle = { isChecked ->
                            if (isChecked) {
                                blockedApps.add(app.packageName)
                            } else {
                                blockedApps.remove(app.packageName)
                            }
                            sharedPref.edit().putStringSet("blocked_apps", blockedApps.toSet()).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, isBlocked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var iconBitmap by remember(app.packageName) {
        mutableStateOf<ImageBitmap?>(iconCache[app.packageName])
    }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = pm.getApplicationIcon(app.packageName)
                    // 72px cukup untuk tampilan 40dp @ 2x density, hemat memory & decode lebih cepat
                    val bitmap = drawable.toBitmap(72, 72).asImageBitmap()
                    iconCache[app.packageName] = bitmap
                    withContext(Dispatchers.Main) { iconBitmap = bitmap }
                } catch (_: Exception) {}
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = iconBitmap,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "IconFade"
            ) { bmp ->
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(modifier = Modifier.size(40.dp)
                        .background(cardAltBg(), RoundedCornerShape(8.dp)))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Bold, color = textPrimC())
                Text(app.category, fontSize = 12.sp, color = textSecC())
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF00C853),
                    uncheckedThumbColor = Color(0xFFBDBDBD),
                    uncheckedTrackColor = if (LocalIsDarkMode.current) Color(0xFF334155) else Color(0xFFEEEEEE)
                )
            )
        }
    }
}

@Composable
fun TimerCard(remainingTimeMillis: Long, progress: Float, onTambahWaktu: () -> Unit) {
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = greenBgC(),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🕒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Sisa Waktu Penggunaan",
                        color = if (dark) DarkTheme.green else LightTheme.greenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                val trackColor = if (dark) DarkTheme.bgTrack else Color(0xFFF0F0F0)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF00C853),
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTimeMillis)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTimeMillis) % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimC()
                    )
                    Text(
                        "MENIT : DETIK",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecC(),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onTambahWaktu,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Waktu", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MotivasiCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF004D40), Color(0xFF00695C))))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "MOTIVASI HARI INI",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\"Karena sesungguhnya sesudah kesulitan itu ada kemudahan.\"",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Light
                )
                Text(
                    "— QS. Al-Insyirah: 5",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
    iconBg: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textPrimC()
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = textSecC())
        }
    }
}

@Composable
fun AyatReadCard(total: Int, daily: Int) {
    val dark = LocalIsDarkMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (dark) Color(0xFF3B2F00) else Color(0xFFFFF8E1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Jumlah Ayat Dibaca",
                        fontWeight = FontWeight.Bold,
                        color = textPrimC())
                    Text(
                        if (daily > 0) "+$daily ayat hari ini" else "Belum ada bacaan hari ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daily > 0) Color(0xFF4ADE80) else textSecC()
                    )
                }
            }
            Text(
                total.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFA000)
            )
        }
    }
}