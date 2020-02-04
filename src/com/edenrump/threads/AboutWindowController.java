/*------------------------------------------------------------------------------
 - Copyright (c) 09/12/2019, 14:03.2019. EmpowerLCSimConverter by Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 -
 - Based on a work at https://github.com/nested-space/fxLCInfoConverter.To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 -----------------------------------------------------------------------------*/

package com.edenrump.threads;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Class controls the About window in the converter application
 */
public class AboutWindowController implements Initializable {

    /**
     * The label that displays the build number of the application
     */
    public Label buildNumberLabel;

    /**
     * The label that displays the version number of the application
     */
    public Label versionNumberLabel;

    /**
     * The close button
     */
    public Button closeButton;

    /**
     * Method responsible for closing the window
     * @param actionEvent event taht triggers the method
     */
    public void handleClose(ActionEvent actionEvent) {
        closeButton.getScene().getWindow().hide();
    }

    /**
     * Method to open a hyperlink clicked by the user
     * @param actionEvent the event that triggers the opening of the hyperlink (usually clicking a hyperlink)
     */
    public void handleOpenHyperlink(ActionEvent actionEvent) {
        Hyperlink hl = (Hyperlink) actionEvent.getTarget();
        Application app = new Application() {
            @Override
            public void start(Stage stage) throws Exception {

            }
        };
        app.getHostServices().showDocument(hl.getAccessibleText());
        actionEvent.consume();
    }

    /**
     * The default property name for the build time
     */
    private static final String BUILD_TIME = "build.time";

    /**
     * The default property name for the version number
     */
    private static final String VERSION_NUMBER = "version";

    /**
     * Initial actions taken by the controller to populate fields on initialisation
     * @param location the URL location
     * @param resources the resource bundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Properties prop = new Properties();
        try {
            prop.load(getClass().getResourceAsStream("/properties/build.properties"));
            buildNumberLabel.setText(prop.getProperty(BUILD_TIME));
            versionNumberLabel.setText(prop.getProperty(VERSION_NUMBER));
        } catch (IOException e) {
            buildNumberLabel.setText("Build number not correctly retrieved...");
            versionNumberLabel.setText("Version number not correctly retrieved...");
            e.printStackTrace();
        }

    }
}
