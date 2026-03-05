package com.dame.ungker.ungkeh

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class UngkerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "UNGKER_NOTIF"
        val channel = NotificationChannel(channelId, "Ungker Guardian", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("UNGKER Active")
            .setContentText("Monitoring distractions...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        serviceScope.launch {
            while (true) {
                // Ambil data "buku catatan" sukses dari SharedPreferences
                val sharedPref = getSharedPreferences("UNGKER_PREF", Context.MODE_PRIVATE)
                val lastSuccess = sharedPref.getLong("last_success", 0L)
                val currentTime = System.currentTimeMillis()

                // Cek apakah masih dalam masa izin (misal: 60 detik setelah sukses ngaji)
                val isStillAllowed = (currentTime - lastSuccess) < (60 * 1000)

                if (sedangBukaTikTok(this@UngkerService)) {
                    if (!isStillAllowed) {
                        // Jika buka TikTok tapi BELUM ngaji / masa izin habis, tarik ke MainActivity
                        val intentBuka = Intent(this@UngkerService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            // Tambahkan extra supaya MainActivity tahu ini adalah "Interruption Mode"
                            putExtra("is_interruption", true)
                        }
                        startActivity(intentBuka)
                    }
                }
                delay(2000) // Cek setiap 2 detik
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}