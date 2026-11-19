package xyz.mpv.rex.utils.media

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Storage for the custom *app UI* font (the font used by the app's own screens).
 *
 * Deliberately separate from `filesDir/fonts`: that directory is mpv's `sub-fonts-dir` and feeds the subtitle font
 * lists, so a UI font dropped there would show up as a subtitle font and be touched by "Clear cached fonts".
 *
 * Only one UI font is kept at a time. Every install is written to a new, unique file name
 * (`app_font_<time>.ttf|otf`), so the preference value changes whenever the font changes and the theme recomposes
 * with the new font even if the user re-imports a file with the same original name.
 */
object AppFontStore {
  private const val DIR_NAME = "app_fonts"
  private const val STAGING_PREFIX = ".appfont_"
  private const val STAGING_SUFFIX = ".part"

  /** Result of installing a font: the stored file name (for the preference) and a human-readable label. */
  data class Installed(
    val storedFileName: String,
    val label: String,
  )

  private fun fontsDir(context: Context): File = File(context.filesDir, DIR_NAME)

  /** The stored font file for [storedFileName], or null if the name is blank or the file is gone. */
  fun fileFor(
    context: Context,
    storedFileName: String,
  ): File? {
    if (storedFileName.isBlank() || File(storedFileName).name != storedFileName) return null
    return File(fontsDir(context), storedFileName).takeIf { it.isFile && it.length() > 0L }
  }

  /** Installs a font picked with the system document picker. */
  suspend fun importFromUri(
    context: Context,
    uri: Uri,
  ): Result<Installed> =
    withContext(Dispatchers.IO) {
      runCatching {
        val label = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "font.ttf"
        install(context, label) { context.contentResolver.openInputStream(uri) }
      }
    }

  /**
   * Downloads a font from [url] and installs it. [label] is the name shown in the UI; the extension is taken from
   * the URL path (`.ttf` / `.otf`).
   */
  suspend fun download(
    context: Context,
    client: OkHttpClient,
    url: String,
    label: String,
  ): Result<Installed> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = url.substringBefore('?').substringAfterLast('/').let { Uri.decode(it) }
        val extension = fileName.substringAfterLast('.', "ttf").lowercase()
        install(context, "$label.$extension") {
          val response = client.newCall(Request.Builder().url(url).build()).execute()
          if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
          }
          response.body.byteStream()
        }
      }
    }

  /** Removes the installed UI font (every file in the directory). */
  suspend fun clear(context: Context) =
    withContext(Dispatchers.IO) {
      fontsDir(context).listFiles()?.forEach { it.delete() }
    }

  // ---------------------------------------------------------------------------------------------

  /**
   * Streams [open] into a staging file, checks that it really is a usable font, then swaps it in. The previous
   * font is removed only AFTER the new one is in place, so a failed import never leaves the app without a font.
   */
  private fun install(
    context: Context,
    displayName: String,
    open: () -> InputStream?,
  ): Installed {
    val extension =
      displayName
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in SUPPORTED_FONT_EXTENSIONS }
        ?: throw IOException("Unsupported font type (only .ttf and .otf)")

    val dir = fontsDir(context)
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create fonts directory")

    val staging = File.createTempFile(STAGING_PREFIX, STAGING_SUFFIX, dir)
    try {
      val input = open() ?: throw IOException("Cannot open font")
      var total = 0L
      input.use { src ->
        staging.outputStream().use { dst ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val read = src.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FONT_FILE_BYTES) throw IOException("Font file is too large")
            dst.write(buffer, 0, read)
          }
        }
      }
      if (total <= 0L) throw IOException("Empty file")
      if (!hasSfntSignature(staging)) throw IOException("Not a valid font file")
      // Make sure Android can actually build a typeface from it (throws on corrupt / unsupported fonts).
      Typeface.createFromFile(staging)

      val target = File(dir, "app_font_${System.currentTimeMillis()}.$extension")
      if (!staging.renameTo(target)) throw IOException("Cannot store font")

      dir.listFiles()?.forEach { if (it != target) it.delete() }
      return Installed(storedFileName = target.name, label = displayName)
    } finally {
      staging.delete() // no-op once renamed
    }
  }

  private fun queryDisplayName(
    context: Context,
    uri: Uri,
  ): String? =
    runCatching {
      context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
