package com.stopsopa.musicfilter;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        MusicController controller = new MusicController(stage);

        StackPane root = new StackPane(controller.getView());
        Scene scene = new Scene(root, 1060, 800);

        // Handle Drag and Drop on the Scene
        scene.setOnDragOver(controller::handleDragOver);
        scene.setOnDragDropped(controller::handleDragDropped);

        // Handle Global Key Events via Event Filter
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, controller::handleKeyPressed);

        stage.setScene(scene);
        stage.setTitle("Music Filter");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
