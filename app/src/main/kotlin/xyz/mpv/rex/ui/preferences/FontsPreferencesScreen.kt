package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.MotionStyle
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.AppFontStore

/** A font offered under "Quick font downloads". */
private data class QuickFont(
  /** Shown in the list and used as the stored label. */
  val name: String,
  /** Direct link to a .ttf / .otf file. */
  val url: String,
)

/**
 * Direct download links of the quick fonts. All of them point at the OFL-licensed files of the google/fonts
 * repository; if a link ever moves, only this list needs to change (a bad link just shows "Download failed").
 */
private val QUICK_FONTS =
  listOf(
    QuickFont(
      name = "Google Sans",
      url = "https://github.com/google/fonts/raw/main/ofl/googlesansflex/GoogleSansFlex%5BGRAD%2CROND%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf",
    ),
    QuickFont(
      name = "Lato",
      url = "https://github.com/google/fonts/raw/main/ofl/lato/Lato-Regular.ttf",
    ),
    QuickFont(
      name = "Roboto",
      url = "https://github.com/google/fonts/raw/main/ofl/roboto/Roboto%5Bwdth%2Cwght%5D.ttf",
    ),
    QuickFont(
      name = "Comic Neue",
      url = "https://github.com/google/fonts/raw/main/ofl/comicneue/ComicNeue-Regular.ttf",
    ),
    QuickFont(
      name = "Atkinson Hyperlegible Next",
      url = "https://github.com/google/fonts/raw/main/ofl/atkinsonhyperlegiblenext/AtkinsonHyperlegibleNext%5Bwght%5D.ttf",
    ),
  )

@Serializable
object FontsPreferencesScreen : Screen {
  /** Settings keep the app's original full horizontal push. */
  override val motion: MotionStyle
    get() = MotionStyle.Default

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val okHttpClient = koinInject<OkHttpClient>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customFontFile by preferences.customFontFile.collectAsState()
    val customFontLabel by preferences.customFontLabel.collectAsState()
    var downloading by remember { mutableStateOf<String?>(null) }

    val pageColor = MaterialTheme.colorScheme.surfaceContainer
    val cardColor = MaterialTheme.colorScheme.surface

    val hasCustomFont = customFontFile.isNotBlank()

    fun applyFont(installed: AppFontStore.Installed) {
      preferences.customFontFile.set(installed.storedFileName)
      preferences.customFontLabel.set(installed.label)
      Toast.makeText(context, R.string.pref_fonts_applied, Toast.LENGTH_SHORT).show()
    }

    val filePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
          AppFontStore.importFromUri(context, uri).fold(
            onSuccess = ::applyFont,
            onFailure = {
              Toast.makeText(context, R.string.pref_fonts_import_failed, Toast.LENGTH_LONG).show()
            },
          )
        }
      }

    CollapsibleHeaderScreen(
      containerColor = pageColor,
      title = stringResource(R.string.pref_fonts_title),
      showBackButton = true,
      onBackClick = backstack::navigateBack,
    ) { contentModifier ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      val listState = rememberPreferenceLazyListState()
      ProvidePreferenceLocals {
        LazyColumn(
          state = listState,
          modifier = contentModifier,
          contentPadding = PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          item { PreferenceSectionHeader(title = stringResource(R.string.pref_fonts_title)) }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                color = cardColor,
                tonalElevation = 0.dp,
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_fonts_choose_title,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_fonts_choose_title)) },
                  summary = {
                    Text(
                      text =
                        if (hasCustomFont && customFontLabel.isNotBlank()) {
                          customFontLabel
                        } else {
                          stringResource(R.string.pref_fonts_choose_none)
                        },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  // Document pickers often report fonts as application/octet-stream, so don't filter by MIME type;
                  // AppFontStore validates the file itself.
                  onClick = { filePicker.launch(arrayOf("*/*")) },
                )
              }
              GroupedPreferenceCard(
                color = cardColor,
                tonalElevation = 0.dp,
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_fonts_reset_title,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_fonts_reset_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_fonts_reset_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch {
                      AppFontStore.clear(context)
                      preferences.customFontFile.set("")
                      preferences.customFontLabel.set("")
                      preferences.useSystemFont.set(true)
                      Toast.makeText(context, R.string.pref_fonts_reset_done, Toast.LENGTH_SHORT).show()
                    }
                  },
                )
              }
            }
          }

          item { PreferenceSectionHeader(title = stringResource(R.string.pref_fonts_quick_downloads)) }

          item {
            GroupedListColumn {
              QUICK_FONTS.forEachIndexed { index, font ->
                val position =
                  when {
                    QUICK_FONTS.size == 1 -> GroupPosition.ONLY
                    index == 0 -> GroupPosition.FIRST
                    index == QUICK_FONTS.lastIndex -> GroupPosition.LAST
                    else -> GroupPosition.MIDDLE
                  }
                val isDownloading = downloading == font.name
                val isInUse = hasCustomFont && customFontLabel.substringBeforeLast('.') == font.name

                GroupedPreferenceCard(
                  color = cardColor,
                  tonalElevation = 0.dp,
                  position = position,
                ) {
                  Preference(
                    title = { Text(font.name) },
                    summary = {
                      Text(
                        text =
                          stringResource(
                            when {
                              isDownloading -> R.string.pref_fonts_downloading
                              isInUse -> R.string.pref_fonts_in_use
                              else -> R.string.pref_fonts_tap_to_download
                            },
                          ),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                    enabled = downloading == null,
                    onClick = {
                      downloading = font.name
                      scope.launch {
                        AppFontStore.download(context, okHttpClient, font.url, font.name).fold(
                          onSuccess = ::applyFont,
                          onFailure = {
                            Toast.makeText(context, R.string.pref_fonts_download_failed, Toast.LENGTH_LONG).show()
                          },
                        )
                        downloading = null
                      }
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
