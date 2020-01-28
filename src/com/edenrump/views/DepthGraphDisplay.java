/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.views;

import com.edenrump.config.Defaults;
import com.edenrump.graph.DataAndNodes;
import com.edenrump.models.VertexData;
import com.edenrump.ui.components.TitledContentPane;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import javafx.util.Pair;

import java.nio.file.DirectoryStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.edenrump.config.Defaults.DELAY_TIME;

/**
 * Class representing a display pane for a graph
 * <p>
 * It contains vertex and edge information necessary to display a graph
 * It contains separate preparation and display portions of the scene graph.
 * Changes to vertex position in the graph are animated
 * <p>
 * Animation is achieved by piggy-backing on JavaFX's own layout system.
 * It allows JavaFX to calculate layout bounds in the preparation area, and then animating
 * nodes in the display area to move from their current layout positions to the updated ones.
 */
public class DepthGraphDisplay {

    private HorizontalDirection plottingDirection;

    /**
     * Scrollpane that encapsulates all process preparation and display containers.
     */
    ScrollPane linkMapDisplay;
    /**
     * Container in the background of the display that deals with position of nodes
     * <p>
     * This is used in prototyping because it seems easier to have JavaFX deal with the positions of the nodes
     * and then (if positions change) to create animations on the nodes in the anchorpane displayOverlay which
     * move nodes from their original position to their new one. This might be less efficient, but it means I don't
     * have to code my own Region types to hold the nodes.
     */
    HBox preparationContainer = new HBox();
    /**
     * Anchorpane at the front of the display in which real nodes are placed
     */
    AnchorPane displayOverlay = new AnchorPane();

    /**
     * A map of node containers to info nodes
     */
    Map<Integer, Node> depthToPrepContainerMap = new HashMap<>();
    /**
     * Map which links preparation nodes with their display counterparts
     */
    Map<Node, Node> preparationDisplayMap = new HashMap<>();
    /**
     * A map that contains all the edges that are connected to each node.
     */
    Map<Node, List<Node>> vertexToEdgesMap = new HashMap<>();
    /**
     * A list of all prep and display labels linked to the depth to which they're assigned
     */
    Map<String, Pair<Label, Label>> idPrepDisplayLabelMap = new HashMap<>();

    /**
     * A list of nodes that are being prepared for removal from the display pane on the next pass
     */
    List<Node> toBeRemovedOnNextPass = new ArrayList<>();
    /**
     * Whether the display currently has content that is unsaved. Modification of the display switches the flag to
     * true (i.e. is unsaved). Controllers using this display should manually switch the flag back upon file save
     */
    BooleanProperty hasUnsavedContent = new SimpleBooleanProperty(false);
    /**
     * A map of IDs to preparation and display nodes for all nodes in memory
     */
    Map<String, DataAndNodes> allNodesIDMap = new HashMap<>();

    /**
     * A list of the currently-selected vertices in the process display window
     */
    ObservableList<String> selectedVertices = FXCollections.observableArrayList();
    /**
     * The last-selected node in the display. Null if no node selected.
     */
    VertexData lastSelected;

    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public DepthGraphDisplay(ScrollPane display, HorizontalDirection plottingDirection) {
        this.plottingDirection = plottingDirection;

        linkMapDisplay = display;
        display.setPannable(true);
        linkMapDisplay.setFitToHeight(true);
        linkMapDisplay.setFitToWidth(true);
        Platform.runLater(() -> {
            PauseTransition timeForWindowToLoad = new PauseTransition(Duration.seconds(2));
            timeForWindowToLoad.setOnFinished(event -> {
                linkMapDisplay.heightProperty().addListener((obs, o, n) -> Platform.runLater(() -> reconcilePrepAndDisplay(1)));
                linkMapDisplay.widthProperty().addListener((obs, o, n) -> Platform.runLater(() -> reconcilePrepAndDisplay(1)));
            });
            timeForWindowToLoad.play();
        });

        StackPane prepDisplayStack = new StackPane();
        linkMapDisplay.setContent(prepDisplayStack);
        prepDisplayStack.getChildren().addAll(displayOverlay, preparationContainer);

        displayOverlay.setOnMouseClicked(event -> {
            resetHighlightingOnAllNodes();
            selectedVertices.clear();
            lastSelected = null;
        });

        preparationContainer.setPadding(new Insets(25, 25, 25, 35));
        preparationContainer.setSpacing(125);
        preparationContainer.setAlignment(Pos.CENTER);
        preparationContainer.setOpacity(0.1);
        preparationContainer.setMouseTransparent(true);
    }

    /**
     * Return the list of the currently selected vertices as an observable list
     *
     * @return an observable list of selected vertices
     */
    public ObservableList<String> getSelectedVerticesObservableList() {
        return selectedVertices;
    }

    /**
     * Select a vertex by its id. Return a read-only version of that vertex
     *
     * @param id the id of the vertex
     * @return a read-only version of the vertex
     */
    public ReadOnlyObjectWrapper<VertexData> getVertex(String id) {
        return allNodesIDMap.get(id).getVertexData().readOnly();
    }

    public void createVertex(VertexData data) {
        hasUnsavedContent.set(true);
        createNode(data);

        //if node is connected to other nodes, reflect this connection in those nodes
        data.getConnectedVertices().forEach(id -> {
            allNodesIDMap.get(id).getVertexData().addConnection(data.getId());
            createEdge(allNodesIDMap.get(data.getId()), allNodesIDMap.get(id), 0);
        });

        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                allNodesIDMap.get(data.getId()).getDisplayNode().setLayoutX(ltsX(allNodesIDMap.get(data.getId()).getPreparationNode()));
                allNodesIDMap.get(data.getId()).getDisplayNode().setLayoutY(ltsY(allNodesIDMap.get(data.getId()).getPreparationNode()));
                reconcilePrepAndDisplay(DELAY_TIME);
            });
            t.playFromStart();
        });

    }

    /**
     * Remove a vertex from the graph. Schedule
     *
     * @param id the id of the vertex to be removed
     */
    public void removeVertex(String id) {
        hasUnsavedContent.set(true);

        DataAndNodes toRemove = allNodesIDMap.get(id);
        toBeRemovedOnNextPass.add(toRemove.getDisplayNode());
        toBeRemovedOnNextPass.addAll(vertexToEdgesMap.get(toRemove.getDisplayNode()));

        preparationDisplayMap.remove(toRemove.getPreparationNode());
        allNodesIDMap.remove(id);

        allNodesIDMap.values().stream()
                .map(DataAndNodes::getVertexData)
                .forEach(vertex -> vertex.getConnectedVertices().remove(id));

        selectedVertices.clear();
        resetHighlightingOnAllNodes();

        resetPreparationDisplay(allNodesIDMap); //also removes from depthToPrepContainer and preparationContainer

        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            pause.setOnFinished(event -> reconcilePrepAndDisplay(DELAY_TIME));
            pause.play();
        });
    }

    /**
     * Update the display nodes. If depth has changed, reset the preparation display. Update the vertex data
     *
     * @param id     the id of the vertex to update
     * @param vertex the vertexData object containing the updated information
     */
    public void updateVertex(String id, VertexData vertex) {
        hasUnsavedContent.set(true);

        updateNode(allNodesIDMap.get(id).getPreparationNode(), vertex);
        updateNode(allNodesIDMap.get(id).getDisplayNode(), vertex);

        if (allNodesIDMap.get(id).getVertexData().getDepth() != vertex.getDepth()) {
            allNodesIDMap.get(id).getVertexData().update(vertex);
            resetPreparationDisplay(allNodesIDMap);
        } else {
            allNodesIDMap.get(id).getVertexData().update(vertex);
        }
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(e -> reconcilePrepAndDisplay(1));
            pause.play();
        });
    }

    /**
     * Return whether the display has unsaved content.
     *
     * @return whether the display has unsaved content
     */
    public boolean hasUnsavedContent() {
        return hasUnsavedContent.get();
    }

    /**
     * Return the property associated with content saving
     *
     * @return return a property that represents whether the display contains unsaved content
     */
    public BooleanProperty hasUnsavedContentProperty() {
        return hasUnsavedContent;
    }

    /**
     * Set the unsavedContent property to the desired value
     *
     * @param hasUnsavedContent whether the dispaly has unsaved content
     */
    public void setHasUnsavedContent(boolean hasUnsavedContent) {
        this.hasUnsavedContent.set(hasUnsavedContent);
    }

    /**
     * Clear all objects from the display pane and the preparation pane.
     */
    public void clearAll() {
        clearNodes();
        clearInfo();
    }

    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void create(List<VertexData> vertices) {
        PreparationDisplayMaps pdm = createNodeDisplay(vertices);
        allNodesIDMap = pdm.idNodeMap;
        depthToPrepContainerMap = pdm.depthToPrepcontainer;
        preparationDisplayMap = pdm.prepToDisplay;
    }

    /**
     * Method to allow external programs to show the context of the cached data on the display
     */
    public void show() {
        recastDisplayFromCachedData();
    }

    /**
     * Return all VertexData currently cached in the display. Does not check whether nodes are currently dispalyed
     * (this functionality is not be default supported anyway, but extensions of this class should override this method
     * if they wanted to implement node-hiding
     *
     * @return all VertexData currently cached in the display
     */
    public List<VertexData> getVertexInfo() {
        return allNodesIDMap.values().stream().map(DataAndNodes::getVertexData).collect(Collectors.toList());
    }

    /**
     * Deselect all nodes
     */
    public void deselectAll() {
        selectedVertices.clear();
        resetHighlightingOnAllNodes();
    }

    /**
     * Select all nodes
     */
    public void selectAll() {
        selectedVertices.setAll(allNodesIDMap.values().stream().map(dataAndNodes -> dataAndNodes.getVertexData().getId()).collect(Collectors.toList()));
        highlightSelectedNodes();
        lowlightUnselectedNodes();
    }

    /**
     * For each level of depth in the map provided, create a container node. For each vertex within the nested map,
     * create a display node and add it to the container. Return a map of depths to containers
     *
     * @return a map of depth level to container nodes
     */
    PreparationDisplayMaps createNodeDisplay(List<VertexData> vertices) {
        PreparationDisplayMaps pdm = new PreparationDisplayMaps();
        vertices.stream()
                .map(VertexData::getDepth)
                .distinct()
                .forEach(depth -> {
                    List<String> nodeIds = new ArrayList<>();
                    vertices.stream()
                            .filter(v -> v.getDepth() == depth)
                            .forEach(vertexData -> {
                                DataAndNodes nodeData = createNodes(vertexData);
                                nodeIds.add(nodeData.getVertexData().getId());
                                pdm.idNodeMap.put(vertexData.getId(), nodeData);
                                pdm.prepToDisplay.put(nodeData.getPreparationNode(), nodeData.getDisplayNode());
                            });
                    pdm.depthToPrepcontainer.put(depth, createPrepColumn(nodeIds, "Depth: " + depth));
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
    DataAndNodes createNodes(VertexData data) {
        //Create node for preparation area of display
        TitledContentPane prepNode = convertDataToNode(data);
        if (!data.getHyperlinkURL().equals("")) prepNode.addTag("url", data.getHyperlinkURL());

        //Create node for display overlay
        TitledContentPane displayNode = convertDataToNode(data);
        if (!data.getHyperlinkURL().equals("")) displayNode.addTag("url", data.getHyperlinkURL());

        displayNode.setLayoutX(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinX());
        displayNode.setLayoutY(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinY());
        displayNode.setId(data.getId());
        displayNode.setOnContextMenuRequested(event -> {
            showContextMenu(displayNode, standardVertexContextMenu(data.getId()), event);
            event.consume();
        });
        displayNode.setOnMouseClicked(event -> handleSelection(data.getId(), event));

        return new DataAndNodes(data, prepNode, displayNode);
    }

    /**
     * Clear the current display and return it to an unloaded state
     */
    void clearNodes() {
        preparationContainer.getChildren().clear();
        displayOverlay.getChildren().clear();
    }

    /**
     * Utility method to clear the preparation container of all children and re-load from the depthToPrepContainerMap
     */
    void resetPreparationDisplay(Map<String, DataAndNodes> vertexMap) {
        preparationContainer.getChildren().clear();
        depthToPrepContainerMap.clear();
        idPrepDisplayLabelMap.clear();

        vertexMap.values().stream()
                .map(data -> data.getVertexData().getDepth())
                .distinct()
                .sorted(plottingDirection == HorizontalDirection.LEFT ? Comparator.reverseOrder() : Comparator.naturalOrder())
                .collect(Collectors.toList())
                .forEach(depth -> {
                    List<String> prepNodes = new ArrayList<>();
                    vertexMap.values().stream()
                            .filter(v -> v.getVertexData().getDepth() == depth)
                            .forEach(dn -> prepNodes.add(dn.getVertexData().getId()));

                    depthToPrepContainerMap
                            .put(depth, createPrepColumn(prepNodes, "Depth: " + depth));
                    preparationContainer.getChildren().add(depthToPrepContainerMap.get(depth));
                });
    }

    /**
     * Create animations to move display nodes to the same scene-locations as the preparation nodes
     */
    void reconcilePrepAndDisplay(double animationLength) {
        Timeline all = new Timeline();

        for (Node node : toBeRemovedOnNextPass) {
            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(node.opacityProperty(), node.getOpacity())),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(node.opacityProperty(), 0))
            );
        }

        for (Node displayNode : displayOverlay.getChildren()) {
            if (displayNode.getOpacity() == 0) {
                all.getKeyFrames().addAll(
                        new KeyFrame(Duration.millis(0), new KeyValue(displayNode.opacityProperty(), 0)),
                        new KeyFrame(Duration.millis(animationLength), new KeyValue(displayNode.opacityProperty(), 1)));
            }
        }

        for (Node prepNode : preparationDisplayMap.keySet()) {
            Node displayNode = preparationDisplayMap.getOrDefault(prepNode, new TitledContentPane());
            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(displayNode.layoutXProperty(), ltsX(prepNode))),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(displayNode.layoutYProperty(), ltsY(prepNode))));
        }

        for (String title : idPrepDisplayLabelMap.keySet()) {
            Node displayNode = idPrepDisplayLabelMap.get(title).getValue();
            Node prepLabel = idPrepDisplayLabelMap.get(title).getKey();
            all.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutXProperty(), displayNode.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(displayNode.layoutYProperty(), displayNode.getLayoutY())),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(displayNode.layoutXProperty(), ltsX(prepLabel))),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(displayNode.layoutYProperty(), ltsY(prepLabel))));
        }
        all.setOnFinished(event -> {
            displayOverlay.getChildren().removeAll(toBeRemovedOnNextPass);
            toBeRemovedOnNextPass.clear();
        });
        all.playFromStart();
    }

    /**
     * Calculate the positions of vertices and re-create edges for the display based on data in the vertices list
     */
    void recastDisplayFromCachedData() {
        clearNodes();
        displayOverlay.getChildren().addAll(preparationDisplayMap.values());
        for (Node n : displayOverlay.getChildren()) n.setOpacity(0);

        resetPreparationDisplay(allNodesIDMap);
        addEdges(allNodesIDMap, 0);

        //Delay to allow layout cascade to happen, then load the displayOverlay with nodes
        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                for (String id : allNodesIDMap.keySet()) {
                    allNodesIDMap.get(id).getDisplayNode().setLayoutX(allNodesIDMap.get(id).getPreparationNode().getLayoutX());
                    allNodesIDMap.get(id).getDisplayNode().setLayoutY(allNodesIDMap.get(id).getPreparationNode().getLayoutY() + 50);
                }

                for (String title : idPrepDisplayLabelMap.keySet()) {
                    Node displayLabel = idPrepDisplayLabelMap.get(title).getValue();
                    Node prepLabel = idPrepDisplayLabelMap.get(title).getKey();
                    displayLabel.setLayoutX(prepLabel.getLayoutX());
                    displayLabel.setLayoutY(prepLabel.getLayoutY());
                }

                reconcilePrepAndDisplay(DELAY_TIME);
            });
            t.playFromStart();
        });
    }

    void addEdges(Map<String, DataAndNodes> vertexMap, double opacity) {
        //add edges
        List<VertexData> unvisitedNodes = getUpstreamLeaves(vertexMap.values().stream().map(DataAndNodes::getVertexData).collect(Collectors.toList()));
        List<String> visitedNodes = new ArrayList<>();
        while (unvisitedNodes.size() > 0) {
            VertexData currentVertex = unvisitedNodes.remove(0);
            visitedNodes.add(currentVertex.getId());

            currentVertex.getConnectedVertices().stream()
                    .filter(id -> !visitedNodes.contains(id) && vertexMap.containsKey(id))
                    .forEach(id -> unvisitedNodes.add(vertexMap.get(id).getVertexData()));

            currentVertex.getConnectedVertices().stream().filter(vertexMap::containsKey)
                    .map(id -> vertexMap.get(id).getVertexData())
                    .filter(endVertex -> plottingDirection == HorizontalDirection.LEFT ? endVertex.getDepth() < currentVertex.getDepth() : endVertex.getDepth() > currentVertex.getDepth())
                    .forEach(endVertex -> createEdge(vertexMap.get(currentVertex.getId()), vertexMap.get(endVertex.getId()), opacity));
        }
    }

    /**
     * Select the vertex identified. Apply UX logic to determine whether to keep the current selection, remove it,
     * or expand it.
     *
     * @param vertexId the vertex selected
     * @param event    the mouse-event that triggered the selection
     */
    void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && selectedVertices.contains(vertexId)) {
            event.consume();
            return;
        }

        VertexData vertexClicked = allNodesIDMap.get(vertexId).getVertexData();
        if (event.isShiftDown() && lastSelected != null) {
            List<VertexData> vertices = findShortestPath(lastSelected, vertexClicked,
                    allNodesIDMap.values().stream()
                            .map(DataAndNodes::getVertexData)
                            .collect(Collectors.toList()));
            selectedVertices.setAll(vertices.stream().map(VertexData::getId).collect(Collectors.toList()));
        } else if (event.isControlDown()) {
            if (!selectedVertices.contains(vertexClicked.getId())) {
                selectedVertices.add(vertexClicked.getId());
                lastSelected = vertexClicked;
            } else {
                selectedVertices.remove(vertexClicked.getId());
            }
        } else {
            selectedVertices.setAll(vertexClicked.getId());
            lastSelected = vertexClicked;
        }

        highlightSelectedNodes();
        lowlightUnselectedNodes();
        event.consume();
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
    List<VertexData> findShortestPath(VertexData startVertex, VertexData desinationVertex, List<VertexData> allVertices) {
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

    List<VertexData> unidirectionalFill(String vertexID, DepthDirection direction) {
        VertexData currentVertex = allNodesIDMap.get(vertexID).getVertexData();
        List<VertexData> unvisitedVertices = currentVertex.getConnectedVertices().stream()
                .map(id -> allNodesIDMap.get(id).getVertexData())
                .filter(data -> {
                    if (direction == DepthDirection.INCREASING_DEPTH) {
                        return data.getDepth() > allNodesIDMap.get(vertexID).getVertexData().getDepth();
                    } else {
                        return data.getDepth() < allNodesIDMap.get(vertexID).getVertexData().getDepth();
                    }
                })
                .collect(Collectors.toList());

        List<VertexData> visitedVertices = new ArrayList<>(Collections.singletonList(currentVertex));
        while (unvisitedVertices.size() > 0) {
            currentVertex = unvisitedVertices.remove(0);
            visitedVertices.add(currentVertex);

            unvisitedVertices.addAll(currentVertex.getConnectedVertices().stream()
                    .map(id -> allNodesIDMap.get(id).getVertexData())
                    .filter(data -> !visitedVertices.contains(data))
                    .filter(data -> {
                        if (direction == DepthDirection.INCREASING_DEPTH) {
                            return data.getDepth() > allNodesIDMap.get(vertexID).getVertexData().getDepth();
                        } else {
                            return data.getDepth() < allNodesIDMap.get(vertexID).getVertexData().getDepth();
                        }
                    }).collect(Collectors.toList()));
        }

        return visitedVertices;
    }

    /**
     * Separate vertex nodes by selection. Highlight selected nodes. Lowlight unselected nodes.
     */
    void highlightSelectedNodes() {
        selectedVertices.stream()
                .map(id -> (TitledContentPane) allNodesIDMap.get(id).getDisplayNode())
                .forEach(pane -> {
                    pane.highlight();
                    if (selectedVertices.size() == 1) {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, singleSelectedVertexContextMenu(pane.getId()), e));
                    } else {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, standardVertexContextMenu(pane.getId()), e));
                    }
                });
    }

    void lowlightUnselectedNodes() {
        allNodesIDMap.values().stream().map(DataAndNodes::getVertexData) //all vertices
                .filter(v -> !selectedVertices.contains(v.getId()))
                .map(v -> (TitledContentPane) allNodesIDMap.get(v.getId()).getDisplayNode())
                .forEach(pane -> {
                    pane.lowlight();
                    pane.setOnContextMenuRequested(null);
                });
    }

    /**
     * Utility method to unhighlight all nodes in the idToNodeMap
     */
    void resetHighlightingOnAllNodes() {
        for (String id : allNodesIDMap.keySet()) {
            TitledContentPane displayNode = (TitledContentPane) allNodesIDMap.get(id).getDisplayNode();
            displayNode.resetHighlighting();
        }
    }

    /**
     * Utility method. Ensure vertex data is consistently translated into a dispay object
     *
     * @param v the vertex to display
     * @return a consistent node to display on the scene graph
     */
    private TitledContentPane convertDataToNode(VertexData v) {
        TitledContentPane node = new TitledContentPane();
        node.addHeaderBox(v.getName(), v.getId(), Color.web("#D1DBE3"));
        return node;
    }

    /**
     * The current context menu. Prevents multiple context menus being shown simultaneously.
     */
    private ContextMenu currentlyShown = new ContextMenu();

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
     * Simplest context menu associated with a vertex
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    private ContextMenu standardVertexContextMenu(String id) {
        ContextMenu cm = new ContextMenu();

        //TODO: calculate priority of task based on priority of task itself and priority of pre-existing connected nodes.
        MenuItem addLessDepth = new MenuItem(plottingDirection == HorizontalDirection.RIGHT ? "<- Add Node Left" : "Add Node Right ->");
        addLessDepth.setOnAction(event -> {
            int depth = allNodesIDMap.get(id).getVertexData().getDepth() - 1;
            createVertex(new VertexData("New Node", UUID.randomUUID().toString(), Collections.singletonList(id),
                    depth,
                    calculatePriority(depth, VerticalDirection.DOWN),
                    ""));
        });

        MenuItem addMoreDepth = new MenuItem(plottingDirection == HorizontalDirection.RIGHT ? "Add Node Right ->" : "<- Add Node Left");
        addMoreDepth.setOnAction(event -> {
            int depth = allNodesIDMap.get(id).getVertexData().getDepth() + 1;
            createVertex(new VertexData("New Node", UUID.randomUUID().toString(), Collections.singletonList(id),
                    depth,
                    calculatePriority(depth, VerticalDirection.DOWN),
                    ""));
        });

        cm.getItems().addAll(addLessDepth, addMoreDepth);
        return cm;
    }


    int calculatePriority(int depth, VerticalDirection topOrBottom) {
        int rowPriorityIncrement = 32000;

        List<Integer> siblingPriorities = allNodesIDMap.values()
                .stream()
                .filter(data -> data.getVertexData().getDepth() == depth)
                .map(data -> data.getVertexData().getPriority())
                .collect(Collectors.toList());

        return topOrBottom == VerticalDirection.DOWN ?
                Collections.max(siblingPriorities) + rowPriorityIncrement :
                Collections.min(siblingPriorities) - rowPriorityIncrement;
    }

    /**
     * Create a context menu associated with a vertex where only a single vertex has been selected
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    private ContextMenu singleSelectedVertexContextMenu(String id) {
        ContextMenu cm = standardVertexContextMenu(id);
        MenuItem delete = new MenuItem("Delete");

        delete.setOnAction(event -> removeVertex(id));

        cm.getItems().addAll(delete);
        return cm;
    }

    /**
     * Create a new NodeAndData construct from the given vertex data. Link the new node to the source
     * node. Create an edge to represent the link. Add nodes to their respective points in teh scene graph
     * and refresh the display
     *
     * @param newNodeVertexData the data associated with the new vertex
     */
    void createNode(VertexData newNodeVertexData) {
        DataAndNodes newNodeData = createNodes(newNodeVertexData);
        newNodeData.getDisplayNode().setOpacity(0);
        preparationDisplayMap.put(newNodeData.getPreparationNode(), newNodeData.getDisplayNode());
        allNodesIDMap.put(newNodeVertexData.getId(), newNodeData);
        resetPreparationDisplay(allNodesIDMap);
        displayOverlay.getChildren().add(newNodeData.getDisplayNode());
    }

    /**
     * Create a node (line) which links two vertices in the display. Return the node.
     *
     * @param vertex1 the first vertex
     * @param vertex2 the second vertex
     */
    private void createEdge(DataAndNodes vertex1, DataAndNodes vertex2, double opacity) {
        boolean v2_deeper_v1 = vertex2.getVertexData().getDepth() > vertex1.getVertexData().getDepth();

        Region startBox;
        Region endBox;
        if ((!v2_deeper_v1 && plottingDirection == HorizontalDirection.LEFT) || (v2_deeper_v1 && plottingDirection == HorizontalDirection.RIGHT)) {
            startBox = (Region) vertex1.getDisplayNode();
            endBox = (Region) vertex2.getDisplayNode();
        } else if ((v2_deeper_v1 && plottingDirection == HorizontalDirection.LEFT) || (!v2_deeper_v1 && plottingDirection == HorizontalDirection.RIGHT)) {
            startBox = (Region) vertex2.getDisplayNode();
            endBox = (Region) vertex1.getDisplayNode();
        } else {
            throw new IllegalArgumentException("createEdge() doesn't support two nodes at equal depth (yet)"); //TODO: add support for nodes at equal depth.
        }

        CubicCurve edge = new CubicCurve();
        edge.startXProperty().bind(startBox.layoutXProperty().add(startBox.widthProperty()));
        edge.startYProperty().bind(startBox.layoutYProperty().add(startBox.heightProperty().divide(2)));
        edge.endXProperty().bind(endBox.layoutXProperty());
        edge.endYProperty().bind(endBox.layoutYProperty().add(endBox.heightProperty().divide(2)));
        edge.controlX1Property().bind(startBox.layoutXProperty().add(startBox.widthProperty()).add(50));
        edge.controlY1Property().bind(startBox.layoutYProperty().add(startBox.heightProperty().divide(2)));
        edge.controlX2Property().bind(endBox.layoutXProperty().subtract(50));
        edge.controlY2Property().bind(endBox.layoutYProperty().add(endBox.heightProperty().divide(2)));
        edge.setStroke(Color.web("#003865"));
        edge.setStrokeWidth(0.75);
        edge.setStrokeLineCap(StrokeLineCap.ROUND);
        edge.setFill(Color.TRANSPARENT);

        edge.setOpacity(opacity);
        displayOverlay.getChildren().add(0, edge);

        linkVertexToEdge(startBox, edge);
        linkVertexToEdge(endBox, edge);
    }

    /**
     * Utility method. Maintain the vertexToEdgesMap to maintain links between edges in the display
     * and the vertices they're linked to
     *
     * @param vertex the vertex to which the edge should be bound
     * @param edge   the edge
     */
    private void linkVertexToEdge(Node vertex, Node edge) {
        vertexToEdgesMap.computeIfAbsent(vertex, k -> new ArrayList<>());
        if (!vertexToEdgesMap.get(vertex).contains(edge)) vertexToEdgesMap.get(vertex).add(edge);
    }

    /**
     * Update the node n with vertex information v. This method does not check whether the node and vertex
     * are correctly linked (i.e. using the idNodeMap). Callers should check that the correct node has been selected
     *
     * @param n the node to update
     * @param v the vertex containing the information to place inside the node.
     */
    private void updateNode(Node n, VertexData v) {
        if (n instanceof TitledContentPane) {
            TitledContentPane tcp = (TitledContentPane) n;
            tcp.setTitle(v.getName());
            if (!v.getHyperlinkURL().equals("")) {
                if (!tcp.hasTag("url")) {
                    tcp.addTag("url", v.getHyperlinkURL());
                } else {
                    tcp.updateTag("url", v.getHyperlinkURL());
                }
            } else {
                if (tcp.hasTag("url")) tcp.removeTag("url");
            }
        }
    }

    /**
     * Collect the nodes with no upstream linkages. Return these as a list.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no upstream linkages
     */
    private List<VertexData> getUpstreamLeaves(List<VertexData> vertexDataList) {
        if (plottingDirection == HorizontalDirection.LEFT) {
            return vertexDataList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.max(vertexDataList
                            .stream()
                            .map(VertexData::getDepth)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        } else {
            return vertexDataList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.min(vertexDataList
                            .stream()
                            .map(VertexData::getDepth)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Return the Bounds of the node in the scene. Utility method to help concise code.
     *
     * @param node the node in the scene
     * @return the bounds in scene
     */
    Double ltsX(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinX() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinX();
    }

    /**
     * Return relevant bounds in the current container that are the same in-scene bounds as a node in a different container
     * Utility method to help concise code.
     *
     * @return the bounds in the current container that replicate in-scene the bounds of the prep node
     */
    Double ltsY(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinY() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinY();
    }

    /**
     * Utility method to create a consistent column in the preparation display
     *
     * @param nodeIds the nodes to be added to the column
     * @param title   the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    private VBox createPrepColumn(List<String> nodeIds, String title) {
        VBox container = new VBox();
        container.setAlignment(Pos.TOP_CENTER);
        container.setSpacing(25);

        Label prepTitleLabel = new Label(title);
        Label displayTitleLabel = new Label(title);

        if (!idPrepDisplayLabelMap.containsKey(title)) {
            idPrepDisplayLabelMap.put(title, new Pair<>(prepTitleLabel, displayTitleLabel));
            displayOverlay.getChildren().add(displayTitleLabel);
            displayTitleLabel.setOpacity(0);
        } else {
            prepTitleLabel = idPrepDisplayLabelMap.get(title).getKey();
            displayTitleLabel = idPrepDisplayLabelMap.get(title).getValue();
            if (!displayOverlay.getChildren().contains(displayTitleLabel))
                displayOverlay.getChildren().add(displayTitleLabel);
        }

        VBox head = new VBox(prepTitleLabel);
        head.setAlignment(Pos.CENTER);
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);

        Comparator<VertexData> sortPriority = Comparator.comparingDouble(VertexData::getPriority);

        allNodesIDMap.keySet().stream()
                .filter(nodeIds::contains)
                .map(id -> allNodesIDMap.get(id).getVertexData())
                .sorted(sortPriority)
                .forEachOrdered(data -> body.getChildren().add(allNodesIDMap.get(data.getId()).getPreparationNode()));

        container.getChildren().addAll(head, body);
        return container;
    }

    /**
     * Clear all information in the preparation display maps
     */
    private void clearInfo() {
        allNodesIDMap.clear();
        preparationDisplayMap.clear();
    }

    /* ****************************************************************************************************************
     *                                              INNER CLASSES
     **************************************************************************************************************** */

    enum DepthDirection {
        INCREASING_DEPTH, DECREASING_DEPTH
    }

    /**
     * Class representing the maps required to create a preparation section of the scene graph and mimic it in
     * a displayed portion of the scene graph
     */
    static class PreparationDisplayMaps {
        Map<Node, Node> prepToDisplay = new HashMap<>();
        Map<Integer, Node> depthToPrepcontainer = new HashMap<>();
        Map<String, DataAndNodes> idNodeMap = new HashMap<>();
    }

    /**
     * Class representing an item in the priority queue of a Dijkstra's shortest-path calculation.
     */
    private static class PriorityItem implements Comparable<PriorityItem> {

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

    /* ****************************************************************************************************************
     *                                                       GETTERS AND SETTERS
     **************************************************************************************************************** */

}
