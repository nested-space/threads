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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

public class ProcessDisplay {

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
    /**
     * A map of node depth to node data
     */
    private Map<Integer, List<VertexData>> nodeDepthMap = new HashMap<>();
    /**
     * A map of node containers to info nodes
     */
    private Map<Integer, Node> depthToPrepContainerMap = new HashMap<>();
    /**
     * A map of IDs to preparation and display nodes.
     */
    private Map<String, DataAndNodes> idToNodeMap;
    /**
     * Map which links preparation nodes with their display counterparts
     */
    private Map<Node, Node> preparationDisplayMap = new HashMap<>();
    /**
     * A list of edges currently displayed
     */
    private List<Node> edges = new ArrayList<>();
    /**
     * The vertex data currently associated with the display
     */
    private List<VertexData> vertices = new ArrayList<>();

    /**
     * Create a new Process Display
     * @param display the pane on which the processdisplay should be rendered
     */
    public ProcessDisplay(ScrollPane display) {
        processDisplay = display;
        display.setPannable(true);
        processDisplay.setFitToHeight(true);
        processDisplay.setFitToWidth(true);
        processDisplay.heightProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));
        processDisplay.widthProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));

        StackPane prepDisplayStack = new StackPane();
        processDisplay.setContent(prepDisplayStack);
        prepDisplayStack.getChildren().addAll(displayOverlay, preparationContainer);

        preparationContainer.setOpacity(0);
        preparationContainer.setAlignment(Pos.CENTER);
        preparationContainer.setSpacing(125);
        preparationContainer.setMouseTransparent(true);
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
                DataAndNodes nodeData = createNodes(data, depth);
                container.getChildren().add(nodeData.preparationNode);
                pdm.idNodeMap.put(data.getId(), nodeData);
                pdm.prepToDisplay.put(nodeData.preparationNode, nodeData.displayNode);
            }
            pdm.depthToPrepcontainer.put(depth, container);
        }
        return pdm;
    }

    /**
     * Create a DataAndNodes construct linking vertex data with nodes in the scene graph
     * for a given VD and depth
     * @param data the vertex data
     * @param depth the graph depth of the vertex
     * @return a construct linking vertex graph depth, preparationNode, displayNode and underlying VertexData
     */
    private DataAndNodes createNodes(VertexData data, Integer depth) {
        //Create node for preparation area of display
        HolderRectangle prepNode = new HolderRectangle();
        prepNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);

        //Create node for display overlay
        HolderRectangle displayNode = new HolderRectangle();
        displayNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);
        displayNode.setLayoutX(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinX());
        displayNode.setLayoutY(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinY());
        displayNode.setOnContextMenuRequested(event -> {
            vertexContextMenu(data.getId()).show(displayNode, event.getScreenX(), event.getScreenY());
        });

        return new DataAndNodes(data, prepNode, displayNode, depth);
    }

    /**
     * Create a context menu associated with a vertex
     * @param id the id of the vertex
     * @return the context menu
     */
    private ContextMenu vertexContextMenu(String id) {
        ContextMenu cm = new ContextMenu();

        MenuItem addDownstream = new MenuItem("Add Downstream Node");
        addDownstream.setOnAction(event -> {
            int depth = idToNodeMap.getOrDefault(id, new DataAndNodes(null, null, null, -1500)).depth;
            if (depth != -1500) {
                VertexData vdNew = new VertexData("New Node");
                vdNew.addUpstream(id);
                for (VertexData vdSource : vertices) {
                    if (vdSource.getId().equals(id)) vdSource.addDownstream(vdNew.getId());
                }
                addNode(vdNew, depth - 1, id, Side.RIGHT);

            }
        });

        MenuItem addUpstream = new MenuItem("Add Upstream Node");
        addUpstream.setOnAction(event -> {
            int depth = idToNodeMap.getOrDefault(id, new DataAndNodes(null, null, null, -1500)).depth;
            if (depth != -1500) {
                VertexData vdNew = new VertexData("New Node");
                vdNew.addDownstream(id);
                for (VertexData vdSource : vertices) {
                    if (vdSource.getId().equals(id)) vdSource.addUpstream(vdNew.getId());
                }
                addNode(vdNew, depth + 1, id, Side.LEFT);

            }
        });

        cm.getItems().addAll(addDownstream, addUpstream);
        return cm;
    }

    /**
     * Create a new NodeAndData construct from the given vertex data. Link the new node to the source
     * node. Create an edge to represent the link. Add nodes to their respective points in teh scene graph
     * and refresh the display
     * @param newNodeVertexData the data associated with the new vertex
     * @param newNodeDepth the graph depth of the new vertex
     * @param sourceVertexId the node to which the new node should be linked. Logic is you cannot
     * @param newNodeSide the relative position (left or right) of the new vertex with respect to the source vertex
     */
    private void addNode(VertexData newNodeVertexData, int newNodeDepth, String sourceVertexId, Side newNodeSide) {
        DataAndNodes newNodeData = createNodes(newNodeVertexData, newNodeDepth);
        Pane container = (Pane) depthToPrepContainerMap.get(newNodeDepth);

        if (container == null) {
            container = getStyledContainer();
            depthToPrepContainerMap.put(newNodeDepth, container);
            resetPreparationContainerOrders();
        }

        container.getChildren().add(newNodeData.preparationNode);

        newNodeData.displayNode.setOpacity(0);

        vertices.add(newNodeVertexData);
        preparationDisplayMap.put(newNodeData.preparationNode, newNodeData.displayNode);
        idToNodeMap.put(newNodeVertexData.getId(), newNodeData);

        Node edge;
        if (newNodeSide == Side.RIGHT) {
            edge = createEdge((HolderRectangle) idToNodeMap.get(sourceVertexId).displayNode,
                    (HolderRectangle) idToNodeMap.get(newNodeVertexData.getId()).displayNode);
        } else if (newNodeSide == Side.LEFT) {
            edge = createEdge((HolderRectangle) idToNodeMap.get(newNodeVertexData.getId()).displayNode,
                    (HolderRectangle) idToNodeMap.get(sourceVertexId).displayNode);
        } else {
            throw new IllegalStateException("Only upstream and downstream nodes are allowed. Unprocessable Side on node creation");
        }

        edge.setOpacity(0);
        displayOverlay.getChildren().add(newNodeData.displayNode);
        displayOverlay.getChildren().add(0, edge);

        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                newNodeData.displayNode.setLayoutX(ltsX(newNodeData.preparationNode));
                newNodeData.displayNode.setLayoutY(ltsY(newNodeData.preparationNode));
                reconcilePrepAndDisplay();
            });
            t.playFromStart();
        });

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

        for (Node displayNode : displayOverlay.getChildren()) {
            if (displayNode.getOpacity() == 0) {
                all.getKeyFrames().addAll(
                        new KeyFrame(Duration.millis(0), new KeyValue(displayNode.opacityProperty(), 0)),
                        new KeyFrame(Duration.millis(length), new KeyValue(displayNode.opacityProperty(), 1)));
                if (displayNode instanceof HolderRectangle) {
                    HolderRectangle d = (HolderRectangle) displayNode;
                    all.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(0), new KeyValue(d.translateYProperty(), -12)),
                            new KeyFrame(Duration.millis(length), new KeyValue(d.translateYProperty(), 0)));
                }
            }
        }

        for (Node prepNode : preparationDisplayMap.keySet()) {
            Node displayNode = preparationDisplayMap.getOrDefault(prepNode, new HolderRectangle());


            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutXProperty(), ltsX(prepNode))),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutYProperty(), ltsY(prepNode))));
        }
        all.playFromStart();
    }

    /**
     * Utility method to clear the preparation container of all children and re-load from the depthToPrepContainerMap
     */
    private void resetPreparationContainerOrders() {
        preparationContainer.getChildren().clear();
        for (Object key : new LinkedList<>(depthToPrepContainerMap.keySet()).stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList())) {
            if (key instanceof Integer) {
                preparationContainer.getChildren().add(depthToPrepContainerMap.get(key));
            }

        }
    }

    /**
     * Calculate the positions of vertices and re-create edges for the display based on data in the "vertices" list
     */
    private void recastDisplayFromCachedData() {
        clearNodes();
        displayOverlay.getChildren().addAll(preparationDisplayMap.values());
        for (Node n : displayOverlay.getChildren()) n.setOpacity(0);

        //Load the preparation container with nodes
        resetPreparationContainerOrders();

        //add edges
        List<VertexData> unvisitedNodes = findUpstreamLeaves(vertices);
        while (unvisitedNodes.size() > 0) {
            VertexData vd = unvisitedNodes.remove(0);
            for (String id : vd.getDownstream()) unvisitedNodes.add(idToNodeMap.get(id).vertexData);

            HolderRectangle dStart = (HolderRectangle) idToNodeMap.get(vd.getId()).displayNode;
            for (String id : vd.getDownstream()) {
                HolderRectangle dEnd = (HolderRectangle) idToNodeMap.get(id).displayNode;
                Node edge = createEdge(dStart, dEnd);
                edge.setOpacity(0);
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

    /**
     * Clear all objects from the display pane and the preparation pane.
     */
    public void clearAll() {
        clearNodes();
        clearInfo();
    }

    /**
     * Clear the current display and return it to an unloaded state
     */
    private void clearNodes() {
        preparationContainer.getChildren().clear();
        displayOverlay.getChildren().clear();
    }

    /**
     * Clear all information in the preparation display maps
     */
    private void clearInfo() {
        vertices.clear();
        edges.clear();
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

    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void create(List<VertexData> vertices) {
        this.vertices = vertices;
        nodeDepthMap = createNodeMapping(vertices);
        PreparationDisplayMaps pdm = createNodeDisplay(nodeDepthMap);
        idToNodeMap = pdm.idNodeMap;
        depthToPrepContainerMap = pdm.depthToPrepcontainer;
        preparationDisplayMap = pdm.prepToDisplay;
    }

    /**
     * Create a node (line) which links two vertices in the display. Return the node.
     *
     * @param startBox the vertex at the start of the line
     * @param endBox   the vertex at the end of the line
     * @return a node that acts as an edge in the display
     */
    private Node createEdge(HolderRectangle startBox, HolderRectangle endBox) {
        CubicCurve newEdge = new CubicCurve();
        newEdge.startXProperty().bind(startBox.layoutXProperty().add(startBox.getHeaderRect().widthProperty()));
        newEdge.startYProperty().bind(startBox.layoutYProperty().add(startBox.getHeaderRect().heightProperty().divide(2)));
        newEdge.endXProperty().bind(endBox.layoutXProperty());
        newEdge.endYProperty().bind(endBox.layoutYProperty().add(endBox.getHeaderRect().heightProperty().divide(2)));
        newEdge.controlX1Property().bind(startBox.layoutXProperty().add(startBox.getHeaderRect().widthProperty()).add(50));
        newEdge.controlY1Property().bind(startBox.layoutYProperty().add(startBox.getHeaderRect().heightProperty().divide(2)));
        newEdge.controlX2Property().bind(endBox.layoutXProperty().subtract(50));
        newEdge.controlY2Property().bind(endBox.layoutYProperty().add(endBox.getHeaderRect().heightProperty().divide(2)));
        newEdge.setStroke(Color.BLUE);
        newEdge.setStrokeWidth(0.5);
        newEdge.setStrokeLineCap(StrokeLineCap.ROUND);
        newEdge.setFill(Color.TRANSPARENT);
        return newEdge;
    }

    /**
     * Method to allow external programs to show the context of the cached data on the display
     */
    public void show() {
        recastDisplayFromCachedData();
    }

    /**
     * Class representing the maps required to create a preparation section of the scene graph and mimic it in
     * a displayed portion of the scene graph
     */
    private class PreparationDisplayMaps {
        Map<Node, Node> prepToDisplay = new HashMap<>();
        Map<Integer, Node> depthToPrepcontainer = new HashMap<>();
        Map<String, DataAndNodes> idNodeMap = new HashMap<>();
    }

    /**
     * Class representing the information necessary to link the vertex data with its preparation node and its
     * living node in the scene graph.
     */
    private class DataAndNodes {
        int depth;
        VertexData vertexData;
        Node preparationNode;
        Node displayNode;

        DataAndNodes(VertexData vd, Node p, Node d, int depth) {
            this.depth = depth;
            this.vertexData = vd;
            this.preparationNode = p;
            this.displayNode = d;
        }
    }
}
