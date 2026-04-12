package com.ungker.ungkeh

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

data class PrayerTimes(
    val subuh: String,
    val syuruq: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String,
    val syuruqDecimal: Double,   // untuk arc matahari
    val maghribDecimal: Double,  // untuk arc matahari
    val nowDecimal: Double,      // jam sekarang dalam desimal
)

data class CityLocation(val name: String, val lat: Double, val lng: Double, val tzOffset: Int, val province: String)

/** 43 kota besar Indonesia dengan koordinat akurat */
val INDONESIA_CITIES: List<CityLocation> = listOf(
    CityLocation("Banda Aceh",      5.5483,  95.3238, 7, "Aceh"),
    CityLocation("Medan",           3.5952,  98.6722, 7, "Sumatera Utara"),
    CityLocation("Padang",         -0.9471, 100.4172, 7, "Sumatera Barat"),
    CityLocation("Pekanbaru",       0.5335, 101.4474, 7, "Riau"),
    CityLocation("Batam",           1.0746, 104.0305, 7, "Kepulauan Riau"),
    CityLocation("Jambi",          -1.6102, 103.6131, 7, "Jambi"),
    CityLocation("Palembang",      -2.9761, 104.7754, 7, "Sumatera Selatan"),
    CityLocation("Bengkulu",       -3.7928, 102.2608, 7, "Bengkulu"),
    CityLocation("Bandar Lampung", -5.3971, 105.2668, 7, "Lampung"),
    CityLocation("Pangkalpinang",  -2.1337, 106.1164, 7, "Bangka Belitung"),
    CityLocation("Jakarta",        -6.2088, 106.8456, 7, "DKI Jakarta"),
    CityLocation("Bogor",          -6.5971, 106.8060, 7, "Jawa Barat"),
    CityLocation("Bandung",        -6.9175, 107.6191, 7, "Jawa Barat"),
    CityLocation("Bekasi",         -6.2383, 106.9756, 7, "Jawa Barat"),
    CityLocation("Depok",          -6.4025, 106.7942, 7, "Jawa Barat"),
    CityLocation("Tangerang",      -6.1783, 106.6319, 7, "Banten"),
    CityLocation("Serang",         -6.1204, 106.1503, 7, "Banten"),
    CityLocation("Semarang",       -6.9932, 110.4203, 7, "Jawa Tengah"),
    CityLocation("Solo",           -7.5642, 110.8317, 7, "Jawa Tengah"),
    CityLocation("Yogyakarta",     -7.7956, 110.3695, 7, "DI Yogyakarta"),
    CityLocation("Surabaya",       -7.2575, 112.7521, 7, "Jawa Timur"),
    CityLocation("Malang",         -7.9654, 112.6326, 7, "Jawa Timur"),
    CityLocation("Kediri",         -7.8480, 112.0178, 7, "Jawa Timur"),
    CityLocation("Denpasar",       -8.6705, 115.2126, 8, "Bali"),
    CityLocation("Mataram",        -8.5833, 116.1167, 8, "NTB"),
    CityLocation("Kupang",        -10.1789, 123.6070, 8, "NTT"),
    CityLocation("Pontianak",       0.0263, 109.3425, 7, "Kalimantan Barat"),
    CityLocation("Palangkaraya",   -2.2136, 113.9108, 7, "Kalimantan Tengah"),
    CityLocation("Banjarmasin",    -3.3194, 114.5908, 8, "Kalimantan Selatan"),
    CityLocation("Samarinda",      -0.5022, 117.1536, 8, "Kalimantan Timur"),
    CityLocation("Balikpapan",     -1.2654, 116.8312, 8, "Kalimantan Timur"),
    CityLocation("Tarakan",         3.3013, 117.5783, 8, "Kalimantan Utara"),
    CityLocation("Makassar",       -5.1477, 119.4327, 8, "Sulawesi Selatan"),
    CityLocation("Parepare",       -4.0135, 119.6298, 8, "Sulawesi Selatan"),
    CityLocation("Kendari",        -3.9985, 122.5127, 8, "Sulawesi Tenggara"),
    CityLocation("Palu",           -0.8917, 119.8707, 8, "Sulawesi Tengah"),
    CityLocation("Gorontalo",       0.5435, 123.0600, 8, "Gorontalo"),
    CityLocation("Manado",          1.4748, 124.8421, 8, "Sulawesi Utara"),
    CityLocation("Ternate",         0.7833, 127.3667, 9, "Maluku Utara"),
    CityLocation("Ambon",          -3.6954, 128.1814, 9, "Maluku"),
    CityLocation("Sorong",         -0.8833, 131.2500, 9, "Papua Barat"),
    CityLocation("Manokwari",      -0.8615, 134.0650, 9, "Papua Barat Daya"),
    CityLocation("Jayapura",       -2.5337, 140.7181, 9, "Papua"),
)

/**
 * Menghitung waktu sholat sesuai metode Kemenag RI.
 * @param year, month, day  — tanggal Masehi
 * @param lat, lng           — koordinat desimal (negatif = S/W)
 * @param tz                 — offset zona waktu dari UTC (WIB=7, WITA=8, WIT=9)
 * @param fajrAngle          — sudut Subuh (20° sesuai Kemenag)
 * @param ishaAngle          — sudut Isya  (18° sesuai Kemenag)
 * @param ihtiyat            — ihtiyat/kehati-hatian dalam menit (default 2)
 */
fun hitungWaktuSholat(
    year: Int, month: Int, day: Int,
    lat: Double, lng: Double, tz: Int,
    fajrAngle: Double = 20.0, ishaAngle: Double = 18.0, ihtiyat: Int = 2
): PrayerTimes {
    // ── 1. Julian Day Number ─────────────────────────────────────────────────
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 12 else month
    val a = y / 100
    val b = 2 - a + a / 4
    val jd = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toInt() + day + b - 1524.5

    // ── 2. Julian Century ────────────────────────────────────────────────────
    val t = (jd - 2451545.0) / 36525.0

    // ── 3. Posisi Matahari ───────────────────────────────────────────────────
    val l0  = ((280.46646 + 36000.76983 * t + 0.0003032 * t * t) % 360 + 360) % 360
    val msr = ((357.52911 + 35999.05029 * t - 0.0001537 * t * t) % 360).toRadians()
    val c   = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(msr) +
            (0.019993 - 0.000101 * t) * sin(2 * msr) + 0.000289 * sin(3 * msr)
    val sunLon  = l0 + c
    val omega   = (125.04 - 1934.136 * t).toRadians()
    val lam     = (sunLon - 0.00569 - 0.00478 * sin(omega)).toRadians()

    // ── 4. Deklinasi & Equation of Time ─────────────────────────────────────
    val eps0 = 23 + 26.0 / 60 + 21.448 / 3600 - (46.8150 / 3600) * t - (0.00059 / 3600) * t * t + (0.001813 / 3600) * t * t * t
    val eps  = (eps0 + 0.00256 * cos(omega)).toRadians()
    val dec  = asin(sin(eps) * sin(lam))   // deklinasi matahari (radian)

    val yy   = tan(eps / 2).pow(2)
    val l0r  = l0.toRadians()
    val e    = 0.016708634 - 0.000042037 * t
    val eqT  = 4 * Math.toDegrees(
        yy * sin(2 * l0r) - 2 * e * sin(msr) + 4 * e * yy * sin(msr) * cos(2 * l0r) -
                0.5 * yy * yy * sin(4 * l0r) - 1.25 * e * e * sin(2 * msr)
    )  // dalam menit

    // ── 5. Transit (Dhuhur) ─────────────────────────────────────────────────
    val transit = 12.0 + tz - lng / 15.0 - eqT / 60.0

    val latR = lat.toRadians()

    fun hourAngle(elevDeg: Double): Double {
        val cosHA = (sin(elevDeg.toRadians()) - sin(latR) * sin(dec)) / (cos(latR) * cos(dec))
        return Math.toDegrees(acos(cosHA.coerceIn(-1.0, 1.0))) / 15.0
    }

    // ── 6. Waktu-waktu sholat ────────────────────────────────────────────────
    val tSubuh   = transit - hourAngle(-fajrAngle)
    val tSyuruq  = transit - hourAngle(-0.8333)
    val asrElev  = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(latR - dec)))))
    val tAshar   = transit + hourAngle(asrElev)
    val tMaghrib = transit + hourAngle(-0.8333)
    val tIsya    = transit + hourAngle(-ishaAngle)

    // ── 7. Format ke HH:MM dengan ihtiyat ───────────────────────────────────
    fun Double.toHHMM(addIhtiyat: Boolean = true): String {
        val total = this + if (addIhtiyat) ihtiyat / 60.0 else 0.0
        val h = total.toInt() % 24
        var mn = ((total % 1.0) * 60.0).let { round(it).toInt() }
        val hFinal = if (mn == 60) { mn = 0; (h + 1) % 24 } else h
        return String.format(Locale.US, "%02d:%02d", hFinal, mn)
    }

    // Jam sekarang dalam desimal lokal
    val cal = Calendar.getInstance()
    val hourNow = cal.get(Calendar.HOUR_OF_DAY)
    val minNow = cal.get(Calendar.MINUTE)
    val secNow = cal.get(Calendar.SECOND)
    val nowDecimal = hourNow.toDouble() + minNow / 60.0 + secNow / 3600.0

    return PrayerTimes(
        subuh   = tSubuh.toHHMM(),
        syuruq  = tSyuruq.toHHMM(false),
        dzuhur  = transit.toHHMM(),
        ashar   = tAshar.toHHMM(),
        maghrib = tMaghrib.toHHMM(),
        isya    = tIsya.toHHMM(),
        syuruqDecimal  = tSyuruq + ihtiyat / 60.0,
        maghribDecimal = tMaghrib + ihtiyat / 60.0,
        nowDecimal     = nowDecimal,
    )
}

private fun Double.toRadians(): Double = this * PI / 180.0

/** Tentukan sholat berikutnya berdasarkan waktu sekarang */
fun getNextPrayer(pt: PrayerTimes): Pair<String, String> {
    fun toDouble(hhmm: String): Double {
        val parts = hhmm.split(":"); return parts[0].toDouble() + parts[1].toDouble() / 60.0
    }
    val now = pt.nowDecimal
    val prayers = listOf(
        "Subuh" to toDouble(pt.subuh), "Dzuhur" to toDouble(pt.dzuhur),
        "Ashar" to toDouble(pt.ashar), "Maghrib" to toDouble(pt.maghrib),
        "Isya" to toDouble(pt.isya),
    )
    val next = prayers.firstOrNull { it.second > now } ?: prayers.first()
    val diffMin = ((next.second - now + 24) % 24 * 60).toInt()
    val label = if (diffMin < 60) "Menuju ${next.first} ($diffMin menit lagi)" else "Menuju ${next.first}"
    return next.first to label
}

/** Ambil lokasi GPS dari LocationManager */
fun getGpsLocation(
    context: Context,
    onSuccess: (lat: Double, lng: Double, tz: Int, label: String) -> Unit,
    onFail: () -> Unit
) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) { onFail(); return }

        // Tentukan timezone dari longitude secara otomatis (untuk Indonesia)
        fun tzFromLng(lng: Double): Int = when {
            lng < 115.0 -> 7   // WIB
            lng < 135.0 -> 8   // WITA
            else        -> 9   // WIT
        }

        // Coba last known location dulu (sangat cepat, tidak perlu tunggu)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var bestLocation: Location? = null
        for (p in providers) {
            if (lm.isProviderEnabled(p)) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }
        }
        if (bestLocation != null) {
            val tz = tzFromLng(bestLocation.longitude)
            val label = findNearestCity(bestLocation.latitude, bestLocation.longitude)
            onSuccess(bestLocation.latitude, bestLocation.longitude, tz, label)
            return
        }

        // Fallback: minta update aktif
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { onFail(); return }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                lm.removeUpdates(this)   // hapus langsung setelah dapat 1 lokasi
                val tz    = tzFromLng(loc.longitude)
                val label = findNearestCity(loc.latitude, loc.longitude)
                onSuccess(loc.latitude, loc.longitude, tz, label)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) { onFail() }
        }
        @Suppress("MissingPermission")
        lm.requestLocationUpdates(provider, 0L, 0f, listener)
    } catch (_: SecurityException) { onFail() }
    catch (_: Exception)           { onFail() }
}

/** Cari nama kota terdekat dari koordinat GPS */
fun findNearestCity(lat: Double, lng: Double): String {
    var nearest = INDONESIA_CITIES[10]  // default Jakarta
    var minDist = Double.MAX_VALUE
    for (city in INDONESIA_CITIES) {
        val d = sqrt((lat - city.lat).pow(2) + (lng - city.lng).pow(2))
        if (d < minDist) { minDist = d; nearest = city }
    }
    return nearest.name
}
