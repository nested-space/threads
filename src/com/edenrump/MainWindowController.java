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
import com.edenrump.views.DepthGraphDisplay;
import com.edenrump.views.TreeDepthGraphDisplay;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
    private DepthGraphDisplay depthGraphDisplay;

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

        depthGraphDisplay = new TreeDepthGraphDisplay(displayWrapper);
        BooleanProperty unsavedInDisplay = depthGraphDisplay.hasUnsavedContentProperty();
        unsavedInDisplay.addListener((obs, o, n) ->{
            if(n){
                registerChange();
            }
        });
        createNew();

        ObservableList<String> selectedVertices = depthGraphDisplay.getSelectedVerticesObservableList();
        selectedVertices.addListener((ListChangeListener<String>) c -> {
            setInfoPaneTitle(vertexInfoInMemory.size(), c.getList().size());
            c.next();
            setInfoPaneComments(c.getList().stream().map(id -> depthGraphDisplay.getVertex(id).get()).collect(Collectors.toList()));
        });

        Platform.runLater(() -> stage.getScene().setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.DELETE) {
                if (selectedVertices.size() == 1) {
                    depthGraphDisplay.removeVertex(selectedVertices.get(0));
                } else if (selectedVertices.size() > 1) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Multiple vertex deletion");
                    alert.setHeaderText("Multiple vertices are selected");
                    alert.setContentText("Proceed to delete " + selectedVertices.size() + " vertices?");
                    alert.showAndWait();
                    new ArrayList<>(selectedVertices).forEach(id -> depthGraphDisplay.removeVertex(id));
                }
            } else if (key.getCode() == KeyCode.ESCAPE) {
                depthGraphDisplay.deselectAll();
            } else if (key.getCode() == KeyCode.A && key.isControlDown()) {
                depthGraphDisplay.selectAll();
            }
        }));
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
            if (programState == ProgramState.UNSAVED) {
                if(cancelActionToSaveContent()) Platform.exit();
            } else {
                Platform.exit();
            }
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

        boolean fate = JSONLoader.saveToJSON(new ThreadsData("Test", "Test", depthGraphDisplay.getVertexInfo()), file);

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
        if (programState == ProgramState.UNSAVED && cancelActionToSaveContent()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Threads file (.wool)", "*.wool"));
        File file = fileChooser.showOpenDialog(stage.getScene().getWindow());
        if (file == null) return;

        ThreadsData loaded = JSONLoader.loadOneFromJSON(file);

        if (loaded != null) {
            clearAll();
            vertexInfoInMemory = loaded.getVertices();
            fileName = loaded.getName();
            fileID = loaded.getId();

            depthGraphDisplay.create(vertexInfoInMemory);
            depthGraphDisplay.show();

            setInfoPaneTitle(vertexInfoInMemory.size(), 0);
            setInfoPaneComments(new ArrayList<>());
        }

        registerChange();
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
        infoPaneTitle.setText("Vertex info: " + " (" + selected + " selected)");
    }

    /**
     * Set the data in the info pane //TODO: replace with better vertex descriptions
     *
     * @param data the data to be added
     */
    private void setInfoPaneComments(List<VertexData> data) {
        commentPane.getChildren().clear();
        if (data.size() == 0) return;
        for (VertexData vertex : data) {
            GridPane holder = new GridPane();
            holder.setPadding(new Insets(10));
            holder.setVgap(5);
            holder.setHgap(5);

            HBox buttons = new HBox();
            buttons.setAlignment(Pos.CENTER_RIGHT);
            buttons.setSpacing(10);
            Button edit = new Button("Edit");
            Button cancel = new Button("Cancel");
            Button confirm = new Button("OK");

            Label titleKey = new Label("Title: ");
            titleKey.setPrefHeight(27);
            titleKey.setPrefWidth(120);
            Label titleValue = new Label(vertex.getName());
            TextField titleEdit = new TextField(vertex.getName());

            GridPane.setConstraints(titleKey, 0, 0);
            GridPane.setConstraints(titleKey, 0, 0);
            GridPane.setConstraints(titleValue, 1, 0);
            GridPane.setConstraints(titleValue, 1, 0);
            GridPane.setConstraints(titleEdit, 1, 0);
            GridPane.setConstraints(titleEdit, 1, 0);
            holder.getChildren().addAll(titleKey, titleValue);

            Label hyperlinkKey = new Label("Hyperlink");
            hyperlinkKey.setPrefHeight(27);
            hyperlinkKey.setPrefWidth(120);
            Label hyperlinkValue = new Label(vertex.getHyperlinkURL() == null ? "(none)" : vertex.getHyperlinkURL());
            hyperlinkValue.setPrefWidth(151);
            TextField hyperlinkEdit = new TextField(vertex.getHyperlinkURL() == null ? "" : vertex.getHyperlinkURL());
            hyperlinkEdit.setPrefWidth(151);
            hyperlinkEdit.setOnKeyPressed(event -> {
                        if (event.getCode() == KeyCode.ENTER) {
                            updateVertex(holder, titleEdit, hyperlinkEdit, titleValue, hyperlinkValue, vertex);
                            buttons.getChildren().setAll(edit);
                        } else if (event.getCode() == KeyCode.ESCAPE){
                            holder.getChildren().removeAll(titleEdit, hyperlinkEdit);
                            holder.getChildren().addAll(titleValue, hyperlinkValue);
                            buttons.getChildren().setAll(edit);
                        }
                    }
            );
            titleEdit.setOnKeyPressed(hyperlinkEdit.getOnKeyPressed());

            ImageView launch = new ImageView(new Image(getClass().getResourceAsStream("/img/internet.png")));
            GridPane.setConstraints(hyperlinkKey, 0, 1);
            GridPane.setConstraints(hyperlinkKey, 0, 1);
            GridPane.setConstraints(hyperlinkValue, 1, 1);
            GridPane.setConstraints(hyperlinkValue, 1, 1);
            GridPane.setConstraints(hyperlinkEdit, 1, 1);
            GridPane.setConstraints(hyperlinkEdit, 1, 1);
            GridPane.setConstraints(launch, 2, 1);
            GridPane.setConstraints(launch, 2, 1);
            holder.getChildren().addAll(hyperlinkKey, hyperlinkValue);

            edit.setOnAction(event -> {
                holder.getChildren().removeAll(titleValue, hyperlinkValue);
                titleEdit.setText(titleValue.getText());
                hyperlinkEdit.setText(vertex.getHyperlinkURL() == null ? "" : vertex.getHyperlinkURL());
                holder.getChildren().addAll(titleEdit, hyperlinkEdit);
                buttons.getChildren().setAll(cancel, confirm);
                titleEdit.requestFocus();
                titleEdit.positionCaret(0);
                titleEdit.selectAll();
            });

            cancel.setOnAction(event -> {
                holder.getChildren().removeAll(titleEdit, hyperlinkEdit);
                holder.getChildren().addAll(titleValue, hyperlinkValue);
                buttons.getChildren().setAll(edit);
            });

            confirm.setOnAction(event -> {
                updateVertex(holder, titleEdit, hyperlinkEdit, titleValue, hyperlinkValue, vertex);
                buttons.getChildren().setAll(edit);
            });

            GridPane.setConstraints(buttons, 1, 4);
            GridPane.setConstraints(buttons, 1, 4);

            buttons.getChildren().setAll(edit);
            holder.getChildren().addAll(buttons);
            commentPane.getChildren().addAll(holder, new Separator(Orientation.HORIZONTAL));
        }
    }

    private void updateVertex(Pane holder, TextField titleEdit, TextField hyperlinkEdit,
                              Label titleValue, Label hyperlinkValue, VertexData vertex) {
        holder.getChildren().removeAll(titleEdit, hyperlinkEdit);
        holder.getChildren().addAll(titleValue, hyperlinkValue);

        vertex.setName(titleEdit.getText());
        vertex.setHyperlinkURL(hyperlinkEdit.getText());

        titleValue.setText(titleEdit.getText());
        hyperlinkValue.setText(hyperlinkEdit.getText() == null ? "(none)" : vertex.getHyperlinkURL());

        depthGraphDisplay.updateVertex(vertex.getId(), vertex);

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

        depthGraphDisplay.create(vertexInfoInMemory);
        depthGraphDisplay.show();

        setInfoPaneTitle(vertexInfoInMemory.size(), 0);
        setInfoPaneComments(new ArrayList<>());
    }

    /**
     * Create a new display with a standard set-up of vertices
     *
     * @return the seed display
     */
    private ThreadsData initialState() {
        File example = new File("res/examples/AceticAcid.wool");
        return JSONLoader.loadOneFromJSON(new File("res/examples/AceticAcid.wool"));
    }

    /**
     * Clear all vertices in memory. Clear the process dispaly. Close the current file. Reset the window title
     */
    private void clearAll() {
        vertexInfoInMemory.clear();
        depthGraphDisplay.clearAll();

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
