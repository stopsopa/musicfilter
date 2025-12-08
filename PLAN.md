# Project Plan & History

This document tracks the evolution of the Music Filter project, documenting user requests and the resulting implementation decisions.

## 1. Initial Request

**Goal**: Create a simple Java app to filter MP3 files.
**Features**:

- **Drag & Drop**: Accept MP3 files or folders containing them.
- **List View**: Render a list of the dropped files.
- **Navigation**:
  - `UP`/`DOWN` arrows: Navigate the list and auto-play the selected song.
  - `LEFT`/`RIGHT` arrows: Seek backward/forward by 3 seconds.
- **Deletion**:
  - `BACKSPACE`: Remove song from list and delete the file from the hard drive (ideally to Trash). No confirmation needed.

## 2. Native Desktop Application

**Refinement**: The user specified it must be a "native desktop application".
**Decision**: Use **JavaFX** as it provides:

- Native packaging capabilities.
- Built-in `MediaPlayer` for MP3s.
- Rich UI controls for Drag & Drop and Lists.

## 3. Maintenance Scripts & Documentation

**Refinement**: Create Bash scripts for easy maintenance and a README.
**Deliverables**:

- `install.sh`: Installs dependencies (`mvn clean install -U`).
- `compile.sh`: Compiles the project (`mvn compile`).
- `run.sh`: Runs the application (`mvn javafx:run`).
- `README.md`: Instructions on how to use the scripts and the app.

## 4. Playback Controls

**Refinement**: Add basic UI controls for playback.
**Features**:

- **Play/Pause**: A button to toggle playback.
- **Slider**: A slider to visualize progress and seek to any point in the track.

## 5. Documentation (This File)

## 6. Git Ignore

**Refinement**: Update `.gitignore` to include all necessary ignore patterns.

## 7. Global Shortcuts & Metadata

**Refinement**:

- **Global Key Bindings**: Ensure shortcuts (Arrows, Space, Backspace) work regardless of which control has focus.
- **Metadata Columns**: Display Title, Artist, and Album in the list (requires switching to a TableView).

## 8. Soft Delete & Restore

**Refinement**:

- **Soft Delete**: Pressing `BACKSPACE` should move the file to a temporary location (e.g., `_deleted` folder) and gray out the item in the list instead of removing it.
- **Restore**: Pressing `BACKSPACE` on a grayed-out item should restore the file to its original location and un-gray it.
- **Persistence**: It is acceptable that this state is lost on app restart (files remain in the deleted folder).
