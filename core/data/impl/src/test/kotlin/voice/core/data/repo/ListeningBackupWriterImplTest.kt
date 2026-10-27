package voice.core.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookMetadata
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.listeningBackup
import voice.core.data.repo.internals.AppDb
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ListeningBackupWriterImplTest {
  private val db = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(),
    AppDb::class.java,
  ).allowMainThreadQueries().build()
  private val contents = BookContentRepoImpl(db.bookContentDao())
  private val chapters = ChapterRepoImpl(db.chapterDao())
  private val bookmarks = BookmarkRepoImpl(db.bookmarkDao(), db, Clock.systemUTC())
  private val writer = ListeningBackupWriterImpl(db, contents, chapters, bookmarks)
  private val chapter = Chapter(ChapterId("file:///book/one.mp3"), "One", 10000, Instant.EPOCH, 1200, emptyList())
  private val book = Book(
    BookContent(
      id = BookId("book"), playbackSpeed = 1.5F, skipSilence = true, isActive = true,
      lastPlayedAt = Instant.EPOCH, addedAt = Instant.EPOCH, chapters = listOf(chapter.id),
      currentChapter = chapter.id, positionInChapter = 50, gain = 0F,
      metadata = BookMetadata("Book", null, null, null, null, null, null, null, null, null),
      customName = "My book", customCover = null,
    ),
    listOf(chapter),
  )
  private val bookmark = Bookmark(book.id, chapter.id, "A note", 500, Instant.EPOCH, Bookmark.Id.random(), Bookmark.Kind.Favorite)

  @After
  fun close() {
    db.close()
  }

  private suspend fun prepare() {
    chapters.put(chapter)
    contents.put(book.content)
  }

  @Test
  fun `restore updates cache and database and merges bookmarks idempotently`() = runTest {
    prepare()
    val other = bookmark.copy(id = Bookmark.Id.random(), title = "Keep this")
    bookmarks.addBookmark(other)
    val saved = book.update { it.copy(positionInChapter = 750, lastPlayedAt = Instant.ofEpochSecond(10)) }
    val plan = listeningBackup(listOf(saved), listOf(bookmark)).plan(listOf(book))
    assertEquals(1, writer.apply(plan))
    assertEquals(1, writer.apply(plan))
    val restored = contents.get(book.id)!!
    assertEquals(750L, restored.positionInChapter)
    assertEquals(restored, db.bookContentDao().all().single())
    assertEquals("My book", restored.customName)
    assertEquals(1.5F, restored.playbackSpeed)
    assertEquals(setOf(bookmark, other), bookmarks.flow().first().toSet())
  }

  @Test
  fun `failed bookmark write rolls back progress and refreshes the cache`() = runTest {
    prepare()
    val failing = mockk<BookmarkRepo> { coEvery { addBookmark(any()) } throws IllegalStateException("test") }
    val writer = ListeningBackupWriterImpl(db, contents, chapters, failing)
    val saved = book.update { it.copy(positionInChapter = 900) }
    val plan = listeningBackup(listOf(saved), listOf(bookmark)).plan(listOf(book))
    assertFailsWith<IllegalStateException> { writer.apply(plan) }
    assertEquals(50L, contents.get(book.id)!!.positionInChapter)
    assertEquals(50L, db.bookContentDao().all().single().positionInChapter)
    assertEquals(emptyList(), bookmarks.flow().first())
  }

  @Test
  fun `changed or inactive books after review are skipped`() = runTest {
    prepare()
    val plan = listeningBackup(listOf(book.update { it.copy(positionInChapter = 500) }), emptyList()).plan(listOf(book))
    contents.put(book.content.copy(isActive = false))
    assertEquals(0, writer.apply(plan))
    assertEquals(50L, contents.get(book.id)!!.positionInChapter)
  }

  @Test
  fun `import keeps an existing bookmark note edited since the backup`() = runTest {
    prepare()
    val edited = bookmark.copy(title = "Newer note")
    bookmarks.addBookmark(edited)
    val plan = listeningBackup(listOf(book), listOf(bookmark)).plan(listOf(book))
    assertEquals(1, writer.apply(plan))
    assertEquals(listOf(edited), bookmarks.flow().first())
  }
}
