package voice.features.folderPicker.selectType

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.folders.discoverLibrary
import voice.core.documentfile.FileBasedDocumentFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FolderStructureTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `a folder of chapter files is one book`() {
    assertGuess(FolderMode.SingleBook, "01.mp3", "02.mp3", "03.mp3")
  }

  @Test
  fun `a folder split into discs is one book`() {
    assertGuess(FolderMode.SingleBook, "CD 1/01.mp3", "CD 2/01.mp3", "My Book - Disc2/01.mp3")
  }

  @Test
  fun `a folder named audiobooks with chapter files is still one book`() {
    assertGuess(FolderMode.SingleBook, "1.mp3", "2.mp3", folderName = "Audiobooks")
  }

  @Test
  fun `chapter files with a bonus folder are one book`() {
    assertGuess(FolderMode.SingleBook, "01.mp3", "02.mp3", "03.mp3", "Bonus/interview.mp3")
  }

  @Test
  fun `several audiobook files are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune.m4b", "Hyperion.m4b")
  }

  @Test
  fun `a single audiobook file is a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune.m4b")
  }

  @Test
  fun `audiobook files with a stray chapter file are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune.m4b", "Hyperion.m4b", "intro.mp3")
  }

  @Test
  fun `book folders are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune/01.mp3", "Hyperion/01.mp3", "Hyperion/CD 2/01.mp3", "Neuromancer.m4b")
  }

  @Test
  fun `folders of book folders are authors`() {
    assertGuess(
      FolderMode.Authors,
      "Frank Herbert/Dune/01.mp3",
      "Frank Herbert/Children of Dune/01.mp3",
      "Dan Simmons/Hyperion/01.mp3",
    )
  }

  @Test
  fun `a book that looks like several books is flagged`() {
    val folder = folder("Discworld/Mort/1.mp3", "Discworld/Eric/1.mp3", "Dune/1.mp3", "Dune/CD 2/1.mp3")

    val books = folder.books(FolderMode.Audiobooks).map { it.book }.associateBy { it.name }

    assertEquals(expected = 2, actual = books.getValue("Discworld").possibleBookCount)
    assertEquals(expected = 0, actual = books.getValue("Dune").possibleBookCount)
    assertEquals(expected = 1, actual = books.getValue("Dune").partCount)
  }

  @Test
  fun `books in author mode carry their author`() {
    val folder = folder("Dan Simmons/Hyperion/01.mp3", "Dan Simmons/Hyperion/02.mp3", "Loose.m4b")

    assertEquals(
      expected = listOf(
        SelectFolderTypeViewState.Book("Hyperion", author = "Dan Simmons", fileCount = 2, partCount = 0, possibleBookCount = 0),
        SelectFolderTypeViewState.Book("Loose", author = null, fileCount = 1, partCount = 0, possibleBookCount = 0),
      ),
      actual = folder.books(FolderMode.Authors).map { it.book }.sortedBy { it.name },
    )
  }

  @Test
  fun `folders without audio are no books`() {
    val folder = folder("Dune/01.mp3", "Covers/dune.jpg")

    assertEquals(expected = listOf("Dune"), actual = folder.books(FolderMode.Audiobooks).map { it.book.name })
  }

  @Test
  fun `mixed layouts are discovered at every depth`() {
    val folder = folder(
      "Loose.m4b",
      "A Book/1.mp3",
      "A Book/2.mp3",
      "Radio/Novels/Emma/1.mp3",
      "Radio/Novels/Persuasion/1.mp3",
      "Radio/Detective/Series 1/1.mp3",
      "Radio/Detective/Series 2/1.mp3",
      "Radio/Standalone.m4b",
      "Covers/cover.jpg",
    )

    assertEquals(FolderMode.Automatic, folder.guessFolderMode())
    val books = folder.books(FolderMode.Automatic).map { it.book }.associateBy { it.name }
    assertEquals(setOf("Loose", "A Book", "Emma", "Persuasion", "Detective", "Standalone"), books.keys)
    assertEquals(2, books.getValue("Detective").partCount)
    assertEquals(2, books.getValue("Detective").fileCount)
  }

  @Test
  fun `automatic discovery keeps discs and numbered volumes together`() {
    val folder = folder(
      "Book/CD 1/1.mp3",
      "Book/CD 2/1.mp3",
      "Drama/Volume 01 (2016)/1.mp3",
      "Drama/Volume 02 (2017)/1.mp3",
      "Other/01/1.mp3",
      "Other/02/1.mp3",
      "Detective/Series 1/1.mp3",
      "Detective/Series 8 - More adventures/1.mp3",
    )

    assertEquals(
      listOf("Book", "Detective", "Drama", "Other"),
      folder.books(FolderMode.Automatic).map { it.book.name }.sorted(),
    )
  }

  @Test
  fun `non audio folders do not split a book into separate chapters`() {
    val folder = folder("Book/1.mp3", "Book/2.mp3", "Book/Artwork/cover.jpg", "Empty/readme.txt")

    assertEquals(listOf("Book"), folder.books(FolderMode.Automatic).map { it.book.name })
  }

  @Test
  fun `fixed depth layouts remain available for a mixed library`() {
    val folder = folder("Radio/Author/Novel/1.mp3", "Other/1.mp3")

    assertEquals(listOf("Other", "Radio"), folder.books(FolderMode.Audiobooks).map { it.book.name }.sorted())
    assertEquals(listOf("Novel", "Other"), folder.books(FolderMode.Automatic).map { it.book.name }.sorted())
  }

  @Test
  fun `series become collections while compact discs stay one book`() {
    val library = folder(
      "Detective/Series 10/01.mp3",
      "Detective/Series 2/01.mp3",
      "Detective/Series 1/01.mp3",
      "Novel/CD 1/01.mp3",
      "Novel/CD 2/01.mp3",
      "Loose.m4b",
    ).discoverLibrary()

    assertEquals(listOf("Detective"), library.collections.map { it.name })
    assertEquals(listOf("Series 1", "Series 2", "Series 10"), library.collections.single().parts.map { it.name })
    assertEquals(listOf("Loose.m4b", "Novel", "Series 1", "Series 10", "Series 2"), library.books.map { it.name.orEmpty() }.sorted())
  }

  @Test
  fun `loose audio in a series container stays in its combined book`() {
    val library = folder("Detective/Intro.mp3", "Detective/Series 1/01.mp3", "Detective/Series 2/01.mp3").discoverLibrary()

    assertEquals(emptyList(), library.collections)
    assertEquals(listOf("Detective"), library.books.map { it.name })
  }

  private fun assertGuess(
    expected: FolderMode,
    vararg files: String,
    folderName: String = "folder",
  ) {
    assertEquals(expected = expected, actual = folder(*files, folderName = folderName).guessFolderMode())
  }

  private fun folder(
    vararg files: String,
    folderName: String = "folder",
  ): FileBasedDocumentFile {
    val root = temporaryFolder.newFolder(folderName)
    files.forEach { path ->
      File(root, path).apply {
        parentFile!!.mkdirs()
        createNewFile()
      }
    }
    return FileBasedDocumentFile(root)
  }
}
