package com.ungker.ungkeh.data

import android.content.Context
import com.ungker.ungkeh.QuranDatabaseHelper
import com.ungker.ungkeh.Verse
import com.ungker.ungkeh.ChapterMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuranRepository(context: Context) {
    private val dbHelper = QuranDatabaseHelper(context)

    suspend fun getChaptersMeta(): List<ChapterMeta> = withContext(Dispatchers.IO) {
        dbHelper.getAllChaptersMeta()
    }

    suspend fun getVersesByPage(page: Int): List<Verse> = withContext(Dispatchers.IO) {
        dbHelper.getVersesByPage(page)
    }

    suspend fun getRandomVerse(): Pair<String, String> = withContext(Dispatchers.IO) {
        dbHelper.getRandomVerse()
    }
}
