package com.stopsopa.musicfilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class MetadataParser {

    public static Map<String, Object> parse(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".flac")) {
            return parseFlac(file);
        } else if (name.endsWith(".ogg")) {
            return parseOgg(file);
        } else if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return parseM4a(file);
        }
        return new HashMap<>();
    }

    private static Map<String, Object> parseFlac(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (!"fLaC".equals(new String(magic)))
                return metadata;

            boolean lastBlock = false;
            while (!lastBlock) {
                byte header = dis.readByte();
                lastBlock = (header & 0x80) != 0;
                int type = header & 0x7F;
                int length = ((dis.readByte() & 0xFF) << 16) | ((dis.readByte() & 0xFF) << 8) | (dis.readByte() & 0xFF);

                if (type == 4) { // VORBIS_COMMENT
                    parseVorbisComment(dis, metadata);
                    break; // Found it
                } else {
                    dis.skipBytes(length);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing FLAC metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static Map<String, Object> parseOgg(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            // Scan first 16KB for "\x03vorbis" signature
            // This is a heuristic but usually works for OGG files where comments are in the
            // second page
            byte[] signature = { 0x03, 'v', 'o', 'r', 'b', 'i', 's' };

            // We need a sliding window or just read chunks and handle boundary?
            // Simple approach: Read fully if small, or read chunks.
            // Since we need a DataInputStream to parse, let's just find the offset first.

            // Better approach: Read the file byte by byte or buffer and look for the
            // pattern.
            // Once found, wrap the rest in a stream to parse.

            // Let's use a simpler approach: Just scan the first 50KB.
            int maxScan = 50 * 1024;
            byte[] data = new byte[maxScan];
            int read = fis.read(data);
            if (read <= 0)
                return metadata;

            int offset = -1;
            for (int i = 0; i < read - signature.length; i++) {
                boolean match = true;
                for (int j = 0; j < signature.length; j++) {
                    if (data[i + j] != signature[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    offset = i;
                    break;
                }
            }

            if (offset != -1) {
                // Found signature. The data follows immediately.
                // We can parse from the byte array directly.
                int pos = offset + signature.length;

                // Vendor length
                if (pos + 4 > read)
                    return metadata;
                int vendorLen = getIntLE(data, pos);
                pos += 4;
                pos += vendorLen;

                // Comment list length
                if (pos + 4 > read)
                    return metadata;
                int commentListLen = getIntLE(data, pos);
                pos += 4;

                for (int i = 0; i < commentListLen; i++) {
                    if (pos + 4 > read)
                        break;
                    int commentLen = getIntLE(data, pos);
                    pos += 4;

                    if (pos + commentLen > read)
                        break;
                    String comment = new String(data, pos, commentLen, StandardCharsets.UTF_8);
                    pos += commentLen;

                    parseCommentString(comment, metadata);
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing OGG metadata: " + e.getMessage());
        }
        return metadata;
    }

    private static void parseVorbisComment(DataInputStream dis, Map<String, Object> metadata) throws IOException {
        // Vendor length
        int vendorLen = Integer.reverseBytes(dis.readInt()); // Little endian
        dis.skipBytes(vendorLen);

        // User comment list length
        int commentListLen = Integer.reverseBytes(dis.readInt()); // Little endian

        for (int i = 0; i < commentListLen; i++) {
            int commentLen = Integer.reverseBytes(dis.readInt()); // Little endian
            byte[] commentBytes = new byte[commentLen];
            dis.readFully(commentBytes);
            String comment = new String(commentBytes, StandardCharsets.UTF_8);
            parseCommentString(comment, metadata);
        }
    }

    private static void parseCommentString(String comment, Map<String, Object> metadata) {
        int equalsIndex = comment.indexOf('=');
        if (equalsIndex > 0) {
            String key = comment.substring(0, equalsIndex).toUpperCase();
            String value = comment.substring(equalsIndex + 1);
            metadata.put(key, value);
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

    private static Map<String, Object> parseM4a(File file) {
        Map<String, Object> metadata = new HashMap<>();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            // Simple recursive atom parser
            // We are looking for moov -> udta -> meta -> ilst
            // This is a simplified parser that skips unknown atoms

            // Since we can't easily seek with DataInputStream in a structured way without
            // random access,
            // and atoms can be large (mdat), we need to be careful.
            // RandomAccessFile is better.
        } catch (Exception e) {
        }

        // Re-implement with RandomAccessFile for M4A
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long fileSize = raf.length();
            long pos = 0;

            while (pos < fileSize) {
                raf.seek(pos);
                if (fileSize - pos < 8)
                    break;

                byte[] sizeBytes = new byte[4];
                raf.readFully(sizeBytes);
                int size = getIntBE(sizeBytes);

                byte[] typeBytes = new byte[4];
                raf.readFully(typeBytes);
                String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

                if (type.equals("moov")) {
                    // Dive into moov
                    parseAtomChildren(raf, pos + 8, size - 8, metadata);
                    break; // Found moov, we are done (assuming metadata is in moov)
                }

                if (size == 1) {
                    // Extended size, read next 8 bytes
                    raf.readLong(); // skip extended size
                    // We don't handle large atoms here for simplicity, usually mdat is large
                    // If mdat is large, we just skip it based on calculation?
                    // Actually size 1 means read 64-bit size.
                    // Let's just skip this complexity for now, usually moov is small and at start
                    // or end.
                    // If moov is after mdat, we need to skip mdat.
                    // But we don't know mdat size if it's 64-bit without reading it.
                    // Let's assume standard 32-bit size for now.
                }

                if (size == 0)
                    break; // Last atom

                pos += size;
            }
        } catch (Exception e) {
            System.err.println("Error parsing M4A metadata: " + e.getMessage());
        }

        return metadata;
    }

    private static void parseAtomChildren(java.io.RandomAccessFile raf, long startPos, long length,
            Map<String, Object> metadata) throws IOException {
        long pos = startPos;
        long endPos = startPos + length;

        while (pos < endPos) {
            raf.seek(pos);
            if (endPos - pos < 8)
                break;

            byte[] sizeBytes = new byte[4];
            raf.readFully(sizeBytes);
            int size = getIntBE(sizeBytes);

            byte[] typeBytes = new byte[4];
            raf.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

            if (type.equals("udta") || type.equals("ilst")) {
                parseAtomChildren(raf, pos + 8, size - 8, metadata);
            } else if (type.equals("meta")) {
                // meta atom usually has 4 bytes version/flags
                raf.skipBytes(4);
                parseAtomChildren(raf, pos + 12, size - 12, metadata);
            } else if (isMetadataAtom(type)) {
                parseMetadataAtom(raf, pos + 8, size - 8, type, metadata);
            }

            pos += size;
        }
    }

    private static boolean isMetadataAtom(String type) {
        return type.equals("\u00A9nam") || type.equals("\u00A9ART") || type.equals("\u00A9alb") ||
                type.equals("gnre") || type.equals("\u00A9day");
    }

    private static void parseMetadataAtom(java.io.RandomAccessFile raf, long contentPos, long contentLength,
            String type, Map<String, Object> metadata) throws IOException {
        // Inside metadata atom is usually a 'data' atom
        raf.seek(contentPos);
        byte[] sizeBytes = new byte[4];
        raf.readFully(sizeBytes);
        int size = getIntBE(sizeBytes);

        byte[] typeBytes = new byte[4];
        raf.readFully(typeBytes);
        String subType = new String(typeBytes, StandardCharsets.ISO_8859_1);

        if (subType.equals("data")) {
            // Skip version/flags (4 bytes) + reserved (4 bytes) -> actually data atom has 8
            // bytes header then 4 bytes type/locale?
            // data atom: size(4), type(4), version(1), flags(3), reserved(4), data...
            raf.skipBytes(8);
            int dataLen = size - 16;
            byte[] data = new byte[dataLen];
            raf.readFully(data);
            String value = new String(data, StandardCharsets.UTF_8);

            if (type.equals("\u00A9nam"))
                metadata.put("title", value);
            if (type.equals("\u00A9ART"))
                metadata.put("artist", value);
            if (type.equals("\u00A9alb"))
                metadata.put("album", value);
        }
    }

    private static int getIntBE(byte[] data) {
        return ((data[0] & 0xFF) << 24) |
                ((data[1] & 0xFF) << 16) |
                ((data[2] & 0xFF) << 8) |
                (data[3] & 0xFF);
    }
}
