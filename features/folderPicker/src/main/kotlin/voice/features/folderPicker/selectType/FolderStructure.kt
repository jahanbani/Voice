package voice.features.folderPicker.selectType

import voice.core.data.audioFileCount
import voice.core.data.folders.discoverAudiobooks
import voice.core.data.folders.isBookPartFolder
import voice.core.data.isAudioFile
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension

/**
 * Guesses how the books in this folder are organized, including nested collections:
 * - Only folders like "CD 1" and "CD 2", or mostly loose chapter files: one book.
 * - Mostly folders that hold further book folders: authors (or series) with their books.
 * - Otherwise: a library where every folder or audiobook file is a book.
 */
internal fun CachedDocumentFile.guessFolderMode(): FolderMode {
  val folders = audioFolders()
  if (folders.isNotEmpty() && folders.all { it.isBookPartFolder() }) {
    return FolderMode.SingleBook
  }
  val (wholeBooks, looseChapters) = children
    .filter { it.isAudioFile() }
    .partition { it.looksLikeWholeBook() }
  if (looseChapters.size > folders.size + wholeBooks.size) {
    return FolderMode.SingleBook
  }
  val authorFolders = folders.count { it.looksLikeAuthor() }
  val mode = if (authorFolders * 2 > folders.size) FolderMode.Authors else FolderMode.Audiobooks
  val automatic = discoverAudiobooks().map { it.uri }.toSet()
  return if (automatic != books(mode).map { it.file.uri }.toSet()) FolderMode.Automatic else mode
}

/**
 * The books Voice finds in this folder in the given [mode]. This mirrors what the scanner does.
 */
internal fun CachedDocumentFile.books(mode: FolderMode): List<FolderBook> {
  return when (mode) {
    FolderMode.Automatic -> discoverAudiobooks().map { it.toFolderBook(author = null) }
    FolderMode.SingleBook -> listOf(toFolderBook(author = null))
    FolderMode.Audiobooks -> children.map { it.toFolderBook(author = null) }
    FolderMode.Authors -> children.flatMap { author ->
      if (author.isFile) {
        listOf(author.toFolderBook(author = null))
      } else {
        val authorName = author.nameWithoutExtension()
        author.children.map { it.toFolderBook(author = authorName) }
      }
    }
  }.filter { it.book.fileCount > 0 }
}

/**
 * A book in the folder and what its files tell without analyzing them.
 */
internal class FolderBook(
  val file: CachedDocumentFile,
  val book: SelectFolderTypeViewState.Book,
)

private fun CachedDocumentFile.toFolderBook(author: String?): FolderBook {
  val (parts, books) = audioFolders().partition { it.isBookPartFolder() }
  return FolderBook(
    file = this,
    book = SelectFolderTypeViewState.Book(
      name = nameWithoutExtension(),
      author = author,
      fileCount = audioFileCount(),
      partCount = parts.size,
      // a single sub folder is often just bonus material
      possibleBookCount = books.size.takeIf { it > 1 } ?: 0,
    ),
  )
}

private fun CachedDocumentFile.audioFolders(): List<CachedDocumentFile> {
  return children.filter { it.isDirectory && it.audioFileCount() > 0 }
}

// an author folder only holds book folders
private fun CachedDocumentFile.looksLikeAuthor(): Boolean {
  return children.none { it.isAudioFile() } && audioFolders().any { !it.isBookPartFolder() }
}

private fun CachedDocumentFile.looksLikeWholeBook(): Boolean {
  return name.orEmpty().endsWith(".m4b", ignoreCase = true) || length > WHOLE_BOOK_MIN_BYTES
}

private const val WHOLE_BOOK_MIN_BYTES = 200_000_000L
