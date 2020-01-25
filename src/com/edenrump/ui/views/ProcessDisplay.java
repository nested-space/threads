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
import com.edenrump.ui.components.TitledContentPane;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
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
     * A map of node containers to info nodes
     */
    private Map<Integer, Node> depthToPrepContainerMap = new HashMap<>();
    /**
     * Map which links preparation nodes with their display counterparts
     */
    private Map<Node, Node> preparationDisplayMap = new HashMap<>();
    /**
     * A map that contains all the edges that are connected to each node.
     */
    private Map<Node, List<Node>> vertexToEdgesMap = new HashMap<>();

    /**
     * A list of nodes that are being prepared for removal from the display pane on the next pass
     */
    private List<Node> toBeRemovedOnNextPass = new ArrayList<>();

    /**
     * A map of IDs to preparation and display nodes.
     */
    private Map<String, DataAndNodes> idToNodeMap = new HashMap<>();

    /**
     * A list of the currently-selected vertices in the process display window
     */
    private ObservableList<VertexData> selectedVertices = FXCollections.observableArrayList();
    /**
     * The last-selected node in the display. Null if no node selected.
     */
    private VertexData lastSelected;


    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public ProcessDisplay(ScrollPane display) {
        processDisplay = display;
        display.setPannable(true);
        processDisplay.setFitToHeight(true);
        processDisplay.setFitToWidth(true);
        Platform.runLater(() -> {
            PauseTransition timeForWindowToLoad = new PauseTransition(Duration.seconds(2));
            timeForWindowToLoad.setOnFinished(event -> {
                processDisplay.heightProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));
                processDisplay.widthProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));
            });
            timeForWindowToLoad.play();
        });

        StackPane prepDisplayStack = new StackPane();
        processDisplay.setContent(prepDisplayStack);
        prepDisplayStack.getChildren().addAll(displayOverlay, preparationContainer);

        displayOverlay.setOnMouseClicked(event -> {
            resetHighlightingOnAllNodes();
            selectedVertices.clear();
            lastSelected = null;
        });

        preparationContainer.setOpacity(0);
        preparationContainer.setAlignment(Pos.CENTER);
        preparationContainer.setSpacing(125);
        preparationContainer.setMouseTransparent(true);
    }

    /**
     * Return the list of the currently selected vertices as an observable list
     *
     * @return an observable list of selected vertices //TODO: wrap as read-only
     */
    public ObservableList<VertexData> getSelectedVertices() {
        return selectedVertices;
    }

    /**
     * For each level of depth in the map provided, create a container node. For each vertex within the nested map,
     * create a display node and add it to the container. Return a map of depths to containers
     *
     * @return a map of depth level to container nodes
     */
    private PreparationDisplayMaps createNodeDisplay(List<VertexData> vertices) {
        PreparationDisplayMaps pdm = new PreparationDisplayMaps();
        vertices.stream()
                .map(VertexData::getDepth)
                .distinct()
                .forEach(depth -> {
                    List<Node> prepNodes = new ArrayList<>();
                    vertices.stream()
                            .filter(v -> v.getDepth() == depth)
                            .forEach(vertexData -> {
                                DataAndNodes nodeData = createNodes(vertexData);
                                prepNodes.add(nodeData.preparationNode);
                                pdm.idNodeMap.put(vertexData.getId(), nodeData);
                                pdm.prepToDisplay.put(nodeData.preparationNode, nodeData.displayNode);
                            });
                    pdm.depthToPrepcontainer.put(depth, createPrepColumn(prepNodes, "Depth: " + depth));
                });
        return pdm;
    }

    /**
     * Create a DataAndNodes construct linking vertex data with nodes in the scene graph
     * for a given VD and depth
     *
     * @param data the vertex data
     * @return a construct linking vertex graph depth, preparationNode, displayNode and underlying VertexData
     */
    private DataAndNodes createNodes(VertexData data) {
        //Create node for preparation area of display
        TitledContentPane prepNode = new TitledContentPane();
        prepNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);

        //Create node for display overlay
        TitledContentPane displayNode = new TitledContentPane();
        displayNode.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);
        displayNode.setLayoutX(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinX());
        displayNode.setLayoutY(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinY());
        displayNode.setId(data.getId());
        displayNode.setOnContextMenuRequested(event -> {
            showContextMenu(displayNode, standardVertexContextMenu(data.getId()), event);
            event.consume();
        });
        displayNode.setOnMouseClicked(event -> {
            handleSelection(data.getId(), event);
        });

        return new DataAndNodes(data, prepNode, displayNode);
    }

    /**
     * The current context menu. Prevents multiple context menus being shown simultaneously.
     */
    private ContextMenu currentlyShown = new ContextMenu();

    /**
     * Select the vertex identified. Apply UX logic to determine whether to keep the current selection, remove it,
     * or expand it.
     *
     * @param vertexId the vertex selected
     * @param event    the mouse-event that triggered the selection
     */
    private void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && selectedVertices.stream().map(VertexData::getId).collect(Collectors.toList()).contains(vertexId)) {
            event.consume();
            return;
        }

        VertexData vertexClicked = idToNodeMap.get(vertexId).vertexData;
        if (event.isShiftDown() && lastSelected != null) {
            selectedVertices.setAll(findShortestPath(lastSelected, vertexClicked,
                    idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList())));
        } else if (event.isControlDown()) {
            if (!selectedVertices.contains(vertexClicked)) {
                selectedVertices.add(vertexClicked);
                lastSelected = vertexClicked;
            } else {
                selectedVertices.remove(vertexClicked);
            }
        } else {
            selectedVertices.setAll(vertexClicked);
            lastSelected = vertexClicked;
        }

        highlightSelectedNodes();

        event.consume();
    }

    /**
     * Close the currently shown context method and show the context menu provided
     *
     * @param pane the pane generating the context menu
     * @param c    the context menu to be shown
     * @param e    the event generating the context menu
     */
    private void showContextMenu(Pane pane, ContextMenu c, ContextMenuEvent e) {
        currentlyShown.hide();
        currentlyShown = c;
        currentlyShown.show(pane, e.getScreenX(), e.getScreenY());

    }

    /**
     * Determine the shortest path between two vertices in the given vertex data map. Implements Dijkstra's shortest path
     * No benefit to implementing A* because network topology will likely be low and computational time taken to
     * optimise priority queue will likely outweigh gains.
     *
     * @param startVertex      the statring vertex for the search
     * @param desinationVertex the destination vertex
     * @param allVertices      the vertex data map
     * @return an ordered list containing
     */
    private List<VertexData> findShortestPath(VertexData startVertex, VertexData desinationVertex, List<VertexData> allVertices) {
        if (desinationVertex == startVertex) return new ArrayList<>(Collections.singletonList(startVertex));

        //if startVertex isn't in allVertices, return empty list because this isn't going to work...
        if (!allVertices.contains(startVertex)) return new ArrayList<>();

        //populate initial priority queue
        List<PriorityItem> priorityQueue = new LinkedList<>();
        Map<String, PriorityItem> idPriorityItemMap = new HashMap<>();
        for (VertexData v : allVertices) {
            PriorityItem item;
            if (v.equals(startVertex)) {
                item = new PriorityItem(0, v);
            } else {
                item = new PriorityItem(Integer.MAX_VALUE, v);
            }
            priorityQueue.add(item);
            idPriorityItemMap.put(v.getId(), item);
        }
        Collections.sort(priorityQueue);
        List<PriorityItem> visited = new ArrayList<>();

        int cycles = 0;
        boolean bestPathFound = false;
        while (cycles < 1500 && !bestPathFound) {
            PriorityItem currentItem = priorityQueue.get(0);
            visited.add(currentItem);
            priorityQueue.remove(currentItem);

            for (String id : currentItem.vertex.getConnectedVertices()) {
                PriorityItem adjacentItem = idPriorityItemMap.get(id);
                if (visited.contains(adjacentItem)) continue; //don't go back to nodes already visited
                if ((currentItem.distance + 1) < adjacentItem.distance) {
                    adjacentItem.distance = currentItem.distance + 1; //all edge lengths are 1 in this implementation
                    adjacentItem.previousItem = currentItem;
                }
            }

            cycles++;
            Collections.sort(priorityQueue);
            if (priorityQueue.get(0).vertex == desinationVertex) {
                bestPathFound = true;
            } else {
                currentItem = priorityQueue.get(0);
            }
        }

        if (!bestPathFound) return new ArrayList<>(); //if maxed out cycles, return empty list rather than null

        List<VertexData> path = new LinkedList<>();
        PriorityItem retraceCaret = priorityQueue.get(0);
        while (retraceCaret.vertex != startVertex) {
            path.add(retraceCaret.vertex);
            retraceCaret = retraceCaret.previousItem;
        }
        path.add(retraceCaret.vertex);
        Collections.reverse(path);
        return path;
    }

    /**
     * Utility method to unhighlight all nodes in the idToNodeMap
     */
    private void resetHighlightingOnAllNodes() {
        for (String id : idToNodeMap.keySet()) {
            TitledContentPane displayNode = (TitledContentPane) idToNodeMap.get(id).displayNode;
            displayNode.resetHighlighting();
        }
    }

    /**
     * Separate vertex nodes by selection. Highlight selected nodes. Lowlight unselected nodes.
     */
    private void highlightSelectedNodes() {
        selectedVertices.stream()
                .map(v -> (TitledContentPane) idToNodeMap.get(v.getId()).displayNode)
                .forEach(pane -> {
                    pane.highlight();
                    if (selectedVertices.size() == 1) {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, singleSelectedVertexContextMenu(pane.getId()), e));
                    } else {
                        pane.setOnContextMenuRequested(e -> {
                            showContextMenu(pane, standardVertexContextMenu(pane.getId()), e);
                        });
                    }
                });

        idToNodeMap.values().stream().map(data -> data.vertexData) //all vertices
                .filter(v -> !selectedVertices.contains(v))
                .map(v -> (TitledContentPane) idToNodeMap.get(v.getId()).displayNode)
                .forEach(pane -> {
                    pane.lowlight();
                    pane.setOnContextMenuRequested(null);
                });
    }

    /**
     * Simplest context menu associated with a vertex
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    private ContextMenu standardVertexContextMenu(String id) {
        ContextMenu cm = new ContextMenu();

        MenuItem addDownstream = new MenuItem("Add Downstream Node");
        addDownstream.setOnAction(event -> {
            int depth = idToNodeMap.get(id).vertexData.getDepth();
            VertexData vdNew = new VertexData("New Node", depth - 1, 0);
            vdNew.addConnection(id);
            for (VertexData vdSource : idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList())) {
                if (vdSource.getId().equals(id)) vdSource.addConnection(vdNew.getId());
            }
            addNode(vdNew, depth - 1, id, Side.RIGHT);
        });

        MenuItem addUpstream = new MenuItem("Add Upstream Node");
        addUpstream.setOnAction(event -> {
            int depth = idToNodeMap.get(id).vertexData.getDepth();
            VertexData vdNew = new VertexData("New Node", depth + 1, 0);
            vdNew.addConnection(id);
            for (VertexData vdSource : idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList())) {
                if (vdSource.getId().equals(id)) vdSource.addConnection(vdNew.getId());
            }
            addNode(vdNew, depth + 1, id, Side.LEFT);
        });

        cm.getItems().addAll(addDownstream, addUpstream);
        return cm;
    }

    /**
     * Create a context menu associated with a vertex where only a single vertex has been selected
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    private ContextMenu singleSelectedVertexContextMenu(String id) {
        ContextMenu cm = standardVertexContextMenu(id);
        MenuItem delete = new MenuItem("Delete (currently not live)");
        MenuItem edit = new MenuItem("Edit (currently not live)");
        cm.getItems().addAll(delete, edit);
        return cm;
    }

    /**
     * Create a new NodeAndData construct from the given vertex data. Link the new node to the source
     * node. Create an edge to represent the link. Add nodes to their respective points in teh scene graph
     * and refresh the display
     *
     * @param newNodeVertexData the data associated with the new vertex
     * @param newNodeDepth      the graph depth of the new vertex
     * @param sourceVertexId    the node to which the new node should be linked. Logic is you cannot
     * @param newNodeSide       the relative position (left or right) of the new vertex with respect to the source vertex
     */
    private void addNode(VertexData newNodeVertexData, int newNodeDepth, String sourceVertexId, Side newNodeSide) {
        DataAndNodes newNodeData = createNodes(newNodeVertexData);
        newNodeData.displayNode.setOpacity(0);

        preparationDisplayMap.put(newNodeData.preparationNode, newNodeData.displayNode);
        idToNodeMap.put(newNodeVertexData.getId(), newNodeData);

        resetPreparationDisplay();

        Node edge;
        if (newNodeSide == Side.RIGHT) {
            edge = createEdge((TitledContentPane) idToNodeMap.get(sourceVertexId).displayNode,
                    (TitledContentPane) idToNodeMap.get(newNodeVertexData.getId()).displayNode);
        } else if (newNodeSide == Side.LEFT) {
            edge = createEdge((TitledContentPane) idToNodeMap.get(newNodeVertexData.getId()).displayNode,
                    (TitledContentPane) idToNodeMap.get(sourceVertexId).displayNode);
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
     * Remove a vertex from the graph. Schedule
     *
     * @param toRemove
     */
    public void removeVertex(VertexData toRemove) {
        toBeRemovedOnNextPass.add(idToNodeMap.get(toRemove.getId()).displayNode);
        toBeRemovedOnNextPass.addAll(vertexToEdgesMap.get(idToNodeMap.get(toRemove.getId()).displayNode));

        preparationDisplayMap.remove(idToNodeMap.get(toRemove.getId()).preparationNode);
        idToNodeMap.remove(toRemove.getId());

        idToNodeMap.values().stream()
                .map(data -> data.vertexData)
                .forEach(vertex -> vertex.getConnectedVertices().remove(toRemove.getId()));

        selectedVertices.clear();
        resetHighlightingOnAllNodes();

        resetPreparationDisplay(); //also removes from depthToPrepContainer and preparationContainer

        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            pause.setOnFinished(event -> reconcilePrepAndDisplay());
            pause.play();
        });
    }

    /**
     * Collect the nodes with no upstream linkages. Return these as a list.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no upstream linkages
     */
    private List<VertexData> getUpstreamLeaves(List<VertexData> vertexDataList) {
        return vertexDataList
                .stream()
                .filter(v -> v.getDepth() == Collections.max(vertexDataList
                        .stream()
                        .map(VertexData::getDepth)
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    /**
     * Determine the minimum depth of the graph. Return a list of vertices with the determined depth.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes at the minimum depth of the map
     */
    private List<VertexData> getDownstreamLeaves(List<VertexData> vertexDataList) {
        return vertexDataList
                .stream()
                .filter(v -> v.getDepth() == Collections.min(vertexDataList
                        .stream()
                        .map(VertexData::getDepth)
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
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

        for (Node node : toBeRemovedOnNextPass) {
            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(node.opacityProperty(), node.getOpacity())),
                    new KeyFrame(Duration.millis(length), new KeyValue(node.opacityProperty(), 0))
            );
        }

        for (Node displayNode : displayOverlay.getChildren()) {
            if (displayNode.getOpacity() == 0) {
                all.getKeyFrames().addAll(
                        new KeyFrame(Duration.millis(0), new KeyValue(displayNode.opacityProperty(), 0)),
                        new KeyFrame(Duration.millis(length), new KeyValue(displayNode.opacityProperty(), 1)));
                if (displayNode instanceof TitledContentPane) {
                    TitledContentPane d = (TitledContentPane) displayNode;
                    all.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(0), new KeyValue(d.translateYProperty(), -12)),
                            new KeyFrame(Duration.millis(length), new KeyValue(d.translateYProperty(), 0)));
                }
            }
        }

        for (Node prepNode : preparationDisplayMap.keySet()) {
            Node displayNode = preparationDisplayMap.getOrDefault(prepNode, new TitledContentPane());


            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutXProperty(), ltsX(prepNode))),
                    new KeyFrame(Duration.millis(length), new KeyValue(displayNode.layoutYProperty(), ltsY(prepNode))));
        }
        all.setOnFinished(event -> {
            displayOverlay.getChildren().removeAll(toBeRemovedOnNextPass);
            toBeRemovedOnNextPass.clear();
        });
        all.playFromStart();
    }

    /**
     * Utility method to clear the preparation container of all children and re-load from the depthToPrepContainerMap
     */
    private void resetPreparationDisplay() {
        preparationContainer.getChildren().clear();
        depthToPrepContainerMap.clear();

        idToNodeMap.values().stream()
                .map(data -> data.vertexData.getDepth())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList())
                .forEach(depth -> {
                    List<Node> prepNodes = new ArrayList<>();
                    idToNodeMap.values().stream()
                            .filter(v -> v.vertexData.getDepth() == depth)
                            .forEach(dn -> prepNodes.add(dn.preparationNode));

                    depthToPrepContainerMap
                            .put(depth, createPrepColumn(prepNodes, "Depth: " + depth));
                    preparationContainer.getChildren().add(depthToPrepContainerMap.get(depth));
                });
    }

    /**
     * Utility method to create a consistent column in the preparation display TODO: link prep title with real display title
     *
     * @param prepNodes the nodes to be added to the column
     * @param title     the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    private VBox createPrepColumn(List<Node> prepNodes, String title) {
        VBox container = new VBox();
        container.setAlignment(Pos.TOP_CENTER);
        container.setSpacing(25);
        VBox head = new VBox(new Label(title));
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);
        prepNodes.forEach(node -> body.getChildren().add(node));
        container.getChildren().addAll(head, body);
        return container;
    }

    /**
     * Calculate the positions of vertices and re-create edges for the display based on data in the vertices list
     */
    private void recastDisplayFromCachedData() {
        clearNodes();
        displayOverlay.getChildren().addAll(preparationDisplayMap.values());
        for (Node n : displayOverlay.getChildren()) n.setOpacity(0);

        //Load the preparation container with nodes
        resetPreparationDisplay();

        //add edges
        List<VertexData> unvisitedNodes = getUpstreamLeaves(idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList()));
        List<String> visitedNodes = new ArrayList<>();
        while (unvisitedNodes.size() > 0) {
            VertexData currentVertex = unvisitedNodes.remove(0);
            visitedNodes.add(currentVertex.getId());

            currentVertex.getConnectedVertices().stream()
                    .filter(id -> !visitedNodes.contains(id))
                    .forEach(id -> unvisitedNodes.add(idToNodeMap.get(id).vertexData));

            TitledContentPane dStart = (TitledContentPane) idToNodeMap.get(currentVertex.getId()).displayNode;
            currentVertex.getConnectedVertices().stream()
                    .map(id -> idToNodeMap.get(id).vertexData)
                    .filter(endVertex -> endVertex.getDepth() < currentVertex.getDepth())
                    .forEach(endVertex -> {
                        TitledContentPane dEnd = (TitledContentPane) idToNodeMap.get(endVertex.getId()).displayNode;
                        Node edge = createEdge(dStart, dEnd);
                        edge.setOpacity(0);
                        displayOverlay.getChildren().add(0, edge);

                    });
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
        idToNodeMap.clear();
        preparationDisplayMap.clear();
    }

    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void create(List<VertexData> vertices) {
        PreparationDisplayMaps pdm = createNodeDisplay(vertices);
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
    private Node createEdge(TitledContentPane startBox, TitledContentPane endBox) {
        CubicCurve newEdge = new CubicCurve();
        newEdge.startXProperty().bind(startBox.layoutXProperty().add(startBox.widthProperty()));
        newEdge.startYProperty().bind(startBox.layoutYProperty().add(startBox.heightProperty().divide(2)));
        newEdge.endXProperty().bind(endBox.layoutXProperty());
        newEdge.endYProperty().bind(endBox.layoutYProperty().add(endBox.heightProperty().divide(2)));
        newEdge.controlX1Property().bind(startBox.layoutXProperty().add(startBox.widthProperty()).add(50));
        newEdge.controlY1Property().bind(startBox.layoutYProperty().add(startBox.heightProperty().divide(2)));
        newEdge.controlX2Property().bind(endBox.layoutXProperty().subtract(50));
        newEdge.controlY2Property().bind(endBox.layoutYProperty().add(endBox.heightProperty().divide(2)));
        newEdge.setStroke(Color.BLUE);
        newEdge.setStrokeWidth(0.5);
        newEdge.setStrokeLineCap(StrokeLineCap.ROUND);
        newEdge.setFill(Color.TRANSPARENT);

        linkVertexToEdge(startBox, newEdge);
        linkVertexToEdge(endBox, newEdge);
        return newEdge;
    }

    private void linkVertexToEdge(Node vertex, Node edge) {
        vertexToEdgesMap.computeIfAbsent(vertex, k -> new ArrayList<>());
        if (!vertexToEdgesMap.get(vertex).contains(edge)) vertexToEdgesMap.get(vertex).add(edge);
    }

    /**
     * Method to allow external programs to show the context of the cached data on the display
     */
    public void show() {
        recastDisplayFromCachedData();
    }

    public List<VertexData> getVertexInfo() {
        return idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList());
    }

    /**
     * TODO: Determine whether there are unsaved changes in the program. If necessary, prompt user whether to discard changes
     *
     * @return whether it's OK to close the display
     */
    public boolean requestClose() {
        return true;
    }

    public void deselectAll() {
        selectedVertices.clear();
        resetHighlightingOnAllNodes();
    }

    public void selectAll() {
        selectedVertices.setAll(idToNodeMap.values().stream().map(data -> data.vertexData).collect(Collectors.toList()));
        highlightSelectedNodes();
    }

    public void deleteSelected() {
        if (selectedVertices.size() == 1) {
            removeVertex(selectedVertices.get(0));
        } else if (selectedVertices.size() > 1) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Multiple vertex deletion");
            alert.setHeaderText("Multiple vertices are selected");
            alert.setContentText("Proceed to delete " + selectedVertices.size() + " vertices?");
            alert.showAndWait();
            new ArrayList<>(selectedVertices).forEach(this::removeVertex);
        }
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
        VertexData vertexData;
        Node preparationNode;
        Node displayNode;

        DataAndNodes(VertexData vd, Node p, Node d) {
            this.vertexData = vd;
            this.preparationNode = p;
            this.displayNode = d;
        }
    }

    /**
     * Class representing an item in the priority queue of a Dijkstra's shortest-path calculation.
     */
    private class PriorityItem implements Comparable<PriorityItem> {

        int distance;
        private VertexData vertex;
        PriorityItem previousItem;

        PriorityItem(int distance, VertexData vertex) {
            this.distance = distance;
            this.vertex = vertex;
            previousItem = null;
        }

        /**
         * Compares this object with the specified object for order.  Returns a
         * negative integer, zero, or a positive integer as this object is less
         * than, equal to, or greater than the specified object.
         *
         * @param other the object to be compared.
         * @return a negative integer, zero, or a positive integer as this object
         * is less than, equal to, or greater than the specified object.
         * @throws NullPointerException if the specified object is null
         * @throws ClassCastException   if the specified object's type prevents it
         *                              from being compared to this object.
         */
        @Override
        public int compareTo(PriorityItem other) {
            return Integer.compare(this.distance, other.distance);
        }
    }

}
