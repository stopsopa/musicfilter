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
                System.out.println("Duration found: " + totalDuration.get());
            } else {
                System.out.println("Duration not found in properties.");
            }
        } catch (Exception e) {
            System.err.println("Error calculating duration: " + e.getMessage());
            e.printStackTrace();
        }
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
                if (seekRequested) {
                    synchronized (this) {
                        seekRequested = false;
                        long bytesToSkip = (long) (seekDuration.toSeconds() * decodedFormat.getFrameRate()
                                * decodedFormat.getFrameSize());

                        // Close and reopen to seek
                        decodedStream.close();
                        encodedStream.close();
                        openStreams();

                        // Skip to position
                        long remaining = bytesToSkip;
                        while (remaining > 0) {
                            long skipped = decodedStream.skip(remaining);
                            if (skipped <= 0)
                                break; // EOF or error
                            remaining -= skipped;
                        }

                        line.flush();
                        totalBytesRead = bytesToSkip;

                        // Update UI immediately
                        Platform.runLater(() -> currentTime.set(seekDuration));
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
                if (nBytesRead == -1)
                    break;

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
            encodedStream = AudioSystem.getAudioInputStream(file);
        }

        AudioFormat baseFormat = encodedStream.getFormat();
        System.out.println("Source format: " + baseFormat);

        // Explicitly define target format to avoid "unknown" fields
        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false);

        decodedStream = AudioSystem.getAudioInputStream(decodedFormat, encodedStream);
        System.out.println("Decoded format: " + decodedStream.getFormat());
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
