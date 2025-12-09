// Preload script
import { contextBridge, ipcRenderer, webUtils } from 'electron';

window.addEventListener('DOMContentLoaded', () => {
  const replaceText = (selector: string, text: string) => {
    const element = document.getElementById(selector);
    if (element) element.innerText = text;
  };

  for (const type of ['chrome', 'node', 'electron']) {
    replaceText(`${type}-version`, process.versions[type as keyof NodeJS.ProcessVersions] || '');
  }
});

contextBridge.exposeInMainWorld('electronAPI', {
  scanDirectory: (path: string) => ipcRenderer.invoke('scan-directory', path),
  getPathForFile: (file: File) => webUtils.getPathForFile(file)
});
