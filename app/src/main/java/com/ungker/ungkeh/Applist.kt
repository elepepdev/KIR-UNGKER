package com.ungker.ungkeh

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// buat ngelist app, gtw lah
// ─── Design Tokens (Sync with SholatLockActivity) ──────────────────────────
// Hardcoded tokens removed. Using Theme.kt helpers instead.

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: String
)

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun DaftarAplikasiScreen(isDarkMode: Boolean = false, highlightPackage: String? = null) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // LazyColumn state for auto-scrolling
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val sharedPref = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
    val userRole = remember { sharedPref.getString("user_role", "personal") ?: "personal" }
    val parentPassword = remember { sharedPref.getString("parent_password", "") ?: "" }
    
    val blockedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPref.getStringSet("blocked_apps", emptySet()) ?: emptySet())
        }
    }

    // Confirmation Logic States
    var appToUnblock by remember { mutableStateOf<AppInfo?>(null) }
    var showConfirm1 by remember { mutableStateOf(false) }
    var showConfirm2 by remember { mutableStateOf(false) }
    var showConfirm3 by remember { mutableStateOf(false) }
    var promiseInput by remember { mutableStateOf("") }
    val correctPromise = "Aku menonaktifkan pengaturan ini dengan kesadaranku sendiri"

    // Dialogs
    if (showConfirm1) {
        PremiumAlertDialog(
            onDismiss = { showConfirm1 = false; appToUnblock = null },
            title = LocaleManager.L("confirm_title"),
            text = LocaleManager.L("confirm_unblock"),
            confirmLabel = LocaleManager.L("confirm_yes"),
            onConfirm = { showConfirm1 = false; showConfirm2 = true }
        )
    }
    if (showConfirm2) {
        PremiumAlertDialog(
            onDismiss = { showConfirm2 = false; appToUnblock = null },
            title = LocaleManager.L("dialog_warning_title"),
            text = LocaleManager.L("confirm_warning"),
            confirmLabel = LocaleManager.L("confirm_still"),
            onConfirm = { showConfirm2 = false; showConfirm3 = true }
        )
    }
    if (showConfirm3) {
        if (userRole == "parent") {
            ParentPasswordConfirmDialog(
                savedPassword = parentPassword,
                onDismiss = { showConfirm3 = false; appToUnblock = null; promiseInput = "" },
                onConfirm = {
                    appToUnblock?.let {
                        blockedApps.remove(it.packageName)
                        sharedPref.edit { putStringSet("blocked_apps", blockedApps.toSet()) }
                    }
                    showConfirm3 = false; appToUnblock = null; promiseInput = ""
                }
            )
        } else {
            PremiumPromiseDialog(
                promiseInput = promiseInput,
                onValueChange = { promiseInput = it },
                correctPromise = correctPromise,
                onDismiss = { showConfirm3 = false; appToUnblock = null; promiseInput = "" },
                onConfirm = {
                    appToUnblock?.let {
                        blockedApps.remove(it.packageName)
                        sharedPref.edit { putStringSet("blocked_apps", blockedApps.toSet()) }
                    }
                    showConfirm3 = false; appToUnblock = null; promiseInput = ""
                }
            )
        }
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
                        ApplicationInfo.CATEGORY_SOCIAL -> { categoryStr = LocaleManager.L("app_category_social"); isTarget = true }
                        ApplicationInfo.CATEGORY_GAME -> { categoryStr = LocaleManager.L("app_category_game"); isTarget = true }
                        ApplicationInfo.CATEGORY_VIDEO -> { categoryStr = LocaleManager.L("app_category_entertainment"); isTarget = true }
                        else -> {}
                    }
                }
                val lowerPackage = packageName.lowercase()
                if (lowerPackage.contains("instagram") || lowerPackage.contains("tiktok") ||
                    lowerPackage.contains("youtube") || lowerPackage.contains("facebook") ||
                    lowerPackage.contains("twitter") || lowerPackage.contains("game") ||
                    lowerPackage.contains("mobile.legends")) {
                    isTarget = true
                    if (categoryStr == "Lainnya") categoryStr = if (lowerPackage.contains("game")) LocaleManager.L("app_category_game") else LocaleManager.L("app_category_entertainment")
                }

                if (isTarget) AppInfo(name, packageName, categoryStr) else null
            }.distinctBy { it.packageName }.sortedBy { it.name }

            withContext(Dispatchers.Main) { apps = appList; isLoading = false }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val isAllSelected = filteredApps.isNotEmpty() && filteredApps.all { blockedApps.contains(it.packageName) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(pageBg(), cardBg().copy(alpha = 0.5f), pageBg())
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Column {
                Text(
                    LocaleManager.L("app_list_main_title"),
                    style = MaterialTheme.typography.labelMedium,
                    color = greenAccent(),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    LocaleManager.L("app_list_sub_title"),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = textPrimC(),
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Premium Summary Card
            GlassSummaryCard(
                blockedCount = blockedApps.size,
                isAllSelected = isAllSelected,
                onToggleAll = {
                    if (isAllSelected) filteredApps.forEach { blockedApps.remove(it.packageName) }
                    else filteredApps.forEach { if (!blockedApps.contains(it.packageName)) blockedApps.add(it.packageName) }
                    sharedPref.edit { putStringSet("blocked_apps", blockedApps.toSet()) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Modern Search Bar
            PremiumSearchBar(searchQuery) { searchQuery = it }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = greenAccent(), strokeWidth = 3.dp)
                }
            } else {
                // Auto-scroll to highlighted package
                LaunchedEffect(highlightPackage, apps) {
                    if (highlightPackage != null && apps.isNotEmpty()) {
                        val index = apps.indexOfFirst { it.packageName == highlightPackage }
                        if (index >= 0) {
                            lazyListState.animateScrollToItem(index, scrollOffset = -100)
                        }
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            LocaleManager.LF("app_list_installed", filteredApps.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = textMutC(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        PremiumAppItem(
                            app = app,
                            isBlocked = blockedApps.contains(app.packageName),
                            onToggle = { isChecked ->
                                if (isChecked) {
                                    blockedApps.add(app.packageName)
                                    sharedPref.edit { putStringSet("blocked_apps", blockedApps.toSet()) }
                                } else {
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
}

@Composable
private fun GlassSummaryCard(blockedCount: Int, isAllSelected: Boolean, onToggleAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = greenAccent().copy(alpha = 0.12f),
                border = BorderStroke(1.dp, greenAccent().copy(0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(LocaleManager.L("app_shield"), fontSize = 26.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    LocaleManager.LF("app_list_locked_count", blockedCount),
                    color = textPrimC(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    if (blockedCount > 0) LocaleManager.L("app_list_protection_active") else LocaleManager.L("app_list_no_protection"),
                    color = if (blockedCount > 0) greenAccent() else textMutC(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onToggleAll,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = greenAccent().copy(alpha = 0.1f),
                    contentColor = greenAccent()
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, greenAccent().copy(0.2f))
            ) {
                Text(
                    if (isAllSelected) LocaleManager.L("app_list_btn_cancel") else LocaleManager.L("app_list_btn_all"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun PremiumSearchBar(query: String, onValueChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderC().copy(alpha = 0.3f), CircleShape),
        placeholder = { Text(LocaleManager.L("app_search_placeholder"), color = textMutC().copy(0.6f), fontSize = 15.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = greenAccent(), modifier = Modifier.size(20.dp)) },
        shape = CircleShape,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cardBg(),
            unfocusedContainerColor = cardBg(),
            disabledContainerColor = cardBg(),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = textPrimC(),
            unfocusedTextColor = textPrimC(),
            cursorColor = greenAccent()
        )
    )
}

@Composable
fun PremiumAppItem(app: AppInfo, isBlocked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(iconCache[app.packageName]) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(app.packageName)
                    val bitmap = drawable.toBitmap(96, 96).asImageBitmap()
                    iconCache[app.packageName] = bitmap
                    withContext(Dispatchers.Main) { iconBitmap = bitmap }
                } catch (_: Exception) {}
            }
        }
    }

    val bgColor = if (isBlocked) greenAccent().copy(alpha = 0.08f) else cardAltBg().copy(alpha = 0.4f)
    val borderColor = if (isBlocked) greenAccent().copy(alpha = 0.3f) else borderC().copy(alpha = 0.1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(pageBg()),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        colorFilter = if (!isBlocked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) }) else null,
                        alpha = if (isBlocked) 1f else 0.7f
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    fontWeight = FontWeight.Bold,
                    color = textPrimC(),
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    app.category.uppercase(),
                    fontSize = 10.sp,
                    color = if (isBlocked) greenAccent() else textMutC(),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
            
            UngkerSwitch(checked = isBlocked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun UngkerSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, spring(), label = "thumb")
    Box(
        modifier = Modifier
            .size(44.dp, 24.dp)
            .clip(CircleShape)
            .background(if (checked) greenAccent().copy(0.2f) else trackBg())
            .border(1.dp, if (checked) greenAccent().copy(0.4f) else borderC().copy(0.1f), CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                .size(16.dp)
                .background(if (checked) greenAccent() else textMutC().copy(0.6f), CircleShape)
        )
    }
}

@Composable
private fun PremiumAlertDialog(onDismiss: () -> Unit, title: String, text: String, confirmLabel: String, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg(),
        shape = RoundedCornerShape(28.dp),
        title = { Text(title.uppercase(), color = greenAccent(), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 14.sp) },
        text = { Text(text, color = textPrimC(), fontSize = 15.sp, lineHeight = 24.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = greenAccent()),
                shape = RoundedCornerShape(14.dp)
            ) { Text(confirmLabel, color = cardBg(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocaleManager.L("button_batal"), color = textMutC(), fontWeight = FontWeight.Bold) }
        },
        modifier = Modifier.border(1.dp, borderC().copy(0.1f), RoundedCornerShape(28.dp))
    )
}

@Composable
private fun ParentPasswordConfirmDialog(
    savedPassword: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg(),
        shape = RoundedCornerShape(28.dp),
        title = { Text(LocaleManager.L("parent_confirm_title"), color = greenAccent(), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 14.sp) },
        text = {
            Column {
                Text(LocaleManager.L("parent_confirm_desc"), color = textMutC(), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { 
                        input = it
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(LocaleManager.L("parent_password_field"), color = textMutC().copy(0.3f), fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    isError = isError,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = greenAccent(),
                        unfocusedBorderColor = borderC().copy(0.1f),
                        focusedTextColor = textPrimC(),
                        unfocusedTextColor = textPrimC()
                    )
                )
                if (isError) {
                    Text(LocaleManager.L("parent_password_error"), color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (input == savedPassword) {
                        onConfirm()
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = greenAccent()),
                shape = RoundedCornerShape(14.dp)
            ) { Text(LocaleManager.L("parent_unlock_button"), color = cardBg(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocaleManager.L("button_batal"), color = textMutC(), fontWeight = FontWeight.Bold) }
        },
        modifier = Modifier.border(1.dp, borderC().copy(0.1f), RoundedCornerShape(28.dp))
    )
}

@Composable
private fun PremiumPromiseDialog(promiseInput: String, onValueChange: (String) -> Unit, correctPromise: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg(),
        shape = RoundedCornerShape(28.dp),
        title = { Text(LocaleManager.L("promise_dialog_title"), color = greenAccent(), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 14.sp) },
        text = {
            Column {
                Text(LocaleManager.L("promise_dialog_desc"), color = textMutC(), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("\"$correctPromise\"", color = greenAccent(), fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = promiseInput,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(LocaleManager.L("promise_dialog_placeholder"), color = textMutC().copy(0.3f), fontSize = 13.sp) },
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
                enabled = promiseInput.trim().lowercase() == correctPromise.lowercase(),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = greenAccent(), disabledContainerColor = textMutC().copy(0.05f)),
                shape = RoundedCornerShape(14.dp)
            ) { Text(LocaleManager.L("promise_confirm"), color = if (promiseInput.trim() == correctPromise) cardBg() else textMutC(), fontWeight = FontWeight.Bold) }
        },
        modifier = Modifier.border(1.dp, borderC().copy(0.1f), RoundedCornerShape(28.dp))
    )
}
