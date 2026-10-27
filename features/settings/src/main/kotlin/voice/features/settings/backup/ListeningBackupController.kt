package voice.features.settings.backup

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import voice.core.common.DispatcherProvider
import voice.core.data.ListeningBackup
import voice.core.data.ListeningBackupWriter
import voice.core.data.ListeningRestorePlan
import voice.core.data.listeningBackup
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.playback.CurrentBookResolver
import voice.core.playback.PlayerController
import voice.core.scanner.MediaScanTrigger

@Inject
class ListeningBackupController(
  private val context: Context,
  private val books: BookRepository,
  private val bookmarks: BookmarkRepo,
  private val currentBook: CurrentBookResolver,
  private val writer: ListeningBackupWriter,
  private val player: PlayerController,
  private val dispatcher: DispatcherProvider,
  val scanner: MediaScanTrigger,
) {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  suspend fun export(uri: Uri) = withContext(dispatcher.io) {
    check(!scanner.scannerActive.first())
    val current = withContext(dispatcher.mainImmediate) { currentBook.currentBook() }
    val library = books.all().map { book -> current?.takeIf { it.id == book.id } ?: book }
    val text = json.encodeToString(ListeningBackup.serializer(), listeningBackup(library, bookmarks.flow().first()))
    check(text.toByteArray().size <= MAX_BACKUP_BYTES)
    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup")
    output.bufferedWriter().use { it.write(text) }
  }

  suspend fun review(uri: Uri): ListeningRestorePlan = withContext(dispatcher.io) {
    check(!scanner.scannerActive.first())
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
    val bytes = ByteArray(MAX_BACKUP_BYTES + 1)
    val count = input.use {
      var count = 0
      while (count < bytes.size) {
        val read = it.read(bytes, count, bytes.size - count)
        if (read < 0) break
        count += read
      }
      count
    }
    require(count <= MAX_BACKUP_BYTES)
    val backup = json.decodeFromString(ListeningBackup.serializer(), bytes.decodeToString(endIndex = count, throwOnInvalidSequence = true))
    backup.plan(books.all())
  }

  suspend fun restore(plan: ListeningRestorePlan): Int {
    check(!scanner.scannerActive.first())
    player.pauseForLibraryChange()
    return withContext(dispatcher.io) { writer.apply(plan) }
  }

  private companion object {
    const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
  }
}
