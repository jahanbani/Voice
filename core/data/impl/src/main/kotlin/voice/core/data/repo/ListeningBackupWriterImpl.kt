package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import voice.core.data.Bookmark
import voice.core.data.ListeningBackupWriter
import voice.core.data.ListeningRestorePlan
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.transaction
import java.time.Instant
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
public class ListeningBackupWriterImpl internal constructor(
  private val db: AppDb,
  private val contentRepo: BookContentRepoImpl,
  private val chapters: ChapterRepo,
  private val bookmarks: BookmarkRepo,
) : ListeningBackupWriter {
  override suspend fun apply(plan: ListeningRestorePlan): Int = try {
    db.transaction {
      val contents = db.bookContentDao().all().associateBy { it.id }
      val existingBookmarks = db.bookmarkDao().all().map { it.id }.toSet()
      var restored = 0
      for (entry in plan.books) {
        val content = contents[entry.id] ?: continue
        if (!content.isActive || content.chapters != entry.chapters) continue
        val saved = entry.saved
        val chapterId = entry.chapters[saved.chapter]
        val chapter = chapters.get(chapterId) ?: continue
        db.bookContentDao().insert(
          content.copy(
            currentChapter = chapterId,
            positionInChapter = saved.positionMs.coerceAtMost(chapter.duration),
            lastPlayedAt = Instant.parse(saved.lastPlayedAt),
          ),
        )
        for (bookmark in saved.bookmarks) {
          val bookmarkId = Bookmark.Id(Uuid.parse(bookmark.id))
          if (bookmarkId in existingBookmarks) continue
          val target = entry.chapters[bookmark.chapter]
          val duration = chapters.get(target)?.duration ?: continue
          bookmarks.addBookmark(
            Bookmark(
              bookId = entry.id,
              chapterId = target,
              title = bookmark.title,
              time = bookmark.positionMs.coerceAtMost(duration),
              addedAt = Instant.parse(bookmark.addedAt),
              id = bookmarkId,
              kind = Bookmark.Kind.valueOf(bookmark.kind),
            ),
          )
        }
        restored++
      }
      restored
    }
  } finally {
    withContext(NonCancellable) { contentRepo.refreshAfterWrite() }
  }
}
