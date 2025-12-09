package com.stopsopa.musicfilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MetadataParser {

    public static Map<String, Object> parse(File file) {
        Map<String, Object> metadata = new HashMap<>();
        String name = file.getName().toLowerCase();

        System.out.println("Parsing metadata for: " + name);

        // 1. Try ID3v2 (Common in MP3, AAC, AIFF, WAV) - Check start of file
        metadata.putAll(parseId3v2(file, 0));

        // 2. Try ID3v1 (Common in MP3, AAC) - Check end of file
        metadata.putAll(parseId3v1(file));

        // 3. Format specific parsing
        if (name.endsWith(".flac")) {
            metadata.putAll(parseFlac(file));
        } else if (name.endsWith(".ogg")) {
            metadata.putAll(parseOgg(file));
        } else if (name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp4")) {
            // Check if it's an ADTS stream (starts with 0xFFF)
            if (isAdts(file)) {
                System.out.println("Identified as ADTS AAC: " + name);
                // ADTS usually uses ID3v1/v2 which are already checked.
            } else {
                metadata.putAll(parseM4a(file));
            }
        } else if (name.endsWith(".wav")) {
            metadata.putAll(parseWav(file));
        } else if (name.endsWith(".aif") || name.endsWith(".aiff")) {
            metadata.putAll(parseAiff(file));
        }

        return metadata;
    }

    private static boolean isAdts(File file) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            if (raf.length() < 2)
                return false;
            byte[] header = new byte[2];
            raf.readFully(header);
            // Sync word is 12 bits of 1s: 0xFFF
            return (header[0] & 0xFF) == 0xFF && (header[1] & 0xF0) == 0xF0;
        } catch (IOException e) {
            return false;
        }
    }

    private static Map<String, Object> parseId3v1(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            if (raf.length() < 128)
                return metadata;
            raf.seek(raf.length() - 128);
            byte[] tag = new byte[128];
            raf.readFully(tag);

            if (tag[0] == 'T' && tag[1] == 'A' && tag[2] == 'G') {
                System.out.println("Found ID3v1 tag at end of file");
                String title = new String(tag, 3, 30, StandardCharsets.ISO_8859_1).trim();
                String artist = new String(tag, 33, 30, StandardCharsets.ISO_8859_1).trim();
                String album = new String(tag, 63, 30, StandardCharsets.ISO_8859_1).trim();

                if (!title.isEmpty())
                    metadata.put("title", title);
                if (!artist.isEmpty())
                    metadata.put("artist", artist);
                if (!album.isEmpty())
                    metadata.put("album", album);
            }
        } catch (Exception e) {
            System.err.println("Error parsing ID3v1: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseId3v2(File file, long offset) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            if (offset >= raf.length())
                return metadata;
            raf.seek(offset);
            byte[] header = new byte[10];
            raf.readFully(header);

            // Check for "ID3" signature
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3')
                return metadata;

            // Parse size (synchsafe integer)
            int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                    ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);

            System.out.println("Found ID3v2 tag at offset " + offset + ", size: " + size);

            // Read frames
            long pos = offset + 10;
            long endPos = pos + size;

            while (pos < endPos) {
                raf.seek(pos);
                byte[] frameHeader = new byte[10];
                raf.readFully(frameHeader);
                pos += 10;

                String frameId = new String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1);
                if (frameId.equals("\0\0\0\0"))
                    break; // Padding

                int frameSize = getIntBE(frameHeader, 4);
                // ID3v2.4 uses synchsafe, v2.3 uses standard.
                if (header[3] == 4) {
                    frameSize = ((frameHeader[4] & 0x7F) << 21) | ((frameHeader[5] & 0x7F) << 14) |
                            ((frameHeader[6] & 0x7F) << 7) | (frameHeader[7] & 0x7F);
                }

                if (frameSize <= 0 || frameSize > endPos - pos)
                    break;

                byte[] frameData = new byte[frameSize];
                raf.readFully(frameData);
                pos += frameSize;

                if (frameId.startsWith("T")) {
                    int encoding = frameData[0];
                    String text = "";
                    if (frameSize > 1) { // Ensure there's actual data after encoding byte
                        if (encoding == 0)
                            text = new String(frameData, 1, frameSize - 1, StandardCharsets.ISO_8859_1);
                        else if (encoding == 1)
                            text = new String(frameData, 1, frameSize - 1, StandardCharsets.UTF_16);
                        else if (encoding == 3)
                            text = new String(frameData, 1, frameSize - 1, StandardCharsets.UTF_8);
                    }

                    text = text.trim();
                    if (!text.isEmpty()) {
                        if (frameId.equals("TIT2"))
                            metadata.put("title", text);
                        if (frameId.equals("TPE1"))
                            metadata.put("artist", text);
                        if (frameId.equals("TALB"))
                            metadata.put("album", text);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing ID3v2: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseWav(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            byte[] header = new byte[12];
            raf.readFully(header);
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F')
                return metadata;
            if (header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
                return metadata;

            long fileSize = raf.length();
            long pos = 12;

            System.out.println("Parsing WAV chunks for: " + file.getName());

            while (pos < fileSize) {
                raf.seek(pos);
                if (fileSize - pos < 8)
                    break;

                byte[] chunkHeader = new byte[8];
                raf.readFully(chunkHeader);
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.ISO_8859_1);
                int chunkSize = Integer.reverseBytes(getIntBE(chunkHeader, 4)); // WAV is Little Endian

                System.out.println("  Found chunk: " + chunkId + ", size: " + chunkSize + " at " + pos);

                if (chunkId.equals("LIST")) {
                    byte[] typeBytes = new byte[4];
                    raf.readFully(typeBytes);
                    String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
                    System.out.println("    LIST type: " + type);

                    if (type.equals("INFO")) {
                        long listEnd = pos + 8 + chunkSize;
                        long subPos = pos + 12;

                        while (subPos < listEnd) {
                            raf.seek(subPos);
                            if (listEnd - subPos < 8)
                                break;

                            byte[] subHeader = new byte[8];
                            raf.readFully(subHeader);
                            String subId = new String(subHeader, 0, 4, StandardCharsets.ISO_8859_1);
                            int subSize = Integer.reverseBytes(getIntBE(subHeader, 4));

                            System.out.println("      Sub-chunk: " + subId + ", size: " + subSize);

                            if (subSize > listEnd - subPos - 8)
                                break;

                            byte[] subData = new byte[subSize];
                            raf.readFully(subData);
                            String value = new String(subData, StandardCharsets.UTF_8).trim();
                            if (value.endsWith("\0"))
                                value = value.substring(0, value.length() - 1);

                            System.out.println("      Value: " + value);

                            if (subId.equals("INAM"))
                                metadata.put("title", value);
                            if (subId.equals("IART"))
                                metadata.put("artist", value);
                            if (subId.equals("IPRD"))
                                metadata.put("album", value);

                            if (subSize % 2 != 0)
                                subSize++;
                            subPos += 8 + subSize;
                        }
                    }
                } else if (chunkId.equals("id3 ") || chunkId.equals("ID3 ")) { // Lowercase id3 in WAV?
                    metadata.putAll(parseId3v2(file, pos + 8));
                }

                if (chunkSize % 2 != 0)
                    chunkSize++;
                pos += 8 + chunkSize;
            }
        } catch (Exception e) {
            System.err.println("Error parsing WAV metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseAiff(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            byte[] header = new byte[12];
            raf.readFully(header);
            if (header[0] != 'F' || header[1] != 'O' || header[2] != 'R' || header[3] != 'M')
                return metadata;
            if (header[8] != 'A' || header[9] != 'I' || header[10] != 'F' || header[11] != 'F')
                return metadata;

            long fileSize = raf.length();
            long pos = 12;

            System.out.println("Parsing AIFF chunks for: " + file.getName());

            while (pos < fileSize) {
                raf.seek(pos);
                if (fileSize - pos < 8)
                    break;

                byte[] chunkHeader = new byte[8];
                raf.readFully(chunkHeader);
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.ISO_8859_1);
                int chunkSize = getIntBE(chunkHeader, 4);

                System.out.println("  Found chunk: " + chunkId + ", size: " + chunkSize + " at " + pos);

                if (chunkId.equals("NAME")) {
                    byte[] data = new byte[chunkSize];
                    raf.readFully(data);
                    metadata.put("title", new String(data, StandardCharsets.ISO_8859_1).trim());
                } else if (chunkId.equals("AUTH")) {
                    byte[] data = new byte[chunkSize];
                    raf.readFully(data);
                    metadata.put("artist", new String(data, StandardCharsets.ISO_8859_1).trim());
                } else if (chunkId.equals("ID3 ")) {
                    // ID3 chunk in AIFF
                    metadata.putAll(parseId3v2(file, pos + 8));
                } else if (chunkId.equals("LIST")) {
                    // AIFF can also have LIST chunks similar to WAV
                    byte[] typeBytes = new byte[4];
                    raf.readFully(typeBytes);
                    String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
                    System.out.println("    LIST type: " + type);

                    if (type.equals("INFO")) {
                        long listEnd = pos + 8 + chunkSize;
                        long subPos = pos + 12;

                        while (subPos < listEnd) {
                            raf.seek(subPos);
                            if (listEnd - subPos < 8)
                                break;

                            byte[] subHeader = new byte[8];
                            raf.readFully(subHeader);
                            String subId = new String(subHeader, 0, 4, StandardCharsets.ISO_8859_1);
                            int subSize = getIntBE(subHeader, 4); // AIFF is Big Endian

                            System.out.println("      Sub-chunk: " + subId + ", size: " + subSize);

                            if (subSize > listEnd - subPos - 8)
                                break;

                            byte[] subData = new byte[subSize];
                            raf.readFully(subData);
                            String value = new String(subData, StandardCharsets.UTF_8).trim();
                            if (value.endsWith("\0"))
                                value = value.substring(0, value.length() - 1);

                            System.out.println("      Value: " + value);

                            if (subId.equals("INAM"))
                                metadata.put("title", value);
                            if (subId.equals("IART"))
                                metadata.put("artist", value);
                            if (subId.equals("IPRD"))
                                metadata.put("album", value);

                            if (subSize % 2 != 0)
                                subSize++;
                            subPos += 8 + subSize;
                        }
                    }
                }

                // Chunks are padded to even number of bytes
                if (chunkSize % 2 != 0)
                    chunkSize++;
                pos += 8 + chunkSize;
            }
        } catch (Exception e) {
            System.err.println("Error parsing AIFF metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseFlac(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!"fLaC".equals(new String(magic)))
                return metadata;

            boolean lastBlock = false;
            while (!lastBlock) {
                byte header = raf.readByte();
                lastBlock = (header & 0x80) != 0;
                int type = header & 0x7F;
                int length = ((raf.readByte() & 0xFF) << 16) | ((raf.readByte() & 0xFF) << 8) | (raf.readByte() & 0xFF);

                if (type == 4) { // VORBIS_COMMENT
                    // Vendor length
                    int vendorLen = Integer.reverseBytes(raf.readInt());
                    raf.skipBytes(vendorLen);

                    // User comment list length
                    int commentListLen = Integer.reverseBytes(raf.readInt());

                    for (int i = 0; i < commentListLen; i++) {
                        int commentLen = Integer.reverseBytes(raf.readInt());
                        byte[] commentBytes = new byte[commentLen];
                        raf.readFully(commentBytes);
                        String comment = new String(commentBytes, StandardCharsets.UTF_8);
                        parseCommentString(comment, metadata);
                    }
                    break;
                } else {
                    raf.skipBytes(length);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing FLAC metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseOgg(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            // Scan first 5MB for "\x03vorbis" signature (increased from 100KB)
            int maxScan = 5 * 1024 * 1024;
            if (raf.length() < maxScan)
                maxScan = (int) raf.length();
            byte[] data = new byte[maxScan];
            raf.readFully(data);

            System.out.println("Scanning OGG for Vorbis comments: " + file.getName());

            byte[] signature = { 0x03, 'v', 'o', 'r', 'b', 'i', 's' };
            int offset = -1;

            for (int i = 0; i < maxScan - signature.length; i++) {
                boolean match = true;
                for (int j = 0; j < signature.length; j++) {
                    if (data[i + j] != signature[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    offset = i;
                    System.out.println("  Found Vorbis signature at offset: " + offset);
                    try {
                        int pos = offset + signature.length;

                        if (pos + 4 > maxScan)
                            continue;
                        int vendorLen = getIntLE(data, pos);
                        pos += 4;
                        pos += vendorLen;

                        if (pos + 4 > maxScan)
                            continue;
                        int commentListLen = getIntLE(data, pos);
                        pos += 4;

                        System.out.println("  Vendor length: " + vendorLen + ", Comments: " + commentListLen);

                        for (int k = 0; k < commentListLen; k++) {
                            if (pos + 4 > maxScan)
                                break;
                            int commentLen = getIntLE(data, pos);
                            pos += 4;

                            if (pos + commentLen > maxScan)
                                break;
                            String comment = new String(data, pos, commentLen, StandardCharsets.UTF_8);
                            pos += commentLen;

                            System.out.println("    Comment: " + comment);
                            parseCommentString(comment, metadata);
                        }
                        if (!metadata.isEmpty())
                            break;
                    } catch (Exception e) {
                        System.out.println("  Failed to parse at offset " + offset + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing OGG metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static void parseCommentString(String comment, Map<String, Object> metadata) {
        int equalsIndex = comment.indexOf('=');
        if (equalsIndex > 0) {
            String key = comment.substring(0, equalsIndex).toUpperCase();
            String value = comment.substring(equalsIndex + 1);
            // Map to standard keys
            if (key.equals("TITLE"))
                metadata.put("title", value);
            if (key.equals("ARTIST"))
                metadata.put("artist", value);
            if (key.equals("ALBUM"))
                metadata.put("album", value);
        }
    }

    private static int getIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private static int getIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
                ((data[offset + 1] & 0xFF) << 16) |
                ((data[offset + 2] & 0xFF) << 8) |
                (data[offset + 3] & 0xFF);
    }

    private static Map<String, Object> parseM4a(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long fileSize = raf.length();
            long pos = 0;

            System.out.println("Parsing M4A atoms for: " + file.getName());

            while (pos < fileSize) {
                raf.seek(pos);
                if (fileSize - pos < 8)
                    break;

                byte[] sizeBytes = new byte[4];
                raf.readFully(sizeBytes);
                int size = getIntBE(sizeBytes, 0);

                byte[] typeBytes = new byte[4];
                raf.readFully(typeBytes);
                String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

                System.out.println("  Found atom: " + type + ", size: " + size + " at " + pos);

                if (type.equals("moov")) {
                    // Dive into moov
                    parseAtomChildren(raf, pos + 8, size - 8, metadata, "  ");
                    break;
                }

                if (size == 1) {
                    raf.readLong(); // skip extended size
                    // Assuming standard atoms for now
                }

                if (size == 0)
                    break;

                pos += size;
            }
        } catch (Exception e) {
            System.err.println("Error parsing M4A metadata: " + e.getMessage());
        }

        return metadata;
    }

    private static void parseAtomChildren(java.io.RandomAccessFile raf, long startPos, long length,
            Map<String, Object> metadata, String indent) throws IOException {
        long pos = startPos;
        long endPos = startPos + length;

        while (pos < endPos) {
            raf.seek(pos);
            if (endPos - pos < 8)
                break;

            byte[] sizeBytes = new byte[4];
            raf.readFully(sizeBytes);
            int size = getIntBE(sizeBytes, 0);

            byte[] typeBytes = new byte[4];
            raf.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

            System.out.println(indent + "Found sub-atom: " + type + ", size: " + size);

            // Recursively search for metadata atoms
            if (type.equals("udta") || type.equals("ilst") || type.equals("moov") || type.equals("trak")
                    || type.equals("mdia") || type.equals("minf") || type.equals("stbl")) {
                parseAtomChildren(raf, pos + 8, size - 8, metadata, indent + "  ");
            } else if (type.equals("meta")) {
                // meta atom usually has 4 bytes version/flags
                raf.skipBytes(4);
                parseAtomChildren(raf, pos + 12, size - 12, metadata, indent + "  ");
            } else if (isMetadataAtom(type)) {
                parseMetadataAtom(raf, pos + 8, size - 8, type, metadata);
            }

            pos += size;
        }
    }

    private static boolean isMetadataAtom(String type) {
        return type.equals("\u00A9nam") || type.equals("\u00A9ART") || type.equals("\u00A9alb") ||
                type.equals("gnre") || type.equals("\u00A9day") || type.equals("trkn") || type.equals("disk");
    }

    private static void parseMetadataAtom(java.io.RandomAccessFile raf, long contentPos, long contentLength,
            String type, Map<String, Object> metadata) throws IOException {
        // Inside metadata atom is usually a 'data' atom
        raf.seek(contentPos);
        if (contentLength < 8)
            return;

        byte[] sizeBytes = new byte[4];
        raf.readFully(sizeBytes);
        int size = getIntBE(sizeBytes, 0);

        byte[] typeBytes = new byte[4];
        raf.readFully(typeBytes);
        String subType = new String(typeBytes, StandardCharsets.ISO_8859_1);

        if (subType.equals("data")) {
            raf.skipBytes(8); // version/flags + reserved
            int dataLen = size - 16;
            if (dataLen > 0) {
                byte[] data = new byte[dataLen];
                raf.readFully(data);

                // Try to guess encoding or just use UTF-8
                String value = new String(data, StandardCharsets.UTF_8);
                System.out.println("    Extracted " + type + ": " + value);

                if (type.equals("\u00A9nam"))
                    metadata.put("title", value);
                if (type.equals("\u00A9ART"))
                    metadata.put("artist", value);
                if (type.equals("\u00A9alb"))
                    metadata.put("album", value);
            }
        }
    }
}
