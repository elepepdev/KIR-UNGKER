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
        try {
            val apiKey = BuildConfig.ANTHROPIC_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "API Key is missing")
                return@withContext fallbackResponse()
            }

            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.doOutput = true

            val systemPrompt = """
                Balas dalam 1-2 kalimat Bahasa Indonesia yang hangat, lucu kalau perlu, dan selalu akhiri dengan pengingat lembut tentang ngaji atau sholat.
                Jangan ceramah. Jangan lebay.
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
        return@withContext fallbackResponse()
    }

    private fun fallbackResponse(): String {
        val fallbacks = listOf(
            "Jangan lupa ngaji dan sholat ya! Semangat! 🌟",
            "Hehe, boleh kok istirahat bentar, tapi jangan kelamaan ya. Jangan lupa sholat! 😊",
            "Semangat langkahmu! Nanti kalau sudah selesai, jangan lupa ngaji ya. ✨",
            "Wah, mau santai dulu ya? Oke deh, tapi ingat waktu ya, jangan sampai kelewat waktu sholatnya. 🕌",
            "Istirahatnya sebentar saja ya, mari kembali untuk melanjutkan hal baik lainnya. Semangat! 💪",
            "Hehe, bosen ya? Gak apa-apa kok, tapi habis ini coba baca satu lembar Al-Quran, pasti hati jadi tenang. 📖",
            "Scrolling-nya jangan jauh-jauh ya, nanti tersesat lho. Ingat sholat itu utama! 🌟",
            "Boleh istirahat sejenak, asalkan jangan lupa kewajiban sholat dan ngajinya ya. 😊",
            "Istirahat itu perlu, tapi ingat ya, dunia digital gak ada habisnya. Jangan lupa ngaji! ✨",
            "Silakan istirahat sejenak, tapi berjanji ya habis ini kita tadarus bersama? Oke? 😉",
            "Jangan sampai keasyikan beraktivitas sampai lupa sholat ya. Kami ingatkan! 🔔",
            "Boleh beraktivitas, tapi jika sudah adzan, segera hentikan dan tunaikan kewajiban. Luar biasa! 🌟",
            "Istirahat sebentar untuk cari ide ya? Habis itu lanjut tadarus lagi, oke? Kami dukung! 💪",
            "Semoga harimu menyenangkan! Akan lebih bermakna jika kita tunaikan sholat tepat waktu. Mari coba! 😊",
            "Jangan lupa istirahat sejenak! Dan jangan lupa tunaikan sholat juga pas waktunya tiba. ✨",
            "Santai sejenak boleh banget, tapi jangan sampai lalai sama komitmenmu ya. Semangat terus! 📖",
            "Lakukan aktivitas secukupnya, tadarus sebanyak-banyaknya. Itu baru individu yang hebat! 🌟",
            "Kami senang kamu jujur alasannya. Sekarang istirahat sebentar ya, nanti kita lanjut tadarus. 😊",
            "Ingat ya, gadget itu cuma alat, tapi sholat itu tujuan hidup. Jangan terbalik ya! ✨",
            "Nikmati harimu dengan lebih ringan! Terus jangan lupa tunaikan sholat ya. 😊",
            "Istirahat untuk refreshing? Oke! Tapi jangan lupa lapor pada Allah lewat sholat ya kalau sudah waktunya. 🕌",
            "Wah, semangat banget aktivitasmu. Harus lebih semangat lagi pas waktunya ibadah nanti ya! 🌟",
            "Istirahat yang berkualitas itu bukan cuma beraktivitas di gawai, tapi juga menunaikan sholat. Coba rasakan bedanya! ✨"
        )
        return fallbacks.random()
    }
}
