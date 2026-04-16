package com.ungker.ungkeh

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ClaudeApiClient {
    private const val TAG = "ClaudeApiClient"
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    
    suspend fun getWarmResponse(reason: String): String = withContext(Dispatchers.IO) {
        val trimmedReason = reason.trim()
        
        // Validasi input acak/tidak bermakna
        if (!isInputValid(trimmedReason)) {
            return@withContext "Kalau nulis yang benar kocak"
        }

        try {
            val apiKey = BuildConfig.ANTHROPIC_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "API Key is missing")
                return@withContext fallbackResponse(reason)
            }

            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.doOutput = true

            val systemPrompt = """
                Berlakulah sebagai teman sebaya yang santai dan cerdas, bukan guru atau orang tua. 
                Tanggapi alasan pengguna (reason) dengan nada yang sesuai:
                1. Alasan konyol/absurd/gabut (misal: 'gatau', 'iseng') -> humor ringan + pengingat santai.
                2. Alasan masuk akal/fungsional -> apresiasi singkat + pengingat lembut.
                3. Alasan serius/curhat -> empati dulu, baru ingatkan ibadah.
                
                Ketentuan:
                - Maksimal 2 kalimat. Bahasa Indonesia santai/semi-gaul.
                - JANGAN pakai kata "Kakak", "Adik", "Nak", "kami", "kita".
                - Hindari nada menggurui/khotbah.
                - Akhiri dengan pengingat sholat atau ngaji yang natural.
                - Boleh pakai satu emoji relevan di akhir.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "claude-haiku-4-5-20251001")
                put("max_tokens", 100)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", reason)
                    })
                })
            }

            OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val contentArray = jsonResponse.getJSONArray("content")
                if (contentArray.length() > 0) {
                    return@withContext contentArray.getJSONObject(0).getString("text")
                }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "API Error: $responseCode - $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Claude API", e)
        }
        return@withContext fallbackResponse(reason)
    }

    private fun isInputValid(input: String): Boolean {
        if (input.length < 5) return false
        
        // Cek jika hanya angka acak panjang
        if (input.all { it.isDigit() } && input.length > 5) return false
        
        // Cek keyboard smash (kata terlalu panjang tanpa spasi)
        val words = input.split(" ")
        if (words.any { it.length > 18 }) return false
        
        // Cek rasio vokal (ketikan acak biasanya sedikit vokal)
        val vowels = "aeiouAEIOU"
        val vowelCount = input.count { it in vowels }
        if (input.length > 10 && vowelCount.toDouble() / input.length < 0.1) return false
        
        return true
    }

    private fun fallbackResponse(reason: String): String {
        val lowerReason = reason.lowercase()
        
        // Untuk alasan konyol/santai (9 string)
        val konyol = listOf(
            "Alasan klasik nih, haha. Tapi ya udah, jangan lupa sholat ya! 😂",
            "Random banget, tapi oke lah. Jangan sampai lupa ngaji juga.",
            "Gabutnya produktif dikit lah, sambil dengerin murottal gitu misalnya.",
            "Wkwk ada-ada aja. Tapi tetep, urusan sama Yang Di Atas jangan telat.",
            "Lucu juga alasannya, tapi jangan selucu itu sampai lupa waktu sholat.",
            "Oke, dimaafin karena jujur. Tapi tetep harus sholat tepat waktu ya!",
            "Iya deh si paling random. Inget ngaji ya kalau udah nggak gabut.",
            "Hehe, gila sih. Tapi serius, sholatnya jangan ditinggalin ya.",
            "Hahaha, ya udah sana. Tapi inget, akhirat tetep nomor satu! ☝️"
        )

        // Untuk alasan serius/curhat (9 string)
        val serius = listOf(
            "Lagi berat ya? Semangat! Jangan lupa curhatnya ke Allah lewat sholat.",
            "I feel you. Tarik napas dulu, nanti coba tenangin hati pakai ngaji.",
            "Capek itu wajar, tapi sholat itu yang bikin tenang. Semangat ya!",
            "Butuh jeda ya? Fair. Semoga abis sholat hatinya jadi lebih adem.",
            "Emang kadang hidup sebercanda itu. Sempetin ngaji biar nggak oleng.",
            "Lagi pusing ya? Coba deh wudhu dulu terus sholat, pasti beda rasanya.",
            "Gapapa rehat dulu. Nanti balik lagi dengan versi terbaikmu setelah ngaji.",
            "Stay strong! Jangan sampe masalah dunia bikin kamu jauh dari sholat.",
            "Sabar ya, semua pasti ada jalannya. Yuk, tenangin diri lewat tilawah."
        )

        // Untuk alasan umum/netral (9 string)
        val umum = listOf(
            "Oke, lanjutin aja kalau penting, tapi sholat jangan sampe kelewat ya. 🙏",
            "Siap, asalkan nanti sempetin ngaji bentar biar makin berkah harinya.",
            "Boleh kok, tapi tetep inget waktu ya, jangan lupa ibadah juga.",
            "Gaskeun, asal kewajiban tetep jadi prioritas utama. Semangat!",
            "Fokus selesaikan urusannya ya, terus nanti healing-nya lewat sholat.",
            "Valid sih alasannya. Nanti kalau udah senggang, lanjut tilawah ya.",
            "Sip, yang penting tetep produktif dan nggak lupa waktu sholat. ✨",
            "Oke deh, tapi janji ya habis ini sempetin baca Al-Quran bentar.",
            "Lanjut aja dulu, tapi pastiin hati tetep tenang dengan dzikir atau sholat."
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
