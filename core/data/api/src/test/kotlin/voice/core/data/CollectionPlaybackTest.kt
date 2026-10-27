package voice.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionPlaybackTest {
  private val first = book()
  private val second = book()
  private val third = book()
  private val collection =
    BookCollection(BookId("collection"), "Collection", listOf(first, second, third).map { BookCollectionPart(it.id, it.content.name) })

  @Test
  fun `resume skips the finished current series and picks the first unfinished one`() {
    assertEquals(second.id, collection.resumeBook(listOf(first.finished(), second, third), first.id)?.id)
  }

  @Test
  fun `next respects collection order and skips finished or missing members`() {
    assertEquals(third.id, collection.nextUnfinishedBook(first.id, listOf(third, first, second.finished()))?.id)
    assertEquals(third.id, collection.nextUnfinishedBook(first.id, listOf(first, third))?.id)
    assertNull(collection.nextUnfinishedBook(third.id, listOf(first, second, third)))
    assertNull(collection.nextUnfinishedBook(BookId("other"), listOf(first, second, third)))
  }

  @Test
  fun `a completed collection remains available for replay`() {
    assertEquals(second.id, collection.resumeBook(listOf(first.finished(), second.finished(), third.finished()), second.id)?.id)
  }

  private fun Book.finished(): Book = update { it.copy(currentChapter = chapters.last().id, positionInChapter = chapters.last().duration) }
}
