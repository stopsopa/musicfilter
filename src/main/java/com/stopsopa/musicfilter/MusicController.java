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
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class MusicController {

    private final TableView<Mp3File> tableView;
    private MediaPlayer mediaPlayer;
    private final Slider timeSlider;
    private final Button playPauseButton;
    private final Label timeLabel;
    private boolean isSliderDragging = false;
    private Duration duration;

    public static class Mp3File {
        private final ObjectProperty<File> file;
        private final SimpleStringProperty filename;
        private final SimpleStringProperty title;
        private final SimpleStringProperty artist;
        private final SimpleStringProperty album;

        public Mp3File(File file) {
            this.file = new SimpleObjectProperty<>(file);
            this.filename = new SimpleStringProperty(file.getName());
            this.title = new SimpleStringProperty("");
            this.artist = new SimpleStringProperty("");
            this.album = new SimpleStringProperty("");
            loadMetadata();
        }

        private void loadMetadata() {
            try {
                Media media = new Media(file.get().toURI().toString());
                media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
                    if (change.wasAdded()) {
                        String key = change.getKey();
                        Object value = change.getValueAdded();
                        if ("title".equals(key))
                            title.set(value.toString());
                        if ("artist".equals(key))
                            artist.set(value.toString());
                        if ("album".equals(key))
                            album.set(value.toString());
                    }
                });
            } catch (Exception e) {
                // Ignore metadata errors
            }
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
    }

    @SuppressWarnings("unchecked")
    public MusicController(Stage stage) {
        this.tableView = new TableView<>();

        TableColumn<Mp3File, String> filenameCol = new TableColumn<>("Filename");
        filenameCol.setCellValueFactory(cellData -> cellData.getValue().filename);

        TableColumn<Mp3File, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> cellData.getValue().title);

        TableColumn<Mp3File, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(cellData -> cellData.getValue().artist);

        TableColumn<Mp3File, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(cellData -> cellData.getValue().album);

        tableView.getColumns().addAll(filenameCol, titleCol, artistCol, albumCol);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Row Factory for graying out deleted items
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Mp3File item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    setOpacity(1.0);
                } else {
                    // Update opacity based on isDeleted state
                    updateOpacity(item);

                    // Listen for file path changes to update opacity dynamically
                    item.fileProperty().addListener((obs, oldFile, newFile) -> {
                        updateOpacity(item);
                    });
                }
            }

            private void updateOpacity(Mp3File item) {
                setOpacity(item.isDeleted() ? 0.5 : 1.0);
            }
        });

        // Handle selection change to play music
        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                playFile(newValue);
            }
        });

        // Controls
        timeSlider = new Slider();
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeSlider.setMinWidth(50);
        timeSlider.setMaxWidth(Double.MAX_VALUE);

        playPauseButton = new Button("||");
        playPauseButton.setOnAction(e -> togglePlayPause());

        timeLabel = new Label("00:00 / 00:00");

        // Slider listeners
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isSliderDragging && mediaPlayer != null) {
                mediaPlayer.seek(duration.multiply(timeSlider.getValue() / 100.0));
            }
        });

        timeSlider.setOnMousePressed(e -> isSliderDragging = true);
        timeSlider.setOnMouseReleased(e -> {
            isSliderDragging = false;
            if (mediaPlayer != null) {
                mediaPlayer.seek(duration.multiply(timeSlider.getValue() / 100.0));
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
            List<Mp3File> mp3Files = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    mp3Files.addAll(findMp3sInDirectory(file));
                } else {
                    if (isMp3(file)) {
                        mp3Files.add(new Mp3File(file));
                    }
                }
            }

            tableView.getItems().addAll(mp3Files);
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private List<Mp3File> findMp3sInDirectory(File dir) {
        List<Mp3File> mp3s = new ArrayList<>();
        // Skip _deleted directories
        if (dir.getName().equals("_deleted")) {
            return mp3s;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    mp3s.addAll(findMp3sInDirectory(file));
                } else if (isMp3(file)) {
                    mp3s.add(new Mp3File(file));
                }
            }
        }
        return mp3s;
    }

    private boolean isMp3(File file) {
        return file.getName().toLowerCase().endsWith(".mp3");
    }

    private void playFile(Mp3File mp3File) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        // Allow playing deleted files as requested

        try {
            Media media = new Media(mp3File.getFile().toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> System.err.println("Media error: " + mediaPlayer.getError()));

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> updateValues());
            mediaPlayer.setOnReady(() -> {
                duration = mediaPlayer.getMedia().getDuration();
                updateValues();
            });
            mediaPlayer.setOnEndOfMedia(() -> {
                // Auto play next
                int nextIndex = tableView.getSelectionModel().getSelectedIndex() + 1;
                if (nextIndex < tableView.getItems().size()) {
                    tableView.getSelectionModel().select(nextIndex);
                }
            });

            mediaPlayer.play();
            playPauseButton.setText("||");
        } catch (Exception e) {
            System.err.println("Error playing file: " + mp3File.getFile().getAbsolutePath());
            e.printStackTrace();
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null)
            return;

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText(">");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("||");
        }
    }

    private void updateValues() {
        if (timeLabel != null && timeSlider != null && duration != null && mediaPlayer != null) {
            Platform.runLater(() -> {
                Duration currentTime = mediaPlayer.getCurrentTime();
                timeLabel.setText(formatTime(currentTime, duration));
                timeSlider.setDisable(duration.isUnknown());
                if (!timeSlider.isDisabled() && duration.greaterThan(Duration.ZERO) && !isSliderDragging) {
                    timeSlider.setValue(currentTime.toMillis() / duration.toMillis() * 100.0);
                }
            });
        }
    }

    private static String formatTime(Duration elapsed, Duration duration) {
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
        if (mediaPlayer != null) {
            mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(seconds)));
        }
    }

    private void handleBackspace() {
        Mp3File selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null)
            return;

        if (selectedItem.isDeleted()) {
            restoreFile(selectedItem);
        } else {
            softDeleteFile(selectedItem);
        }
    }

    private void softDeleteFile(Mp3File item) {
        try {
            // Stop playback if playing this file
            if (mediaPlayer != null && mediaPlayer.getMedia().getSource().contains(item.getFile().toURI().toString())) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
                playPauseButton.setText(">");
                timeLabel.setText("00:00 / 00:00");
                timeSlider.setValue(0);
            }

            File original = item.getFile();
            File parent = original.getParentFile();
            File deletedDir = new File(parent, "_deleted");

            // Create _deleted directory if needed
            if (!deletedDir.exists()) {
                deletedDir.mkdirs();
            }

            File deleted = new File(deletedDir, original.getName());

            // Move file
            Files.move(original.toPath(), deleted.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Update item state
            item.setFile(deleted);
            System.out.println("Soft deleted: " + original.getName());

            // Select next item
            int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
            if (selectedIndex < tableView.getItems().size() - 1) {
                tableView.getSelectionModel().select(selectedIndex + 1);
            }

        } catch (IOException e) {
            System.err.println("Failed to soft delete: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void restoreFile(Mp3File item) {
        try {
            File deleted = item.getFile();
            File deletedDir = deleted.getParentFile();
            File parent = deletedDir.getParentFile();
            File original = new File(parent, deleted.getName());

            if (deleted.exists()) {
                Files.move(deleted.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Update item state
                item.setFile(original);
                System.out.println("Restored: " + original.getName());
            }
        } catch (IOException e) {
            System.err.println("Failed to restore: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
