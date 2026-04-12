package com.ungker.ungkeh

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CreditUtils {
    const val MAX_CREDIT_MS = 3_600_000L // 1 jam limit

    /**
     * Tambah kredit waktu ke SharedPreferences.
     * @param context Context
     * @param amountMs Jumlah milidetik yang ditambahkan
     * @param verseCount Jumlah ayat yang diselesaikan (untuk statistik)
     */
    fun addCredit(context: Context, amountMs: Long, verseCount: Int = 0) {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        
        // Ensure reset happens before update
        checkDailyReset(context)
        
        val currentCredit = sp.getLong("remaining_credit", 0L)
        val newCredit = minOf(MAX_CREDIT_MS, currentCredit + amountMs)

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        sp.edit {
            putLong("remaining_credit", newCredit)
            
            if (verseCount > 0) {
                val totalVerses = sp.getInt("total_verses", 0)
                val dailyVerses = sp.getInt("daily_verses", 0)
                
                putInt("total_verses", totalVerses + verseCount)
                putInt("daily_verses", dailyVerses + verseCount)
                putString("last_verse_date", todayStr)
            }

            // Update Focus Time
            val currentFocusMs = sp.getLong("focus_time_today_ms", 0L)
            putLong("focus_time_today_ms", currentFocusMs + amountMs)
            putString("last_focus_date", todayStr)
            
            // Set timestamp dismiss agar service masuk grace period
            putLong("lock_dismissed_at", System.currentTimeMillis())
        }
    }

    /**
     * Hanya update statistik bacaan tanpa menambah kredit waktu.
     */
    fun updateStatsOnly(context: Context, verseCount: Int) {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        
        // Ensure reset happens before update
        checkDailyReset(context)
        
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        sp.edit {
            if (verseCount > 0) {
                val totalVerses = sp.getInt("total_verses", 0)
                val dailyVerses = sp.getInt("daily_verses", 0)
                
                putInt("total_verses", totalVerses + verseCount)
                putInt("daily_verses", dailyVerses + verseCount)
                putString("last_verse_date", todayStr)
            }
        }
    }

    /**
     * Memastikan statistik harian di-reset jika hari sudah berganti.
     */
    fun checkDailyReset(context: Context) {
        val sp = context.getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val lastVerseDate = sp.getString("last_verse_date", "") ?: ""
        val lastFocusDate = sp.getString("last_focus_date", "") ?: ""

        sp.edit {
            if (lastVerseDate != todayStr && lastVerseDate.isNotEmpty()) {
                putInt("daily_verses", 0)
                // We keep last_verse_date empty or old until a new verse is read, 
                // but setting it to today is safer to avoid multiple resets
                putString("last_verse_date", todayStr)
            }
            if (lastFocusDate != todayStr && lastFocusDate.isNotEmpty()) {
                putLong("focus_time_today_ms", 0L)
                putString("last_focus_date", todayStr)
            }
        }
    }
}
