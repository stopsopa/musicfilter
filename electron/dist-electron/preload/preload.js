"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
// Preload script
const electron_1 = require("electron");
window.addEventListener('DOMContentLoaded', () => {
    const replaceText = (selector, text) => {
        const element = document.getElementById(selector);
        if (element)
            element.innerText = text;
    };
    for (const type of ['chrome', 'node', 'electron']) {
        replaceText(`${type}-version`, process.versions[type] || '');
    }
});
electron_1.contextBridge.exposeInMainWorld('electronAPI', {
    scanDirectory: (path) => electron_1.ipcRenderer.invoke('scan-directory', path),
    getPathForFile: (file) => electron_1.webUtils.getPathForFile(file),
    getMetadata: (path) => electron_1.ipcRenderer.invoke('get-metadata', path)
});
