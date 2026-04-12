package com.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Periksa dan update semua badge — dipanggil saat ProfilScreen dibuka */
fun checkAndUpdateBadges(context: Context) {
    val sp      = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    val sessions = sp.getStringSet("reading_sessions", emptySet()) ?: emptySet()
    
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val lastDate = sp.getString("no_brainwash_last_date", "") ?: ""
    val todayMs  = getDistractionTodayMs(context)
    val userSocialMediaLimitMinutes = sp.getLong("social_media_limit_minutes", 90L) // Default 90 mins (1h 30m)
    val userDetoxLimitMs = userSocialMediaLimitMinutes * 60 * 1000L

    sp.edit {
        // ── Badge 1: Rising Sun ───────────────────────────────────────────────
        if (!sp.getBoolean("badge_rising_sun", false)) {
            val hasEarlySession = sessions.any { s ->
                val parts = s.split("_")
                val hour  = parts.getOrNull(1)?.toIntOrNull() ?: -1
                hour in 4..6
            }
            if (hasEarlySession) putBoolean("badge_rising_sun", true)
        }

        // ── Badge 2: Too Late! Go To Sleep ───────────────────────────────────
        if (!sp.getBoolean("badge_too_late", false)) {
            val hasLateSession = sessions.any { s ->
                val hour = s.split("_").getOrNull(1)?.toIntOrNull() ?: -1
                hour >= 22 || (hour >= 0 && hour < 4)
            }
            if (hasLateSession) putBoolean("badge_too_late", true)
        }

        // ── DETOX STREAK & Badge 3 ───────────────────────────────────────────
        var currentStreak = sp.getInt("no_brainwash_streak", 0)

        // 1. Reset SEGERA jika hari ini sudah tembus limit
        if (todayMs >= userDetoxLimitMs) {
            currentStreak = 0
            putInt("no_brainwash_streak", 0)
            putString("no_brainwash_last_date", todayStr)
        } else {
            // 2. Jika hari berganti, cek apakah kemarin lulus
            if (lastDate.isNotEmpty() && lastDate != todayStr) {
                // Cek apakah ada bolong hari (gap > 1 hari)
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                try {
                    val last = fmt.parse(lastDate)
                    val now  = fmt.parse(todayStr)
                    if (last != null && now != null) {
                        val diffDays = (now.time - last.time) / (24 * 3600 * 1000)
                        
                        if (diffDays == 1L) {
                            // Berhasil melewati kemarin tanpa tembus limit (karena streak tidak nol hari ini)
                            currentStreak += 1
                        } else if (diffDays > 1L) {
                            // Ada hari yang terlewat tidak buka aplikasi / tidak lulus
                            currentStreak = 1 // Mulai lagi dari 1 untuk hari ini yang masih di bawah limit
                        }
                    }
                } catch (_: Exception) {
                    currentStreak = 1
                }
                putInt("no_brainwash_streak", currentStreak)
                putString("no_brainwash_last_date", todayStr)
            } else if (lastDate.isEmpty()) {
                // Inisialisasi pertama kali
                currentStreak = 1
                putInt("no_brainwash_streak", 1)
                putString("no_brainwash_last_date", todayStr)
            }
        }

        // 3. Unlock badge jika syarat terpenuhi
        if (currentStreak >= 3 && !sp.getBoolean("badge_no_brainwash", false)) {
            putBoolean("badge_no_brainwash", true)
        }
    }
}

// ─── Data class untuk badge ──────────────────────────────────────────────────

data class BadgeInfo(
    val key       : String,
    val emoji     : String,
    val name      : String,
    val desc      : String,
    val color     : Color
)

val ALL_BADGES = listOf(
    BadgeInfo("badge_rising_sun",    "🌅", "Rising Sun",
        "Membaca Al-Qur'an di mushaf UNGKER antara jam 04.00–06.59",
        Color(0xFFFF8F00)),
    BadgeInfo("badge_too_late",      "🌙", "Too Late! Go To Sleep.",
        "Membaca Al-Qur'an di mushaf UNGKER setelah jam 22.00",
        Color(0xFF5C6BC0)),
    BadgeInfo("badge_no_brainwash",  "🛡️", "I'm Not Getting Brainwashed!",
        "Penggunaan medsos & game di bawah 3 jam per hari selama 3 hari berturut-turut",
        Color(0xFF2E7D32)),
)

// ─── ProfileScreen ───────────────────────────────────────────────────────────

@Composable
fun ProfilScreen() {
    val context = LocalContext.current
    val sp      = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    val dark    = LocalIsDarkMode.current

    // ── UID + nama ───────────────────────────────────────────────────────
    val uid = remember {
        sp.getString("user_uid", null) ?: run {
            val g = (10000..99999).random().toString()
            sp.edit {putString("user_uid", g)}; g
        }
    }
    var userName    by remember { mutableStateOf(sp.getString("user_name", null) ?: "Fulan#$uid") }
    var editingName by remember { mutableStateOf(false) }
    var nameInput   by remember { mutableStateOf(userName) }

    // ── Badge states ─────────────────────────────────────────────────────
    val badgeStates = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { checkAndUpdateBadges(context) }
        delay(200)
        ALL_BADGES.forEach { b -> badgeStates[b.key] = sp.getBoolean(b.key, false) }
    }

    // ── Statistik ────────────────────────────────────────────────────────
    val totalVerses  = remember { sp.getInt("total_verses", 0) }
    val streak       = remember { sp.getInt("no_brainwash_streak", 0) }
    val unlockedCount = badgeStates.values.count { it }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Kartu Identitas ───────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(containerColor = cardBg()),
                shape     = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar lingkaran dengan inisial
                    Box(
                        modifier         = Modifier
                            .size(88.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF2E7D32), Color(0xFF43A047))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = (userName.firstOrNull() ?: 'F').uppercaseChar().toString(),
                            fontSize   = 38.sp,
                            fontWeight = FontWeight.Black,
                            color      = Color.White
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // Nama — mode tampil
                    if (!editingName) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                userName,
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color      = textPrimC()
                            )
                            Spacer(Modifier.width(6.dp))
                            // Ikon pensil — bukan gear
                            Surface(
                                onClick  = { editingName = true; nameInput = userName },
                                color    = Color.Transparent,
                                shape    = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("✏️", fontSize = 14.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        // UID — tampil profesional dengan monospace style
                        Surface(
                            color = if (dark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "# $uid",
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style      = MaterialTheme.typography.labelMedium,
                                color      = textMutC(),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Nama — mode edit
                    if (editingName) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { if (it.length <= 24) nameInput = it },
                            singleLine    = true,
                            placeholder   = { Text("Nama kamu...", color = textMutC()) },
                            textStyle     = androidx.compose.ui.text.TextStyle(
                                textAlign  = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Color(0xFF2E7D32),
                                unfocusedBorderColor    = borderC(),
                                focusedContainerColor   = cardBg(),
                                unfocusedContainerColor = cardBg(),
                                focusedTextColor        = textPrimC(),
                                unfocusedTextColor      = textPrimC()
                            ),
                            shape    = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick  = { editingName = false; nameInput = userName },
                                modifier = Modifier.weight(1f),
                                border   = BorderStroke(1.dp, borderC()),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("Batal", color = textSecC()) }
                            Button(
                                onClick = {
                                    val t = nameInput.trim()
                                    if (t.isNotEmpty()) {
                                        userName = t
                                        sp.edit {putString("user_name", t)}
                                    }
                                    editingName = false
                                },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("Simpan") }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = dividerC())
                    Spacer(Modifier.height(20.dp))

                    // Statistik 3 kolom
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfilStat("$totalVerses", "Ayat", Color(0xFF2E7D32))
                        ProfilStatDivider()
                        ProfilStat("$unlockedCount/${ALL_BADGES.size}", "Badge", Color(0xFFFF8F00))
                        ProfilStatDivider()
                        ProfilStat("$streak", "Streak", Color(0xFF1565C0))
                    }
                }
            }
        }

        // ── Section: Lemari Badge ─────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏆", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Lemari Badge",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = textPrimC()
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (dark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "$unlockedCount/${ALL_BADGES.size} terbuka",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = textSecC()
                    )
                }
            }
        }

        // ── Badge cards ───────────────────────────────────────────────────
        items(ALL_BADGES.size) { index ->
            val badge = ALL_BADGES[index]
            val unlocked = badgeStates[badge.key] == true
            BadgeCard(badge = badge, unlocked = unlocked, dark = dark, index = index)
        }
    }
}

// ── Helper composable: stat kolom ────────────────────────────────────────────
@Composable
private fun ProfilStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = textSecC())
    }
}

@Composable
private fun ProfilStatDivider() {
    Box(modifier = Modifier.height(32.dp).width(1.dp).background(dividerC()))
}

// ── Badge card ───────────────────────────────────────────────────────────────
@Composable
private fun BadgeCard(badge: BadgeInfo, unlocked: Boolean, dark: Boolean, index: Int) {
    // Animasi scale saat pertama kali unlock
    val animScale by animateFloatAsState(
        targetValue    = if (unlocked) 1f else 0.97f,
        animationSpec  = spring(Spring.DampingRatioMediumBouncy),
        label          = "badgeScale"
    )

    // Animasi masuk untuk tiap card (staggered)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInHorizontally(initialOffsetX = { it / 2 })
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth().graphicsLayer { scaleX = animScale; scaleY = animScale },
            colors    = CardDefaults.cardColors(
                containerColor = if (unlocked) badge.color.copy(alpha = 0.10f) else cardBg()
            ),
            shape     = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(if (unlocked) 2.dp else 0.dp),
            border    = if (unlocked) BorderStroke(1.dp, badge.color.copy(alpha = 0.35f))
            else BorderStroke(1.dp, borderC())
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                // Icon box
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            if (unlocked) badge.color.copy(0.18f)
                            else if (dark) Color(0xFF1E293B) else Color(0xFFF0F0F0),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unlocked) badge.emoji else "🔒",
                        fontSize = 28.sp
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Nama badge + chip UNLOCKED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            badge.name,
                            fontWeight = FontWeight.Bold,
                            color      = if (unlocked) badge.color else textSecC(),
                            style      = MaterialTheme.typography.bodyLarge
                        )
                        if (unlocked) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = badge.color,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "✓",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        badge.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = textMutC(),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}