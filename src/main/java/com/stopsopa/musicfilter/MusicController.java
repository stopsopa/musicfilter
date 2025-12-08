package com.stopsopa.musicfilter;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.MapChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableRow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.media.Media;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MusicController {

    private final TableView<AudioFile> tableView;
    private AudioPlayer audioPlayer;
    private final Slider timeSlider;
    private final Button playPauseButton;
    private final Label timeLabel;
    private boolean isSliderDragging = false;
    private Duration duration;

    public static class AudioFile {
        private final ObjectProperty<File> file;
        private final SimpleStringProperty filename;
        private final SimpleStringProperty title;
        private final SimpleStringProperty artist;
        private final SimpleStringProperty album;

        public AudioFile(File file) {
            this.file = new SimpleObjectProperty<>(file);
            this.filename = new SimpleStringProperty(file.getName());
            this.title = new SimpleStringProperty("<not available>");
            this.artist = new SimpleStringProperty("<not available>");
            this.album = new SimpleStringProperty("<not available>");
            loadMetadata();
        }

        private void loadMetadata() {
            File f = file.get();
            String name = f.getName().toLowerCase();

            if (isJavaFXSupported(name)) {
                try {
                    Media media = new Media(f.toURI().toString());
                    media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
                        if (change.wasAdded()) {
                            updateMetadata(media.getMetadata());
                        }
                    });
                } catch (Exception e) {
                    // Ignore
                }
            } else {
                // Try JavaSound properties for FLAC/OGG
                new Thread(() -> {
                    try {
                        AudioFileFormat aff = AudioSystem.getAudioFileFormat(f);
                        if (aff instanceof TAudioFileFormat) {
                            Map<String, Object> props = ((TAudioFileFormat) aff).properties();
                            updateMetadata(props);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }).start();
            }
        }

        private boolean isJavaFXSupported(String name) {
            return name.endsWith(".mp3") || name.endsWith(".wav") ||
                    name.endsWith(".aif") || name.endsWith(".aiff") ||
                    name.endsWith(".m4a") || name.endsWith(".aac");
        }

        private void updateMetadata(Map<String, Object> metadata) {
            Platform.runLater(() -> {
                title.set(extractMetadata(metadata, "title"));
                artist.set(extractMetadata(metadata, "artist", "author")); // fallback to author
                album.set(extractMetadata(metadata, "album"));
            });
        }

        private String extractMetadata(Map<String, Object> metadata, String... keys) {
            try {
                for (String key : keys) {
                    if (metadata.containsKey(key)) {
                        Object value = metadata.get(key);
                        if (value != null && !value.toString().trim().isEmpty()) {
                            return value.toString();
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback
            }
            return "<not available>";
        }

        public File getFile() {
            return file.get();
        }

        public ObjectProperty<File> fileProperty() {
            return file;
        }

        public void setFile(File file) {
            this.file.set(file);
        }

        public String getFilename() {
            return filename.get();
        }

        public String getTitle() {
            return title.get();
        }

        public String getArtist() {
            return artist.get();
        }

        public String getAlbum() {
            return album.get();
        }

        public boolean isDeleted() {
            return file.get().getParentFile().getName().equals("_deleted");
        }

        // Helper interface for Tritonus properties
        private interface TAudioFileFormat {
            Map<String, Object> properties();
        }
    }

    @SuppressWarnings("unchecked")
    public MusicController(Stage stage) {
        this.tableView = new TableView<>();

        TableColumn<AudioFile, String> filenameCol = new TableColumn<>("Filename");
        filenameCol.setCellValueFactory(cellData -> cellData.getValue().filename);

        TableColumn<AudioFile, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> cellData.getValue().title);

        TableColumn<AudioFile, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(cellData -> cellData.getValue().artist);

        TableColumn<AudioFile, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(cellData -> cellData.getValue().album);

        tableView.getColumns().addAll(filenameCol, titleCol, artistCol, albumCol);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AudioFile item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    setOpacity(1.0);
                } else {
                    updateOpacity(item);
                    item.fileProperty().addListener((obs, oldFile, newFile) -> updateOpacity(item));
                }
            }

            private void updateOpacity(AudioFile item) {
                setOpacity(item.isDeleted() ? 0.5 : 1.0);
            }
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                playFile(newValue);
            }
        });

        timeSlider = new Slider();
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeSlider.setMinWidth(50);
        timeSlider.setMaxWidth(Double.MAX_VALUE);

        playPauseButton = new Button("||");
        playPauseButton.setOnAction(e -> togglePlayPause());

        timeLabel = new Label("00:00 / 00:00");

        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isSliderDragging && audioPlayer != null) {
                audioPlayer.seek(duration.multiply(timeSlider.getValue() / 100.0));
            }
        });

        timeSlider.setOnMousePressed(e -> isSliderDragging = true);
        timeSlider.setOnMouseReleased(e -> {
            isSliderDragging = false;
            if (audioPlayer != null) {
                audioPlayer.seek(duration.multiply(timeSlider.getValue() / 100.0));
            }
        });
    }

    public Parent getView() {
        BorderPane root = new BorderPane();
        root.setCenter(tableView);

        HBox controls = new HBox(10);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);
        controls.getChildren().addAll(playPauseButton, timeSlider, timeLabel);

        root.setBottom(controls);
        return root;
    }

    public void handleDragOver(DragEvent event) {
        if (event.getGestureSource() != tableView && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    public void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            List<AudioFile> audioFiles = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    audioFiles.addAll(findAudioFilesInDirectory(file));
                } else {
                    if (isSupportedAudioFile(file)) {
                        audioFiles.add(new AudioFile(file));
                    }
                }
            }

            // audioFiles.sort((a, b) -> a.getFilename().compareTo(b.getFilename()));

            tableView.getItems().addAll(audioFiles);

            // Apply sorting on drop
            tableView.getSortOrder().clear();
            tableView.getSortOrder().add(tableView.getColumns().get(0)); // Filename column
            tableView.getColumns().get(0).setSortType(TableColumn.SortType.ASCENDING);
            tableView.sort();
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private List<AudioFile> findAudioFilesInDirectory(File dir) {
        List<AudioFile> audioFiles = new ArrayList<>();
        // Skip _deleted directories
        if (dir.getName().equals("_deleted")) {
            return audioFiles;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    audioFiles.addAll(findAudioFilesInDirectory(file));
                } else if (isSupportedAudioFile(file)) {
                    audioFiles.add(new AudioFile(file));
                }
            }
        }
        return audioFiles;
    }

    private boolean isSupportedAudioFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") ||
                name.endsWith(".wav") ||
                name.endsWith(".aif") ||
                name.endsWith(".aiff") ||
                name.endsWith(".m4a") ||
                name.endsWith(".aac") ||
                name.endsWith(".flac") ||
                name.endsWith(".ogg");
    }

    private void playFile(AudioFile audioFile) {
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer.dispose();
            audioPlayer = null;
        }

        // Allow playing deleted files as requested

        try {
            File file = audioFile.getFile();
            String name = file.getName().toLowerCase();

            System.out.println("Attempting to play: " + name);
            if (name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".aac")) {
                System.out.println("Using JavaSoundAudioPlayer");
                audioPlayer = new JavaSoundAudioPlayer(file);
            } else {
                System.out.println("Using JavaFXAudioPlayer");
                audioPlayer = new JavaFXAudioPlayer(file);
            }

            audioPlayer.setOnError(() -> System.err.println("Media error reported by player"));

            audioPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> updateValues());
            audioPlayer.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
                duration = newDur;
                updateValues();
            });

            // Initial duration might be available immediately or later
            duration = audioPlayer.totalDurationProperty().getValue();

            audioPlayer.setOnEndOfMedia(() -> {
                int nextIndex = tableView.getSelectionModel().getSelectedIndex() + 1;
                if (nextIndex < tableView.getItems().size()) {
                    tableView.getSelectionModel().select(nextIndex);
                }
            });

            audioPlayer.play();
            playPauseButton.setText("||");
        } catch (Exception e) {
            System.err.println("Error playing file: " + audioFile.getFile().getAbsolutePath());
            e.printStackTrace();
        }
    }

    private void togglePlayPause() {
        if (audioPlayer == null)
            return;

        if (audioPlayer.statusProperty().get() == AudioPlayer.Status.PLAYING) {
            audioPlayer.pause();
            playPauseButton.setText(">");
        } else {
            audioPlayer.play();
            playPauseButton.setText("||");
        }
    }

    private void updateValues() {
        if (timeLabel != null && timeSlider != null && duration != null && audioPlayer != null) {
            Platform.runLater(() -> {
                Duration currentTime = audioPlayer.currentTimeProperty().getValue();

                timeLabel.setText(formatTime(currentTime, duration));
                timeSlider.setDisable(duration.isUnknown());
                if (!timeSlider.isDisabled() && duration.greaterThan(Duration.ZERO) && !isSliderDragging) {
                    timeSlider.setValue(currentTime.toMillis() / duration.toMillis() * 100.0);
                }
            });
        }
    }

    private static String formatTime(Duration elapsed, Duration duration) {
        if (elapsed == null)
            elapsed = Duration.ZERO;
        if (duration == null)
            duration = Duration.UNKNOWN;

        int intElapsed = (int) Math.floor(elapsed.toSeconds());
        int elapsedHours = intElapsed / (60 * 60);
        if (elapsedHours > 0) {
            intElapsed -= elapsedHours * 60 * 60;
        }
        int elapsedMinutes = intElapsed / 60;
        int elapsedSeconds = intElapsed - elapsedHours * 60 * 60 - elapsedMinutes * 60;

        if (duration.greaterThan(Duration.ZERO)) {
            int intDuration = (int) Math.floor(duration.toSeconds());
            int durationHours = intDuration / (60 * 60);
            if (durationHours > 0) {
                intDuration -= durationHours * 60 * 60;
            }
            int durationMinutes = intDuration / 60;
            int durationSeconds = intDuration - durationHours * 60 * 60 - durationMinutes * 60;
            if (durationHours > 0) {
                return String.format("%d:%02d:%02d / %d:%02d:%02d",
                        elapsedHours, elapsedMinutes, elapsedSeconds,
                        durationHours, durationMinutes, durationSeconds);
            } else {
                return String.format("%02d:%02d / %02d:%02d",
                        elapsedMinutes, elapsedSeconds,
                        durationMinutes, durationSeconds);
            }
        } else {
            if (elapsedHours > 0) {
                return String.format("%d:%02d:%02d / ...",
                        elapsedHours, elapsedMinutes, elapsedSeconds);
            } else {
                return String.format("%02d:%02d / ...",
                        elapsedMinutes, elapsedSeconds);
            }
        }
    }

    public void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.LEFT) {
            seek(-3);
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            seek(3);
            event.consume();
        } else if (event.getCode() == KeyCode.BACK_SPACE) {
            handleBackspace();
            event.consume();
        } else if (event.getCode() == KeyCode.SPACE) {
            togglePlayPause();
            event.consume();
        } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
            if (!tableView.isFocused()) {
                tableView.requestFocus();
            }
        }
    }

    private void seek(double seconds) {
        if (audioPlayer != null) {
            audioPlayer.seek(audioPlayer.currentTimeProperty().getValue().add(Duration.seconds(seconds)));
        }
    }

    private void handleBackspace() {
        AudioFile selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        if (selectedItem.isDeleted()) {
            restoreFile(selectedItem);
        } else {
            softDeleteFile(selectedItem);
        }
    }

    private void softDeleteFile(AudioFile item) {
        try {
            // Stop playback if playing this file
            if (audioPlayer != null
                    && item.getFile().equals(tableView.getSelectionModel().getSelectedItem().getFile())) {
                audioPlayer.stop();
                audioPlayer.dispose();
                audioPlayer = null;
                playPauseButton.setText(">");
                timeLabel.setText("00:00 / 00:00");
                timeSlider.setValue(0);
            }

            File original = item.getFile();
            File parent = original.getParentFile();
            File deletedDir = new File(parent, "_deleted");

            if (!deletedDir.exists()) {
                deletedDir.mkdirs();
            }

            File deleted = new File(deletedDir, original.getName());
            Files.move(original.toPath(), deleted.toPath(), StandardCopyOption.REPLACE_EXISTING);

            item.setFile(deleted);
            System.out.println("Soft deleted: " + original.getName());

            int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
            if (selectedIndex < tableView.getItems().size() - 1) {
                tableView.getSelectionModel().select(selectedIndex + 1);
            }

        } catch (IOException e) {
            System.err.println("Failed to soft delete: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void restoreFile(AudioFile item) {
        try {
            File deleted = item.getFile();
            File deletedDir = deleted.getParentFile();
            File parent = deletedDir.getParentFile();
            File original = new File(parent, deleted.getName());

            if (deleted.exists()) {
                Files.move(deleted.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
                item.setFile(original);
                System.out.println("Restored: " + original.getName());
            }
        } catch (IOException e) {
            System.err.println("Failed to restore: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
