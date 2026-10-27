# Organizing Audiobooks

Pick the folder with your audiobooks. Voice looks at how the files inside are organized and shows you the books it found before
adding them. If the books don't look right, tap **Change how books are found** and pick one of the four options below. You can
change this later in the settings under **Audiobook folders** by tapping the folder. Your progress and bookmarks stay with your books.

## 1. **Every folder is a book**

Each folder within your selected folder is a separate audiobook. Any audio files directly inside the selected folder (not in a
subfolder) are audiobooks as well.

**Example:**

```
/Audiobooks
├─ TheHobbit/
│   ├─ chapter1.mp3
│   └─ chapter2.mp3
├─ MobyDick/
│   ├─ chapter1.mp3
│   └─ chapter2.mp3
└─ LittlePrince.mp3
```

Voice recognizes three audiobooks: `TheHobbit`, `MobyDick`, and the single-file book `LittlePrince`.

## 2. **One book**

The selected folder itself is one audiobook, with files inside treated as chapters. This includes files in subfolders, such as
`CD 1` and `CD 2`.

**Example:**

```
/PrideAndPrejudice
├─ Chapter1.mp3
├─ Chapter2.mp3
└─ Chapter3.mp3
```

Voice recognizes one audiobook: `PrideAndPrejudice`.

## 3. **Folders are authors**

First-level folders represent authors (or series), and their subfolders represent audiobooks.

**Example:**

```
/Authors
├─ Woolf/
│   ├─ MrsDalloway/
│   │   ├─ chapter1.mp3
│   │   └─ chapter2.mp3
│   └─ ToTheLighthouse/
│       ├─ chapter1.mp3
│       └─ chapter2.mp3
└─ Tolkien/
    └─ TheHobbit/
        ├─ chapter1.mp3
        └─ chapter2.mp3
```

Voice recognizes authors (`Woolf`, `Tolkien`) and their audiobooks (`MrsDalloway`, `ToTheLighthouse`, `TheHobbit`).
## 4. **Automatic**

For a library with mixed layouts, Voice searches through collections at any depth. A folder with audio files and no
audio subfolders is one book. A folder whose audio subfolders are all numbered parts, discs or volumes
is also one book, with the files in those subfolders played in their usual order. Numbered series and seasons form
browsable collections as described below. For other folders, Voice
continues searching inside them and treats their loose audio files as separate books. Folders without audio are ignored.

For example, `Audiobooks/Radio/Jane Austen/Emma/01.mp3` becomes **Emma**, while
`Audiobooks/Radio/Detective/Series 1/01.mp3` and `Series 2/01.mp3` become a **Detective** collection with two series.
Single-file books directly inside `Audiobooks` or a collection are found too.

Check the preview before adding a folder. These are folder-based guesses, not metadata-based identification: unnumbered
part folders may become separate books, and several complete books stored together in one leaf folder become one book.
Use **One book** for a folder whose parts should always stay together, or use the folder grouping controls below.
Existing chapter ordering and playback controls are used.
Your files and folders are never reorganized.

### Browsing series

With Automatic discovery, a book containing two or more audio subfolders named `Series 1`, `Series 2`, and so on
(or `Season 1`, `Season 2`) appears as a collection. Tap it to see its series in numbered order. Each series has its
own listening position and play button; **Continue listening** opens an unfinished series, preferring the current
or most recently played one. A completed collection remains available for replay.
Ordinary books still open directly in the player, and `CD 1` / `CD 2` folders still form one book.

When upgrading from the earlier discovery build, the current chapter, position and bookmarks move from the combined
book into the series containing those files. Other series start with their own positions. The library rescans when
you open it; there is no need to remove the audiobook folder or reorganize files.

This applies to numbered Series/Season folders in Automatic mode. Collections with arbitrary folder names or loose
audio beside their series remain under the existing discovery rules. Search and external playback surfaces continue
to list the individual series. When a series finishes, the player offers **Next in this collection**, skipping finished
or missing later series. Playback waits for a tap, so a sleep timer never unexpectedly starts another series.

### Correcting grouping

Open **Settings → Folder grouping**, then browse a root added in **Automatic** mode and select a folder.
**One book** combines all its audio, including subfolders. **Collection** keeps each immediate audio subfolder as
one book, regardless of its name, and each loose audio file as a separate book. Parts are naturally sorted.
**Automatic** removes the override and restores discovery. An explicit parent choice controls its whole subtree;
switch it to Automatic before editing a deeper folder. Choices survive restarts and trigger a rescan.
Changing grouping pauses playback and carries the current position and bookmarks to the matching audio files.

### Backing up listening positions

In **Settings → Listening backup**, export a JSON file containing local-book positions and bookmarks, including
their notes and kinds. On another phone, add the same audio files using the same book grouping before importing.
Voice matches an unchanged book ID or a unique ordered list of audio filenames and sizes. Missing books,
ambiguous copies and changed files are skipped; the review shows counts before applying anything.

Import pauses playback, replaces matched positions and adds missing bookmarks. Existing bookmark notes are kept.
The database changes are transactional; malformed or unsupported backups are rejected before writing anything.
Backups are limited to 8 MiB. Audio, covers, settings, folder permissions, grouping choices, listening history and
Audiobookshelf server progress are not included. Providers that do not report filenames and sizes can only restore
the same chapter IDs on the same device.
