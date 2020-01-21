/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.ui.views;

import com.edenrump.config.Defaults;
import com.edenrump.models.VertexData;
import com.edenrump.ui.components.HolderRectangle;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessDisplay {

    private StackPane prepDisplayStack = new StackPane();

    /**
     * Scrollpane that encapsulates all process preparation and display containers.
     */
    public ScrollPane processDisplay;

    /**
     * Container in the background of the display that deals with position of nodes
     * <p>
     * This is used in prototyping because it seems easier to have JavaFX deal with the positions of the nodes
     * and then (if positions change) to create animations on the nodes in the anchorpane displayOverlay which
     * move nodes from their original position to their new one. This might be less efficient, but it means I don't
     * have to code my own Region types to hold the nodes.
     */
    public HBox preparationContainer = new HBox();

    /**
     * Anchorpane at the front of the display in which real nodes are placed
     */
    public AnchorPane displayOverlay = new AnchorPane();

    public ProcessDisplay(ScrollPane display){
        processDisplay = display;
        processDisplay.setFitToHeight(true);
        processDisplay.setFitToWidth(true);
        processDisplay.setContent(prepDisplayStack);

        prepDisplayStack.getChildren().addAll(displayOverlay, preparationContainer);

        preparationContainer.setOpacity(0);
        preparationContainer.setAlignment(Pos.CENTER);
        preparationContainer.setSpacing(125);
    }

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
     * The vertex data currently associated with the display
     */
    private List<VertexData> vertices = new ArrayList<>();

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
     * Create animations to move display nodes to the same scene-locations as the preparation nodes
     * TODO: determine whether node is still present, remove if necessary
     * TODO: determine new nodes. Add if necessary
     */
    public void reconcilePrepAndDisplay() {
        double length = 350;
        Timeline all = new Timeline(30);

        for (Node prepNode : preparationDisplayMap.keySet()) {
            Node displayNode = preparationDisplayMap.getOrDefault(prepNode, new HolderRectangle());

            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutXProperty(), ltsX(prepNode))),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutYProperty(), ltsY(prepNode))));


        }
        all.setOnFinished(event -> {
        });
        all.playFromStart();
//        System.out.println();
    }

    private void recastDisplayFromCachedData() {
        displayOverlay.getChildren().addAll(preparationDisplayMap.values());

        //Load the preparation container with nodes
        for (int i = displayDepthMap.keySet().size() - 1; i > -1; i--) {
            preparationContainer.getChildren().addAll(displayDepthMap.get(i));
        }

        //add edges
        List<VertexData> unvisitedNodes = findUpstreamLeaves(vertices);
        while (unvisitedNodes.size() > 0) {
            VertexData vd = unvisitedNodes.remove(0);
            for (String id : vd.getDownstream()) unvisitedNodes.add(idToNodeMap.get(id).vertexData);

            HolderRectangle dStart = (HolderRectangle) idToNodeMap.get(vd.getId()).displayNode;
            for (String id : vd.getDownstream()) {
                HolderRectangle dEnd = (HolderRectangle) idToNodeMap.get(id).displayNode;
                Line edge = new Line();
                edge.setStrokeWidth(1);
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

    public void clearDisplay(){
        clearDisplayObjects();
        clearPreparationInfoObjects();
    }

    /**
     * Clear the current display and return it to an unloaded state
     */
    private void clearDisplayObjects() {
        preparationContainer.getChildren().clear();
        displayOverlay.getChildren().clear();
        vertices.clear();
        edges.clear();
    }

    /**
     * Clear all information in the preparation display maps
     */
    private void clearPreparationInfoObjects(){
        displayOverlay.getChildren().clear();
        preparationDisplayMap.clear();
        nodeDepthMap.clear();
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
        container.setSpacing(50);
        return container;
    }

    public void create(List<VertexData> vertices) {
        this.vertices = vertices;
        nodeDepthMap = createNodeMapping(vertices);
        PreparationDisplayMaps pdm = createNodeDisplay(nodeDepthMap);
        idToNodeMap = pdm.idNodeMap;
        displayDepthMap = pdm.depthToPrep;
        preparationDisplayMap = pdm.prepToDisplay;
    }

    public void show() {
        recastDisplayFromCachedData();
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
