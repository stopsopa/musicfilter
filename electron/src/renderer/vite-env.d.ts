/// <reference types="vite/client" />

interface ElectronAPI {
  scanDirectory: (path: string) => Promise<string[]>;
  getPathForFile: (file: File) => string;
  getMetadata: (path: string) => Promise<{ title?: string; artist?: string; album?: string; duration?: number }>;
  readFileBuffer: (path: string) => Promise<ArrayBuffer | null>;
}

interface Window {
  electronAPI: ElectronAPI;
}

// File interface doesn't need path anymore, as we use webUtils
interface File {
  // path: string; // Removed as we use getPathForFile
}
