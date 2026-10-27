package voice.features.bookOverview.collection

import androidx.compose.runtime.mutableStateOf
import voice.core.data.BookCollection
import voice.core.data.BookCollectionPart
import voice.core.data.BookId
import voice.features.bookOverview.book
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.toItemViewState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class BookCollectionTest {
  @Test
  fun `series keep their own progress and folder labels in natural scan order`() {
    val first = book(name = "Same album", time = 2_000)
    val second = book(name = "Same album", time = 7_000)
    val collection = BookCollection(
      BookId("collection"),
      "Detective",
      listOf(BookCollectionPart(first.id, "Series 1"), BookCollectionPart(second.id, "Series 2")),
    )

    val state = collectionViewState(collection, listOf(second, first), second.id)

    assertEquals(listOf("Series 1", "Series 2"), state.books.map { it.name })
    assertEquals(listOf(0.1F, 0.35F), state.books.map { it.progress })
    assertEquals(second.id, state.resumeId)
    val renamed = first.update { it.copy(customName = "My first series") }
    assertEquals("My first series", collectionViewState(collection, listOf(renamed, second), null).books.first().name)
  }

  @Test
  fun `resume chooses the most recently played series when another book is current`() {
    val first = book(time = 500).update { it.copy(lastPlayedAt = Instant.ofEpochSecond(10)) }
    val second = book(time = 800).update { it.copy(lastPlayedAt = Instant.ofEpochSecond(20)) }
    val collection = BookCollection(
      BookId("collection"),
      "Detective",
      listOf(BookCollectionPart(first.id, "Series 1"), BookCollectionPart(second.id, "Series 2")),
    )

    assertEquals(second.id, collectionViewState(collection, listOf(first, second), BookId("other")).resumeId)
    assertEquals(first.id, collectionViewState(collection, listOf(first, second), first.id).resumeId)
  }

  @Test
  fun `missing series are left out and an empty collection has no resume target`() {
    val collection = BookCollection(BookId("collection"), "Detective", listOf(BookCollectionPart(BookId("gone"), "Series 1")))

    val state = collectionViewState(collection, emptyList(), null)

    assertEquals(emptyList(), state.books)
    assertEquals(null, state.resumeId)
  }

  @Test
  fun `library replaces series with one collection and preserves unrelated books`() {
    val first = book(time = 2_000)
    val second = book(time = 6_000)
    val ordinary = book(time = 0)
    val collection = BookCollection(
      BookId("collection"),
      "Detective",
      listOf(BookCollectionPart(first.id, "Series 1"), BookCollectionPart(second.id, "Series 2")),
    )
    val firstState = mutableStateOf(first.toItemViewState())
    val items = mapOf(
      BookOverviewCategory.CURRENT to mapOf(first.id to firstState, second.id to mutableStateOf(second.toItemViewState())),
      BookOverviewCategory.NOT_STARTED to mapOf(ordinary.id to mutableStateOf(ordinary.toItemViewState())),
    )

    val grouped = collectionLibraryItems(
      items,
      listOf(collection),
      mapOf(first.id to first.duration, second.id to second.duration),
    )

    assertEquals(setOf(collection.id), grouped.getValue(BookOverviewCategory.CURRENT).keys)
    assertEquals(setOf(ordinary.id), grouped.getValue(BookOverviewCategory.NOT_STARTED).keys)
    val state = grouped.getValue(BookOverviewCategory.CURRENT).getValue(collection.id)
    assertEquals("Detective", state.value.name)
    assertEquals(0.2F, state.value.progress)
    firstState.value = firstState.value.copy(progress = 0.5F)
    assertEquals(0.4F, state.value.progress)
  }

  @Test
  fun `a collection is finished only when all its series are finished`() {
    val first = book()
    val second = book()
    val collection = BookCollection(
      BookId("collection"),
      "Detective",
      listOf(BookCollectionPart(first.id, "Series 1"), BookCollectionPart(second.id, "Series 2")),
    )
    val items = mapOf(
      BookOverviewCategory.FINISHED to mapOf(first.id to mutableStateOf(first.toItemViewState().copy(progress = 1F))),
      BookOverviewCategory.NOT_STARTED to mapOf(second.id to mutableStateOf(second.toItemViewState().copy(progress = 0F))),
    )

    assertEquals(
      setOf(collection.id),
      collectionLibraryItems(items, listOf(collection), emptyMap()).getValue(BookOverviewCategory.CURRENT).keys,
    )
  }
}
