@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package voice.features.folderPicker.grouping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.common.rootGraphAs
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderGrouping
import voice.core.data.folders.FolderGroupingStore
import voice.core.data.folders.FolderType
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.core.playback.PlayerController
import voice.core.scanner.MediaScanTrigger
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

@Inject
class FolderGroupingController(
  val store: FolderGroupingStore,
  val scanner: MediaScanTrigger,
  val navigator: Navigator,
  private val folders: AudiobookFolders,
  private val factory: CachedDocumentFileFactory,
  private val dispatcher: DispatcherProvider,
  private val player: PlayerController,
) {
  suspend fun load(uri: String?): GroupingFolder = withContext(dispatcher.io) {
    if (uri == null) {
      val roots = folders.all().first()[FolderType.Automatic].orEmpty().map { it.documentFile }
      GroupingFolder(null, null, roots.map { GroupingChild(it.uri.toString(), it.nameWithoutExtension()) })
    } else {
      val folder = factory.create(uri.toUri())
      GroupingFolder(
        uri,
        folder.nameWithoutExtension(),
        folder.children.filter {
          it.isDirectory
        }.map { GroupingChild(it.uri.toString(), it.nameWithoutExtension()) },
      )
    }
  }

  suspend fun save(
    uri: String,
    grouping: FolderGrouping?,
  ) {
    player.pauseForLibraryChange()
    store.updateData { if (grouping == null) it - uri else it + (uri to grouping) }
    scanner.scan(restartIfScanning = true)
  }
}

data class GroupingFolder(
  val uri: String?,
  val name: String?,
  val children: List<GroupingChild>,
)
data class GroupingChild(
  val uri: String,
  val name: String,
)

@ContributesTo(AppScope::class)
interface FolderGroupingGraph {
  val folderGroupingController: FolderGroupingController
}

@BindingContainer
@ContributesTo(AppScope::class)
object FolderGroupingProvider {
  @Provides
  @IntoSet
  fun entry(): NavEntryProvider<*> = NavEntryProvider<Destination.FolderGrouping> { key ->
    NavEntry(key) { FolderGroupingScreen() }
  }
}

@Composable
private fun FolderGroupingScreen() {
  val controller = remember { rootGraphAs<FolderGroupingGraph>().folderGroupingController }
  var path by rememberSaveable { mutableStateOf(emptyList<String>()) }
  val uri = path.lastOrNull()
  var loadError by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  val folder by produceState<GroupingFolder?>(null, uri, reload) {
    value = null
    loadError = false
    try {
      value = controller.load(uri)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      loadError = true
    }
  }
  val grouping by controller.store.data.collectAsState(initial = null)
  val scanning by controller.scanner.scannerActive.collectAsState(initial = false)
  var saving by remember { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val savedMessage = stringResource(StringsR.string.grouping_saved)
  val errorMessage = stringResource(StringsR.string.common_error_generic_message)
  val onBack = { if (path.isNotEmpty()) path = path.dropLast(1) else controller.navigator.goBack() }
  BackHandler(path.isNotEmpty(), onBack)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.grouping_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              if (LocalLayoutDirection.current == LayoutDirection.Rtl) VoiceIcons.ArrowForward else VoiceIcons.ArrowBack,
              stringResource(StringsR.string.common_action_back),
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { Text(stringResource(StringsR.string.grouping_help)) }
      val loaded = folder?.takeIf { it.uri == uri }
      if ((loaded == null && !loadError) || grouping == null || saving || scanning) {
        item { LoadingIndicator() }
      }
      if (loadError) {
        item {
          Text(errorMessage)
          TextButton(onClick = { reload++ }) { Text(stringResource(StringsR.string.common_error_generic_retry)) }
        }
      }
      if (uri != null && loaded != null && grouping != null) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(loaded.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
            listOf(null, FolderGrouping.OneBook, FolderGrouping.Collection).forEach { mode ->
              val selected = grouping?.get(uri) == mode
              val onClick = {
                scope.launch {
                  saving = true
                  try {
                    controller.save(uri, mode)
                    snackbar.showSnackbar(savedMessage)
                  } catch (e: CancellationException) {
                    throw e
                  } catch (_: Exception) {
                    snackbar.showSnackbar(errorMessage)
                  } finally {
                    saving = false
                  }
                }
                Unit
              }
              GroupingModeChip(
                mode = mode,
                selected = selected,
                onClick = { if (!selected) onClick() },
                enabled = !saving && !scanning,
              )
            }
          }
        }
      }
      if (uri == null && loaded?.children?.isEmpty() == true) item { Text(stringResource(StringsR.string.grouping_empty)) }
      items(
        loaded?.children.orEmpty().takeIf { uri == null || grouping?.get(uri) == null }.orEmpty().sortedWith(
          compareBy(NaturalOrderComparator.stringComparator) {
            it.name
          },
        ),
        key = { it.uri },
      ) { child ->
        TextButton(onClick = { path = path + child.uri }, modifier = Modifier.fillMaxWidth(), enabled = !saving && !scanning) {
          Text(child.name, modifier = Modifier.weight(1F))
          Icon(VoiceIcons.Folder, contentDescription = null)
        }
      }
    }
  }
}

@Composable
private fun GroupingModeChip(
  mode: FolderGrouping?,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val label = stringResource(
    when (mode) {
      null -> StringsR.string.folder_mode_automatic_label
      FolderGrouping.OneBook -> StringsR.string.grouping_one_book
      FolderGrouping.Collection -> StringsR.string.library_collection
    },
  )
  androidx.compose.material3.FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    label = { Text(label) },
    leadingIcon = if (selected) {
      { Icon(VoiceIcons.Check, contentDescription = null) }
    } else {
      null
    },
  )
}

@Preview
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large", fontScale = 2F)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun FolderGroupingPreview() {
  VoiceTheme {
    androidx.compose.material3.Surface {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(StringsR.string.grouping_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(StringsR.string.grouping_help))
        Text("Detective Brooks", style = MaterialTheme.typography.titleLarge)
        listOf(null, FolderGrouping.OneBook, FolderGrouping.Collection).forEach { mode ->
          GroupingModeChip(mode, mode == FolderGrouping.Collection, true, {})
        }
      }
    }
  }
}
