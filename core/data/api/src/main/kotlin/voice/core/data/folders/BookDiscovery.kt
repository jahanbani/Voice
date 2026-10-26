package voice.core.data.folders

import voice.core.data.isAudioFile
import voice.core.documentfile.CachedDocumentFile

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
