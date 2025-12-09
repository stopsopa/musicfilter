package com.stopsopa.musicfilter;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JavaSoundAudioPlayer implements AudioPlayer {

    private final File file;
    private SourceDataLine line;
    private AudioInputStream encodedStream;
    private AudioInputStream decodedStream;
    private Thread playbackThread;
    private volatile boolean stopRequested = false;
    private volatile boolean pauseRequested = false;

    private final SimpleObjectProperty<Duration> currentTime = new SimpleObjectProperty<>(Duration.ZERO);
    private final SimpleObjectProperty<Duration> totalDuration = new SimpleObjectProperty<>(Duration.UNKNOWN);
    private final SimpleObjectProperty<Status> status = new SimpleObjectProperty<>(Status.READY);

    private Runnable onEndOfMedia;
    private Runnable onError;

    public JavaSoundAudioPlayer(File file) {
        this.file = file;
        calculateDuration();
    }

    private void calculateDuration() {
        try {
            AudioFileFormat fileFormat;
            if (file.getName().toLowerCase().endsWith(".ogg")) {
                fileFormat = new javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioFileFormat(file);
            } else {
                fileFormat = AudioSystem.getAudioFileFormat(file);
            }

            Map<String, Object> properties = fileFormat.properties();
            System.out.println("Audio properties for " + file.getName() + ": " + properties);

            Long microseconds = null;
            if (properties.containsKey("duration")) {
                microseconds = (Long) properties.get("duration");
            } else if (properties.containsKey("duration [us]")) {
                microseconds = (Long) properties.get("duration [us]");
            }

            if (microseconds != null) {
                totalDuration.set(Duration.millis(microseconds / 1000.0));
                System.out.println("Duration found from properties: " + totalDuration.get());
            } else {
                // Fallback 1: Calculate from AudioFileFormat frame length
                long frameLength = fileFormat.getFrameLength();
                float frameRate = fileFormat.getFormat().getFrameRate();

                if (frameLength != AudioSystem.NOT_SPECIFIED && frameRate != AudioSystem.NOT_SPECIFIED
                        && frameRate > 0) {
                    double seconds = frameLength / frameRate;
                    totalDuration.set(Duration.seconds(seconds));
                    System.out.println("Duration calculated from AudioFileFormat frames: " + totalDuration.get());
                } else {
                    // Fallback 2: Try opening the stream directly (some SPIs only calculate length
                    // on stream open)
                    try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
                        frameLength = in.getFrameLength();
                        frameRate = in.getFormat().getFrameRate();
                        if (frameLength != AudioSystem.NOT_SPECIFIED && frameRate != AudioSystem.NOT_SPECIFIED
                                && frameRate > 0) {
                            double seconds = frameLength / frameRate;
                            totalDuration.set(Duration.seconds(seconds));
                            System.out.println(
                                    "Duration calculated from AudioInputStream frames: " + totalDuration.get());
                        } else {
                            System.out.println("Duration could not be determined from Stream. FrameLength: "
                                    + frameLength + ", FrameRate: " + frameRate);

                            // Fallback 3: Manual FLAC header parsing
                            if (file.getName().toLowerCase().endsWith(".flac")) {
                                Duration flacDuration = calculateFlacDuration(file);
                                if (flacDuration != null) {
                                    totalDuration.set(flacDuration);
                                    System.out.println("Duration calculated from manual FLAC header parsing: "
                                            + totalDuration.get());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("Failed to open stream for duration calculation: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calculating duration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Duration calculateFlacDuration(File file) {
        try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.FileInputStream(file))) {
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (!"fLaC".equals(new String(magic)))
                return null;

            // Read Metadata Block Header
            byte header = dis.readByte();
            int type = header & 0x7F; // 0 is STREAMINFO

            // Skip length (3 bytes)
            dis.skipBytes(3);

            if (type != 0)
                return null; // Expected STREAMINFO as first block

            // STREAMINFO payload
            // Skip min/max block/frame sizes (10 bytes)
            dis.skipBytes(10);

            // Read next 8 bytes containing sample rate and total samples
            long combined = dis.readLong();

            // Sample Rate: 20 bits
            long sampleRate = (combined >> 44) & 0xFFFFF;

            // Total Samples: 36 bits (lower 36 bits)
            long totalSamples = combined & 0xFFFFFFFFFL;

            if (sampleRate > 0 && totalSamples > 0) {
                double seconds = (double) totalSamples / sampleRate;
                return Duration.seconds(seconds);
            }
        } catch (Exception e) {
            System.err.println("Failed to read FLAC header: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void play() {
        if (status.get() == Status.PLAYING)
            return;

        if (status.get() == Status.PAUSED) {
            pauseRequested = false;
            status.set(Status.PLAYING);
            synchronized (this) {
                notifyAll();
            }
            return;
        }

        stopRequested = false;
        pauseRequested = false;
        status.set(Status.PLAYING);

        playbackThread = new Thread(this::playbackLoop);
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private volatile boolean seekRequested = false;
    private volatile Duration seekDuration = Duration.ZERO;

    private void playbackLoop() {
        try {
            openStreams();
            AudioFormat decodedFormat = decodedStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(decodedFormat);
            line.start();

            byte[] buffer = new byte[4096];
            int nBytesRead;
            long totalBytesRead = 0;

            while (!stopRequested) {
                Duration targetSeekDuration = null;
                synchronized (this) {
                    if (seekRequested) {
                        targetSeekDuration = seekDuration;
                        seekRequested = false;
                    }
                }

                if (targetSeekDuration != null) {
                    try {
                        long bytesToSkip = (long) (targetSeekDuration.toSeconds() * decodedFormat.getFrameRate()
                                * decodedFormat.getFrameSize());

                        // Close and reopen to seek
                        // No need to synchronize here as we are the only thread manipulating the
                        // streams
                        if (decodedStream != null)
                            decodedStream.close();
                        if (encodedStream != null)
                            encodedStream.close();
                        if (line != null)
                            line.flush();

                        openStreams();

                        // Skip to position
                        long remaining = bytesToSkip;
                        byte[] skipBuffer = new byte[65536]; // Larger buffer for faster skipping
                        while (remaining > 0) {
                            if (stopRequested || seekRequested)
                                break; // Abort if stopped or new seek

                            long skipped = 0;
                            try {
                                skipped = decodedStream.skip(remaining);
                            } catch (IOException e) {
                                // skip not supported, fall back to read
                                skipped = 0;
                            }

                            if (skipped <= 0) {
                                // Fallback: read to skip
                                int toRead = (int) Math.min(remaining, skipBuffer.length);
                                int read = decodedStream.read(skipBuffer, 0, toRead);
                                if (read == -1)
                                    break; // EOF
                                skipped = read;
                            }
                            remaining -= skipped;
                        }

                        // Reset line buffer
                        if (line != null) {
                            line.flush();
                        }

                        totalBytesRead = bytesToSkip;

                        // Update UI immediately
                        Duration finalSeekDuration = targetSeekDuration;
                        Platform.runLater(() -> currentTime.set(finalSeekDuration));
                    } catch (Exception e) {
                        System.err.println("Error during seek: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                while (pauseRequested && !seekRequested && !stopRequested) {
                    synchronized (this) {
                        try {
                            wait(100); // Wait with timeout to check flags
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }

                if (stopRequested)
                    break;
                if (seekRequested)
                    continue; // Loop back to handle seek

                nBytesRead = decodedStream.read(buffer, 0, buffer.length);
                if (nBytesRead == -1) {
                    System.out.println("End of stream reached (read returned -1)");
                    break;
                }
                // System.out.println("Read " + nBytesRead + " bytes");

                // Ensure we write an integral number of frames
                int frameSize = decodedFormat.getFrameSize();
                if (frameSize > 0) {
                    int remainder = nBytesRead % frameSize;
                    if (remainder != 0) {
                        nBytesRead -= remainder;
                    }
                }

                if (nBytesRead > 0) {
                    line.write(buffer, 0, nBytesRead);
                    totalBytesRead += nBytesRead;
                }

                // Update current time
                long finalTotalBytesRead = totalBytesRead;
                Platform.runLater(() -> {
                    double seconds = (double) finalTotalBytesRead / decodedFormat.getFrameSize()
                            / decodedFormat.getFrameRate();
                    currentTime.set(Duration.seconds(seconds));
                });
            }

            line.drain();
            line.stop();
            line.close();
            encodedStream.close();
            decodedStream.close();

            if (!stopRequested && onEndOfMedia != null) {
                Platform.runLater(onEndOfMedia);
            }

            Platform.runLater(() -> status.set(Status.STOPPED));

        } catch (Exception e) {
            e.printStackTrace();
            if (onError != null)
                Platform.runLater(onError);
        }
    }

    private void openStreams() throws UnsupportedAudioFileException, IOException {
        System.out.println("Opening streams for: " + file.getName());

        if (file.getName().toLowerCase().endsWith(".ogg")) {
            System.out.println("Using direct VorbisAudioFileReader for OGG");
            encodedStream = new javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioInputStream(file);
        } else {
            // For FLAC (jflac-codec) and AAC (JAAD), use standard AudioSystem
            encodedStream = AudioSystem.getAudioInputStream(file);
        }

        AudioFormat baseFormat = encodedStream.getFormat();
        System.out.println("Source format: " + baseFormat);

        if (file.getName().toLowerCase().endsWith(".ogg")) {
            // Explicitly define target format to avoid "unknown" fields for OGG
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, encodedStream);
        } else {
            // For FLAC, try to decode to native bit depth first
            int bitDepth = baseFormat.getSampleSizeInBits();
            if (bitDepth == AudioSystem.NOT_SPECIFIED)
                bitDepth = 16;

            AudioFormat nativePcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    bitDepth,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * (bitDepth / 8),
                    baseFormat.getSampleRate(),
                    false);

            System.out.println("Attempting to decode to native PCM: " + nativePcmFormat);
            try {
                AudioInputStream pcmStream = AudioSystem.getAudioInputStream(nativePcmFormat, encodedStream);

                // Check if this format is supported by the line
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, nativePcmFormat);
                if (AudioSystem.isLineSupported(info)) {
                    System.out.println("Native PCM format is supported by line.");
                    decodedStream = pcmStream;
                } else {
                    System.out.println("Native PCM format not supported. Downsampling to 16-bit.");
                    // Downsample to 16-bit
                    AudioFormat format16 = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false);
                    decodedStream = AudioSystem.getAudioInputStream(format16, pcmStream);
                }
            } catch (Exception e) {
                System.out.println(
                        "Failed to decode to native PCM (" + e.getMessage() + "). Trying direct 16-bit conversion.");
                AudioFormat format16 = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false);
                decodedStream = AudioSystem.getAudioInputStream(format16, encodedStream);
            }
        }

        System.out.println("Final decoded format: " + decodedStream.getFormat());
    }

    @Override
    public void pause() {
        if (status.get() == Status.PLAYING) {
            pauseRequested = true;
            status.set(Status.PAUSED);
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
        status.set(Status.STOPPED);
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void dispose() {
        stop();
        if (line != null) {
            line.close();
        }
    }

    @Override
    public void seek(Duration seekTime) {
        synchronized (this) {
            seekDuration = seekTime;
            seekRequested = true;
            notifyAll();
        }
    }

    @Override
    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return currentTime;
    }

    @Override
    public ReadOnlyObjectProperty<Duration> totalDurationProperty() {
        return totalDuration;
    }

    @Override
    public ReadOnlyObjectProperty<Status> statusProperty() {
        return status;
    }

    @Override
    public void setOnEndOfMedia(Runnable runnable) {
        this.onEndOfMedia = runnable;
    }

    @Override
    public void setOnError(Runnable runnable) {
        this.onError = runnable;
    }

}
