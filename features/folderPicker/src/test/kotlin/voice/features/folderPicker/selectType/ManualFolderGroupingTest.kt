package voice.features.folderPicker.selectType

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.folders.FolderGrouping
import voice.core.data.folders.discoverLibrary
import voice.core.data.isAudioFile
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.documentfile.walk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ManualFolderGroupingTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `a book with irregular part names can be forced into one book`() {
    val root = library("Story/Beginning/01.mp3", "Story/Ending/01.mp3", "Other/01.mp3")
    val story = root.children.first { it.name == "Story" }
    val result = root.discoverLibrary(mapOf(story.uri.toString() to FolderGrouping.OneBook))
    assertEquals(setOf("Story", "Other"), result.books.map { it.name }.toSet())
    assertCoverage(root, result.books)
  }

  @Test
  fun `arbitrary named subfolders form a naturally ordered collection`() {
    val root = library("Stories/Adventure 10/CD 1/01.mp3", "Stories/Adventure 2/01.mp3", "Stories/Adventure 1/01.mp3", "Stories/cover.jpg")
    val stories = root.children.single()
    val result = root.discoverLibrary(mapOf(stories.uri.toString() to FolderGrouping.Collection))
    assertEquals(listOf("Adventure 1", "Adventure 2", "Adventure 10"), result.collections.single().parts.map { it.name })
    assertCoverage(root, result.books)
    assertEquals(emptyList(), root.discoverLibrary().collections)
  }

  @Test
  fun `one-book override combines an automatic series collection and resetting restores it`() {
    val root = library("Detective/Series 1/01.mp3", "Detective/Series 2/01.mp3")
    val detective = root.children.single()
    assertEquals(1, root.discoverLibrary().collections.size)
    val combined = root.discoverLibrary(mapOf(detective.uri.toString() to FolderGrouping.OneBook))
    assertEquals(listOf("Detective"), combined.books.map { it.name })
    assertEquals(emptyList(), combined.collections)
    assertCoverage(root, combined.books)
    assertEquals(1, root.discoverLibrary(emptyMap()).collections.size)
  }

  @Test
  fun `collection includes loose audio once and ignores empty or picture folders`() {
    val root = library("Story/CD 1/01.mp3", "Story/CD 2/01.mp3", "Story/Bonus.mp3", "Story/Pictures/cover.jpg")
    val story = root.children.single()
    val result = root.discoverLibrary(mapOf(story.uri.toString() to FolderGrouping.Collection))
    assertEquals(3, result.books.size)
    assertCoverage(root, result.books)
  }

  private fun library(vararg paths: String): CachedDocumentFile {
    val root = temp.newFolder()
    paths.forEach { path ->
      File(root, path).apply {
        parentFile!!.mkdirs()
        writeText("file")
      }
    }
    return FileBasedDocumentFile(root)
  }

  private fun assertCoverage(
    root: CachedDocumentFile,
    books: List<CachedDocumentFile>,
  ) {
    val expected = root.walk().filter { it.isAudioFile() }.map { it.uri }.toSet()
    val actual = books.flatMap { it.walk().filter { file -> file.isAudioFile() }.map { file -> file.uri }.toList() }
    assertEquals(expected, actual.toSet())
    assertEquals(expected.size, actual.size)
  }
}
