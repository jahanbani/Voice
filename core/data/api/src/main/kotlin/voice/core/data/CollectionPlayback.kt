package voice.core.data

import java.time.Instant

public val Book.isFinished: Boolean get() = duration > 0 && position > 0 && position >= duration - 5_000

public fun BookCollection.resumeBook(
  books: List<Book>,
  current: BookId?,
): Book? {
  val byId = books.associateBy { it.id }
  val members = parts.mapNotNull { byId[it.id] }
  val unfinished = members.filterNot { it.isFinished }
  return unfinished.find { it.id == current }
    ?: unfinished.filter { it.content.lastPlayedAt > Instant.EPOCH }.maxByOrNull { it.content.lastPlayedAt }
    ?: unfinished.firstOrNull()
    ?: members.find { it.id == current }
    ?: members.firstOrNull()
}

public fun BookCollection.nextUnfinishedBook(
  after: BookId,
  books: List<Book>,
): Book? {
  val index = parts.indexOfFirst { it.id == after }
  if (index < 0) return null
  val byId = books.associateBy { it.id }
  return parts.drop(index + 1).mapNotNull { byId[it.id] }.firstOrNull { !it.isFinished }
}
