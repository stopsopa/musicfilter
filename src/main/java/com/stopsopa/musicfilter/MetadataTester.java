package com.stopsopa.musicfilter;

import java.io.File;
import java.util.Map;

public class MetadataTester {
    public static void main(String[] args) {
        File sampleDir = new File("music-sample");
        if (!sampleDir.exists()) {
            System.err.println("music-sample directory not found!");
            return;
        }

        File[] files = sampleDir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory() || file.getName().equals(".DS_Store"))
                continue;
            testFile(file);
        }

        // Also test generated files in root
        File[] rootFiles = new File(".").listFiles((dir, name) -> name.startsWith("tagged_test"));
        if (rootFiles != null) {
            for (File file : rootFiles) {
                testFile(file);
            }
        }
    }

    private static void testFile(File file) {
        System.out.println("--------------------------------------------------");
        System.out.println("Testing: " + file.getName());
        try {
            Map<String, Object> metadata = MetadataParser.parse(file);
            System.out.println("Result: " + metadata);
        } catch (Exception e) {
            System.err.println("Failed to parse: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
