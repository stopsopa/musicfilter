package com.stopsopa.musicfilter;

import javafx.application.Platform;
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

import java.awt.Desktop;
import java.io.File;
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
        private final File file;
        private final SimpleStringProperty filename;
        private final SimpleStringProperty title;
        private final SimpleStringProperty artist;
        private final SimpleStringProperty album;

        public Mp3File(File file) {
            this.file = file;
            this.filename = new SimpleStringProperty(file.getName());
            this.title = new SimpleStringProperty("");
            this.artist = new SimpleStringProperty("");
            this.album = new SimpleStringProperty("");
            loadMetadata();
        }

        private void loadMetadata() {
            try {
                Media media = new Media(file.toURI().toString());
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
            return file;
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

        // Handle selection change to play music
        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                playFile(newValue.getFile());
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

    private void playFile(File file) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        try {
            Media media = new Media(file.toURI().toString());
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
            System.err.println("Error playing file: " + file.getAbsolutePath());
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
            deleteCurrentSong();
            event.consume();
        } else if (event.getCode() == KeyCode.SPACE) {
            togglePlayPause();
            event.consume();
        } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
            // Let TableView handle navigation, but ensure focus is there if needed
            // However, since we are using an event filter on Scene, we should be careful
            // not to consume
            // UP/DOWN if the TableView needs them.
            // Actually, if focus is elsewhere, we might want to manually move selection.
            // But standard behavior is usually sufficient if TableView has focus.
            // If user wants UP/DOWN to work GLOBALLY even if focus is on slider:
            if (!tableView.isFocused()) {
                tableView.requestFocus();
                // We don't consume here so TableView can process it after getting focus
            }
        }
    }

    private void seek(double seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(seconds)));
        }
    }

    private void deleteCurrentSong() {
        Mp3File selectedItem = tableView.getSelectionModel().getSelectedItem();
        int selectedIndex = tableView.getSelectionModel().getSelectedIndex();

        if (selectedItem != null) {
            File selectedFile = selectedItem.getFile();
            // Stop playback
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null; // Ensure we don't hold a reference
            }

            playPauseButton.setText(">");
            timeLabel.setText("00:00 / 00:00");
            timeSlider.setValue(0);

            // Remove from list
            tableView.getItems().remove(selectedItem);

            // Select next item if available, or previous
            if (!tableView.getItems().isEmpty()) {
                if (selectedIndex < tableView.getItems().size()) {
                    tableView.getSelectionModel().select(selectedIndex);
                } else {
                    tableView.getSelectionModel().select(tableView.getItems().size() - 1);
                }
            }

            // Delete file
            moveToTrashOrDelete(selectedFile);
        }
    }

    private void moveToTrashOrDelete(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                if (Desktop.getDesktop().moveToTrash(file)) {
                    System.out.println("Moved to trash: " + file.getName());
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to move to trash: " + e.getMessage());
        }

        // Fallback to permanent delete
        try {
            if (file.delete()) {
                System.out.println("Deleted permanently: " + file.getName());
            } else {
                System.err.println("Failed to delete: " + file.getName());
            }
        } catch (Exception e) {
            System.err.println("Error deleting file: " + e.getMessage());
        }
    }
}
