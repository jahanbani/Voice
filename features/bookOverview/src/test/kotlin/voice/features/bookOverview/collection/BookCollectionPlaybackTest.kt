package voice.features.bookOverview.collection

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import voice.core.common.DispatcherProvider
import voice.core.data.BookId
import voice.core.data.store.BookCollectionsStore
import voice.core.data.store.CurrentBookStore
import voice.core.playback.PlayerController
import voice.features.bookOverview.MemoryDataStore
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertEquals

class BookCollectionPlaybackTest {
  @Test
  fun `playing a series prepares that book and opens its player`() = runTest {
    val target = BookId("series2")
    val currentBookStore = CurrentBookStore(MemoryDataStore(BookId("other")))
    val player = mockk<PlayerController>(relaxUnitFun = true)
    val navigator = mockk<Navigator>(relaxUnitFun = true)
    val viewModel = BookCollectionViewModel(
      repo = mockk(),
      collectionsStore = BookCollectionsStore(MemoryDataStore(emptyList())),
      currentBookStore = currentBookStore,
      navigator = navigator,
      playerController = player,
      dispatcherProvider = DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
    )

    viewModel.onPlayClick(target)
    testScheduler.runCurrent()

    assertEquals(target, currentBookStore.data.first())
    verify { player.pauseIfCurrentBookDifferentFrom(target) }
    verify { player.play() }
    verify { navigator.goTo(Destination.Playback(target)) }
  }
}
