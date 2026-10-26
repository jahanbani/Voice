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
audio subfolders is one book. A folder whose audio subfolders are all numbered parts, discs, volumes, series or seasons
is also one book, with the files in those subfolders played in their usual order. Other folders are collections: Voice
continues searching inside them and treats their loose audio files as separate books. Folders without audio are ignored.

For example, `Audiobooks/Radio/Jane Austen/Emma/01.mp3` becomes **Emma**, while
`Audiobooks/Radio/Detective/Series 1/01.mp3` and `Series 2/01.mp3` become one **Detective** book.
Single-file books directly inside `Audiobooks` or a collection are found too.

Check the preview before adding a folder. These are folder-based guesses, not metadata-based identification: unnumbered
part folders may become separate books, and several complete books stored together in one leaf folder become one book.
Use **One book** for a folder whose parts should always stay together. Automatic discovery does not add per-book
combine/split controls or new part headings to the player. Existing chapter ordering and playback controls are used.
Your files and folders are never reorganized.
