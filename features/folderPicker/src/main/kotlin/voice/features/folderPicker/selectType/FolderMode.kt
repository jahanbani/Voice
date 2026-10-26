package voice.features.folderPicker.selectType

import voice.core.data.folders.FolderType

internal enum class FolderMode {
  Automatic,
  SingleBook,
  Audiobooks,
  Authors,
  ;

  fun toFolderType(): FolderType = when (this) {
    Automatic -> FolderType.Automatic
    SingleBook -> FolderType.SingleFolder
    Audiobooks -> FolderType.Root
    Authors -> FolderType.Author
  }
}

internal fun FolderType.toFolderMode(): FolderMode? = when (this) {
  FolderType.Automatic -> FolderMode.Automatic
  FolderType.SingleFile -> null
  FolderType.SingleFolder -> FolderMode.SingleBook
  FolderType.Root -> FolderMode.Audiobooks
  FolderType.Author -> FolderMode.Authors
}
