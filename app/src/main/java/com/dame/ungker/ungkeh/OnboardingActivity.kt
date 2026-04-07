package com.dame.ungker.ungkeh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)

        // Jika sudah pernah onboarding, langsung ke MainActivity
        if (sp.getBoolean("onboarding_done", false)) {
            goToMain(); return
        }

        setContent {
            MaterialTheme {
                OnboardingScreen(
                    onFinish = { name ->
                        // Generate UID jika belum ada
                        val uid = sp.getString("user_uid", null) ?: run {
                            val g = (100000..999999).random().toString()
                            sp.edit { putString("user_uid", g) }; g
                        }
                        sp.edit {
                            putString("user_name", name.trim().ifBlank { "Fulan#$uid" })
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
fun OnboardingScreen(onFinish: (String) -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    // Urutan: 0..pages.size-1 = info, pages.size = izin, pages.size+1 = input nama

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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
                    totalPages = pages.size + 1, // +1 untuk halaman izin
                    onNext = { currentPage++ }
                )
                page == pages.size -> PermissionPage(
                    pageIndex = page,
                    totalPages = pages.size + 1,
                    onNext = { currentPage++ }
                )
                else -> NameInputPage(onFinish = onFinish)
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
            repeat(totalPages + 1) { i ->
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
                if (pageIndex == totalPages - 1) "Siap! →" else "Lanjut →",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        if (pageIndex < totalPages - 1) {
            TextButton(onClick = { /* skip ke halaman nama */ }) {
                // kosong — tidak ada skip, harus baca semua
            }
        }
    }
}

// ── Halaman izin ─────────────────────────────────────────────────────────────

@Composable
fun PermissionPage(pageIndex: Int, totalPages: Int, onNext: () -> Unit) {
    val context = LocalContext.current

    // Cek status izin — refresh setiap kali composable di-recompose
    var hasOverlay  by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasUsage    by remember { mutableStateOf(false) }
    var hasNotif    by remember { mutableStateOf(false) }

    // Refresh status setiap kali user kembali dari Settings
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasOverlay = Settings.canDrawOverlays(context)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val t = System.currentTimeMillis()
        hasUsage = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, t - 10_000, t).isNotEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else hasNotif = true
    }

    // Cek awal
    LaunchedEffect(Unit) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val t = System.currentTimeMillis()
        hasUsage = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, t - 10_000, t).isNotEmpty()
        hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    val allGranted = hasOverlay && hasUsage && hasNotif

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
            "Ungker butuh 3 izin berikut agar bisa bekerja dengan baik.",
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
            repeat(totalPages + 1) { i ->
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
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) Color(0xFF2E7D32) else Color(0xFFB45309)
            )
        ) {
            Text(
                if (allGranted) "Semua Izin Aktif ✅" else "Lanjut Tanpa Semua Izin →",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
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