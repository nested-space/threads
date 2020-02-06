/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.threads;

import com.edenrump.threads.output.PDFExporter;
import com.edenrump.threads.views.TreeDepthGraphDisplay;
import com.edenrump.toolkit.config.Defaults;
import com.edenrump.toolkit.graph.DataAndNodes;
import com.edenrump.toolkit.graph.DepthDirection;
import com.edenrump.toolkit.graph.Graph;
import com.edenrump.toolkit.loaders.JSONLoader;
import com.edenrump.toolkit.models.ThreadsData;
import com.edenrump.toolkit.models.VertexData;
import com.edenrump.toolkit.ui.DepthGraphDisplay;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
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
        unsavedInDisplay.addListener((obs, o, n) -> {
            if (n) {
                registerChange();
            }
        });
        createNew();

        ObservableList<String> selectedVertices = depthGraphDisplay.getSelectedVertices();
        selectedVertices.addListener((ListChangeListener<String>) c -> {
            setInfoPaneTitle(vertexInfoInMemory.size(), c.getList().size());
            c.next();
            setInfoPaneComments(c.getList().stream().map(id -> depthGraphDisplay.getVertex(id).get()).collect(Collectors.toList()));
        });

        IntegerProperty priority = new SimpleIntegerProperty(192000);
        Platform.runLater(() -> stage.getScene().setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.DELETE) {
                if (selectedVertices.size() == 1) {
                    depthGraphDisplay.deleteVertexAndUpdateDisplay(selectedVertices.get(0));
                } else if (selectedVertices.size() > 1) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
                    stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
                    alert.getDialogPane().getStylesheets().add("/css/Global.css");

                    alert.setTitle("Multiple vertex deletion");
                    alert.setHeaderText("Multiple vertices are selected");
                    alert.setContentText("Proceed to delete " + selectedVertices.size() + " vertices?");

                    alert.showAndWait();
                    new ArrayList<>(selectedVertices).forEach(id -> depthGraphDisplay.deleteVertexAndUpdateDisplay(id));
                }
            } else if (key.getCode() == KeyCode.ESCAPE) {
                depthGraphDisplay.deselectAll();
            } else if (key.getCode() == KeyCode.A && key.isControlDown()) {
                depthGraphDisplay.selectAll();
            } else if (key.getCode() == KeyCode.ENTER && key.isControlDown()) {
                priority.set(priority.get() + 32000);
                depthGraphDisplay.addVertexToDisplay(new VertexData("Module", 0, priority.get()));
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
        close.setOnAction(event -> closeFile());

        Menu export = new Menu("_Export");
        MenuItem exportToPDF = new MenuItem("To PDF");
        exportToPDF.setOnAction(e -> exportToPDF());
        MenuItem exportToPNG = new MenuItem("to PNG");
        exportToPNG.setOnAction(e -> exportPNG());
        export.getItems().addAll(exportToPDF, exportToPNG);

        Menu loadFromTemplate = new Menu("Load from _Template");
        MenuItem example = new MenuItem("Example File");
        example.setOnAction(e -> loadFile(new File("res/examples/Example.json")));
        MenuItem CTDtemplate = new MenuItem("Clinical Trials Document");
        CTDtemplate.setOnAction(e -> loadFile(new File("res/examples/CTD_template.json")));
        loadFromTemplate.getItems().addAll(example, CTDtemplate);

        Menu help = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction((launchAbout) -> {
            FXMLLoader fxmlLoader;
            Parent root;
            fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/About.fxml"));
            try {
                root = fxmlLoader.load();
                Stage stage = new Stage();
                stage.setTitle("About");
                stage.initOwner(borderBase.getScene().getWindow());
                stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
                stage.setScene(new Scene(root));
                stage.showAndWait();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        help.getItems().add(about);

        file.getItems().setAll(newFile, openFile, loadFromTemplate, export, saveFile, close);

        Menu view = new Menu("_View");

        MenuItem clearFilters = new MenuItem("Clear filters");
        clearFilters.setOnAction(e -> {
            clearCurrentVisibilityFilters();
            depthGraphDisplay.updateDisplay();
        });

        Menu filter = new Menu("_Filter");

        MenuItem l = colorFilterMenuItem("Lime", "#c4d600");
        MenuItem g = colorFilterMenuItem("Gold", "#f0ab00");
        MenuItem lb = colorFilterMenuItem("Light Blue", "#D1DBE3");
        MenuItem n = colorFilterMenuItem("Navy", "#003865");
        MenuItem m = colorFilterMenuItem("Mulberry", "#830051");
        MenuItem r = colorFilterMenuItem("Red", "#EA3C53");
        MenuItem gr = colorFilterMenuItem("Green", "#50C878");
        filter.getItems().addAll(m, n, l, g, lb, r, gr);

        view.getItems().addAll(clearFilters, filter);

        menu.getMenus().addAll(file, view, help);
        borderPane.setTop(menu);
    }

    private void clearCurrentVisibilityFilters() {
        for (Predicate<? super DataAndNodes> filter : visibilityFilters) {
            depthGraphDisplay.removeVisibilityFilter(filter);
        }
        visibilityFilters.clear();
    }

    private List<Predicate<? super DataAndNodes>> visibilityFilters = new ArrayList<>();

    private MenuItem colorFilterMenuItem(String cName, String cValue) {
        MenuItem m = new MenuItem(cName);

        Predicate<DataAndNodes> filter = data -> {
            if (!data.getVertexData().hasProperty("color")) return false;
            List<VertexData> downstream = Graph.unidirectionalFill(data.getVertexData().getId(), DepthDirection.INCREASING_DEPTH, depthGraphDisplay.getAllVertexData());
            for (VertexData vertex : downstream) {
                if (!vertex.hasProperty("color")) continue;
                if (sameColor(vertex.getProperty("color"), cValue)) return true;
            }
            return sameColor(data.getVertexData().getProperty("color"), cValue);
        };

        m.setOnAction(e -> {
            //clear current filters
            clearCurrentVisibilityFilters();
            visibilityFilters.add(filter);
            depthGraphDisplay.addVisibilityFilter(filter);
            depthGraphDisplay.updateDisplay();
        });
        return m;
    }

    private boolean sameColor(String cValue1, String cValue2) {
        if (cValue1 == null || cValue2 == null) return false;
        if (cValue1.length() == 0 || cValue2.length() == 0) return false;
        Color c1 = Color.web(cValue1);
        Color c2 = Color.web(cValue2);
        return c1.getGreen() == c2.getGreen() &&
                c1.getBlue() == c2.getBlue() &&
                c1.getRed() == c2.getRed();
    }

    private void exportPNG() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export image");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image", "*.png"));
        File file = fileChooser.showSaveDialog(stage.getScene().getWindow());
        if (file == null) return;

        WritableImage snapshot = depthGraphDisplay.getSnapShot();
        try {
            RenderedImage renderedImage = SwingFXUtils.fromFXImage(snapshot, null);
            //Write the snapshot to the chosen file
            ImageIO.write(renderedImage, "png", file);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
            alert.getDialogPane().getStylesheets().add("/css/Global.css");

            alert.setTitle("Export Failure");
            alert.setHeaderText("Failed to export file");
            alert.setContentText(e.getMessage());

            alert.showAndWait();
            e.printStackTrace();
        }
    }

    private void exportToPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = fileChooser.showSaveDialog(stage.getScene().getWindow());
        if (file == null) return;

        try {
            PDFExporter.exportCTDGraphToPDF(file, new ThreadsData(fileName, fileID, depthGraphDisplay.getAllVertexData()));
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
            alert.getDialogPane().getStylesheets().add("/css/Global.css");

            alert.setTitle("Export Successful");
            alert.setHeaderText(null);
            alert.setContentText("Data successfully exported to: \n" + file.getAbsolutePath());

            alert.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
            alert.getDialogPane().getStylesheets().add("/css/Global.css");

            alert.setTitle("Export Failure");
            alert.setHeaderText("Failed to export file");
            alert.setContentText(e.getMessage());

            alert.showAndWait();
            e.printStackTrace();
        }
    }

    /**
     * Prompt the user to select a file and save the currently loaded process display to a flat file on the users hard drive
     */
    private void saveFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save To File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Threads file", "*.json"));
        File file = fc.showSaveDialog(stage.getScene().getWindow());
        if (file == null) {
            return;
        }

        if (!file.getName().contains(".")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        boolean fate = JSONLoader.saveToJSON(new ThreadsData("Test", "Test", depthGraphDisplay.getAllVertexData()), file);

        if (fate) {
            stage.setTitle(Defaults.createTitle(file.getName()));
            depthGraphDisplay.setHasUnsavedContent(false);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));
            alert.getDialogPane().getStylesheets().add("/css/Global.css");

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
        if (programState == ProgramState.UNSAVED && !proceeedWithActionAndDiscardUnsavedContent()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Threads file", "*.json"));
        File file = fileChooser.showOpenDialog(stage.getScene().getWindow());
        if (file == null) return;

        loadFile(file);
        registerChange();
    }

    private void loadFile(File file) {
        ThreadsData loaded = JSONLoader.loadOneFromJSON(file);
        if (loaded != null) {
            clearAll();
            vertexInfoInMemory = loaded.getVertices();
            fileName = loaded.getName();
            fileID = loaded.getId();

            depthGraphDisplay.createNewDisplayFromVertexData(vertexInfoInMemory);
            depthGraphDisplay.show();

            setInfoPaneTitle(vertexInfoInMemory.size(), 0);
            setInfoPaneComments(new ArrayList<>());
        }
    }

    private void closeFile() {
        if (programState == ProgramState.UNSAVED) {
            if (proceeedWithActionAndDiscardUnsavedContent()) Platform.exit();
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
            Label hyperlinkValue = new Label(vertex.hasProperty("url") ? vertex.getProperty("url") : "(none)");
            hyperlinkValue.setPrefWidth(151);
            TextField hyperlinkEdit = new TextField(vertex.hasProperty("url") ? vertex.getProperty("url") : "");
            hyperlinkEdit.setPrefWidth(151);
            hyperlinkEdit.setOnKeyPressed(event -> {
                        if (event.getCode() == KeyCode.ENTER) {
                            updateVertex(holder, titleEdit, hyperlinkEdit, titleValue, hyperlinkValue, vertex);
                            buttons.getChildren().setAll(edit);
                        } else if (event.getCode() == KeyCode.ESCAPE) {
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
                hyperlinkEdit.setText(vertex.hasProperty("url") ? vertex.getProperty("url") : "");
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
        vertex.overwriteProperty("url", hyperlinkEdit.getText());

        titleValue.setText(titleEdit.getText());
        hyperlinkValue.setText(hyperlinkEdit.getText() == null ? "(none)" : vertex.getProperty("url"));

        depthGraphDisplay.updateVertexAndRefreshDisplay(vertex.getId(), vertex);

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
    private boolean proceeedWithActionAndDiscardUnsavedContent() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.getDialogPane().getStylesheets().add("/css/Global.css");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/wool.png")));

        alert.setTitle("Discard Unsaved Content?");
        alert.setHeaderText("Proceeding will discard unsaved content");
        alert.setContentText("Go ahead and discard?");

        Optional<ButtonType> result = alert.showAndWait();
        return result.filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }

    /**
     * The current state of the program.
     */
    private ProgramState programState = ProgramState.CLOSED;

    /**
     * Create a new thread map and display the start node
     */
    private void createNew() {
        if (programState == ProgramState.UNSAVED && proceeedWithActionAndDiscardUnsavedContent()) return;
        clearAll();
        ThreadsData startingState = initialState();
        vertexInfoInMemory = startingState.getVertices();
        fileName = startingState.getName();
        fileID = startingState.getId();

        depthGraphDisplay.createNewDisplayFromVertexData(vertexInfoInMemory);
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
        return new ThreadsData("New File", UUID.randomUUID().toString(), new ArrayList<>());
    }

    /**
     * Clear all vertices in memory. Clear the process dispaly. Close the current file. Reset the window title
     */
    private void clearAll() {
        vertexInfoInMemory.clear();
        depthGraphDisplay.clearDisplay();

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
        stage.setOnCloseRequest((e) -> {
            if (programState == ProgramState.UNSAVED) {
                if (proceeedWithActionAndDiscardUnsavedContent()) {
                    Platform.exit();
                } else {
                    e.consume();
                }
            }
        });
    }

    /**
     * Enum representing the possible states of the program.
     */
    private enum ProgramState {
        SAVED, UNSAVED, CLOSED
    }

}
