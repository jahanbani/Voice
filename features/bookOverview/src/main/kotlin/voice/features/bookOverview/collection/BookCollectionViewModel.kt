package voice.features.bookOverview.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookCollection
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.BookCollectionsStore
import voice.core.data.store.CurrentBookStore
import voice.core.playback.PlayerController
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.toItemViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.time.Instant

internal data class BookCollectionViewState(
  val name: String,
  val books: List<BookOverviewItemViewState>,
  val resumeId: BookId?,
  val loading: Boolean = false,
)

@Inject
@SingleIn(BookOverviewScope::class)
class BookCollectionViewModel(
  private val repo: BookRepository,
  private val collectionsStore: BookCollectionsStore,
  private val currentBookStore: CurrentBookStore,
  private val navigator: Navigator,
  private val playerController: PlayerController,
  dispatcherProvider: DispatcherProvider,
) {

  private val scope = MainScope(dispatcherProvider)

  @Composable
  internal fun state(id: BookId): BookCollectionViewState {
    val collections by remember { collectionsStore.data }.collectAsState(initial = null)
    val books by remember { repo.flow() }.collectAsState(initial = null)
    val currentBook by remember { currentBookStore.data.map(::CollectionCurrentBook) }.collectAsState(initial = null)
    val loadedBooks = books
    val loadedCollections = collections
    val loadedCurrentBook = currentBook
    if (loadedBooks == null || loadedCollections == null || loadedCurrentBook == null) {
      return BookCollectionViewState("", emptyList(), null, loading = true)
    }
    val collection = loadedCollections.find { it.id == id }
      ?: return BookCollectionViewState("", emptyList(), null)
    return collectionViewState(collection, loadedBooks, loadedCurrentBook.id)
  }

  fun onBookClick(id: BookId) {
    navigator.goTo(Destination.Playback(id))
  }

  fun onBookLongClick(id: BookId) {
    navigator.goTo(Destination.BookActions(id))
  }

  fun onPlayClick(id: BookId) {
    scope.launch {
      playerController.pauseIfCurrentBookDifferentFrom(id)
      currentBookStore.updateData { id }
      playerController.play()
      navigator.goTo(Destination.Playback(id))
    }
  }

  fun onBackClick() {
    navigator.goBack()
  }
}

private data class CollectionCurrentBook(val id: BookId?)

internal fun collectionViewState(
  collection: BookCollection,
  books: List<Book>,
  currentBook: BookId?,
): BookCollectionViewState {
  val byId = books.associateBy { it.id }
  val members = collection.parts.mapNotNull { part -> byId[part.id]?.let { part to it } }
  val resume = members.find { it.second.id == currentBook }
    ?: members.filter { it.second.content.lastPlayedAt > Instant.EPOCH }.maxByOrNull { it.second.content.lastPlayedAt }
    ?: members.firstOrNull()
  return BookCollectionViewState(
    name = collection.name,
    books = members.map { (part, book) -> book.toItemViewState().copy(name = book.content.customName ?: part.name) },
    resumeId = resume?.second?.id,
  )
}
