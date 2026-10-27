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

public fun CachedDocumentFile.discoverLibrary(grouping: Map<String, FolderGrouping> = emptyMap()): DiscoveredLibrary {
  val collections = mutableListOf<BookCollection>()
  fun collect(
    folder: CachedDocumentFile,
    parts: List<CachedDocumentFile>,
  ): List<CachedDocumentFile> {
    if (parts.isNotEmpty()) {
      val ordered = parts.sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name.orEmpty() })
      collections += BookCollection(
        id = BookId(folder.uri),
        name = folder.nameWithoutExtension(),
        parts = ordered.map { BookCollectionPart(BookId(it.uri), it.nameWithoutExtension()) },
      )
      return ordered
    }
    return emptyList()
  }
  fun discover(
    file: CachedDocumentFile,
    root: Boolean = false,
  ): List<CachedDocumentFile> {
    if (file.isFile) return if (file.isAudioFile()) listOf(file) else emptyList()
    val mode = grouping[file.uri.toString()]
    if (mode == FolderGrouping.OneBook) return if (file.hasAudio()) listOf(file) else emptyList()
    if (mode == FolderGrouping.Collection) return collect(file, file.children.filter { it.hasAudio() })
    val collectionsBefore = collections.size
    val discovered = file.children.map { it to discover(it) }
    if (root) return discovered.flatMap { it.second }
    val audioFolders = discovered.filter { (child, books) -> child.isDirectory && books.isNotEmpty() }
    val explicitChild = file.children.any { it.hasGrouping(grouping) }
    if (collections.size == collectionsBefore && !explicitChild) {
      if (audioFolders.isEmpty()) return if (discovered.any { it.second.isNotEmpty() }) listOf(file) else emptyList()
      if (audioFolders.all { it.first.isBookPartFolder() }) {
        if (audioFolders.size > 1 && audioFolders.all { seriesFolderName.matches(it.first.name.orEmpty().trim()) } &&
          file.children.none { it.isAudioFile() }
        ) {
          return collect(file, audioFolders.map { it.first })
        }
        return listOf(file)
      }
    }
    return discovered.flatMap { it.second }
  }
  val books = discover(this, root = true)
  return DiscoveredLibrary(books, collections)
}

private fun CachedDocumentFile.hasAudio(): Boolean = if (isFile) isAudioFile() else children.any { it.hasAudio() }

private fun CachedDocumentFile.hasGrouping(grouping: Map<String, FolderGrouping>): Boolean =
  uri.toString() in grouping || (isDirectory && children.any { it.hasGrouping(grouping) })

public fun CachedDocumentFile.discoverAudiobooks(): List<CachedDocumentFile> = discoverLibrary().books

public fun CachedDocumentFile.isBookPartFolder(): Boolean = bookPartFolderName.matches(name.orEmpty().trim())

private val bookPartFolderName = Regex(
  """(?i)^((.*[\s._-])?(cd|disc|disk|tape|side)|part|teil|vol\.?|volume|series|season)?[\s._-]*\d{1,3}(\s*\(\d{4}\)|\s+[-–:]\s+.+)?$""",
)

private val seriesFolderName = Regex("""(?i)^(series|season)[\s._-]*\d{1,3}(\s+[-–:]\s+.+)?$""")
