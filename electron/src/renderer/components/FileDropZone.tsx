import React from 'react';

interface FileDropZoneProps {
  isActive: boolean;
}

export const FileDropZone: React.FC<FileDropZoneProps> = ({ isActive }) => {
  return (
    <div
      style={{
        border: '2px dashed #ccc',
        borderColor: isActive ? '#007bff' : '#ccc',
        borderRadius: '8px',
        padding: '20px',
        textAlign: 'center',
        margin: '20px',
        backgroundColor: isActive ? 'rgba(0, 123, 255, 0.1)' : 'transparent',
        transition: 'all 0.2s ease',
        minHeight: '200px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        color: '#888'
      }}
    >
      <p style={{ fontSize: '1.2em', marginBottom: '10px' }}>
        {isActive ? 'Drop files here...' : 'Drag & drop music folders or files here'}
      </p>
      <p style={{ fontSize: '0.9em' }}>
        Supported formats: MP3, FLAC, OGG, WAV, AIFF, M4A
      </p>
    </div>
  );
};
