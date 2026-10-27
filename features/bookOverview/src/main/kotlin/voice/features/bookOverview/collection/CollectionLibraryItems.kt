package voice.features.bookOverview.collection

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import voice.core.data.BookCollection
import voice.core.data.BookId
import voice.core.ui.formatTime
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState

internal fun collectionLibraryItems(
  items: Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>,
  collections: List<BookCollection>,
  durations: Map<BookId, Long>,
): Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>> {
  val all = items.values.flatMap { it.entries }.associate { it.toPair() }
  val result = items.mapValues { (_, books) -> books.toMutableMap() }.toMutableMap()
  collections.forEach { collection ->
    val members = collection.parts.mapNotNull { part -> all[part.id]?.let { part.id to it } }
    if (members.isEmpty()) return@forEach
    val ids = members.map { it.first }.toSet()
    val category = when {
      ids.all { it in items[BookOverviewCategory.FINISHED].orEmpty() } -> BookOverviewCategory.FINISHED
      ids.any { it in items[BookOverviewCategory.CURRENT].orEmpty() || it in items[BookOverviewCategory.FINISHED].orEmpty() } ->
        BookOverviewCategory.CURRENT
      else -> BookOverviewCategory.NOT_STARTED
    }
    result.values.forEach { it.keys.removeAll(ids) }
    result.getOrPut(category) { mutableMapOf() }[collection.id] = derivedStateOf {
      val duration = members.sumOf { durations[it.first] ?: 0L }
      val position = members.sumOf { ((durations[it.first] ?: 0L) * it.second.value.progress).toLong() }
      BookOverviewItemViewState(
        id = collection.id,
        name = collection.name,
        author = null,
        collection = true,
        cover = members.firstNotNullOfOrNull { it.second.value.cover },
        progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0F, 1F) else 0F,
        remainingTime = formatTime((duration - position).coerceAtLeast(0)),
      )
    }
  }
  return result
}
