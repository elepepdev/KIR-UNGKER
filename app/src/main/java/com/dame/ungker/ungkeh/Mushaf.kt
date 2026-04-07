package com.dame.ungker.ungkeh

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Konstanta dan Helper ───────────────────────────────────────────────────

val JUZ_PAGE_START = intArrayOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
    202, 222, 242, 262, 282, 302, 322, 342, 362, 382,
    402, 422, 442, 462, 482, 502, 522, 542, 562, 582
)

fun getJuzOfPage(page: Int): Int {
    var juz = 1
    for (i in JUZ_PAGE_START.indices) {
        if (page >= JUZ_PAGE_START[i]) juz = i + 1 else break
    }
    return juz
}

@Composable
fun QuranScreen(onHideNavBar: (Boolean) -> Unit = {}) {
    val context  = LocalContext.current
    val dbHelper = remember(context) { QuranDatabaseHelper(context) }
    var showMushaf      by remember { mutableStateOf(false) }
    var showSambungAyat by remember { mutableStateOf(false) }

    when {
        showMushaf      -> MushafScreen(dbHelper = dbHelper, onBack = { showMushaf = false }, onHideNavBar = onHideNavBar)
        showSambungAyat -> SambungAyatScreen(dbHelper = dbHelper, onBack = { showSambungAyat = false }, onHideNavBar = onHideNavBar, isMushafTab = true)
        else -> QuranHubScreen(onMushaf = { showMushaf = true }, onSambungAyat = { showSambungAyat = true })
    }
}

@Composable
fun QuranHubScreen(onMushaf: () -> Unit, onSambungAyat: () -> Unit) {
    val context = LocalContext.current
    var readingStreak by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { readingStreak = getReadingStreak(context) } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Al-Qur'an", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = textPrimC())
        Text("Pilih aktivitas yang ingin kamu lakukan", style = MaterialTheme.typography.bodyMedium, color = textSecC())
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(20.dp), 
            colors = CardDefaults.cardColors(containerColor = if (readingStreak > 0) Color(0xFFFFF3E0) else cardBg()), 
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(52.dp).background(if (readingStreak > 0) Color(0xFFFFE0B2) else greenBgC(), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (readingStreak > 0) "🔥" else "📖", fontSize = 18.sp)
                        if (readingStreak > 0) Text("$readingStreak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (readingStreak > 0) "Streak $readingStreak Hari!" else "Mulai Streak-mu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (readingStreak > 0) Color(0xFFE65100) else textPrimC())
                    Text(if (readingStreak > 0) "Jaga terus, jangan putus ya! 💪" else "Baca mushaf hari ini untuk memulai", style = MaterialTheme.typography.bodySmall, color = textSecC())
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(modifier = Modifier.fillMaxWidth().clickable { onMushaf() }, colors = CardDefaults.cardColors(containerColor = cardBg()), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).background(greenBgC(), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("📖", fontSize = 32.sp) }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mushaf", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textPrimC())
                    Text("Baca Al-Qur'an per halaman", style = MaterialTheme.typography.bodySmall, color = textSecC())
                }
                Text("›", fontSize = 28.sp, color = greenAccent(), fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth().clickable { onSambungAyat() }, colors = CardDefaults.cardColors(containerColor = cardBg()), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).background(Color(0xFFFFF3E0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("🧩", fontSize = 32.sp) }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sambung Ayat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textPrimC())
                    Text("Susun potongan ayat pilihanmu", style = MaterialTheme.typography.bodySmall, color = textSecC())
                }
                Text("›", fontSize = 28.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MushafScreen(dbHelper: QuranDatabaseHelper, onBack: () -> Unit, onHideNavBar: (Boolean) -> Unit = {}) {
    var selectedPage by remember { mutableIntStateOf(-1) }
    var navForward by remember { mutableStateOf(true) }

    LaunchedEffect(selectedPage) {
        onHideNavBar(selectedPage != -1)
    }

    if (selectedPage != -1) {
        AnimatedContent(targetState = selectedPage, transitionSpec = {
            if (navForward) (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            else (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
        }, label = "Page") { animatedPage ->
            MushafPageReader(
                page = animatedPage, 
                dbHelper = dbHelper, 
                onBack = { selectedPage = -1 }, 
                onNav = { newPage -> navForward = newPage > selectedPage; selectedPage = newPage }
            )
        }
    } else {
        MushafPagePicker(onBack = onBack, onOpenPage = { selectedPage = it })
    }
}

@Composable
fun MushafPagePicker(onBack: () -> Unit, onOpenPage: (Int) -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    var bookmarks by remember { mutableStateOf(sp.getStringSet("mushaf_bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPages = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) (1..604).toList()
        else (1..604).filter { it.toString().startsWith(q) }
    }

    Column(modifier = Modifier.fillMaxSize().background(pageBg())) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC()) }
            Text("Mushaf", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = textPrimC(), modifier = Modifier.weight(1f))
            if (bookmarks.isNotEmpty()) Surface(onClick = { onOpenPage(bookmarks.max()) }, shape = RoundedCornerShape(10.dp), color = greenBgC()) { Text("🔖 Lanjut", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = greenAccent(), fontWeight = FontWeight.Bold) }
        }
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it.take(10) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), placeholder = { Text("Cari halaman (1–604)", color = textMutC()) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecC()) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var lastJuz = -1
            filteredPages.forEach { pageNum ->
                val pageJuz = getJuzOfPage(pageNum)
                if (searchQuery.isBlank() && pageJuz != lastJuz && JUZ_PAGE_START.contains(pageNum)) {
                    lastJuz = pageJuz
                    item(key = "sep_$pageJuz", span = { GridItemSpan(4) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerC())
                            Surface(color = greenAccent(), shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
                                Text(text = " Juz $pageJuz ", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerC())
                        }
                    }
                }
                val isBookmarked = bookmarks.contains(pageNum)
                item(key = "p_$pageNum") {
                    Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onOpenPage(pageNum) }, colors = CardDefaults.cardColors(containerColor = if (isBookmarked) greenBgC() else cardBg()), shape = RoundedCornerShape(12.dp), border = BorderStroke(if (isBookmarked) 2.dp else 1.dp, if (isBookmarked) greenAccent() else borderC())) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isBookmarked) Text("🔖", fontSize = 10.sp)
                                Text("Hal", fontSize = 10.sp, color = if (isBookmarked) greenAccent() else textSecC())
                                Text(pageNum.toString(), fontWeight = FontWeight.Bold, color = if (isBookmarked) greenAccent() else textPrimC())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MushafPageReader(page: Int, dbHelper: QuranDatabaseHelper, onBack: () -> Unit, onNav: (Int) -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE) }
    var pageVerses by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var isPageLoading by remember { mutableStateOf(true) }
    val scrollState = rememberLazyListState()

    var secondsRemaining by remember(page) { mutableIntStateOf(45) } 
    var hasAwardedCredit by remember(page) { mutableStateOf(false) }
    
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex >= totalItemsCount - 1 && totalItemsCount > 0
        }
    }

    LaunchedEffect(page) {
        isPageLoading = true
        withContext(Dispatchers.IO) {
            val list = dbHelper.getVersesByPage(page)
            withContext(Dispatchers.Main) { pageVerses = list; isPageLoading = false }
        }
        
        secondsRemaining = 45
        while(secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
    }

    var bookmarks by remember { mutableStateOf(sp.getStringSet("mushaf_bookmarks", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()) }
    val isBookmarked = bookmarks.contains(page)
    fun toggleBookmark() {
        val newSet = if (isBookmarked) bookmarks - page else bookmarks + page
        bookmarks = newSet; sp.edit {putStringSet("mushaf_bookmarks", newSet.map { it.toString() }.toSet())}
    }

    Scaffold(
        containerColor = pageBg(),
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(cardBg()).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC()) }
                Column(modifier = Modifier.weight(1f)) { Text("Halaman $page", fontWeight = FontWeight.Bold, color = textPrimC()); Text("Juz ${getJuzOfPage(page)}", style = MaterialTheme.typography.bodySmall, color = greenAccent()) }
                IconButton(onClick = { if (page > 1) onNav(page - 1) }, enabled = page > 1) { Text("◀", color = if (page > 1) greenAccent() else textMutC()) }
                IconButton(onClick = { if (page < 604) onNav(page + 1) }, enabled = page < 604) { Text("▶", color = if (page < 604) greenAccent() else textMutC()) }
                IconButton(onClick = { toggleBookmark() }) { Icon(Icons.Filled.Star, null, tint = if (isBookmarked) greenAccent() else textSecC().copy(0.35f)) }
            }
        },
        bottomBar = {
            val juzIdx = JUZ_PAGE_START.indexOfLast { it <= page }
            val juzStart = if (juzIdx == -1) 1 else JUZ_PAGE_START[juzIdx]
            val juzEnd = if (juzIdx != -1 && juzIdx < JUZ_PAGE_START.size - 1) JUZ_PAGE_START[juzIdx + 1] - 1 else 604
            val juzProgress = (page - juzStart).toFloat() / (juzEnd - juzStart).coerceAtLeast(1).toFloat()
            val currentJuz = if (juzIdx == -1) 1 else juzIdx + 1

            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = cardBg(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Progres Juz $currentJuz", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textPrimC())
                            Spacer(Modifier.weight(1f))
                            Text("${(juzProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = greenAccent(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(trackBg(), RoundedCornerShape(5.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(juzProgress).fillMaxHeight().background(
                                Brush.horizontalGradient(listOf(Color(0xFF52B788), Color(0xFF2D6A4F))),
                                RoundedCornerShape(5.dp)
                            ))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Halaman $page dari $juzEnd", style = MaterialTheme.typography.labelSmall, color = textSecC())
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (secondsRemaining == 0 && isNearBottom && !hasAwardedCredit) {
                        hasAwardedCredit = true
                        // Update stats only, removed addCredit
                        CreditUtils.updateStatsOnly(context, verseCount = pageVerses.size)
                        
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val sessions = sp.getStringSet("reading_sessions", emptySet())?.toMutableSet() ?: mutableSetOf()
                        sessions.add("${todayStr}_read")
                        sp.edit { putStringSet("reading_sessions", sessions) }
                    }
                },
                containerColor = if (hasAwardedCredit) greenAccent() else if (secondsRemaining == 0 && isNearBottom) greenAccent() else textMutC(),
                contentColor = Color.White,
                icon = { Icon(if (hasAwardedCredit) Icons.Default.Check else Icons.Default.Star, null) },
                text = {
                    Text(
                        if (hasAwardedCredit) "Selesai" 
                        else if (!isNearBottom) "Scroll ke Bawah"
                        else if (secondsRemaining > 0) "Membaca ($secondsRemaining)" 
                        else "Read",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.offset(y = (-10).dp)
            )
        }
    ) { innerPadding ->
        if (isPageLoading) Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator(color = greenAccent()) }
        else LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().padding(innerPadding), 
            contentPadding = PaddingValues(16.dp), 
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(pageVerses) { _, verse ->
                Text(text = verse.text, style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 50.sp, textAlign = TextAlign.Right, color = textPrimC()), modifier = Modifier.fillMaxWidth())
                Surface(color = greenBgC(), shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) { Text("${verse.surahName} : ${verse.ayahId}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = greenAccent()) }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = dividerC())
            }
        }
    }
}
