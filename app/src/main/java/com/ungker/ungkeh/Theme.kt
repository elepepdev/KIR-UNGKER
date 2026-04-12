package com.ungker.ungkeh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkMode = compositionLocalOf { false }

// ══════════════════════════════════════════════════════════════════════════════
// TOKEN WARNA DARK/LIGHT — Warmer Palette Overhaul
// ══════════════════════════════════════════════════════════════════════════════
object DarkTheme {
    // Background layers
    val bgPage      = Color(0xFF12100E)   // Near black with warm undertone
    val bgCard      = Color(0xFF1C1917)   // Warm stone dark
    val bgCardAlt   = Color(0xFF292524)
    val bgInput     = Color(0xFF1C1917)
    val bgTrack     = Color(0xFF44403C)

    // Teks
    val textPrimary = Color(0xFFFEF3C7)   // Amber tint white
    val textSecond  = Color(0xFFD97706)   // Warm amber
    val textMuted   = Color(0xFF78716C)

    // Warna aksen
    val green       = Color(0xFF52B788)
    val greenDark   = Color(0xFF2D6A4F)
    val greenBg     = Color(0xFF1B4332)
    val amber       = Color(0xFFF59E0B)
    val red         = Color(0xFF9B2226)

    // Border
    val border      = Color(0xFF44403C)
    val divider     = Color(0xFF292524)

    // Canvas / arc
    val arcTrack    = Color(0xFF44403C)
    val sunCircle   = Color(0xFF1C1917)
    val horizon     = Color(0xFF44403C)
}

object LightTheme {
    val bgPage      = Color(0xFFFFF7ED)   // Warm orange-white (Orange 50)
    val bgCard      = Color(0xFFFFFAF0)   // Floral white
    val bgCardAlt   = Color(0xFFFDE68A)   // Soft amber
    val bgInput     = Color.White
    val bgTrack     = Color(0xFFFFEDD5)

    val textPrimary = Color(0xFF451A03)   // Deep warm brown
    val textSecond  = Color(0xFF92400E)   // Rich amber brown
    val textMuted   = Color(0xFFB45309)

    val green       = Color(0xFF2D6A4F)
    val greenDark   = Color(0xFF1B4332)
    val greenBg     = Color(0xFFD8F3DC)
    val amber       = Color(0xFFD97706)
    val red         = Color(0xFFBC4749)

    val border      = Color(0xFFFED7AA)
    val divider     = Color(0xFFFFEDD5)

    val arcTrack    = Color(0xFFFFEDD5)
    val sunCircle   = Color.White
    val horizon     = Color(0xFFFFE4E1)
}

/** Shortcut: ambil token warna sesuai mode saat ini */
@Composable
fun cardBg()      = if (LocalIsDarkMode.current) DarkTheme.bgCard      else LightTheme.bgCard
@Composable
fun cardAltBg()   = if (LocalIsDarkMode.current) DarkTheme.bgCardAlt   else LightTheme.bgCardAlt
@Composable
fun pageBg()      = if (LocalIsDarkMode.current) DarkTheme.bgPage      else LightTheme.bgPage
@Composable
fun textPrimC()   = if (LocalIsDarkMode.current) DarkTheme.textPrimary else LightTheme.textPrimary
@Composable
fun textSecC()    = if (LocalIsDarkMode.current) DarkTheme.textSecond  else LightTheme.textSecond
@Composable
fun textMutC()    = if (LocalIsDarkMode.current) DarkTheme.textMuted   else LightTheme.textMuted
@Composable
fun trackBg()     = if (LocalIsDarkMode.current) DarkTheme.bgTrack     else LightTheme.bgTrack
@Composable
fun greenAccent() = if (LocalIsDarkMode.current) DarkTheme.green       else LightTheme.green
@Composable
fun greenBgC()    = if (LocalIsDarkMode.current) DarkTheme.greenBg     else LightTheme.greenBg
@Composable
fun dividerC()    = if (LocalIsDarkMode.current) DarkTheme.divider     else LightTheme.divider
@Composable
fun borderC()     = if (LocalIsDarkMode.current) DarkTheme.border      else LightTheme.border
