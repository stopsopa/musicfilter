/// <reference types="vite/client" />

interface ElectronAPI {
  scanDirectory: (path: string) => Promise<string[]>;
  getPathForFile: (file: File) => string;
}

interface Window {
  electronAPI: ElectronAPI;
}

// File interface doesn't need path anymore, as we use webUtils
interface File {
  // path: string; // Removed as we use getPathForFile
}
