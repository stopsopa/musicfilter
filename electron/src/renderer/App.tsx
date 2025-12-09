import { useState, useEffect, type DragEvent } from 'react';
import './App.css';
import { FileDropZone } from './components/FileDropZone';
import type { AudioFile } from './types';

function App() {
  const [files, setFiles] = useState<AudioFile[]>([]);
  const [isDragging, setIsDragging] = useState(false);

  useEffect(() => {
    // Prevent default browser behavior globally
    const preventDefault = (e: Event) => e.preventDefault();
    window.addEventListener('dragover', preventDefault);
    window.addEventListener('drop', preventDefault);
    return () => {
      window.removeEventListener('dragover', preventDefault);
      window.removeEventListener('drop', preventDefault);
    };
  }, []);

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (!isDragging) {
        console.log('Drag Over Detected');
        setIsDragging(true);
    }
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    // Only set to false if we are leaving the main container
    if (e.currentTarget.contains(e.relatedTarget as Node)) {
        return;
    }
    console.log('Drag Leave Detected');
    setIsDragging(false);
  };

  const handleDrop = async (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    console.log('Drop Detected');
    setIsDragging(false);

    const droppedFiles = Array.from(e.dataTransfer.files);
    console.log('Files dropped:', droppedFiles);

    // Use webUtils via preload to get path
    const paths = droppedFiles.map((f: File) => {
        try {
            return window.electronAPI.getPathForFile(f);
        } catch (err) {
            console.error('Error getting path for file:', f.name, err);
            return null;
        }
    });
    console.log('Paths extracted:', paths);

    const allFiles: AudioFile[] = [];

    for (const p of paths) {
        if (!p) {
            console.warn('No path found for file');
            continue;
        }
        console.log('Scanning:', p);
        const result = await window.electronAPI.scanDirectory(p);
        console.log('Scan result:', result);
        
        result.forEach(r => {
            allFiles.push({
                path: r,
                name: r.split(/[/\\]/).pop() || r
            });
        });
    }

    setFiles(prev => {
        const existingPaths = new Set(prev.map(f => f.path));
        const filteredNew = allFiles.filter(f => !existingPaths.has(f.path));
        return [...prev, ...filteredNew];
    });
  };

  return (
    <div 
        className="container"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
    >
      <h1>Music Filter</h1>
      
      <FileDropZone isActive={isDragging} />

      <div className="file-list">
        <h2>Files ({files.length})</h2>
        {files.length === 0 ? (
            <p className="empty-state">No files loaded yet.</p>
        ) : (
            <ul>
                {files.map((file, index) => (
                    <li key={file.path} className="file-item">
                        <span className="file-index">{index + 1}.</span>
                        <span className="file-name">{file.name}</span>
                        <span className="file-path" title={file.path}>{file.path}</span>
                    </li>
                ))}
            </ul>
        )}
      </div>
    </div>
  );
}

export default App;
