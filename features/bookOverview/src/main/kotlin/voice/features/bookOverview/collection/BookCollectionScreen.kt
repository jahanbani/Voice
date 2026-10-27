@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package voice.features.bookOverview.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.VoiceTheme
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.features.bookOverview.di.BookOverviewGraph
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.views.ListBookRow
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@BindingContainer
@ContributesTo(AppScope::class)
object BookCollectionProvider {
  @Provides
  @IntoSet
  fun collectionNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BookCollection> { key ->
    NavEntry(key) {
      val graph = retain<BookOverviewGraph> {
        rootGraphAs<BookOverviewGraph.Factory.Provider>().bookOverviewGraphProviderFactory.create()
      }
      val viewModel = graph.bookCollectionViewModel
      BookCollectionView(
        state = viewModel.state(key.collectionId),
        onBookClick = viewModel::onBookClick,
        onBookLongClick = viewModel::onBookLongClick,
        onPlayClick = viewModel::onPlayClick,
        onBackClick = viewModel::onBackClick,
      )
    }
  }
}

@Composable
internal fun BookCollectionView(
  state: BookCollectionViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onPlayClick: (BookId) -> Unit,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = state.name.ifBlank { stringResource(StringsR.string.library_collection) },
            style = MaterialTheme.typography.titleLarge.bookText(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              if (LocalLayoutDirection.current == LayoutDirection.Rtl) VoiceIcons.ArrowForward else VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.common_action_back),
            )
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.loading) {
        item { LoadingIndicator() }
      } else if (state.books.isEmpty()) {
        item { Text(stringResource(StringsR.string.library_empty_title)) }
      }
      state.resumeId?.let { resumeId ->
        item {
          Button(onClick = { onPlayClick(resumeId) }, modifier = Modifier.fillMaxWidth()) {
            Column {
              Text(stringResource(StringsR.string.library_continue_listening))
              state.books.find { it.id == resumeId }?.let { book ->
                Text(text = book.name, style = MaterialTheme.typography.bodySmall.bookText())
              }
            }
          }
        }
      }
      items(state.books, key = { it.id.value }) { book ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          ListBookRow(book, onBookClick, onBookLongClick, modifier = Modifier.weight(1F), finished = book.progress >= 1F)
          IconButton(onClick = { onPlayClick(book.id) }) {
            Icon(VoiceIcons.PlayArrow, contentDescription = stringResource(StringsR.string.playback_action_play) + ": " + book.name)
          }
        }
      }
    }
  }
}

@Preview
@Preview(name = "Large text", fontScale = 2F)
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", locale = "ar")
@Preview(name = "Landscape", widthDp = 840, heightDp = 360)
@Composable
private fun BookCollectionPreview() {
  VoiceTheme {
    BookCollectionView(
      state = BookCollectionViewState(
        name = "Detective Brooks",
        books = (1..4).map { series ->
          BookOverviewItemViewState(
            id = BookId("series$series"),
            name = "Series $series",
            author = null,
            cover = null,
            progress = if (series == 2) 0.45F else 0F,
            remainingTime = "02:10",
          )
        },
        resumeId = BookId("series2"),
      ),
      onBookClick = {},
      onBookLongClick = {},
      onPlayClick = {},
      onBackClick = {},
    )
  }
}
