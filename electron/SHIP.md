# Ship Plan

## Goal

Prepare the `MusicFilter` Electron application for simple installation by end-users, leveraging GitHub Actions for automated releases.

## Strategy

1.  **Release Drafter**: Automatically creates a "Draft Release" with categorized changelogs (Features, Fixes, etc.) based on PRs.
2.  **UI Driven Release**: Maintainer reviews the draft in GitHub Releases UI and clicks "Publish".
3.  **Automation**: "Publish" action triggers the build workflow.
4.  **Architecture**: Universal binaries (x64/arm64) for macOS.

## 1. Prepare `package.json`

(Completed) `electron-builder` configured for universal dmg.

## 2. GitHub Actions Workflow

- `.github/workflows/release-drafter.yml`: Updates the draft release on every merge to `main`.
- `.github/workflows/release.yml`: Triggered when a release is **published**. Builds and uploads assets.

## 3. How to Release

1.  **Merge Changes**: Merge PRs to `main`. `release-drafter` will automatically update the draft release with new entries.
2.  **Review**: Go to [Releases](../../releases). Edit the draft if needed (e.g., adjust version number sequence).
3.  **Publish**: Click **Publish release**.
    - This creates the tag (e.g., `v1.0.0`).
    - Triggers the build workflow.
    - Uploads `MusicFilter-*-universal.dmg` to the release.

## 3. Installation Instructions for Users

(To be added to `README.md`)

### Installation (macOS)

1.  Go to the [Releases](../../releases) page.
2.  Download the `.dmg` file (e.g., `MusicFilter-1.0.0-universal.dmg`).
3.  Double-click to open.
4.  Drag `MusicFilter` to the `Applications` folder.
5.  **First Run**:
    - Since this app is not signed by Apple, you might see a warning: _"MusicFilter" can't be opened because it is from an unidentified developer._
    - To fix this:
      1.  **Right-click** (or Control-click) the app in your Applications folder.
      2.  Select **Open**.
      3.  Click **Open** in the dialog box.
    - You only need to do this once.
