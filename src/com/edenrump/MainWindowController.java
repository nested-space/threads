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
import com.edenrump.ui.views.ProcessDisplay;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import sun.security.provider.certpath.Vertex;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MainWindowController implements Initializable {

    /**
     * Display pane for information about the process and selected contents
     */
    public VBox infoPane;

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
     * Initialise the application window
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        addMainMenu(borderBase);

        processDisplay = new ProcessDisplay(displayWrapper);
        createNew();

        ObservableList<String> selectedVertices = processDisplay.getSelectedVerticesObservableList();
        selectedVertices.addListener((ListChangeListener<String>) c -> {
            setInfoPaneTitle(vertexInfoInMemory.size(), c.getList().size());
            if(c.getList().size()>0){
                maximiseInfoPane();
            } else {
                minimiseInfoPane();
            }
                c.next();
            setInfoPaneComments(c.getList().stream().map(id -> processDisplay.getVertex(id).get()).collect(Collectors.toList()));
        });

        Platform.runLater(() -> stage.getScene().setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.DELETE){
                if (selectedVertices.size() == 1) {
                    processDisplay.removeVertex(selectedVertices.get(0));
                } else if (selectedVertices.size() > 1) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Multiple vertex deletion");
                    alert.setHeaderText("Multiple vertices are selected");
                    alert.setContentText("Proceed to delete " + selectedVertices.size() + " vertices?");
                    alert.showAndWait();
                    new ArrayList<>(selectedVertices).forEach(id -> processDisplay.removeVertex(id));
                }
            } else if(key.getCode() == KeyCode.ESCAPE){
                processDisplay.deselectAll();
            } else if(key.getCode() == KeyCode.A && key.isControlDown()){
                processDisplay.selectAll();
            }
        }));
    }

    private Timeline infoPaneTimeline = new Timeline();

    private void minimiseInfoPane(){
        infoPaneTimeline.stop();
        infoPaneTimeline.getKeyFrames().clear();
        infoPaneTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(0), new KeyValue(infoPane.prefWidthProperty(), infoPane.getPrefWidth())),
                new KeyFrame(Duration.millis(250), new KeyValue(infoPane.prefWidthProperty(), 35)));
        infoPaneTimeline.playFromStart();
    }

    private void maximiseInfoPane(){
        infoPaneTimeline.stop();
        infoPaneTimeline.getKeyFrames().clear();
        infoPaneTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(0), new KeyValue(infoPane.prefWidthProperty(), infoPane.getPrefWidth())),
                new KeyFrame(Duration.millis(250), new KeyValue(infoPane.prefWidthProperty(), 250)));
        infoPaneTimeline.playFromStart();
    }


    /**
     * The main menu for the application
     */
    private MenuBar menu = new MenuBar();

    /**
     * Method default menu for the application
     *
     * @param borderPane main display of the application
     */
    private void addMainMenu(BorderPane borderPane) {
        menu.setMinHeight(25);

        Menu file = new Menu("_File");

        MenuItem newFile = new MenuItem("_New");
        newFile.setOnAction(actionEvent -> createNew());

        MenuItem openFile = new MenuItem("_Open");
        openFile.setOnAction(actionEvent -> loadFile());

        MenuItem saveFile = new MenuItem("_Save");
        saveFile.setOnAction(event -> saveFile());

        MenuItem close = new MenuItem("_Close");
        close.setOnAction(event -> {
            if (processDisplay.requestClose()) Platform.exit();
        });

        file.getItems().setAll(newFile, openFile, saveFile, close);
        menu.getMenus().add(file);
        borderPane.setTop(menu);
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

        boolean fate = JSONLoader.saveToJSON(new ThreadsData("Test", "Test", processDisplay.getVertexInfo()), file);

        if (fate) {
            stage.setTitle(Defaults.createTitle(file.getName()));
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failure");
            alert.setHeaderText("Failed to save file");
            alert.setContentText("File name valid but a problem occurred saving the data to JSON format");
            alert.showAndWait();
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
        if (programState == ProgramState.UNSAVED && cancelActionToSaveContent()) return;

        ThreadsData loaded = JSONLoader.loadOneFromJSON(file);

        if (loaded != null) {
            clearAll();
            vertexInfoInMemory = loaded.getVertices();
            fileName = loaded.getName();
            fileID = loaded.getId();

            processDisplay.create(vertexInfoInMemory);
            processDisplay.show();

            setInfoPaneTitle(vertexInfoInMemory.size(), 0);
            setInfoPaneComments(new ArrayList<>());
        }
    }

    /**
     * Container for infopane data
     */
    @FXML
    private VBox commentPane = new VBox();

    /**
     * Label holding the title of the infoPane
     */
    @FXML
    private Label infoPaneTitle = new Label();

    /**
     * Set the title of the infoPane as "Vertices: " + total + " (" + selected + ")"
     *
     * @param total    the total number of vertices in the display
     * @param selected the total number of vertices selected
     */
    private void setInfoPaneTitle(Integer total, Integer selected) {
        infoPaneTitle.setText("Vertices: " + total + " (" + selected + ")");
    }

    /**
     * Set the data in the info pane //TODO: replace with better vertex descriptions
     *
     * @param data the data to be added
     */
    private void setInfoPaneComments(List<VertexData> data) {
        commentPane.getChildren().clear();
        if (data.size() == 0) return;
        commentPane.getChildren().add(new Separator(Orientation.HORIZONTAL));
        for (VertexData vertex : data) {
            Label label = new Label(vertex.getName());
            label.getStyleClass().add("comment-text");
            commentPane.getChildren().add(label);
        }
        commentPane.getChildren().add(new Separator(Orientation.HORIZONTAL));
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
    private boolean cancelActionToSaveContent() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Discard Unsaved Content?");
        alert.setHeaderText("Proceeding will discard unsaved content");
        alert.setContentText("Go ahead and discard?");

        Optional<ButtonType> result = alert.showAndWait();
        return !result.filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }

    /**
     * The current state of the program.
     */
    private ProgramState programState = ProgramState.CLOSED;

    /**
     * Create a new thread map and display the start node
     */
    private void createNew() {
        if (programState == ProgramState.UNSAVED && cancelActionToSaveContent()) return;
        clearAll();
        ThreadsData startingState = initialState();
        vertexInfoInMemory = startingState.getVertices();
        fileName = startingState.getName();
        fileID = startingState.getId();

        processDisplay.create(vertexInfoInMemory);
        processDisplay.show();

        setInfoPaneTitle(vertexInfoInMemory.size(), 0);
        setInfoPaneComments(new ArrayList<>());
    }

    /**
     * Create a new display with a standard set-up of vertices
     *
     * @return the seed display
     */
    private ThreadsData initialState() {
        //Start up a new map
        VertexData startingNode = new VertexData("End Node", 0, 0);
        VertexData minusOne = new VertexData("n-1", 1, 0);
        VertexData minusTwo = new VertexData("n-2", 2, 0);
        VertexData minusTwoA = new VertexData("n-2a", 2, 0);

        minusOne.addConnection(startingNode.getId());
        minusTwo.addConnection((minusOne.getId()));
        minusTwoA.addConnection((minusOne.getId()));

        startingNode.addConnection(minusOne.getId());
        minusOne.addConnection((minusTwo.getId()));
        minusOne.addConnection((minusTwoA.getId()));

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
     *
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
