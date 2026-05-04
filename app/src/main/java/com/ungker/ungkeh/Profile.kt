package com.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
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
        "badge_rising_sun_desc",
        Color(0xFFFF8F00)),
    BadgeInfo("badge_too_late",      "🌙", "Too Late! Go To Sleep.",
        "badge_too_late_desc",
        Color(0xFF5C6BC0)),
    BadgeInfo("badge_no_brainwash",  "🛡️", "I'm Not Getting Brainwashed!",
        "badge_no_brainwash_desc",
        Color(0xFF2E7D32)),
)

fun getBadgeDescription(key: String): String {
    return LocaleManager.L(key)
}

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

    // ── Profile Picture ──────────────────────────────────────────────────
    val initialProfilePic = remember { sp.getString("profile_picture", null) }
    var profilePicUri by remember { mutableStateOf(initialProfilePic) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                inputStream?.let { stream ->
                    val file = File(context.filesDir, "profile_picture.jpg")
                    file.outputStream().use { out -> stream.copyTo(out) }
                    val newPath = file.absolutePath
                    profilePicUri = newPath
                    sp.edit { putString("profile_picture", newPath) }
                }
                inputStream?.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

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

    // ── Settings state ──────────────────────────────────────────────────
    var showSettings by remember { mutableStateOf(false) }
    var deepFrictionEnabled by remember { mutableStateOf(sp.getBoolean("feature_deep_friction_enabled", true)) }
    var sholatLockEnabled by remember { mutableStateOf(sp.getBoolean("feature_sholat_lock_enabled", true)) }
    var prayerNotifEnabled by remember { mutableStateOf(sp.getBoolean("feature_prayer_notification_enabled", true)) }
    var prayerNotifMinutes by remember { mutableIntStateOf(sp.getInt("prayer_notification_minutes_before", 10)) }
    
    // ── User role untuk password protection ─────────────────────────
    val userRole by remember { mutableStateOf(sp.getString("user_role", "personal")) }
    val savedPassword by remember { mutableStateOf(sp.getString("parent_password", "")) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingToggleDeepFriction by remember { mutableStateOf<Boolean?>(null) }
    var passwordError by remember { mutableStateOf(false) }
    
    // Promise dialog state
    var promiseDialogFeature by remember { mutableStateOf<String?>(null) }
    var promiseInput by remember { mutableStateOf("") }
    
    // Password dialog for parental control
    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false; pendingToggleDeepFriction = null },
            onConfirm = { inputPassword ->
                if (inputPassword == savedPassword) {
                    passwordError = false
                    pendingToggleDeepFriction?.let { newValue ->
                        deepFrictionEnabled = newValue
                        sp.edit { putBoolean("feature_deep_friction_enabled", newValue) }
                    }
                    showPasswordDialog = false
                    pendingToggleDeepFriction = null
                } else {
                    passwordError = true
                }
            },
            isError = passwordError
        )
    }
    
    // Promise dialog for disabling features
    if (promiseDialogFeature != null) {
        PromiseConfirmDialog(
            promiseInput = promiseInput,
            onValueChange = { promiseInput = it },
            correctPromise = LocaleManager.L("profile_promise_confirm"),
            onDismiss = { promiseDialogFeature = null; promiseInput = "" },
            onConfirm = {
                when (promiseDialogFeature) {
                    "deepFriction" -> {
                        deepFrictionEnabled = false
                        sp.edit { putBoolean("feature_deep_friction_enabled", false) }
                    }
                    "sholatLock" -> {
                        sholatLockEnabled = false
                        sp.edit { putBoolean("feature_sholat_lock_enabled", false) }
                    }
                    "prayerNotif" -> {
                        prayerNotifEnabled = false
                        sp.edit { putBoolean("feature_prayer_notification_enabled", false) }
                    }
                }
                promiseDialogFeature = null; promiseInput = ""
            }
        )
    }

if (showSettings) {
        val configuration = LocalConfiguration.current
        val screenHeightDp = configuration.screenHeightDp
        val isSmallScreen = screenHeightDp < 400
        
        val horizontalPadding = if (isSmallScreen) 12.dp else 16.dp
        val verticalPadding = if (isSmallScreen) 8.dp else 12.dp
        val itemSpacing = if (isSmallScreen) 8.dp else 16.dp
        val sectionSpacing = if (isSmallScreen) 16.dp else 24.dp
        val headerSpacing = if (isSmallScreen) 20.dp else 32.dp
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showSettings = false }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
                    Spacer(Modifier.width(12.dp))
                    Text(LocaleManager.L("settings_header"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textPrimC())
                }
            }
            
            item {
                Spacer(Modifier.height(headerSpacing))
                Text(LocaleManager.L("active_features"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textSecC())
            }
            
            item {
                Spacer(Modifier.height(itemSpacing))
                SettingsToggleItem(
                    title = LocaleManager.L("deep_friction_title"),
                    desc = LocaleManager.L("deep_friction_desc"),
                    checked = deepFrictionEnabled,
                    onCheckedChange = { 
                        if (!it && deepFrictionEnabled) {
                            promiseDialogFeature = "deepFriction"
                        } else {
                            deepFrictionEnabled = it
                            sp.edit { putBoolean("feature_deep_friction_enabled", it) }
                        }
                    }
                )
            }
            
            item {
                Spacer(Modifier.height(itemSpacing))
                SettingsToggleItem(
                    title = LocaleManager.L("sholat_lock_title"),
                    desc = LocaleManager.L("sholat_lock_desc"),
                    checked = sholatLockEnabled,
                    onCheckedChange = {
                        if (!it && sholatLockEnabled) {
                            promiseDialogFeature = "sholatLock"
                        } else {
                            sholatLockEnabled = it
                            sp.edit { putBoolean("feature_sholat_lock_enabled", it) }
                        }
                    }
                )
            }
            
            item {
                Spacer(Modifier.height(itemSpacing))
                SettingsToggleItem(
                    title = LocaleManager.L("prayer_notif_title"),
                    desc = LocaleManager.L("prayer_notif_desc"),
                    checked = prayerNotifEnabled,
                    onCheckedChange = {
                        if (!it && prayerNotifEnabled) {
                            promiseDialogFeature = "prayerNotif"
                        } else {
                            prayerNotifEnabled = it
                            sp.edit { putBoolean("feature_prayer_notification_enabled", it) }
                        }
                    }
                )
            }
            
            if (prayerNotifEnabled) {
                item {
                    Spacer(Modifier.height(itemSpacing))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg()),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, borderC())
                    ) {
                        Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else 16.dp)) {
                            Text(
                                LocaleManager.LF("settings_notif_before", prayerNotifMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textPrimC(),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(LocaleManager.L("input_5"), style = MaterialTheme.typography.labelSmall, color = textSecC())
                                Slider(
                                    value = prayerNotifMinutes.toFloat(),
                                    onValueChange = { 
                                        prayerNotifMinutes = it.toInt()
                                        sp.edit { putInt("prayer_notification_minutes_before", it.toInt()) }
                                    },
                                    valueRange = 5f..20f,
                                    steps = 2,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = greenAccent(),
                                        activeTrackColor = greenAccent()
                                    )
                                )
                                Text(LocaleManager.L("input_20"), style = MaterialTheme.typography.labelSmall, color = textSecC())
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            Text(
                                LocaleManager.LF("settings_notif_example", prayerNotifMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecC()
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(sectionSpacing))
                Text(LocaleManager.L("settings_language"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textSecC())
            }
            
            item {
                Spacer(Modifier.height(if (isSmallScreen) 8.dp else 12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg()),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderC())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isSmallScreen) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageButton(
                            label = "English",
                            selected = LocaleManager.getLanguage() == "en",
                            onClick = { LocaleManager.setLanguage("en") }
                        )
                        LanguageButton(
                            label = "Bahasa Indonesia",
                            selected = LocaleManager.getLanguage() == "id",
                            onClick = { LocaleManager.setLanguage("id") }
                        )
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }
            

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
                    // Avatar lingkaran dengan foto profil atau inisial
                    Box(
                        modifier         = Modifier
                            .size(88.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF2E7D32), Color(0xFF43A047))
                                ),
                                shape = CircleShape
                            )
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUri != null && File(profilePicUri!!).exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(profilePicUri!!))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = LocaleManager.L("profile_photo"),
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text       = (userName.firstOrNull() ?: 'F').uppercaseChar().toString(),
                                fontSize   = 38.sp,
                                fontWeight = FontWeight.Black,
                                color      = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
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
                                    Text(LocaleManager.L("edit_icon"), fontSize = 14.sp)
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
                            placeholder   = { Text(LocaleManager.L("input_name_placeholder"), color = textMutC()) },
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
                            ) { Text(LocaleManager.L("button_cancel"), color = textSecC()) }
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
                            ) { Text(LocaleManager.L("button_save")) }
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
                        ProfilStat("$totalVerses", LocaleManager.L("profile_stat_ayat"), Color(0xFF2E7D32))
                        ProfilStatDivider()
                        ProfilStat("$unlockedCount/${ALL_BADGES.size}", LocaleManager.L("profile_stat_badge"), Color(0xFFFF8F00))
                        ProfilStatDivider()
                        ProfilStat("$streak", LocaleManager.L("profile_stat_streak"), Color(0xFF1565C0))
                    }
                }
            }
        }

        // ── Kartu Pengaturan ──────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettings = true },
                colors = CardDefaults.cardColors(containerColor = cardBg()),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF64748B).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, null, tint = Color(0xFF64748B))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(LocaleManager.L("feature_settings"), fontWeight = FontWeight.Bold, color = textPrimC())
                        Text(LocaleManager.L("feature_settings_desc"), style = MaterialTheme.typography.bodySmall, color = textSecC())
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = textMutC())
                }
            }
        }

        // ── Section: Lemari Badge ─────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(LocaleManager.L("badge_header"), fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    LocaleManager.L("badge_cabinet"),
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
                        getBadgeDescription(badge.desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = textMutC(),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    requirePassword: Boolean = false,
    onToggleClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (requirePassword) {
                    onToggleClick()
                } else {
                    onCheckedChange(!checked)
                }
            },
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderC())
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = textPrimC())
                Text(desc, style = MaterialTheme.typography.bodySmall, color = textSecC())
            }
            Switch(
                checked = checked,
                onCheckedChange = { 
                    if (requirePassword) {
                        onToggleClick()
                    } else {
                        onCheckedChange(!checked)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2E7D32)
                )
            )
        }
    }
}

@Composable
fun LanguageButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) greenAccent() else cardBg(),
        border = if (selected) null else BorderStroke(1.dp, borderC())
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else textPrimC()
        )
    }
}

@Composable
fun PasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isError: Boolean = false
) {
    var passwordInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocaleManager.L("parent_password_title"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(LocaleManager.L("parent_password_desc"), color = textSecC())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text(LocaleManager.L("password_placeholder")) },
                    singleLine = true,
                    isError = isError,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2E7D32),
                        unfocusedBorderColor = if (isError) Color.Red else borderC()
                    )
                )
                if (isError) {
                    Text(LocaleManager.L("password_wrong"), color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passwordInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(LocaleManager.L("button_confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocaleManager.L("button_cancel"))
            }
        }
    )
}

@Composable
fun PromiseConfirmDialog(
    promiseInput: String,
    onValueChange: (String) -> Unit,
    correctPromise: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isEnabled = promiseInput.trim().lowercase() == correctPromise.lowercase()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg(),
        shape = RoundedCornerShape(28.dp),
        title = { Text(LocaleManager.L("promise_title_confirm"), color = greenAccent(), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 14.sp) },
        text = {
            Column {
                Text(LocaleManager.L("promise_desc_type"), color = textSecC(), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("\"$correctPromise\"", color = greenAccent(), fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = promiseInput,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(LocaleManager.L("promise_placeholder"), color = textMutC().copy(0.3f), fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = greenAccent(),
                        unfocusedBorderColor = borderC().copy(0.1f),
                        focusedTextColor = textPrimC(),
                        unfocusedTextColor = textPrimC()
                    )
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isEnabled,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = greenAccent(), 
                    disabledContainerColor = textMutC().copy(0.05f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) { 
                Text(
                    "KONFIRMASI", 
                    color = if (isEnabled) cardBg() else textMutC(), 
                    fontWeight = FontWeight.Bold
                ) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocaleManager.L("button_batal"), color = textMutC(), fontWeight = FontWeight.Bold) }
        }
    )
}