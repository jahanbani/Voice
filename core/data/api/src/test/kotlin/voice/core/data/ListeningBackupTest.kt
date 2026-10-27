package voice.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ListeningBackupTest {
  private val original = book(chapters = listOf(audio("old/one.mp3"), audio("old/two.mp3")), time = 500)
  private val bookmark =
    Bookmark(original.id, original.chapters[1].id, "Remember this", 800, Instant.EPOCH, Bookmark.Id(Uuid.random()), Bookmark.Kind.Quote)

  @Test
  fun `backup survives JSON round trip with positions notes and bookmark kinds`() {
    val backup = listeningBackup(listOf(original), listOf(bookmark))
    val decoded = Json.decodeFromString(ListeningBackup.serializer(), Json.encodeToString(ListeningBackup.serializer(), backup))
    assertEquals(backup, decoded)
    val restored = decoded.plan(listOf(original)).books.single()
    assertEquals(500L, restored.saved.positionMs)
    assertEquals("Remember this", restored.saved.bookmarks.single().title)
    assertEquals("Quote", restored.saved.bookmarks.single().kind)
  }

  @Test
  fun `files copied to another phone match without relying on old URIs or titles`() {
    val moved = book(name = "Different metadata", chapters = listOf(audio("new/one.mp3"), audio("new/two.mp3")))
    val plan = listeningBackup(listOf(original), listOf(bookmark)).plan(listOf(moved))
    assertEquals(moved.id, plan.books.single().id)
    assertEquals(moved.content.chapters, plan.books.single().chapters)
    assertEquals(0, plan.skipped)
  }

  @Test
  fun `ambiguous copies and different audio files are skipped`() {
    val moved = book(chapters = listOf(audio("new/one.mp3"), audio("new/two.mp3")))
    val duplicate = moved.update { it.copy(id = BookId("duplicate")) }
    val backup = listeningBackup(listOf(original), emptyList())
    assertEquals(1, backup.plan(listOf(moved, duplicate)).skipped)
    assertEquals(1, backup.plan(listOf(book(chapters = listOf(audio("new/other.mp3"))))).skipped)
    assertEquals(1, backup.plan(emptyList()).skipped)
  }

  @Test
  fun `unsupported or invalid backups are rejected before a restore plan is created`() {
    val backup = listeningBackup(listOf(original), listOf(bookmark))
    assertFailsWith<IllegalArgumentException> { backup.copy(version = 2).plan(listOf(original)) }
    assertFailsWith<IllegalArgumentException> { backup.copy(format = "another-app").plan(listOf(original)) }
    assertFailsWith<IllegalArgumentException> { backup.copy(books = backup.books + backup.books).plan(listOf(original)) }
    val saved = backup.books.single()
    for (bad in listOf(
      saved.copy(chapter = 9),
      saved.copy(positionMs = -1),
      saved.copy(lastPlayedAt = "bad"),
      saved.copy(bookmarks = listOf(saved.bookmarks.single().copy(kind = "unknown"))),
    )) {
      assertFails { backup.copy(books = listOf(bad)).plan(listOf(original)) }
    }
  }

  @Test
  fun `same-device provider without file sizes can still restore exact chapter IDs`() {
    val book = book()
    val plan = listeningBackup(listOf(book), emptyList()).plan(listOf(book))
    assertEquals(book.id, plan.books.single().id)
  }

  private fun audio(path: String): Chapter = chapter(id = ChapterId("file:///$path")).copy(fileSize = 12345)
}
