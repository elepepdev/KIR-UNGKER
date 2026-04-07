package com.dame.ungker.ungkeh

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.File
import kotlin.random.Random

data class Chapter(val id: Int, val name: String, val nameLatin: String, val content: String)

data class ChapterMeta(
    val id        : Int,
    val name      : String,
    val nameLatin : String,
    val verseCount: Int
)

data class Verse(val surahId: Int, val ayahId: Int, val text: String, val surahName: String)

class QuranDatabaseHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "QuranDBHelper"
        private const val DATABASE_NAME = "quran.sqlite"
        private const val PAGES_DATABASE_NAME = "quranpages.sqlite"
        private const val DATABASE_VERSION = 2
        private const val LONG_VERSE_THRESHOLD = 12

        private val HARAKAT_REGEX     = Regex("[\u0640\u064B-\u0652\u0670\u06D6-\u06ED]")
        private val ALEF_REGEX        = Regex("[\u0623\u0625\u0622\u0623]") 
        private val YA_REGEX          = Regex("[\u0649\u064A]") 
        private val PUNCT_REGEX       = Regex("[\u060C\u061B\u061F,.!?;:]")
        private val SPACES_REGEX      = Regex("\\s+")
        private val VERSE_SPLIT_REGEX = Regex("\\[\\d+\\]")

        private val MUQATTAAT_MAP = mapOf(
            "ألف" to "ا", "الف" to "ا", "لام" to "ل", "ميم" to "م", "صاد" to "ص", "ص" to "ص",
            "عين" to "ع", "قاف" to "ق", "كاف" to "ك", "هاء" to "ه", "ها" to "ه",
            "ياء" to "ي", "يا" to "ي", "طاء" to "ط", "طا" to "ط", "سين" to "س",
            "حاء" to "ح", "حا" to "ح", "راء" to "ر", "ra" to "ر"
        )

        val SURAH_JUZ_START = (78..114).associateWith { 1 }
    }

    @Synchronized
    private fun prepareAssets() {
        copyDb(DATABASE_NAME)
        copyDb(PAGES_DATABASE_NAME)
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        prepareAssets()
        return super.getReadableDatabase()
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        prepareAssets()
        return super.getWritableDatabase()
    }

    private fun copyDb(dbName: String) {
        val dbPath = appContext.getDatabasePath(dbName)
        val sp = appContext.getSharedPreferences("quran_db_prefs", Context.MODE_PRIVATE)
        val lastVersion = sp.getInt("${dbName}_version", 0)

        if (!dbPath.exists() || lastVersion < DATABASE_VERSION) {
            try {
                dbPath.parentFile?.mkdirs()
                appContext.assets.open(dbName).use { inputStream ->
                    FileOutputStream(dbPath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                sp.edit { putInt("${dbName}_version", DATABASE_VERSION) }
                Log.d(TAG, "Database $dbName berhasil disalin.")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal copy database $dbName: ${e.message}", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    private fun openPagesDb(): SQLiteDatabase? {
        return try {
            prepareAssets()
            val pagesDbPath = appContext.getDatabasePath(PAGES_DATABASE_NAME)
            if (!pagesDbPath.exists()) return null
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

    fun getAllChaptersMeta(): List<ChapterMeta> {
        val chapters = mutableListOf<ChapterMeta>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, name_ar, name_pron_en, verses_number FROM chapters ORDER BY id ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    chapters.add(ChapterMeta(cursor.getInt(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "", cursor.getInt(3).coerceAtLeast(1)))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getAllChaptersMeta error: ${e.message}", e) }
        return chapters
    }

    fun getVersesByPage(pageNumber: Int): List<Verse> {
        val verses = mutableListOf<Verse>()
        if (pageNumber < 1 || pageNumber > 604) return verses
        val mapping = mutableListOf<Pair<Int, Int>>()
        val pagesDb = openPagesDb() ?: return verses

        try {
            pagesDb.rawQuery("SELECT soraid, ayaid FROM ayarects WHERE page = ? GROUP BY soraid, ayaid ORDER BY MIN(rowid) ASC", arrayOf(pageNumber.toString())).use { cursor ->
                while (cursor.moveToNext()) mapping.add(cursor.getInt(0) to cursor.getInt(1))
            }
        } catch (e: Exception) { Log.e(TAG, "getVersesByPage error: ${e.message}", e) }
        finally { try { pagesDb.close() } catch (_: Exception) {} }

        if (mapping.isEmpty()) return verses
        val surahCache = mutableMapOf<Int, Pair<String, List<String>>>()

        try {
            val mainDb = readableDatabase
            mapping.forEach { (surahId, ayahId) ->
                val (surahName, allVerses) = surahCache.getOrPut(surahId) {
                    var name = ""; var verseList = emptyList<String>()
                    mainDb.rawQuery("SELECT name_ar, content FROM chapters WHERE id = ?", arrayOf(surahId.toString())).use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0) ?: ""
                            verseList = (cursor.getString(1) ?: "").split(VERSE_SPLIT_REGEX).filter { it.isNotBlank() }.map { it.trim() }
                        }
                    }
                    name to verseList
                }
                if (ayahId >= 1 && ayahId <= allVerses.size) {
                    verses.add(Verse(surahId, ayahId, allVerses[ayahId - 1], surahName))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getVersesByPage - main db error: ${e.message}", e) }
        return verses
    }

    fun getQuizVerses(surahId: Int, fromAyah: Int = 1, toAyah: Int = Int.MAX_VALUE, maxCount: Int = 10): Pair<String, List<String>> {
        return try {
            val (nameAr, allVerses) = getVersesFromSurah(surahId)
            if (allVerses.isEmpty()) return "Surah" to emptyList()
            val endIdx = minOf(toAyah, allVerses.size)
            if (endIdx < 1) return nameAr to emptyList()
            val startIdx = (fromAyah - 1).coerceIn(0, endIdx - 1)
            val rangeVerses = allVerses.subList(startIdx, endIdx)
            fun arabWordCount(verse: String): Int = verse.split(Regex("\\s+")).count { token -> token.any { c -> c.code in 0x0621..0x064A } }
            val selected = rangeVerses.filter { arabWordCount(it) in 2..15 }.shuffled().take(maxCount)
            nameAr to if (selected.isEmpty()) rangeVerses.take(maxCount) else selected
        } catch (e: Exception) { "Surah" to emptyList() }
    }

    fun getVersesFromSurah(surahId: Int): Pair<String, List<String>> {
        return try {
            val db = readableDatabase
            db.rawQuery("SELECT name_ar, content FROM chapters WHERE id = ?", arrayOf(surahId.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val verses = (cursor.getString(1) ?: "").split(VERSE_SPLIT_REGEX).filter { it.isNotBlank() }.map { it.trim() }
                    (cursor.getString(0) ?: "Surah") to verses
                } else "Surah" to emptyList()
            }
        } catch (e: Exception) { "Surah" to emptyList() }
    }

    fun normalizeArabic(text: String): String {
        return text.replace(HARAKAT_REGEX, "").replace(ALEF_REGEX, "ا").replace(YA_REGEX, "ي").replace("ة", "ه").replace("ک", "ك").replace(PUNCT_REGEX, "").replace(SPACES_REGEX, " ").trim()
    }

    suspend fun getRandomShortVerseJuz30(): Pair<String, String> = withContext(Dispatchers.IO) {
        val allVersesInJuz30 = mutableListOf<Pair<String, String>>()
        for (surahId in SURAH_JUZ_START.keys) {
            val (surahName, verses) = getVersesFromSurah(surahId)
            verses.forEach { verseText ->
                if (verseText.split(SPACES_REGEX).count { it.isNotBlank() } < LONG_VERSE_THRESHOLD) {
                    allVersesInJuz30.add(surahName to verseText)
                }
            }
        }
        return@withContext allVersesInJuz30.randomOrNull() ?: getRandomVerse()
    }

    suspend fun getRandomVerseSet(): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        val allChapters = getAllChaptersMeta()
        if (allChapters.isEmpty()) return@withContext Pair("Surah", emptyList())
        val randomSurah = allChapters.random()
        val (surahName, allVerses) = getVersesFromSurah(randomSurah.id)
        if (allVerses.isEmpty()) return@withContext Pair(surahName, emptyList())
        val versesSet = allVerses.shuffled().take(Random.nextInt(3, 6).coerceAtMost(allVerses.size))
        return@withContext Pair(surahName, versesSet)
    }

    suspend fun getRandomVerse(): Pair<String, String> = withContext(Dispatchers.IO) {
        val allChapters = getAllChaptersMeta()
        if (allChapters.isEmpty()) return@withContext Pair("Al-Fatihah", "الحمد لله رب العالمين")
        val randomSurah = allChapters.random()
        val (surahName, allVerses) = getVersesFromSurah(randomSurah.id)
        if (allVerses.isEmpty()) return@withContext Pair(surahName, "Tidak ada ayat.")
        return@withContext Pair(surahName, allVerses.random())
    }
}
