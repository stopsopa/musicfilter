
import { app, BrowserWindow, ipcMain } from "electron";
import path from "path";
import fs from "fs";
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUDIO_EXTENSIONS = ['.mp3', '.flac', '.ogg', '.wav', '.aiff', '.m4a'];

function scanDirectory(dir: string): string[] {
  let results: string[] = [];
  try {
    const list = fs.readdirSync(dir);
    list.forEach(file => {
      const filePath = path.join(dir, file);
      const stat = fs.statSync(filePath);
      if (stat && stat.isDirectory()) {
        results = results.concat(scanDirectory(filePath));
      } else {
        if (AUDIO_EXTENSIONS.includes(path.extname(file).toLowerCase())) {
          results.push(filePath);
        }
      }
    });
  } catch (err) {
    console.error(`Error scanning directory ${dir}:`, err);
  }
  return results;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1300,
    height: 600,
    webPreferences: {
      preload: path.join(__dirname, "../preload/preload.js"),
      nodeIntegration: false, // Security best practice when using contextIsolation
      contextIsolation: true, // Required for contextBridge
      webSecurity: false, // Often needed for local file access with FFmpeg
    },
  });

  if (process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else {
    win.loadFile(path.join(__dirname, "../../dist/index.html"));
  }
}

app.whenReady().then(() => {
  ipcMain.handle('scan-directory', async (event, inputPath) => {
    console.log('Main process received scan-directory for:', inputPath);
    // If it's a file, return it if supported. If dir, scan it.
    try {
        const stat = fs.statSync(inputPath);
        if (stat.isDirectory()) {
            return scanDirectory(inputPath);
        } else if (AUDIO_EXTENSIONS.includes(path.extname(inputPath).toLowerCase())) {
            return [inputPath];
        }
        return [];
    } catch (e) {
        console.error("Error handling path:", inputPath, e);
        return [];
    }
  });

  ipcMain.handle('read-file-buffer', async (_event, filePath: string) => {
    try {
      const buffer = fs.readFileSync(filePath);
      return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
    } catch (error) {
      console.error('Error reading file buffer:', filePath, error);
      return null;
    }
  });

  ipcMain.handle('get-metadata', async (event, filePath) => {
    try {
        const { parseFile } = await import('music-metadata');
        const metadata = await parseFile(filePath);
        return {
            title: metadata.common.title,
            artist: metadata.common.artist,
            album: metadata.common.album,
            duration: metadata.format.duration
        };
    } catch (error) {
        console.error('Error reading metadata for:', filePath, error);
        return {};
    }
  });

  ipcMain.handle('soft-delete-file', async (_event, filePath) => {
    try {
      const dir = path.dirname(filePath);
      const filename = path.basename(filePath);
      const deleteDir = path.join(dir, 'delete');
      
      if (!fs.existsSync(deleteDir)) {
        fs.mkdirSync(deleteDir);
      }
      
      const newPath = path.join(deleteDir, filename);
      fs.renameSync(filePath, newPath);
      return { success: true, newPath };
    } catch (error) {
      console.error('Error soft deleting file:', filePath, error);
      return { success: false, error: String(error) };
    }
  });

  ipcMain.handle('restore-file', async (_event, filePath) => {
    try {
        // filePath is currently in the delete folder
        const filename = path.basename(filePath);
        const deleteDir = path.dirname(filePath);
        const originalDir = path.dirname(deleteDir);
        
        // Sanity check that we are in a 'delete' folder
        if (path.basename(deleteDir) !== 'delete') {
            throw new Error('File is not in a delete folder');
        }

        const originalPath = path.join(originalDir, filename);
        
        fs.renameSync(filePath, originalPath);
        return { success: true, newPath: originalPath };
    } catch (error) {
        console.error('Error restoring file:', filePath, error);
        return { success: false, error: String(error) };
    }
  });

  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
