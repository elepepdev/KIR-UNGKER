package com.dame.ungker.ungkeh

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream

data class Chapter(val id: Int, val name: String, val nameLatin: String, val content: String)
data class Verse(val surahId: Int, val ayahId: Int, val text: String, val surahName: String)

class QuranDatabaseHelper(val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "QuranDBHelper"
        private const val DATABASE_NAME = "quran.sqlite"
        private const val PAGES_DATABASE_NAME = "quranpages.sqlite"
        private const val DATABASE_VERSION = 2
        private const val LONG_VERSE_THRESHOLD = 12
        private const val MAX_WORDS_PER_SESSION = 50  // batas total kata per sesi baca

        // Pre-compile regex sekali saja — tidak perlu re-compile setiap panggilan
        private val VERSE_SPLIT_REGEX = Regex("\\[\\d+\\]")
        private val HARAKAT_REGEX     = Regex("[\\u0640\\u064B-\\u0652\\u0670\\u06D6-\\u06ED]")
        private val ALEF_REGEX        = Regex("[أإآٱ]")
        private val YA_REGEX          = Regex("[ىی]")
        private val PUNCT_REGEX       = Regex("[\\u060C\\u061B\\u061F,.!?;:]")
        private val SPACES_REGEX      = Regex("\\s+")
    }

    init {
        // Pastikan kedua database sudah ter-copy sebelum digunakan
        copyDatabasesFromAssets()
    }

    // ─── DATABASE COPY ──────────────────────────────────────────────────────────

    private fun copyDatabasesFromAssets() {
        copyDb(DATABASE_NAME)
        copyDb(PAGES_DATABASE_NAME)
    }

    private fun copyDb(dbName: String) {
        val dbPath = context.getDatabasePath(dbName)
        val sp = context.getSharedPreferences("quran_db_prefs", Context.MODE_PRIVATE)
        val lastVersion = sp.getInt("${dbName}_version", 0)

        if (!dbPath.exists() || lastVersion < DATABASE_VERSION) {
            try {
                dbPath.parentFile?.mkdirs()
                context.assets.open(dbName).use { inputStream ->
                    FileOutputStream(dbPath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                sp.edit().putInt("${dbName}_version", DATABASE_VERSION).apply()
                Log.d(TAG, "Database $dbName berhasil di-copy.")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal copy database $dbName: ${e.message}", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    // ─── HELPER: BUKA PAGES DATABASE DENGAN AMAN ────────────────────────────────

    /**
     * Membuka quranpages.sqlite secara read-only dengan validasi.
     * Mengembalikan null jika database tidak bisa dibuka (tidak crash).
     */
    private fun openPagesDb(): SQLiteDatabase? {
        return try {
            val pagesDbPath = context.getDatabasePath(PAGES_DATABASE_NAME)
            if (!pagesDbPath.exists()) {
                Log.e(TAG, "quranpages.sqlite tidak ditemukan di: ${pagesDbPath.absolutePath}")
                // Coba copy ulang
                copyDb(PAGES_DATABASE_NAME)
            }
            if (!pagesDbPath.exists()) {
                Log.e(TAG, "Gagal: quranpages.sqlite masih tidak ada setelah copy ulang.")
                return null
            }
            SQLiteDatabase.openDatabase(
                pagesDbPath.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuka quranpages.sqlite: ${e.message}", e)
            null
        }
    }

    // ─── QUERY UTAMA ─────────────────────────────────────────────────────────────

    fun getAllChapters(): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        try {
            val db = readableDatabase
            // Tambahkan name_pron_en untuk nama latin surat
            db.rawQuery(
                "SELECT id, name_ar, name_pron_en, content FROM chapters ORDER BY id ASC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id        = cursor.getInt(0)
                    val nameAr    = cursor.getString(1) ?: ""
                    val nameLatin = cursor.getString(2) ?: ""
                    val content   = cursor.getString(3) ?: ""
                    chapters.add(Chapter(id, nameAr, nameLatin, content))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllChapters error: ${e.message}", e)
        }
        return chapters
    }

    /**
     * Mengambil daftar ayat berdasarkan nomor halaman mushaf Rasm Usmani.
     * Bekerja sama antara quranpages.sqlite (mapping halaman→surah/ayah)
     * dan quran.sqlite (konten teks Arab).
     *
     * Dibuat defensive: setiap langkah dilindungi try-catch + null-check
     * agar tidak ada crash yang bisa mematikan aplikasi.
     */
    fun getVersesByPage(pageNumber: Int): List<Verse> {
        val verses = mutableListOf<Verse>()

        // Validasi input
        if (pageNumber < 1 || pageNumber > 604) {
            Log.w(TAG, "getVersesByPage: pageNumber $pageNumber di luar range 1-604")
            return verses
        }

        // ── Langkah 1: Ambil mapping (surahId, ayahId) dari quranpages.sqlite ──
        val mapping = mutableListOf<Pair<Int, Int>>()
        val pagesDb = openPagesDb() ?: return verses // Gagal buka DB → return list kosong, tidak crash

        try {
            // NAMA KOLOM YANG BENAR: soraid dan ayaid (bukan suraid/ayahid)
            pagesDb.rawQuery(
                """
                SELECT soraid, ayaid
                FROM ayarects
                WHERE page = ?
                GROUP BY soraid, ayaid
                ORDER BY MIN(rowid) ASC
                """.trimIndent(),
                arrayOf(pageNumber.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val surahId = cursor.getInt(0)
                    val ayahId  = cursor.getInt(1)
                    if (surahId > 0 && ayahId > 0) { // Abaikan baris tidak valid
                        mapping.add(surahId to ayahId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVersesByPage - query pages error: ${e.message}", e)
        } finally {
            // WAJIB ditutup di sini agar tidak ada resource leak
            try { pagesDb.close() } catch (_: Exception) {}
        }

        if (mapping.isEmpty()) {
            Log.w(TAG, "getVersesByPage: tidak ada data untuk halaman $pageNumber")
            return verses
        }

        // ── Langkah 2: Ambil teks ayat dari quran.sqlite ──
        // Cache surah agar tidak query berulang untuk surah yang sama
        val surahCache = mutableMapOf<Int, Pair<String, List<String>>>() // surahId → (name, verses)

        try {
            val mainDb = readableDatabase

            mapping.forEach { (surahId, ayahId) ->
                // Ambil dari cache dulu
                val (surahName, allVerses) = surahCache.getOrPut(surahId) {
                    var name = ""
                    var verseList = emptyList<String>()
                    try {
                        mainDb.rawQuery(
                            "SELECT name_ar, content FROM chapters WHERE id = ?",
                            arrayOf(surahId.toString())
                        ).use { cursor ->
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(0) ?: ""
                                val content = cursor.getString(1) ?: ""
                                verseList = content
                                    .split(Regex("\\[\\d+\\]"))
                                    .filter { it.isNotBlank() }
                                    .map { it.trim() }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getVersesByPage - query surah $surahId error: ${e.message}", e)
                    }
                    name to verseList
                }

                // Validasi index ayat (ayahId berbasis 1)
                if (ayahId >= 1 && ayahId <= allVerses.size) {
                    verses.add(
                        Verse(
                            surahId  = surahId,
                            ayahId   = ayahId,
                            text     = allVerses[ayahId - 1],
                            surahName = surahName
                        )
                    )
                } else {
                    Log.w(TAG, "getVersesByPage: ayahId $ayahId di luar range surah $surahId (total: ${allVerses.size})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVersesByPage - query main db error: ${e.message}", e)
        }

        return verses
    }

    // ─── VERSE RANDOM ────────────────────────────────────────────────────────────

    fun getRandomVerse(): Pair<String, String> {
        return try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT name_ar, content FROM chapters ORDER BY RANDOM() LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameAr  = cursor.getString(0) ?: "Surah"
                    val content = cursor.getString(1) ?: ""
                    val verses  = content.split(VERSE_SPLIT_REGEX)
                        .filter { it.isNotBlank() }.map { it.trim() }
                    if (verses.isNotEmpty()) nameAr to verses.random()
                    else "Surah" to "Ayat"
                } else {
                    "Surah" to "Ayat"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRandomVerse error: ${e.message}", e)
            "Surah" to "Ayat"
        }
    }

    fun getRandomVerseSet(): Pair<String, List<String>> {
        return try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT name_ar, content FROM chapters ORDER BY RANDOM() LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameAr  = cursor.getString(0) ?: "Surah"
                    val content = cursor.getString(1) ?: ""
                    val verses  = content.split(VERSE_SPLIT_REGEX)
                        .filter { it.isNotBlank() }.map { it.trim() }
                    if (verses.isNotEmpty()) {
                        val start  = (0 until verses.size).random()
                        val target = mutableListOf<String>()
                        var totalWords = 0
                        for (i in start until minOf(start + 5, verses.size)) {
                            val verseWords = verses[i].split(" ").size
                            // Batas 50 kata total — tidak lebani UI
                            if (totalWords + verseWords > MAX_WORDS_PER_SESSION) break
                            target.add(verses[i])
                            totalWords += verseWords
                            // Juga berhenti jika satu ayat sudah panjang
                            if (verseWords > LONG_VERSE_THRESHOLD) break
                        }
                        // Jika tidak ada yang lolos (1 ayat > 50 kata), ambil saja 1 ayat itu
                        if (target.isEmpty()) target.add(verses[start])
                        nameAr to target
                    } else {
                        "Surah" to emptyList()
                    }
                } else {
                    "Surah" to emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRandomVerseSet error: ${e.message}", e)
            "Surah" to emptyList()
        }
    }

    fun getRandomShortVerseJuz30(): Pair<String, String> {
        return try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT name_ar, content FROM chapters WHERE id >= 78 ORDER BY RANDOM()",
                null
            ).use { cursor ->
                var found: Pair<String, String>? = null
                while (cursor.moveToNext() && found == null) {
                    val nameAr  = cursor.getString(0) ?: continue
                    val content = cursor.getString(1) ?: continue
                    val verses  = content.split(VERSE_SPLIT_REGEX)
                        .filter { it.isNotBlank() }.map { it.trim() }
                    val short   = verses.filter { it.split(" ").size in 3..6 }
                    if (short.isNotEmpty()) {
                        found = nameAr to short.random()
                    }
                }
                found ?: getRandomVerse()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRandomShortVerseJuz30 error: ${e.message}", e)
            getRandomVerse()
        }
    }

    // ─── SAMBUNG AYAT ─────────────────────────────────────────────────────────

    /**
     * Ambil semua ayat dari satu surah berdasarkan ID.
     * Return: Pair(nameAr, List<String> ayat)
     */
    fun getVersesFromSurah(surahId: Int): Pair<String, List<String>> {
        return try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT name_ar, content FROM chapters WHERE id = ?",
                arrayOf(surahId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameAr  = cursor.getString(0) ?: "Surah"
                    val content = cursor.getString(1) ?: ""
                    val verses  = content.split(VERSE_SPLIT_REGEX)
                        .filter { it.isNotBlank() }.map { it.trim() }
                    nameAr to verses
                } else {
                    "Surah" to emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVersesFromSurah error: ${e.message}", e)
            "Surah" to emptyList()
        }
    }

    /**
     * Ambil ayat dari surah tertentu untuk kuis susun kata.
     * - Hanya hitung token yang mengandung huruf Arab asli (abaikan tanda waqf/ayat)
     * - Range kata diperlebar agar tidak terlalu ketat untuk surat panjang
     * [fromAyah] dan [toAyah] adalah 1-based index, inklusif.
     * [maxCount] jumlah ayat yang diambil.
     */
    fun getQuizVerses(
        surahId: Int,
        fromAyah: Int = 1,
        toAyah: Int = Int.MAX_VALUE,
        maxCount: Int = 10
    ): Pair<String, List<String>> {
        return try {
            val (nameAr, allVerses) = getVersesFromSurah(surahId)
            if (allVerses.isEmpty()) return "Surah" to emptyList()

            val endIdx   = minOf(toAyah, allVerses.size)
            val startIdx = (fromAyah - 1).coerceIn(0, endIdx - 1)
            val rangeVerses = allVerses.subList(startIdx, endIdx)

            // Hitung kata Arab valid: token yang mengandung minimal 1 huruf Arab asli
            // Range dipersempit ke huruf saja — exclude harakat (U+064B-065F),
            // tanda waqf (U+06D4-06ED), angka Arab, dan simbol non-huruf
            fun arabWordCount(verse: String): Int =
                verse.split(Regex("\\s+")).count { token ->
                    token.any { c ->
                        c.code in 0x0621..0x063A ||  // Huruf Arab dasar
                                c.code in 0x0641..0x064A ||  // Huruf Arab lanjutan
                                c.code in 0x0671..0x06D3     // Huruf Arab extended
                    }
                }

            // Filter: 2-15 kata Arab valid
            // Range lebih lebar agar surat panjang tetap punya cukup soal
            val suitable = rangeVerses.filter { arabWordCount(it) in 2..15 }
            val pool = if (suitable.isNotEmpty()) suitable else rangeVerses

            val selected = pool.shuffled().take(maxCount)
            nameAr to selected
        } catch (e: Exception) {
            Log.e(TAG, "getQuizVerses error: ${e.message}", e)
            "Surah" to emptyList()
        }
    }

    // ─── NORMALISASI ─────────────────────────────────────────────────────────────

    fun normalizeArabic(text: String): String {
        return text
            .replace(HARAKAT_REGEX, "")
            .replace(ALEF_REGEX, "ا")
            .replace(YA_REGEX, "ي")
            .replace("ة", "ه")
            .replace("ک", "ك")
            .replace(PUNCT_REGEX, "")
            .replace(SPACES_REGEX, " ")
            .trim()
    }
}