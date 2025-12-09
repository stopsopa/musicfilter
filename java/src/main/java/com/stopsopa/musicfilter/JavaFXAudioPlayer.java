package com.stopsopa.musicfilter;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;

public class JavaFXAudioPlayer implements AudioPlayer {

    private final MediaPlayer mediaPlayer;
    private final SimpleObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNKNOWN);

    public JavaFXAudioPlayer(File file) {
        Media media = new Media(file.toURI().toString());
        this.mediaPlayer = new MediaPlayer(media);

        this.mediaPlayer.statusProperty().addListener((obs, oldVal, newVal) -> {
            switch (newVal) {
                case READY -> status.set(Status.READY);
                case PLAYING -> status.set(Status.PLAYING);
                case PAUSED -> status.set(Status.PAUSED);
                case STOPPED -> status.set(Status.STOPPED);
                default -> status.set(Status.UNKNOWN);
            }
        });
    }

    @Override
    public void play() {
        mediaPlayer.play();
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
    }

    @Override
    public void stop() {
        mediaPlayer.stop();
    }

    @Override
    public void dispose() {
        mediaPlayer.dispose();
    }

    @Override
    public void seek(Duration seekTime) {
        mediaPlayer.seek(seekTime);
    }

    @Override
    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return mediaPlayer.currentTimeProperty();
    }

    @Override
    public ReadOnlyObjectProperty<Duration> totalDurationProperty() {
        return mediaPlayer.totalDurationProperty(); // Use totalDurationProperty which is
                                                    // ReadOnlyObjectProperty<Duration>
    }

    @Override
    public ReadOnlyObjectProperty<Status> statusProperty() {
        return status;
    }

    @Override
    public void setOnEndOfMedia(Runnable runnable) {
        mediaPlayer.setOnEndOfMedia(runnable);
    }

    @Override
    public void setOnError(Runnable runnable) {
        mediaPlayer.setOnError(runnable);
    }
}
