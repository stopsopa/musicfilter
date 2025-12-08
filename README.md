# Music Filter App

A simple JavaFX application to filter MP3 files in a folder.

## Prerequisites

- Java 17 or later
- Maven

## Usage

1.  **Install/Update Dependencies**:

    ```bash
    ./install.sh
    ```

2.  **Compile**:

    ```bash
    ./compile.sh
    ```

3.  **Run**:
    ```bash
    ./run.sh
    ```

## Features

- **Drag & Drop**: Drop MP3 files or folders containing MP3s onto the application window.
- **Navigation**:
  - `UP` / `DOWN` arrows: Navigate the list and auto-play the selected song.
  - `LEFT` / `RIGHT` arrows: Seek backward/forward by 3 seconds.
- **Deletion**:
  - `BACKSPACE`: Stop playback, remove the song from the list, and move the file to Trash (or delete if Trash is unavailable).
