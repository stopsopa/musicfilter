export interface AudioFile {
  path: string;
  name: string;
  // Metadata will be added in Phase 2
  title?: string;
  artist?: string;
  album?: string;
  duration?: number;
}
