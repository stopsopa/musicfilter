# Ship Plan

## Goal

Prepare the `MusicFilter` Electron application for simple installation by end-users, leveraging GitHub Actions for automated releases.

## Strategy

1.  **Configuration**: Use `electron-builder` (already in devDependencies).
2.  **Architecture**: Use **Universal** binaries for macOS. This allows a single `.dmg` file to work on both Intel and Apple Silicon (M1/M2/M3) Macs. This is the simplest approach for users.
3.  **Automation**: GitHub Actions to build and upload assets whenever a new version tag (e.g., `v1.0.0`) is pushed.
4.  **Distribution**: GitHub Releases.

## 1. Prepare `package.json`

We need to add a `build` configuration object to `electron/package.json`.

```json
"build": {
  "appId": "com.stopsopa.musicfilter",
  "productName": "MusicFilter",
  "directories": {
    "output": "release"
  },
  "mac": {
    "category": "public.app-category.music",
    "target": {
      "target": "dmg",
      "arch": [
        "universal"
      ]
    },
    "hardenedRuntime": true,
    "gatekeeperAssess": false
  },
  "files": [
    "dist-electron/**/*",
    "dist/**/*"
  ]
}
```

_Note: Without an Apple Developer ID ($99/yr), the app will be unsigned. Users will have to right-click (Control-click) and choose "Open" the first time to bypass Gatekeeper._

## 2. GitHub Actions Workflow

Create `.github/workflows/release.yml`.

- **Trigger**: Push to tags `v*`.
- **Job**: `build-mac`
- **Steps**:
  - Checkout code.
  - Setup Node.
  - Install dependencies.
  - Run build (`npm run build`).
  - Run release (`electron-builder --publish always`).
    - Requires `GH_TOKEN` (automatically provided by GitHub Actions).

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
