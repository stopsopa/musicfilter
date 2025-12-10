import { useState, useEffect, useRef, type DragEvent } from 'react';
import './App.css';
import { FileDropZone } from './components/FileDropZone';
import type { AudioFile } from './types';
import { getPlayableUrl } from './audioTranscoder';

function App() {
  const [files, setFiles] = useState<AudioFile[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState<number>(-1);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isTranscoding, setIsTranscoding] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement>(null);
  const currentBlobUrlRef = useRef<string | null>(null);

  useEffect(() => {
    const preventDefault = (e: Event) => e.preventDefault();
    window.addEventListener('dragover', preventDefault);
    window.addEventListener('drop', preventDefault);
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('dragover', preventDefault);
      window.removeEventListener('drop', preventDefault);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [files, selectedIndex]);

  useEffect(() => {
    if (selectedIndex >= 0 && selectedIndex < files.length && audioRef.current) {
        const file = files[selectedIndex];
        
        // Revoke previous blob URL to prevent memory leaks
        if (currentBlobUrlRef.current && currentBlobUrlRef.current.startsWith('blob:')) {
          URL.revokeObjectURL(currentBlobUrlRef.current);
          currentBlobUrlRef.current = null;
        }

        setIsTranscoding(true);
        getPlayableUrl(file.path)
          .then(url => {
            if (audioRef.current) {
              currentBlobUrlRef.current = url;
              audioRef.current.src = url;
              audioRef.current.play().catch(e => console.error("Playback error", e));
            }
          })
          .catch(e => console.error("Transcoding error", e))
          .finally(() => setIsTranscoding(false));
        
        // Auto-scroll
        const row = document.getElementById(`file-row-${selectedIndex}`);
        if (row) {
            row.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }
  }, [selectedIndex]);

  const handleTimeUpdate = () => {
    if (audioRef.current) {
        setCurrentTime(audioRef.current.currentTime);
    }
  };

  const handleDurationChange = () => {
      if (audioRef.current) {
          setDuration(audioRef.current.duration);
      }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
      const time = parseFloat(e.target.value);
      if (audioRef.current) {
          audioRef.current.currentTime = time;
          setCurrentTime(time);
      }
  };

  const togglePlayPause = () => {
      if (audioRef.current) {
          if (audioRef.current.paused) {
              audioRef.current.play();
          } else {
              audioRef.current.pause();
          }
      }
  };

  const handleKeyDown = (e: KeyboardEvent) => {
    if (files.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => Math.min(prev + 1, files.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === ' ') {
        e.preventDefault();
        if (audioRef.current) {
            if (audioRef.current.paused) audioRef.current.play();
            else audioRef.current.pause();
        }
    }
  };

  // ... (drag handlers similar to before)

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (!isDragging) setIsDragging(true);
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setIsDragging(false);
  };

  const handleDrop = async (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);

    const droppedFiles = Array.from(e.dataTransfer.files);
    const paths = droppedFiles.map((f: File) => {
        try {
            return window.electronAPI.getPathForFile(f);
        } catch { return null; }
    }).filter((p): p is string => p !== null);

    const newFiles: AudioFile[] = [];

    for (const p of paths) {
        const result = await window.electronAPI.scanDirectory(p);
        for (const filePath of result) {
             const metadata = await window.electronAPI.getMetadata(filePath);
             newFiles.push({
                 path: filePath,
                 name: filePath.split(/[/\\]/).pop() || filePath,
                 title: metadata.title,
                 artist: metadata.artist,
                 album: metadata.album,
                 duration: metadata.duration
             });
        }
    }

    setFiles(prev => {
        const existingPaths = new Set(prev.map(f => f.path));
        const uniqueNew = newFiles.filter(f => !existingPaths.has(f.path));
        return [...prev, ...uniqueNew];
    });
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds && seconds !== 0) return '--:--';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div 
        className="container"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
    >
      <div className="header-compact">
          <h1>Music Filter</h1>
          
          <div className="player-controls">
            <div className="now-playing">
                {isTranscoding ? (
                    <span>Transcoding...</span>
                ) : selectedIndex >= 0 && files[selectedIndex] ? (
                    <span><b>{files[selectedIndex].title || files[selectedIndex].name}</b></span>
                ) : (
                    <span>Select a track</span>
                )}
            </div>
            <div className="scrubber-container">
                <button className="play-pause-btn" onClick={togglePlayPause}>
                    {isPlaying ? '⏸' : '▶'}
                </button>
                <span className="time-display">{formatDuration(currentTime)}</span>
                <input 
                    type="range" 
                    className="scrubber"
                    min="0"
                    max={duration || 100}
                    value={currentTime}
                    onChange={handleSeek}
                />
                <span className="time-display">{formatDuration(duration)}</span>
            </div>
          </div>

          <audio 
            ref={audioRef} 
            style={{ display: 'none' }} 
            onTimeUpdate={handleTimeUpdate}
            onLoadedMetadata={handleDurationChange}
            onPlay={() => setIsPlaying(true)}
            onPause={() => setIsPlaying(false)}
            onError={(e) => console.error("Audio Error:", e.currentTarget.error)}
          /> 
      </div>
      
      {isDragging && (
          <div className="drop-overlay">
            <FileDropZone isActive={true} />
          </div>
      )}

      {files.length === 0 && !isDragging && (
           <div className="empty-placeholder">
               <FileDropZone isActive={false} />
           </div>
      )}

      <div className="file-list-container">
        <table className="file-table">
            <thead>
                <tr>
                    <th style={{ width: '40px' }}>#</th>
                    <th>Filename</th>
                    <th>Title</th>
                    <th>Artist</th>
                    <th>Album</th>
                    <th style={{ width: '60px' }}>Time</th>
                </tr>
            </thead>
            <tbody>
                {files.map((file, index) => (
                    <tr 
                        key={file.path} 
                        id={`file-row-${index}`}
                        className={selectedIndex === index ? 'selected' : ''}
                        onClick={() => setSelectedIndex(index)}
                    >
                        <td className="index-col">{index + 1}</td>
                        <td className="filename-col" title={file.name}>{file.name}</td>
                        <td>{file.title || '-'}</td>
                        <td>{file.artist || '-'}</td>
                        <td>{file.album || '-'}</td>
                        <td className="time-col">{formatDuration(file.duration)}</td>
                    </tr>
                ))}
            </tbody>
        </table>
      </div>
    </div>
  );
}

export default App;
