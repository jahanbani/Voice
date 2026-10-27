package voice.core.data.folders

import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.BookCollection
import voice.core.data.BookCollectionPart
import voice.core.data.BookId
import voice.core.data.isAudioFile
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension

public data class DiscoveredLibrary(
  val books: List<CachedDocumentFile>,
  val collections: List<BookCollection>,
)

public fun CachedDocumentFile.discoverLibrary(): DiscoveredLibrary {
  val collections = mutableListOf<BookCollection>()
  val books = discoverAudiobooks().flatMap { book ->
    val children = book.children
    val parts = children.filter { it.isDirectory && it.discoverBookOrCollection().isNotEmpty() }
    if (parts.size > 1 && parts.all { seriesFolderName.matches(it.name.orEmpty().trim()) } &&
      children.none { it.isAudioFile() }
    ) {
      val ordered = parts.sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name.orEmpty() })
      collections += BookCollection(
        id = BookId(book.uri),
        name = book.nameWithoutExtension(),
        parts = ordered.map { BookCollectionPart(BookId(it.uri), it.nameWithoutExtension()) },
      )
      ordered
    } else {
      listOf(book)
    }
  }
  return DiscoveredLibrary(books, collections)
}

public fun CachedDocumentFile.discoverAudiobooks(): List<CachedDocumentFile> {
  return children.flatMap { it.discoverBookOrCollection() }
}

private fun CachedDocumentFile.discoverBookOrCollection(): List<CachedDocumentFile> {
  if (isFile) return if (isAudioFile()) listOf(this) else emptyList()
  val discovered = children.map { it to it.discoverBookOrCollection() }
  val audioFolders = discovered.filter { (file, books) -> file.isDirectory && books.isNotEmpty() }
  if (audioFolders.isEmpty()) {
    return if (discovered.any { it.second.isNotEmpty() }) listOf(this) else emptyList()
  }
  if (audioFolders.all { it.first.isBookPartFolder() }) return listOf(this)
  return discovered.flatMap { it.second }
}

public fun CachedDocumentFile.isBookPartFolder(): Boolean = bookPartFolderName.matches(name.orEmpty().trim())

private val bookPartFolderName = Regex(
  """(?i)^((.*[\s._-])?(cd|disc|disk|tape|side)|part|teil|vol\.?|volume|series|season)?[\s._-]*\d{1,3}(\s*\(\d{4}\)|\s+[-–:]\s+.+)?$""",
)

private val seriesFolderName = Regex("""(?i)^(series|season)[\s._-]*\d{1,3}(\s+[-–:]\s+.+)?$""")
