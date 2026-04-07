package com.dame.ungker.ungkeh

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SambungQuizState(
    val surahName: String = "",
    val verses   : List<String> = emptyList(),
    val totalQ   : Int = 0,
    val isMushafTab: Boolean = false 
)

private const val QUIZ_AUTOCHECK_DELAY_MS    = 500L
private const val QUIZ_NEXT_QUESTION_DELAY_MS = 1_200L

@Composable
fun StableFlowLayout(
    modifier: Modifier = Modifier,
    hSpacing: Dp = 0.dp,
    vSpacing: Dp = 0.dp,
    arabRtl: Boolean = false,
    centered: Boolean = false,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val hSpacingPx = hSpacing.roundToPx()
        val vSpacingPx = vSpacing.roundToPx()

        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0

        placeables.forEach { placeable ->
            val spacing = if (currentRow.isEmpty()) 0 else hSpacingPx
            if (currentRowWidth + placeable.width + spacing > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowWidths.add(currentRowWidth)
                rowHeights.add(currentRowHeight)
                currentRow = mutableListOf()
                currentRowWidth = 0
                currentRowHeight = 0
            }

            if (currentRow.isNotEmpty()) {
                currentRowWidth += hSpacingPx
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width
            currentRowHeight = maxOf(currentRowHeight, placeable.height)
        }

        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentRowWidth)
            rowHeights.add(currentRowHeight)
        }

        val totalHeight = rowHeights.sum() + (if (rows.isEmpty()) 0 else (rows.size - 1) * vSpacingPx)
        layout(constraints.maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            rows.forEachIndexed { index, row ->
                var x = if (centered) {
                    (constraints.maxWidth - rowWidths[index]) / 2
                } else if (arabRtl) {
                    constraints.maxWidth
                } else {
                    0
                }
                
                if (arabRtl) {
                    row.forEach { placeable ->
                        x -= placeable.width
                        placeable.place(x, y)
                        x -= hSpacingPx
                    }
                } else {
                    row.forEach { placeable ->
                        placeable.place(x, y)
                        x += placeable.width + hSpacingPx
                    }
                }
                y += rowHeights[index] + vSpacingPx
            }
        }
    }
}

@Composable
fun SambungAyatScreen(
    dbHelper : QuranDatabaseHelper,
    onBack   : () -> Unit,
    onHideNavBar: (Boolean) -> Unit = {},
    isMushafTab: Boolean = false 
) {
    var quizState by remember { mutableStateOf<SambungQuizState?>(null) }

    LaunchedEffect(Unit) {
        onHideNavBar(true)
    }

    DisposableEffect(Unit) {
        onDispose { onHideNavBar(false) }
    }

    if (quizState == null) {
        QuizSetup(dbHelper, onBack = onBack, isMushafTab = isMushafTab) { quizState = it }
    } else {
        SambungQuizPlay(quizState!!, dbHelper, onBack = { quizState = null })
    }
}

@Composable
fun QuizSetup(
    dbHelper: QuranDatabaseHelper,
    onBack: () -> Unit,
    isMushafTab: Boolean,
    onStart: (SambungQuizState) -> Unit
) {
    var surahs by remember { mutableStateOf(emptyList<ChapterMeta>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSurah by remember { mutableStateOf<ChapterMeta?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            surahs = dbHelper.getAllChaptersMeta()
        }
    }

    val filteredSurahs = remember(searchQuery, surahs) {
        surahs.filter { 
            it.nameLatin.contains(searchQuery, ignoreCase = true) || 
            it.id.toString() == searchQuery 
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { 
                    Text("Sambung Ayat", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = textPrimC()) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = greenAccent())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = pageBg()
    ) { p ->
        Box(modifier = Modifier.padding(p).fillMaxSize()) {
            if (selectedSurah == null) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari Surah atau Nomor...", color = textMutC()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = greenAccent()) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = greenAccent(),
                                unfocusedBorderColor = borderC(),
                                focusedContainerColor = cardBg(),
                                unfocusedContainerColor = cardBg(),
                                focusedTextColor = textPrimC(),
                                unfocusedTextColor = textPrimC()
                            ),
                            singleLine = true
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                "Pilih Surah Hafalanmu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textPrimC(),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(filteredSurahs) { surah ->
                            SurahQuizCard(surah = surah) { selectedSurah = surah }
                        }
                        
                        if (filteredSurahs.isEmpty() && surahs.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text("Surah tidak ditemukan", color = textMutC())
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuizConfig(selectedSurah!!, isMushafTab = isMushafTab, onBack = { selectedSurah = null }, onStart = onStart)
                }
            }
        }
    }
}

@Composable
fun SurahQuizCard(surah: ChapterMeta, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg()),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(greenBgC(), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${surah.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = greenAccent()
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.nameLatin,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimC()
                )
                Text(
                    text = "${surah.verseCount} Ayat",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecC()
                )
            }
            
            Text(
                text = surah.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = greenAccent()
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = textMutC(),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun QuizConfig(
    surah: ChapterMeta,
    isMushafTab: Boolean,
    onBack: () -> Unit,
    onStart: (SambungQuizState) -> Unit
) {
    var fromInput by remember { mutableStateOf("1") }
    var toInput   by remember { mutableStateOf("${surah.verseCount}") }
    var jumlahSoal by remember { mutableIntStateOf(10) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    
    val rangeMax = surah.verseCount
    val currentFrom = fromInput.toIntOrNull() ?: 1
    val currentTo = toInput.toIntOrNull() ?: rangeMax
    val availableAyahCount = (currentTo - currentFrom + 1).coerceAtLeast(0)

    LaunchedEffect(currentFrom, currentTo, jumlahSoal) {
        if (currentFrom < 1 || currentFrom > rangeMax) {
            errorMsg = "Rentang ayat tidak valid (1-$rangeMax)"
        } else if (currentTo < currentFrom) {
            errorMsg = "Ayat 'Sampai' tidak boleh lebih kecil dari 'Dari'"
        } else if (currentTo > rangeMax) {
            errorMsg = "Ayat 'Sampai' maksimal $rangeMax"
        } else if (jumlahSoal > availableAyahCount) {
            errorMsg = "Hanya tersedia $availableAyahCount ayat. Kurangi jumlah soal."
        } else {
            errorMsg = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(greenBgC(), CircleShape).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = greenAccent(), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Konfigurasi Sesi", style = MaterialTheme.typography.labelMedium, color = textSecC())
                Text(surah.nameLatin, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = textPrimC())
            }
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = greenAccent().copy(alpha = 0.05f))
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(cardBg(), CircleShape), contentAlignment = Alignment.Center) {
                    Text("💡", fontSize = 24.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Pilih rentang ayat yang sudah kamu hafal agar kuis lebih efektif.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimC()
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text("Rentang Ayat", fontWeight = FontWeight.Bold, color = textPrimC())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigInputField(
                value = fromInput,
                label = "Dari Ayat",
                onValueChange = { if (it.length <= 3) fromInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f)
            )
            ConfigInputField(
                value = toInput,
                label = "Sampai",
                onValueChange = { if (it.length <= 3) toInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Jumlah Soal", fontWeight = FontWeight.Bold, color = textPrimC())
            Spacer(Modifier.weight(1f))
            if (availableAyahCount > 0) {
                Text("$availableAyahCount ayat terpilih", style = MaterialTheme.typography.bodySmall, color = greenAccent())
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        val durations = remember(availableAyahCount) {
            val defaults = listOf(5, 10, 15, 20)
            val possible = defaults.filter { it <= availableAyahCount }.toMutableList()
            if (!possible.contains(availableAyahCount) && availableAyahCount in 1..25) {
                possible.add(availableAyahCount)
            }
            if (possible.isEmpty() && availableAyahCount > 0) possible.add(availableAyahCount)
            possible.sorted().distinct()
        }

        LaunchedEffect(durations) {
            if (durations.isNotEmpty() && !durations.contains(jumlahSoal)) {
                jumlahSoal = durations.last()
            }
        }

        StableFlowLayout(hSpacing = 10.dp, vSpacing = 10.dp, modifier = Modifier.fillMaxWidth()) {
            durations.forEach { num ->
                val sel = jumlahSoal == num
                FilterChip(
                    selected = sel,
                    onClick = { jumlahSoal = num },
                    label = { Text("$num Soal", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = greenAccent(),
                        selectedLabelColor = Color.White,
                        containerColor = cardBg(),
                        labelColor = textSecC()
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = borderC(),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        enabled = true,
                        selected = sel
                    )
                )
            }
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(20.dp))
            Surface(
                color = Color(0xFFEF4444).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMsg!!, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = {
                onStart(SambungQuizState(
                    surahName = surah.name,
                    totalQ    = jumlahSoal,
                    isMushafTab = isMushafTab
                ).copy(
                    verses = listOf("__CONFIG__", surah.id.toString(), currentFrom.toString(), currentTo.toString(), jumlahSoal.toString())
                ))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = greenAccent()),
            enabled = errorMsg == null && fromInput.isNotBlank() && toInput.isNotBlank()
        ) {
            Text("MULAI SEKARANG", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ConfigInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = greenAccent(),
            unfocusedBorderColor = borderC(),
            focusedContainerColor = cardBg(),
            unfocusedContainerColor = cardBg(),
            focusedTextColor = textPrimC(),
            unfocusedTextColor = textPrimC()
        )
    )
}

@Composable
fun SambungQuizPlay(
    state    : SambungQuizState,
    dbHelper : QuranDatabaseHelper,
    onBack   : () -> Unit,
    onFinish : () -> Unit = onBack
) {
    var verses   by remember { mutableStateOf(state.verses) }
    var isLoading by remember { mutableStateOf(verses.firstOrNull() == "__CONFIG__") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (verses.firstOrNull() == "__CONFIG__") {
            val surahId  = verses.getOrNull(1)?.toIntOrNull() ?: 1
            val fromAyah = verses.getOrNull(2)?.toIntOrNull() ?: 1
            val toAyah   = verses.getOrNull(3)?.toIntOrNull() ?: Int.MAX_VALUE
            val count    = verses.getOrNull(4)?.toIntOrNull() ?: 10
            withContext(Dispatchers.IO) {
                val (_, v) = dbHelper.getQuizVerses(surahId, fromAyah, toAyah, count)
                withContext(Dispatchers.Main) {
                    verses   = v
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize().background(pageBg()), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = greenAccent())
                Spacer(Modifier.height(12.dp))
                Text("Menyiapkan hafalan...", color = textSecC(), fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    if (verses.isEmpty()) {
        Box(Modifier.fillMaxSize().background(pageBg()), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("😔", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text("Tidak ada ayat yang cocok.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textPrimC())
                Text("Mungkin rentang ayat terlalu pendek atau kriteria kata tidak terpenuhi.", color = textSecC(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = greenAccent())) {
                    Text("Kembali")
                }
            }
        }
        return
    }

    var currentIdx   by remember { mutableIntStateOf(0) }
    var score        by remember { mutableIntStateOf(0) }
    var isFinished   by remember { mutableStateOf(false) }
    var isSuccess    by remember { mutableStateOf(false) }
    var isError      by remember { mutableStateOf(false) }

    if (isFinished) {
        Box(modifier = Modifier.fillMaxSize().background(
            if (score >= verses.size * 0.7) greenAccent() else Color(0xFF1565C0)
        ), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(if (score >= verses.size * 0.7) "🌟" else "📚", fontSize = 72.sp)
                Text(
                    if (score >= verses.size * 0.7) "Luar Biasa!" else "Terus Berlatih!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black, color = Color.White
                )
                Text("$score / ${verses.size} ayat benar",
                    style = MaterialTheme.typography.titleLarge, color = Color.White.copy(0.9f))
                Text("Surah ${state.surahName}",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))
                
                if (state.isMushafTab) {
                    Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(8.dp)) {
                        Text("Mode Latihan (Tanpa Kredit)", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold,
                        color = if (score >= verses.size * 0.7) greenAccent() else Color(0xFF1565C0))
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(pageBg())) {
        AnimatedContent(
            targetState = currentIdx,
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(tween(400)))
            },
            label = "QuizTransition"
        ) { targetIdx ->
            val currentVerse = verses.getOrNull(targetIdx) ?: ""
            val correctWords = remember(targetIdx, verses) {
                currentVerse.split(Regex("\\s+"))
                    .map { it.trim() }
                    .filter { token ->
                        token.any { c ->
                            c.code in 0x0621..0x063A ||
                                    c.code in 0x0641..0x064A ||
                                    c.code in 0x0671..0x06D3
                        }
                    }
            }
            val shuffledWords = remember(targetIdx, verses) { correctWords.shuffled() }
            val currentSelectedWords = remember(targetIdx, verses) { mutableStateListOf<String>() }
            val currentAvailableWords = remember(targetIdx, verses) {
                mutableStateListOf<String>().apply { addAll(shuffledWords) }
            }
            
            LaunchedEffect(currentSelectedWords.size) {
                if (currentSelectedWords.size == currentCorrectWords.size && currentCorrectWords.isNotEmpty() && !isSuccess && !isError) {
                    delay(QUIZ_AUTOCHECK_DELAY_MS)
                    if (currentSelectedWords.size == currentCorrectWords.size) {
                        if (currentSelectedWords.toList() == currentCorrectWords) {
                            isSuccess = true
                            score++
                            
                            if (!state.isMushafTab) {
                                CreditUtils.addCredit(context, 60_000L, verseCount = 1)
                            }
                            
                            delay(QUIZ_NEXT_QUESTION_DELAY_MS)
                            if (currentIdx < verses.size - 1) {
                                currentIdx++
                                isSuccess = false
                                isError   = false
                            } else {
                                isFinished = true
                            }
                        } else {
                            isError = true
                        }
                    }
                }
            }
            
            // Animasi staggered untuk chip saat ayat baru muncul
            var chipsVisible by remember(targetIdx) { mutableStateOf(false) }
            LaunchedEffect(targetIdx) {
                delay(100)
                chipsVisible = true
            }

            Column(modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimC())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.surahName, fontWeight = FontWeight.Bold, color = textPrimC())
                        Text("Soal ${targetIdx + 1} dari ${verses.size}",
                            style = MaterialTheme.typography.bodySmall, color = textSecC())
                    }
                    Surface(color = greenBgC(), shape = RoundedCornerShape(12.dp)) {
                        Text("⭐ $score",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold, color = greenAccent())
                    }
                }

                Spacer(Modifier.height(12.dp))
                val progress = (targetIdx.toFloat() + (if (isSuccess) 1f else 0f)) / verses.size
                val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring(), label = "progress")
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = greenAccent(),
                    trackColor = trackBg()
                )

                Spacer(Modifier.height(24.dp))
                Text("Susunlah potongan ayat ini:",
                    style = MaterialTheme.typography.bodyLarge, color = textSecC(), fontWeight = FontWeight.Medium)
                
                if (state.isMushafTab) {
                    Text("(Mode Latihan - Tanpa Kredit)", style = MaterialTheme.typography.labelSmall, color = textMutC())
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .border(2.dp,
                            when {
                                isSuccess -> greenAccent()
                                isError   -> Color(0xFFE53935)
                                else      -> borderC()
                            },
                            RoundedCornerShape(20.dp))
                        .background(
                            when {
                                isSuccess -> greenBgC()
                                isError   -> Color(0xFFFFEBEE).copy(alpha = if (LocalIsDarkMode.current) 0.1f else 1f)
                                else      -> cardBg()
                            },
                            RoundedCornerShape(20.dp))
                        .animateContentSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (currentSelectedWords.isEmpty()) {
                        Text(
                            "Ketuk kata di bawah untuk menyusun ayat",
                            color = textMutC(), textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    StableFlowLayout(
                        modifier = Modifier.fillMaxWidth(),
                        hSpacing = 8.dp,
                        vSpacing = 12.dp,
                        arabRtl = true
                    ) {
                        currentSelectedWords.forEachIndexed { index, word ->
                            WordItem(word, isAnswer = true) {
                                currentSelectedWords.removeAt(index)
                                currentAvailableWords.add(word)
                                isError = false
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                StableFlowLayout(
                    modifier = Modifier.fillMaxWidth(),
                    hSpacing = 10.dp,
                    vSpacing = 12.dp,
                    arabRtl = true
                ) {
                    currentAvailableWords.forEachIndexed { index, word ->
                        AnimatedVisibility(
                            visible = chipsVisible,
                            enter = fadeIn(tween(400, delayMillis = index * 50)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400, delayMillis = index * 50))
                        ) {
                            WordItem(word, isAnswer = false) {
                                currentSelectedWords.add(word)
                                currentAvailableWords.removeAt(index)
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (isError) {
                    Button(
                        onClick = {
                            currentAvailableWords.clear()
                            currentAvailableWords.addAll(shuffledWords)
                            currentSelectedWords.clear()
                            isError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("COBA LAGI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(visible = isSuccess, enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.3f)), contentAlignment = Alignment.Center) {
                Surface(color = greenAccent(), shape = CircleShape, modifier = Modifier.size(80.dp), shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✔", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun WordItem(word: String, isAnswer: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isAnswer) greenAccent().copy(0.1f) else cardBg(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, if (isAnswer) greenAccent().copy(0.5f) else borderC()),
        shadowElevation = if (isAnswer) 0.dp else 2.dp
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 20.sp,
            color = textPrimC(),
            fontWeight = FontWeight.SemiBold
        )
    }
}
