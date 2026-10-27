@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package voice.features.settings.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.data.ListeningRestorePlan
import voice.core.ui.VoiceTheme
import voice.features.settings.backup.ListeningBackupController
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface ListeningBackupGraph {
  val listeningBackupController: ListeningBackupController
}

@Composable
internal fun BackupSection(
  snackbar: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  val controller = remember { rootGraphAs<ListeningBackupGraph>().listeningBackupController }
  val scanning by controller.scanner.scannerActive.collectAsState(initial = true)
  val scope = rememberCoroutineScope()
  val resources = LocalResources.current
  var busy by remember { mutableStateOf(false) }
  var plan by remember { mutableStateOf<ListeningRestorePlan?>(null) }
  val savedMessage = stringResource(StringsR.string.backup_saved)
  val errorMessage = stringResource(StringsR.string.backup_error)
  fun run(action: suspend () -> Unit) {
    scope.launch {
      busy = true
      try {
        action()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        snackbar.showSnackbar(errorMessage)
      } finally {
        busy = false
      }
    }
  }
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) {
      run {
        controller.export(uri)
        snackbar.showSnackbar(savedMessage)
      }
    }
  }
  val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) run { plan = controller.review(uri) }
  }
  BackupActions(busy || scanning, {
    export.launch("voice-listening-backup.json")
  }, { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier)
  plan?.let { review ->
    ModalBottomSheet(onDismissRequest = { if (!busy) plan = null }) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(StringsR.string.backup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(StringsR.string.backup_review, review.books.size, review.skipped))
        Button(
          onClick = {
            run {
              val imported = controller.restore(review)
              plan = null
              snackbar.showSnackbar(
                resources.getString(
                  StringsR.string.backup_restored,
                  imported,
                  review.skipped + review.books.size - imported,
                ),
              )
            }
          },
          enabled = !busy && !scanning && review.books.isNotEmpty(),
          modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(StringsR.string.backup_confirm)) }
        if (busy) LoadingIndicator()
      }
    }
  }
}

@Composable
internal fun BackupActions(
  busy: Boolean,
  onExport: () -> Unit,
  onImport: () -> Unit,
  modifier: Modifier = Modifier,
) {
  SettingsIsland(stringResource(StringsR.string.backup_title), MaterialTheme.colorScheme.surfaceContainer, modifier) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(StringsR.string.backup_help))
      TextButton(onClick = onExport, enabled = !busy) { Text(stringResource(StringsR.string.backup_export)) }
      TextButton(onClick = onImport, enabled = !busy) { Text(stringResource(StringsR.string.backup_import)) }
      if (busy) LoadingIndicator()
    }
  }
}

@Preview
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large", fontScale = 2F)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun ListeningBackupPreview() {
  VoiceTheme { BackupActions(false, {}, {}) }
}
