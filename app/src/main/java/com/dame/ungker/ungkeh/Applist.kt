package com.dame.ungker.ungkeh

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: String
)

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

    // State for confirmation flow
    var appToUnblock by remember { mutableStateOf<AppInfo?>(null) }
    var showConfirm1 by remember { mutableStateOf(false) }
    var showConfirm2 by remember { mutableStateOf(false) }
    var showConfirm3 by remember { mutableStateOf(false) }
    var promiseInput by remember { mutableStateOf("") }
    val correctPromise = "Aku berjanji nanti akan mengaktifkan pemblokiran untuk aplikasi ini demi kebaikanku sendiri"

    // Dialog 1
    if (showConfirm1) {
        AlertDialog(
            onDismissRequest = { showConfirm1 = false; appToUnblock = null },
            title = { Text("Konfirmasi", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah kamu yakin mau menonaktifkan pemblokiran untuk aplikasi ini ?") },
            confirmButton = {
                Button(
                    onClick = { showConfirm1 = false; showConfirm2 = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) { Text("Yakin") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm1 = false; appToUnblock = null }) { Text("Batal") }
            }
        )
    }

    // Dialog 2
    if (showConfirm2) {
        AlertDialog(
            onDismissRequest = { showConfirm2 = false; appToUnblock = null },
            text = { Text("Ayolah, ini untuk kebaikanmu sendiri. 🥺🥀", fontSize = 18.sp, textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = { showConfirm2 = false; showConfirm3 = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) { Text("Tetap matikan") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm2 = false; appToUnblock = null }) { Text("Kembali") }
            }
        )
    }

    // Dialog 3
    if (showConfirm3) {
        AlertDialog(
            onDismissRequest = { showConfirm3 = false; appToUnblock = null; promiseInput = "" },
            title = { Text("Janji ya?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Jika memang benar begitu... janji ya nanti diaktifin lagi...")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ketik janji berikut:", fontSize = 12.sp, color = textSecC())
                    Text("\"$correctPromise\"", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0xFF00C853))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = promiseInput,
                        onValueChange = { promiseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ketik janji di sini...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00C853))
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = promiseInput.trim() == correctPromise,
                    onClick = {
                        appToUnblock?.let {
                            blockedApps.remove(it.packageName)
                            sharedPref.edit { putStringSet("blocked_apps", blockedApps.toSet()) }
                        }
                        showConfirm3 = false
                        appToUnblock = null
                        promiseInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853),
                        disabledContainerColor = Color(0xFFE0E0E0)
                    )
                ) { Text("Konfirmasi") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm3 = false; appToUnblock = null; promiseInput = "" }) { Text("Batal") }
            }
        )
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
                        else -> { /* No changes for other categories */ }
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
            "Pilih aplikasi yang akan dibatasi aksesnya",
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
                verticalAlignment = Alignment.CenterVertically) {
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
                        sharedPref.edit {putStringSet("blocked_apps", blockedApps.toSet())}
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
                                sharedPref.edit {putStringSet("blocked_apps", blockedApps.toSet())}
                            } else {
                                // Start confirmation flow
                                appToUnblock = app
                                showConfirm1 = true
                            }
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

    val scale by animateFloatAsState(
        targetValue = if (isBlocked) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) Color(0xFF00C853).copy(alpha = 0.05f) else cardBg()
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (isBlocked) BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBlocked) 4.dp else 1.dp)
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
