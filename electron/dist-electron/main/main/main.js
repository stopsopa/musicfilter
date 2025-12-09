import { app, BrowserWindow, ipcMain } from "electron";
import path from "path";
import fs from "fs";
import { fileURLToPath } from 'url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const AUDIO_EXTENSIONS = ['.mp3', '.flac', '.ogg', '.wav', '.aiff', '.m4a'];
function scanDirectory(dir) {
    let results = [];
    try {
        const list = fs.readdirSync(dir);
        list.forEach(file => {
            const filePath = path.join(dir, file);
            const stat = fs.statSync(filePath);
            if (stat && stat.isDirectory()) {
                results = results.concat(scanDirectory(filePath));
            }
            else {
                if (AUDIO_EXTENSIONS.includes(path.extname(file).toLowerCase())) {
                    results.push(filePath);
                }
            }
        });
    }
    catch (err) {
        console.error(`Error scanning directory ${dir}:`, err);
    }
    return results;
}
function createWindow() {
    const win = new BrowserWindow({
        width: 800,
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
    }
    else {
        win.loadFile(path.join(__dirname, "../../dist/index.html"));
    }
}
app.whenReady().then(() => {
    ipcMain.handle('scan-directory', async (event, path) => {
        console.log('Main process received scan-directory for:', path);
        // If it's a file, return it if supported. If dir, scan it.
        try {
            const stat = fs.statSync(path);
            if (stat.isDirectory()) {
                return scanDirectory(path);
            }
            else if (AUDIO_EXTENSIONS.includes(require('path').extname(path).toLowerCase())) {
                return [path];
            }
            return [];
        }
        catch (e) {
            console.error("Error handling path:", path, e);
            return [];
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
