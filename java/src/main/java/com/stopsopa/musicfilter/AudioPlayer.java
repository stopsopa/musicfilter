package com.stopsopa.musicfilter;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.util.Duration;

public interface AudioPlayer {
    void play();

    void pause();

    void stop();

    void dispose();

    void seek(Duration seekTime);

    ReadOnlyObjectProperty<Duration> currentTimeProperty();

    ReadOnlyObjectProperty<Duration> totalDurationProperty();

    ReadOnlyObjectProperty<Status> statusProperty();

    enum Status {
        READY, PLAYING, PAUSED, STOPPED, UNKNOWN
    }

    void setOnEndOfMedia(Runnable runnable);

    void setOnError(Runnable runnable);
}
