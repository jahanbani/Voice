package voice.core.data

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
public data class ListeningBackup(
  val format: String = "voice-listening-backup",
  val version: Int = 1,
  val books: List<BackupBook>,
) {
  public fun plan(library: List<Book>): ListeningRestorePlan {
    require(format == "voice-listening-backup" && version == 1)
    require(books.map { it.id }.distinct().size == books.size)
    val bookmarkIds = books.flatMap { it.bookmarks }.map { it.id }
    require(bookmarkIds.distinct().size == bookmarkIds.size)
    books.forEach { it.validate() }
    val localBooks = library.filterNot { it.id.isRemote }
    val byId = localBooks.associateBy { it.id.value }
    val byFiles = localBooks.mapNotNull { book -> book.chapters.syncKey()?.let { it to book } }.groupBy({ it.first }, { it.second })
    val matches = books.mapNotNull { saved ->
      val exact = byId[saved.id]?.takeIf { book ->
        if (saved.syncKey != null) book.chapters.syncKey() == saved.syncKey else book.content.chapters.map { it.value } == saved.chapters
      }
      val book = exact ?: saved.syncKey?.let { byFiles[it]?.singleOrNull() } ?: return@mapNotNull null
      if (book.chapters.size != saved.chapters.size) return@mapNotNull null
      ListeningRestoreBook(book.id, book.content.chapters, saved)
    }
    val unique = matches.groupBy { it.id }.values.mapNotNull { it.singleOrNull() }
    return ListeningRestorePlan(unique, books.size - unique.size)
  }
}

@Serializable
public data class BackupBook(
  val id: String,
  val syncKey: String?,
  val chapters: List<String>,
  val chapter: Int,
  val positionMs: Long,
  val lastPlayedAt: String,
  val bookmarks: List<BackupBookmark>,
) {
  internal fun validate() {
    require(chapters.isNotEmpty() && chapters.distinct().size == chapters.size)
    require(chapter in chapters.indices && positionMs >= 0)
    val _ = Instant.parse(lastPlayedAt)
    bookmarks.forEach { bookmark ->
      require(bookmark.chapter in chapters.indices && bookmark.positionMs >= 0)
      val _ = Uuid.parse(bookmark.id)
      val _ = Instant.parse(bookmark.addedAt)
      val _ = Bookmark.Kind.valueOf(bookmark.kind)
    }
  }
}

@Serializable
public data class BackupBookmark(
  val id: String,
  val chapter: Int,
  val positionMs: Long,
  val title: String?,
  val addedAt: String,
  val kind: String,
)

public data class ListeningRestoreBook(
  val id: BookId,
  val chapters: List<ChapterId>,
  val saved: BackupBook,
)
public data class ListeningRestorePlan(
  val books: List<ListeningRestoreBook>,
  val skipped: Int,
)

public interface ListeningBackupWriter {
  public suspend fun apply(plan: ListeningRestorePlan): Int
}

public fun listeningBackup(
  books: List<Book>,
  bookmarks: List<Bookmark>,
): ListeningBackup = ListeningBackup(
  books = books.filterNot { it.id.isRemote }.map { book ->
    BackupBook(
      id = book.id.value,
      syncKey = book.chapters.syncKey(),
      chapters = book.content.chapters.map { it.value },
      chapter = book.content.currentChapterIndex,
      positionMs = book.content.positionInChapter,
      lastPlayedAt = book.content.lastPlayedAt.toString(),
      bookmarks = bookmarks.filter { it.bookId == book.id }.mapNotNull { bookmark ->
        val chapter = book.content.chapters.indexOf(bookmark.chapterId)
        if (chapter < 0) return@mapNotNull null
        BackupBookmark(
          bookmark.id.value.toString(),
          chapter,
          bookmark.time,
          bookmark.title,
          bookmark.addedAt.toString(),
          bookmark.kind.name,
        )
      },
    )
  },
)
