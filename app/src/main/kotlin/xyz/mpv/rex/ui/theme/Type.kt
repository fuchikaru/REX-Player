package xyz.mpv.rex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import xyz.mpv.rex.R
import java.io.File

// Google Sans Flex font family (variable font supporting weights 100-900)
val GoogleSansFlex = FontFamily(
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Thin,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraLight,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Light,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Medium,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.SemiBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Bold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.ExtraBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight.Black,
    style = FontStyle.Normal,
  ),
)

/** Material 3 typography where every text style uses [family]. */
private fun typographyFor(family: FontFamily): Typography = Typography().run {
  copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
  )
}

// Use Google Sans Flex typography app-wide
val AppTypography = typographyFor(GoogleSansFlex)

/**
 * Builds a [FontFamily] from a user-installed font file. The same file is registered for every weight, exactly like
 * [GoogleSansFlex] does, so bold/medium text styles keep resolving to a font. Returns null if [file] can't be used.
 */
@OptIn(ExperimentalTextApi::class)
fun customFontFamilyOrNull(file: File?): FontFamily? {
  if (file == null || !file.isFile) return null
  return runCatching {
    FontFamily(
      listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
      ).map { weight -> Font(file = file, weight = weight, style = FontStyle.Normal) },
    )
  }.getOrNull()
}

/**
 * Typography for the app theme. A custom font chosen in Settings > Appearance > Fonts wins over everything;
 * without one, the existing "Use system font" switch decides between the system font and the bundled Google Sans Flex.
 */
fun getTypography(
  useSystemFont: Boolean,
  customFontFamily: FontFamily? = null,
): Typography =
  when {
    customFontFamily != null -> typographyFor(customFontFamily)
    useSystemFont -> Typography()
    else -> AppTypography
  }
