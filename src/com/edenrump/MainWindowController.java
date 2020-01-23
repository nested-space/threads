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
import com.edenrump.loaders.JSONLoader;
import com.edenrump.models.ThreadsData;
import com.edenrump.models.VertexData;
import com.edenrump.ui.menu.Ribbon;
import com.edenrump.ui.views.ProcessDisplay;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.*;

public class MainWindowController implements Initializable {

    /**
     * The stage on which the scene graph of the application is displayed. Useful for changing window titles
     */
    private Stage stage;

    /**
     * The current loaded file
     */
    private String fileName;

    /**
     * The ID of the current loaded file
     */
    private String fileID;

    /**
     * the display pane of the process.
     */
    private ProcessDisplay processDisplay;

    /**
     * Top layer of application, base pane
     */
    public BorderPane borderBase;

    /**
     * Scrollpane that holds all process preparation and display containers.
     */
    public ScrollPane displayWrapper;

    /**
     * The vertex data currently associated with the display
     */
    private List<VertexData> vertexInfoInMemory = new ArrayList<>();
    
    /**
     * Initial actions to load the ribbon of the display and start the user interaction process
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        processDisplay = new ProcessDisplay(displayWrapper);
        loadRibbon(borderBase);
        createNew();
    }

    /**
     * User control ribbon
     */
    private Ribbon mRibbon = new Ribbon();

    /**
     * Method creates Ribbon node to facilitate user interaction and places it at top of BorderPane
     *
     * @param borderPane BorderPane at the top of which ribbon is to be placed
     */
    private void loadRibbon(BorderPane borderPane) {
        mRibbon.addModule(Defaults.LOAD_MODULE_NAME, false);

        Button newFileButton = new Button("New");
        newFileButton.setOnAction(actionEvent -> createNew());

        Button loadFileButton = new Button("Load");
        loadFileButton.setOnAction(actionEvent -> loadFile());

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> saveFile());

        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, newFileButton);
        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, loadFileButton);
        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, saveButton);

        mRibbon.addModule("Test Buttons", true);

        Button resolve = new Button("Resolve Display");
        resolve.setOnAction(actionEvent -> {
            processDisplay.reconcilePrepAndDisplay();
        });

        mRibbon.addControlToModule("Test Buttons", resolve);

        borderPane.setTop(mRibbon);
    }

    /**
     * Prompt the user to select a file and save the currently loaded process display to a flat file on the users hard drive
     */
    private void saveFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save To File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Threads file (.wool)", "*.wool"));
        File file = fc.showSaveDialog(stage.getScene().getWindow());
        if (file == null) {
            return;
        }

        if (!file.getName().contains(".")) {
            file = new File(file.getAbsolutePath() + ".wool");
        }

        boolean fate = JSONLoader.saveToJSON(new ThreadsData("Test", "Test", vertexInfoInMemory), file);

        if (fate) {
            stage.setTitle(Defaults.createTitle(file.getName()));
        } else {
            showFailureAlert("Save Failure",
                    "Failed to save file",
                    "File name valid but a problem occurred saving the data to JSON format");
        }
    }

    /**
     * Method attempts to load the file, if successful displays the information, if unsuccessful, prompts user
     */
    private void loadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Threads file (.wool)", "*.wool"));
        File file = fileChooser.showOpenDialog(stage.getScene().getWindow());
        if (file == null) return;
        if (programState == ProgramState.UNSAVED && !promptDiscardUnsavedContent()) return;

        ThreadsData loaded = JSONLoader.loadOneFromJSON(file);

        if (loaded != null) {
            clearAll();

            vertexInfoInMemory = loaded.getVertices();
            fileName = loaded.getName();
            fileID = loaded.getId();

            //TODO: pass data to display

            processDisplay.create(vertexInfoInMemory);
            processDisplay.show();

        } else {

        }
    }

    /**
     * Utility method. Create an alert and show it to the user
     * @param windowTitle the window title of the alert
     * @param headerText the text to include as the header text of the alert window
     * @param descriptionText the text to include as the description text of the alert
     */
    private void showFailureAlert(String windowTitle, String headerText, String descriptionText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(windowTitle);
        alert.setHeaderText(headerText);
        alert.setContentText(descriptionText);
        alert.showAndWait();
    }

    /**
     * Utility method. Register that a change has been made to the information in the cache and change the
     * window title to display an asterisk after the file name
     */
    private void registerChange() {
        programState = ProgramState.UNSAVED;
        stage.setTitle(Defaults.createTitle(fileName) + "*");
    }

    /**
     * Prompt user whether to close the current file
     *
     * @return the users decision
     */
    private boolean promptDiscardUnsavedContent() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Discard Unsaved Content?");
        alert.setHeaderText("Proceeding will discard unsaved content");
        alert.setContentText("Go ahead and discard?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.filter(buttonType -> buttonType == ButtonType.OK).isPresent()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * The current state of the program.
     */
    private ProgramState programState = ProgramState.CLOSED;

    /**
     * Create a new thread map and display the start node
     */
    private void createNew() {
        if (programState == ProgramState.UNSAVED && !promptDiscardUnsavedContent()) return;

        clearAll();
        ThreadsData startingState = initialState();
        vertexInfoInMemory = startingState.getVertices();
        fileName = startingState.getName();
        fileID = startingState.getId();

        processDisplay.create(vertexInfoInMemory);
        processDisplay.show();
    }

    /**
     * Create a new display with a standard set-up of vertices
     * @return the seed display
     */
    private ThreadsData initialState(){
        //Start up a new map
        VertexData startingNode = new VertexData("End Node");
        VertexData minusOne = new VertexData("n-1");
        VertexData minusTwo = new VertexData("n-2");
        VertexData minusTwoA = new VertexData("n-2a");

        minusOne.addDownstream(startingNode.getId());
        minusTwo.addDownstream((minusOne.getId()));
        minusTwoA.addDownstream((minusOne.getId()));

        startingNode.addUpstream(minusOne.getId());
        minusOne.addUpstream((minusTwo.getId()));
        minusOne.addUpstream((minusTwoA.getId()));

        return new ThreadsData("New File", UUID.randomUUID().toString(), Arrays.asList(
                startingNode, minusOne, minusTwo, minusTwoA));
    }

    /**
     * Clear all vertices in memory. Clear the process dispaly. Close the current file. Reset the window title
     */
    private void clearAll() {
        vertexInfoInMemory.clear();
        processDisplay.clearAll();

        programState = ProgramState.CLOSED;
        if (stage != null) stage.setTitle(Defaults.createTitle("Visualiser"));
    }

    /**
     * Set the Stage that holds the scene graph for this window. Useful for changing window titles..
     * @param stage the stage for this window
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Enum representing the possible states of the program.
     */
    private enum ProgramState {
        SAVED, UNSAVED, CLOSED
    }

}
