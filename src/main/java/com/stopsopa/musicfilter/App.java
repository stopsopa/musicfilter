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
        Scene scene = new Scene(root, 640, 480);
        
        // Handle Drag and Drop on the Scene
        scene.setOnDragOver(controller::handleDragOver);
        scene.setOnDragDropped(controller::handleDragDropped);
        
        // Handle Key Events
        scene.setOnKeyPressed(controller::handleKeyPressed);

        stage.setScene(scene);
        stage.setTitle("Music Filter");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
