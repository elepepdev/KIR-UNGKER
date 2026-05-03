package com.ungker.ungkeh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ClaudeApiClient {
    
    suspend fun getWarmResponse(reason: String): String = withContext(Dispatchers.IO) {
        val trimmedReason = reason.trim()
        
        // Validasi input acak/tidak bermakna
        if (!isInputValid(trimmedReason)) {
            return@withContext LocaleManager.L("ai_invalid_input")
        }

        return@withContext getLocalResponse(trimmedReason)
    }

    private fun isInputValid(input: String): Boolean {
        if (input.length < 5) return false
        
        // Cek jika hanya angka acak panjang
        if (input.all { it.isDigit() } && input.length > 5) return false
        
        // Cek keyboard smash
        val words = input.split(" ")
        if (words.any { it.length > 18 }) return false
        
        // Cek rasio vokal
        val vowels = "aeiouAEIOU"
        val vowelCount = input.count { it in vowels }
        if (input.length > 10 && vowelCount.toDouble() / input.length < 0.1) return false
        
        return true
    }

    private fun getLocalResponse(reason: String): String {
        val lowerReason = reason.lowercase()
        
        // Untuk alasan konyol/santai
        val konyol = listOf(
            LocaleManager.L("ai_konyol_1"),
            LocaleManager.L("ai_konyol_2"),
            LocaleManager.L("ai_konyol_3"),
            LocaleManager.L("ai_konyol_4"),
            LocaleManager.L("ai_konyol_5"),
            LocaleManager.L("ai_konyol_6"),
            LocaleManager.L("ai_konyol_7"),
            LocaleManager.L("ai_konyol_8"),
            LocaleManager.L("ai_konyol_9")
        )

        // Untuk alasan serius/curhat
        val serius = listOf(
            LocaleManager.L("ai_serius_1"),
            LocaleManager.L("ai_serius_2"),
            LocaleManager.L("ai_serius_3"),
            LocaleManager.L("ai_serius_4"),
            LocaleManager.L("ai_serius_5"),
            LocaleManager.L("ai_serius_6"),
            LocaleManager.L("ai_serius_7"),
            LocaleManager.L("ai_serius_8"),
            LocaleManager.L("ai_serius_9")
        )

        // Untuk alasan umum/netral
        val umum = listOf(
            LocaleManager.L("ai_umum_1"),
            LocaleManager.L("ai_umum_2"),
            LocaleManager.L("ai_umum_3"),
            LocaleManager.L("ai_umum_4"),
            LocaleManager.L("ai_umum_5"),
            LocaleManager.L("ai_umum_6"),
            LocaleManager.L("ai_umum_7"),
            LocaleManager.L("ai_umum_8"),
            LocaleManager.L("ai_umum_9")
        )

        val konyolKeywords = listOf("gatau", "gabut", "iseng", "kepo", "random", "apa aja", "nggak tahu", "tdk tahu", "hehe", "hanya", "cuma")
        val seriusKeywords = listOf("stres", "sedih", "bosen", "capek", "pusing", "masalah", "curhat", "galau", "lelah", "berat", "penat")

        return when {
            konyolKeywords.any { lowerReason.contains(it) } -> konyol.random()
            seriusKeywords.any { lowerReason.contains(it) } -> serius.random()
            else -> umum.random()
        }
    }
}