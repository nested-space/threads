/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump;

import com.edenrump.config.Defaults;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader fxmlLoader;
        Parent root;
        primaryStage.setTitle(Defaults.APPLICATION_NAME + " | File Converter");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("img/wool.png")));
        try {
            fxmlLoader = new FXMLLoader(getClass().getResource("fxml/MainWindow.fxml"));
            root = fxmlLoader.load();
            MainWindowController mainWindowController = fxmlLoader.getController();
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException io) {
            io.printStackTrace();
            Platform.exit();
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}
