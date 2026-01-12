## Installation from binary

Just go to [Github releases](../../releases), pick the latest release, and download the binary, and install it like any other app.

## Known issues:

- [MusicFilter.app is damaged and can't be opened. You should move it to the Bin](https://github.com/stopsopa/musicfilter/issues/1)

# Music Filter

Music Filter is a lightweight desktop utility designed for efficient music library curation. It allows users to quickly preview, organize, and filter large collections of MP3 files through a streamlined, keyboard-driven interface.

![Image](https://github.com/user-attachments/assets/9442183d-3c50-4a52-ac7b-a90261434df3)

## Key Features

- **Drag & Drop**: Effortlessly load individual MP3 files or entire folders.
- **Instant Preview**: Navigate through your song list and hear previews immediately with `UP`/`DOWN` arrows.
- **Fast Navigation**: Seek through tracks using `LEFT`/`RIGHT` arrows.
- **Safe Filtering**: Pressing `BACKSPACE` moves files to a local `_deleted` folder, allowing for quick curation without immediate permanent deletion.
- **Metadata Visibility**: View track information (Title, Artist, Album) at a glance.

Most usable version of this app seems to be the one Electron based using ffmpeg internally: [electron](electron/README.md)
