package voice.features.settings.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookMetadata
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ListeningBackup
import voice.core.data.ListeningBackupWriter
import voice.core.data.listeningBackup
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.playback.CurrentBookResolver
import voice.core.playback.PlayerController
import voice.core.scanner.MediaScanTrigger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ListeningBackupControllerTest {
  private val uri = Uri.parse("content://backup/test")
  private val resolver = mockk<ContentResolver>()
  private val context = mockk<Context> { every { contentResolver } returns resolver }
  private val chapter = Chapter(ChapterId("file:///book/one.mp3"), "One", 10000, Instant.EPOCH, 1200, emptyList())
  private val book = Book(
    BookContent(
      id = BookId("book"), playbackSpeed = 1F, skipSilence = false, isActive = true, lastPlayedAt = Instant.EPOCH,
      addedAt = Instant.EPOCH, chapters = listOf(chapter.id), currentChapter = chapter.id, positionInChapter = 50, gain = 0F,
      metadata = BookMetadata("Book", null, null, null, null, null, null, null, null, null), customName = null, customCover = null,
    ),
    listOf(chapter),
  )
  private val books = mockk<BookRepository> { coEvery { all() } returns listOf(book) }
  private val bookmarks = mockk<BookmarkRepo> { every { flow() } returns flowOf(emptyList()) }
  private val current = mockk<CurrentBookResolver> { coEvery { currentBook() } returns book.update { it.copy(positionInChapter = 750) } }
  private val writer = mockk<ListeningBackupWriter> { coEvery { this@mockk.apply(any()) } returns 1 }
  private val player = mockk<PlayerController>(relaxUnitFun = true)
  private val scanner = mockk<MediaScanTrigger> { every { scannerActive } returns flowOf(false) }

  @Test
  fun `export uses the current live position and writes a readable backup`() = runTest {
    val output = ByteArrayOutputStream()
    every { resolver.openOutputStream(uri, "wt") } returns output
    val controller =
      ListeningBackupController(
        context,
        books,
        bookmarks,
        current,
        writer,
        player,
        DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
        scanner,
      )
    controller.export(uri)
    val backup = Json.decodeFromString(ListeningBackup.serializer(), output.toString(Charsets.UTF_8))
    assertEquals(750L, backup.books.single().positionMs)
    assertEquals(book.id, backup.plan(listOf(book)).books.single().id)
  }

  @Test
  fun `review changes nothing and restore pauses before applying the reviewed plan`() = runTest {
    val text = Json.encodeToString(ListeningBackup.serializer(), listeningBackup(listOf(book), emptyList()))
    every { resolver.openInputStream(uri) } returns ByteArrayInputStream(text.toByteArray())
    val controller =
      ListeningBackupController(
        context,
        books,
        bookmarks,
        current,
        writer,
        player,
        DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
        scanner,
      )
    val plan = controller.review(uri)
    coVerify(exactly = 0) { val _ = writer.apply(any()) }
    assertEquals(1, controller.restore(plan))
    coVerifyOrder {
      player.pauseForLibraryChange()
      val _ = writer.apply(plan)
    }
  }

  @Test
  fun `oversized backups are rejected without applying anything`() = runTest {
    every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(8 * 1024 * 1024 + 1))
    val controller =
      ListeningBackupController(
        context,
        books,
        bookmarks,
        current,
        writer,
        player,
        DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
        scanner,
      )
    assertFailsWith<IllegalArgumentException> { controller.review(uri) }
    coVerify(exactly = 0) { val _ = writer.apply(any()) }
  }
}
