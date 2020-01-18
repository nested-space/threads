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
import com.edenrump.models.VertexData;
import com.edenrump.ui.display.HolderRectangle;
import com.edenrump.ui.menu.Ribbon;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.w3c.dom.css.Rect;
import sun.security.provider.certpath.Vertex;

import javax.xml.ws.Holder;
import java.io.File;
import java.net.URL;
import java.util.*;

public class MainWindowController implements Initializable {

    /**
     * Top layer of application, base pane
     */
    public BorderPane borderBase;

    /**
     * Container in the background of the display that deals with position of nodes
     * <p>
     * This is used in prototyping because it seems easier to have JavaFX deal with the positions of the nodes
     * and then (if positions change) to create animations on the nodes in the anchorpane displayOverlay which
     * move nodes from their original position to their new one. This might be less efficient, but it means I don't
     * have to code my own Region types to hold the nodes.
     */
    public HBox preparationContainer;

    /**
     * Anchorpane at the front of the display in which real nodes are placed
     */
    public AnchorPane displayOverlay;

    /**
     * The vertex data currently associated with the display
     */
    private List<VertexData> vertexInfoInMemory = new ArrayList<>();

    /**
     * A map of node depth to node data
     */
    private Map<Integer, List<VertexData>> nodeDepthMap = new HashMap<>();

    /**
     * A map of node containers to info nodes
     */
    private Map<Integer, Node> displayDepthMap = new HashMap<>();

    /**
     * A map of IDs to preparation and display nodes.
     */
    private Map<String, DataAndNodes> idToNodeMap;

    private Map<Node, Node> preparationDisplayMap = new HashMap<>();

    /**
     * A list of edges currently displayed
     */
    private List<Line> edges = new ArrayList<>();

    /**
     * Initial actions to load the ribbon of the display and start the user interaction process
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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

        Button button = new Button("", new ImageView("/img/folder.png"));
        button.setOnAction(actionEvent -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");
            File file = fileChooser.showOpenDialog(button.getScene().getWindow());
            if (file != null) loadFile(file);
            actionEvent.consume();
        });

        Button newFileButton = new Button("", new ImageView("/img/wool.png"));
        newFileButton.setOnAction(actionEvent -> {
            newButtonPressed();
        });

        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, button);
        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, newFileButton);

        mRibbon.addModule("Test Buttons", true);

        Button testAlignTop = new Button("Align Top");
        testAlignTop.setOnAction(event -> {
            for (Node container : preparationContainer.getChildren()) {
                if (container instanceof VBox) {
                    VBox v = (VBox) container;
                    v.setAlignment(Pos.TOP_CENTER);
                }
            }
            reconcilePrepAndDisplay();
        });

        Button testAlignCenter = new Button("Align Center");
        testAlignCenter.setOnAction(event -> {
            for (Node container : preparationContainer.getChildren()) {
                if (container instanceof VBox) {
                    VBox v = (VBox) container;
                    v.setAlignment(Pos.CENTER);
                }
            }
            reconcilePrepAndDisplay();
        });

        Button testAlignBottom = new Button("Align Bottom");
        testAlignBottom.setOnAction(event -> {
            for (Node container : preparationContainer.getChildren()) {
                if (container instanceof VBox) {
                    VBox v = (VBox) container;
                    v.setAlignment(Pos.BOTTOM_CENTER);
                }
            }
            reconcilePrepAndDisplay();
        });

        Button increaseSpacing = new Button("Spacing + ");
        increaseSpacing.setOnAction(event -> {
            preparationContainer.setSpacing(preparationContainer.getSpacing() + 25);
            reconcilePrepAndDisplay();
        });

        Button decreaseSpacing = new Button("Spacing + ");
        decreaseSpacing.setOnAction(event -> {
            preparationContainer.setSpacing(preparationContainer.getSpacing() - 25);
            reconcilePrepAndDisplay();
        });

        Button resolve = new Button("Resolve Display");
        resolve.setOnAction(actionEvent -> {
            reconcilePrepAndDisplay();
        });

        mRibbon.addControlToModule("Test Buttons", testAlignTop);
        mRibbon.addControlToModule("Test Buttons", testAlignCenter);
        mRibbon.addControlToModule("Test Buttons", testAlignBottom);
        mRibbon.addControlToModule("Test Buttons", resolve);
        mRibbon.addControlToModule("Test Buttons", increaseSpacing);
        mRibbon.addControlToModule("Test Buttons", decreaseSpacing);

        borderPane.setTop(mRibbon);
    }

    /**
     * Determine whether a map is currently loaded. If yes, prompt user to close. If not, load new file.
     */
    private void newButtonPressed() {
        if (programState == ProgramState.Loaded) {
            if (promptUserClose()) createNew();
        } else {
            createNew();
        }
    }

    /**
     * Prompt user whether to close the current file
     *
     * @return the users decision
     */
    private boolean promptUserClose() {
        //TODO: prompt user whether to close the current file. Possibly add save option
        return true;
    }

    /**
     * The current state of the program.
     */
    private ProgramState programState = ProgramState.Closed;

    /**
     * Method attempts to load the file, if successful displays the information, if unsuccessful, prompts user
     *
     * @param file the file to be loaded
     */
    private void loadFile(File file) {
    }

    /**
     * Create a new thread map and display the start node
     */
    private void createNew() {
        //Close everything down
        clearNodeInformation();
        clearNodeReals();
        programState = ProgramState.Closed;

        //Start up a new map
        VertexData startingNode = new VertexData("End Node");
        VertexData minusOne = new VertexData("n-1");
        VertexData minusTwo = new VertexData("n-2");

        minusOne.addDownstream(startingNode.getId());
        startingNode.addUpstream(minusOne.getId());

        minusTwo.addDownstream((minusOne.getId()));
        minusOne.addUpstream((minusTwo.getId()));
        vertexInfoInMemory.addAll(Arrays.asList(startingNode, minusOne, minusTwo));

        nodeDepthMap = createNodeMapping(vertexInfoInMemory);
        PreparationDisplayMaps pdm = createNodeDisplay(nodeDepthMap);
        idToNodeMap = pdm.idNodeMap;
        displayDepthMap = pdm.depthToPrep;
        preparationDisplayMap = pdm.prepToDisplay;

        //Load the preparation container with nodes
        for (int i = displayDepthMap.keySet().size() - 1; i > -1; i--) {
            preparationContainer.getChildren().addAll(displayDepthMap.get(i));
        }

        //add edges
        List<VertexData> unvisitedNodes = findUpstreamLeaves(vertexInfoInMemory);
        while (unvisitedNodes.size() > 0) {
            VertexData vd = unvisitedNodes.remove(0);
            for (String id : vd.getDownstream()) unvisitedNodes.add(idToNodeMap.get(id).vertexData);

            HolderRectangle dStart = (HolderRectangle) idToNodeMap.get(vd.getId()).displayNode;
            System.out.println("Upstream Node: " + vd.getName());
            for (String id : vd.getDownstream()) {
                System.out.println("Downstream Node: " + idToNodeMap.get(id).vertexData.getName());
                HolderRectangle dEnd = (HolderRectangle) idToNodeMap.get(id).displayNode;
                Line edge = new Line();
                edge.setStrokeWidth(2);
                edge.setStrokeLineCap(StrokeLineCap.ROUND);
                edge.startXProperty().bind(dStart.layoutXProperty().add(dStart.getHeaderRect().widthProperty()));
                edge.startYProperty().bind(dStart.layoutYProperty().add(dStart.getHeaderRect().heightProperty().divide(2)));
                edge.endXProperty().bind(dEnd.layoutXProperty());
                edge.endYProperty().bind(dEnd.layoutYProperty().add(dEnd.getHeaderRect().heightProperty().divide(2)));
                edges.add(edge);
                displayOverlay.getChildren().add(0, edge);
            }
        }

        //Delay to allow layout cascade to happen, then load the displayOverlay with nodes
        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                displayOverlay.getChildren().addAll(preparationDisplayMap.values());
                double x = displayOverlay.getLayoutBounds().getWidth() / 2;
                double y = displayOverlay.getLayoutBounds().getHeight() / 2;
                for (Node n : preparationDisplayMap.values()) {
                    n.setLayoutX(x);
                    n.setLayoutY(y);
                }
                reconcilePrepAndDisplay();
            });
            t.playFromStart();
        });
    }

    private void reconcilePrepAndDisplay() {
        Timeline all = new Timeline(30);
        for (Node prepNode : preparationDisplayMap.keySet()) {
            Node displayNode = preparationDisplayMap.get(prepNode);
            double prepX = prepNode.localToScene(prepNode.getLayoutBounds()).getMinX();
            double prepY = prepNode.localToScene(prepNode.getLayoutBounds()).getMinY();
            all.getKeyFrames().addAll(Arrays.asList((
                            new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX()))),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(350), new KeyValue(displayNode.layoutXProperty(), ltsX(prepNode))),
                    new KeyFrame(Duration.millis(350), new KeyValue(displayNode.layoutYProperty(), ltsY(prepNode)))));
        }
        all.playFromStart();
    }

    /**
     * Return the Bounds of the node in the scene. Utility method to help concise code.
     *
     * @param node the node in the scene
     * @return the bounds in scene
     */
    private Double ltsX(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinX() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinX();
    }

    /**
     * Return relevant bounds in the current container that are the same in-scene bounds as a node in a different container
     * Utility method to help concise code.
     *
     * @return the bounds in the current container that replicate in-scene the bounds of the prep node
     */
    private Double ltsY(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinY() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinY();
    }

    /**
     * For each level of depth in the map provided, create a container node. For each vertex within the nested map,
     * create a display node and add it to the container. Return a map of depths to containers
     *
     * @return a map of depth level to container nodes
     */
    private PreparationDisplayMaps createNodeDisplay(Map<Integer, List<VertexData>> nodeDepthMap) {
        PreparationDisplayMaps pdm = new PreparationDisplayMaps();

        for (int depth = 0; depth < nodeDepthMap.keySet().size(); depth++) {
            VBox container = getStyledContainer();
            for (VertexData data : nodeDepthMap.get(depth)) {

                //Create node for preparation area of display
                HolderRectangle prepNode = new HolderRectangle();
                prepNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);
                container.getChildren().add(prepNode);

                //Create node for display overlay
                HolderRectangle displayNode = new HolderRectangle();
                displayNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);
                displayNode.setLayoutX(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinX());
                displayNode.setLayoutY(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinY());

                pdm.idNodeMap.put(data.getId(), new DataAndNodes(data, prepNode, displayNode));
                pdm.prepToDisplay.put(prepNode, displayNode);
            }
            pdm.depthToPrep.put(depth, container);
        }
        return pdm;
    }

    /**
     * Clear the current display and return it to an unloaded state
     */
    private void clearNodeReals() {
        preparationContainer.getChildren().clear();
        displayOverlay.getChildren().clear();
    }

    /**
     * Clear the current node mapping
     */
    private void clearNodeInformation() {
        vertexInfoInMemory.clear();
        nodeDepthMap.clear();
    }

    /**
     * Create node positions from the node map currently in memory
     *
     * @return a positional map of containers and nodes
     */
    private Map<Integer, List<VertexData>> createNodeMapping(List<VertexData> nodeInfo) {
        List<VertexData> liveNodes = new ArrayList<>(nodeInfo);
        Map<Integer, List<VertexData>> nodeMap = new HashMap<>();

        int depth = 0;
        nodeMap.put(depth, findDownsteamLeaves(nodeInfo));
        liveNodes.removeAll(nodeMap.get(depth++));

        while (liveNodes.size() > 0) {
            List<VertexData> nodesAtCurrentDepth = new ArrayList<>();
            List<VertexData> nodesAtDepthAbove = new ArrayList<>(nodeMap.get(depth - 1));
            for (VertexData ed : liveNodes) {
                if (linksToDownstreamNode(ed, nodesAtDepthAbove)) {
                    nodesAtCurrentDepth.add(ed);
                }
            }
            liveNodes.removeAll(nodesAtCurrentDepth);
            nodeMap.put(depth++, nodesAtCurrentDepth);
        }
        return nodeMap;
    }

    /**
     * Return whether the edge data (ed) has a direct upstream link to the node list provided.
     *
     * @param ed                the edge data
     * @param nodesAtDepthAbove the nodes against which to test upstream linkage
     * @return whether the edge data is linked directly to the node list. Upstream only.
     */
    private boolean linksToDownstreamNode(VertexData ed, List<VertexData> nodesAtDepthAbove) {
        for (VertexData downstream : nodesAtDepthAbove) {
            for (String dsID : downstream.getUpstream()) {
                if (dsID.equals(ed.getId())) return true;
            }
        }
        return false;

    }

    /**
     * Collect the nodes with no downstream linkages. Return these as a list.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no downstream linkages
     */
    private List<VertexData> findDownsteamLeaves(List<VertexData> vertexDataList) {
        List<VertexData> leaves = new ArrayList<>();
        for (VertexData ed : vertexDataList) {
            if (ed.getDownstream().size() == 0) leaves.add(ed);
        }
        return leaves;
    }

    /**
     * Collect the nodes with no upstream linkages. Return these as a list.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no upstream linkages
     */
    private List<VertexData> findUpstreamLeaves(List<VertexData> vertexDataList) {
        List<VertexData> leaves = new ArrayList<>();
        for (VertexData ed : vertexDataList) {
            if (ed.getUpstream().size() == 0) leaves.add(ed);
        }
        return leaves;
    }

    /**
     * Enum representing the possible states of the program.
     */
    private enum ProgramState {
        Loaded, Closed
    }

    /**
     * Return a Parent node with styles pre-added. Useful for prototyping but should eventually be accomplished
     * but stylesheets
     *
     * @return a styled VBox
     */
    private VBox getStyledContainer() {
        VBox container = new VBox();
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color: blue");
        return container;
    }

    /**
     * Class representing the maps required to create a preparation section of the scene graph and mimic it in
     * a displayed portion of the scene graph
     */
    private class PreparationDisplayMaps {
        Map<Node, Node> prepToDisplay = new HashMap<>();
        Map<Integer, Node> depthToPrep = new HashMap<>();
        Map<String, DataAndNodes> idNodeMap = new HashMap<>();
    }

    /**
     * Class representing the information necessary to link the vertex data with its preparation node and its
     * living node in the scene graph.
     */
    private class DataAndNodes {
        VertexData vertexData;
        Node preparationNode;
        Node displayNode;

        DataAndNodes(VertexData vd, Node p, Node d) {
            this.vertexData = vd;
            this.preparationNode = p;
            this.displayNode = d;
        }
    }
}
