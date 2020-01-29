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
import com.edenrump.graph.DepthDirection;
import com.edenrump.graph.Graph;
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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.edenrump.config.Defaults.ANIMATION_LENGTH;

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

    /**
     * Scrollpane that encapsulates all process preparation and display containers.
     */
    private ScrollPane linkMapDisplay;
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
    private AnchorPane displayOverlay = new AnchorPane();

    /**
     * A list of all prep and display labels linked to the depth to which they're assigned
     */
    private Map<Integer, Labels> depthPrepDisplayLabelMap = new HashMap<>();
    /**
     * Map which links preparation nodes with their display counterparts
     */
    private Map<Node, Node> preparationDisplayMap = new HashMap<>();
    /**
     * A map that contains all the edges that are connected to each node.
     */
    private Map<Node, List<Node>> displayNodeToEdgesMap = new HashMap<>();
    /**
     * A map of IDs to preparation and display nodes for all nodes in memory
     */
    Map<String, DataAndNodes> allNodesIDMap = new HashMap<>();
    private Set<DataAndNodes> currentlyVisible = new HashSet<>();
    Set<DataAndNodes> shouldBeVisible = new HashSet<>();
    /**
     * A list of nodes that are being prepared for removal from the display pane on the next pass
     */
    private Set<DataAndNodes> toBeRemoved = new HashSet<>();

    /**
     * A map of items that are currently visible on the display
     */
    List<String> visibleVertices = new ArrayList<>();
    /**
     * A list of the currently-selected vertices in the process display window
     */
    private ObservableList<String> selectedVertices = FXCollections.observableArrayList();

    /**
     * Whether the display currently has content that is unsaved. Modification of the display switches the flag to
     * true (i.e. is unsaved). Controllers using this display should manually switch the flag back upon file save
     */
    private BooleanProperty hasUnsavedContent = new SimpleBooleanProperty(false);
    /**
     * The last-selected node in the display. Null if no node selected.
     */
    private VertexData lastSelected;
    private HorizontalDirection plottingDirection;

    /**
     * Filter determining which nodes are visible in the display
     */
    Set<Predicate<? super DataAndNodes>> visibleNodesFilters = new HashSet<>(Collections.singleton(entry -> true));

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
                linkMapDisplay.heightProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));
                linkMapDisplay.widthProperty().addListener((obs, o, n) -> Platform.runLater(this::reconcilePrepAndDisplay));
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
     * Clear all objects from the display pane and the preparation pane.
     */
    public void clearDisplay() {
        clearNodes();
        clearInfo();
    }

    /**
     * Clear all information in the preparation display maps
     */
    private void clearInfo() {
        allNodesIDMap.clear();
        preparationDisplayMap.clear();
    }

    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void spawnNewDisplay(List<VertexData> vertices) {
        PreparationDisplayMaps pdm = new PreparationDisplayMaps();
        vertices.stream()
                .map(VertexData::getDepth)
                .distinct()
                .forEach(depth -> vertices.stream()
                        .filter(v -> v.getDepth() == depth)
                        .forEach(vertexData -> {
                            DataAndNodes nodeData = generateNodes_LinkToData(vertexData);
                            pdm.idNodeMap.put(vertexData.getId(), nodeData);
                            pdm.prepToDisplay.put(nodeData.getPreparationNode(), nodeData.getDisplayNode());
                        }));
        allNodesIDMap = pdm.idNodeMap;
        preparationDisplayMap = pdm.prepToDisplay;
    }

    /**
     * Method to allow external programs to show the context of the cached data on the display
     */
    public void show() {
        recastDisplayFromCachedData();
    }

    /**
     * Return the list of the currently selected vertices as an observable list
     *
     * @return an observable list of selected vertices
     */
    public ObservableList<String> getSelectedVerticesObservableList() {
        return selectedVertices;
    }

    public void createVertex(VertexData data) {
        //curating saved
        hasUnsavedContent.set(true);

        //curating maps
        DataAndNodes newNodeData = generateNodes_LinkToData(data);
        preparationDisplayMap.put(newNodeData.getPreparationNode(), newNodeData.getDisplayNode());
        allNodesIDMap.put(data.getId(), newNodeData);
        data.getConnectedVertices().forEach(id -> {
            //respect symmetrical connections
            allNodesIDMap.get(id).getVertexData().addConnection(data.getId());

            //create edges to new node
            createEdge(allNodesIDMap.get(data.getId()), allNodesIDMap.get(id), 0);
        });

        //add new nodes to display TODO: move responsibility to determineDisplayedNodes()
        newNodeData.getDisplayNode().setOpacity(0);
        displayOverlay.getChildren().add(newNodeData.getDisplayNode());

        requestLayoutPass();
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

    /**
     * Return all VertexData currently cached in the display. Does not check whether nodes are currently dispalyed
     * (this functionality is not be default supported anyway, but extensions of this class should override this method
     * if they wanted to implement node-hiding
     *
     * @return all VertexData currently cached in the display
     */
    public List<VertexData> getAllVertices() {
        return allNodesIDMap.values().stream().map(DataAndNodes::getVertexData).collect(Collectors.toList());
    }

    /**
     * Update the display nodes. If depth has changed, reset the preparation display. Update the vertex data
     *
     * @param id     the id of the vertex to update
     * @param vertex the vertexData object containing the updated information
     */
    public void updateVertex(String id, VertexData vertex) {
        //curating saved
        hasUnsavedContent.set(true);

        //curating maps
        updateNode(allNodesIDMap.get(id).getPreparationNode(), vertex);
        updateNode(allNodesIDMap.get(id).getDisplayNode(), vertex);

        requestLayoutPass();
    }

    /**
     * Remove a vertex from the graph. Schedule
     *
     * @param id the id of the vertex to be removed
     */
    public void deleteVertex(String id) {
        //curating saved
        hasUnsavedContent.set(true);

        //find nodes to be removed
        DataAndNodes toRemove = allNodesIDMap.get(id);

        //prepare objects to be removed TODO: move responsibility to determineDisplayedNodes()
        toBeRemoved.add(toRemove);

        //curate maps
        preparationDisplayMap.remove(toRemove.getPreparationNode());
        allNodesIDMap.remove(id);
        allNodesIDMap.values().stream()
                .map(DataAndNodes::getVertexData)
                .forEach(vertex -> vertex.getConnectedVertices().remove(id));
        //curate user interface
        selectedVertices.clear();

        requestLayoutPass();
        resetHighlightingOnAllNodes();
    }

    void requestLayoutPass() {
        Timeline fades = refreshNodeVisibility();
        //TODO: reorder --> disappear, then move nodes, THEN fade in new nodes.
        fades.setOnFinished(e -> {
            layoutPreparationDisplay(currentlyVisible);
            reconcilePrepAndDisplay();
        });
        fades.play();
    }

    private Timeline refreshNodeVisibility() {
        //find all nodes that should be visible
        Set<DataAndNodes> shouldBeVisible = new HashSet<>(allNodesIDMap.values());
        for (Predicate<? super DataAndNodes> filter : visibleNodesFilters) {
            shouldBeVisible = shouldBeVisible.stream().filter(filter).collect(Collectors.toSet());
        }

        //nodes that should be visible but aren't
        Set<DataAndNodes> appear = new HashSet<>(shouldBeVisible);
        appear.removeAll(currentlyVisible);

        //node sthat shouldn't be visible, but are
        Set<DataAndNodes> disappear = new HashSet<>();
        for (DataAndNodes data : toBeRemoved) {
            if (currentlyVisible.contains(data)) disappear.add(data);
        }

        currentlyVisible = shouldBeVisible;
        displayOverlay.getChildren().setAll(currentlyVisible.stream()
                .map(DataAndNodes::getDisplayNode).collect(Collectors.toList()));

        for(DataAndNodes data : appear){
            data.getDisplayNode().setLayoutX(displayOverlay.getWidth()/2);
            data.getDisplayNode().setLayoutY(displayOverlay.getHeight()/2);
        }

        double animationLength = ANIMATION_LENGTH;
        Timeline fadeTransitions = new Timeline();
        for (DataAndNodes data : appear) {
            fadeTransitions.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(data.getDisplayNode().opacityProperty(), data.getDisplayNode().getOpacity())),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(data.getDisplayNode().opacityProperty(), 1)));
        }
        for (DataAndNodes data : disappear) {
            System.out.println("fading: " + data.getVertexData().getName());
            fadeTransitions.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(data.getDisplayNode().opacityProperty(), data.getDisplayNode().getOpacity())),
                    new KeyFrame(Duration.millis(animationLength), new KeyValue(data.getDisplayNode().opacityProperty(), 0)));
        }
        return fadeTransitions;
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
        selectedVertices.setAll(allNodesIDMap.keySet());
        highlightSelectedNodes();
        lowlightUnselectedNodes();
    }

    /**
     * Create a DataAndNodes construct linking vertex data with nodes in the scene graph
     * for a given VD and depth
     *
     * @param data the vertex data
     * @return a construct linking vertex graph depth, preparationNode, displayNode and underlying VertexData
     */
    private DataAndNodes generateNodes_LinkToData(VertexData data) {
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
    private void clearNodes() {
        preparationContainer.getChildren().clear();
        displayOverlay.getChildren().clear();
    }

    /**
     * Utility method to clear the preparation container of all children and re-load from the depthToPrepContainerMap
     */
    private void layoutPreparationDisplay(Set<DataAndNodes> vertexMap) {
        preparationContainer.getChildren().clear();
        depthPrepDisplayLabelMap.clear();

        vertexMap.stream()
                .map(data -> data.getVertexData().getDepth())
                .distinct()
                .sorted(plottingDirection == HorizontalDirection.LEFT ? Comparator.reverseOrder() : Comparator.naturalOrder())
                .collect(Collectors.toList())
                .forEach(depth -> {
                    List<String> prepNodes = new ArrayList<>();
                    vertexMap.stream()
                            .filter(v -> v.getVertexData().getDepth() == depth)
                            .forEach(dn -> prepNodes.add(dn.getVertexData().getId()));
                    preparationContainer.getChildren().add(createPrepColumn(prepNodes, depth));
                });
    }

    /**
     * Create animations to move display nodes to the same scene-locations as the preparation nodes
     */
    private void reconcilePrepAndDisplay() {
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(Defaults.ANIMATION_LENGTH));
            pause.setOnFinished(event -> {
                double animationLength = ANIMATION_LENGTH;
                Timeline movementTimeline = new Timeline();
                for (Map.Entry<Node, Node> entry : preparationDisplayMap.entrySet()) {
                    movementTimeline.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(0), new KeyValue(entry.getValue().layoutXProperty(), entry.getValue().getLayoutX())),
                            new KeyFrame(Duration.millis(0), new KeyValue(entry.getValue().layoutYProperty(), entry.getValue().getLayoutY())),
                            new KeyFrame(Duration.millis(animationLength), new KeyValue(entry.getValue().layoutXProperty(), ltsX(entry.getKey()))),
                            new KeyFrame(Duration.millis(animationLength), new KeyValue(entry.getValue().layoutYProperty(), ltsY(entry.getKey()))));
                }
                for (Labels labels : depthPrepDisplayLabelMap.values()) {
                    movementTimeline.getKeyFrames().addAll(
                            new KeyFrame(Duration.millis(0), new KeyValue(labels.displayLabel.layoutXProperty(), labels.displayLabel.getLayoutX())),
                            new KeyFrame(Duration.millis(0), new KeyValue(labels.displayLabel.layoutYProperty(), labels.displayLabel.getLayoutY())),
                            new KeyFrame(Duration.millis(animationLength), new KeyValue(labels.displayLabel.layoutXProperty(), ltsX(labels.prepLabel))),
                            new KeyFrame(Duration.millis(animationLength), new KeyValue(labels.displayLabel.layoutYProperty(), ltsY(labels.prepLabel))));
                }
                movementTimeline.playFromStart();
            });
            pause.play();
        });
    }

    /**
     * Calculate the positions of vertices and re-spawnNewDisplay edges for the display based on data in the vertices list
     */
    private void recastDisplayFromCachedData() {
        clearNodes();

        initialVisibilityFilter();

        requestLayoutPass();
    }

    void initialVisibilityFilter() {

    }

    private void addEdges(Set<DataAndNodes> vertexMap, double opacity) {
        //determine variables based on plotting direction
        DepthDirection searchDirection = plottingDirection == HorizontalDirection.LEFT ? DepthDirection.INCREASING_DEPTH : DepthDirection.DECREASING_DEPTH;

        //add edges
        List<VertexData> unvisitedNodes = Graph.getLeavesUnidirectional(searchDirection, vertexMap.stream().map(DataAndNodes::getVertexData).collect(Collectors.toList()));
        List<VertexData> visitedNodes = new ArrayList<>();
        while (unvisitedNodes.size() > 0) {
            VertexData currentVertex = unvisitedNodes.remove(0);
            visitedNodes.add(currentVertex);

            currentVertex.getConnectedVertices().stream()
                    .filter(id -> !visitedNodes.contains(id) &&
                            vertexMap.stream().map(data -> data.getVertexData().getId()).collect(Collectors.toList()).contains(id))
                    .forEach(id -> unvisitedNodes.add(allNodesIDMap.get(id).getVertexData()));

            currentVertex.getConnectedVertices().stream().filter(vertexMap::contains)
                    .map(id -> allNodesIDMap.get(id).getVertexData())
                    .filter(endVertex -> plottingDirection == HorizontalDirection.LEFT ? endVertex.getDepth() < currentVertex.getDepth() : endVertex.getDepth() > currentVertex.getDepth())
                    .forEach(endVertex -> createEdge(allNodesIDMap.get(currentVertex.getId()), allNodesIDMap.get(endVertex.getId()), opacity));
        }
    }

    /**
     * Select the vertex identified. Apply UX logic to determine whether to keep the current selection, remove it,
     * or expand it.
     *
     * @param vertexId the vertex selected
     * @param event    the mouse-event that triggered the selection
     */
    private void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && selectedVertices.contains(vertexId)) {
            event.consume();
            return;
        }

        VertexData vertexClicked = allNodesIDMap.get(vertexId).getVertexData();
        if (event.isShiftDown() && lastSelected != null) {
            List<VertexData> vertices = Graph.findShortestPath(lastSelected, vertexClicked,
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

        addMouseActions(vertexId, event);

        highlightSelectedNodes();
        lowlightUnselectedNodes();
        event.consume();
    }

    void addMouseActions(String vertexId, MouseEvent event) {

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
    private void highlightSelectedNodes() {

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

    private void lowlightUnselectedNodes() {
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
    private void resetHighlightingOnAllNodes() {
        for (Map.Entry<String, DataAndNodes> entry : allNodesIDMap.entrySet()) {
            TitledContentPane displayNode = (TitledContentPane) entry.getValue().getDisplayNode();
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

    private int calculatePriority(int depth, VerticalDirection topOrBottom) {
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

        delete.setOnAction(event -> deleteVertex(id));

        cm.getItems().addAll(delete);
        return cm;
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
        displayNodeToEdgesMap.computeIfAbsent(vertex, k -> new ArrayList<>());
        if (!displayNodeToEdgesMap.get(vertex).contains(edge)) displayNodeToEdgesMap.get(vertex).add(edge);
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
     * Utility method to create a consistent column in the preparation display
     *
     * @param nodeIds the nodes to be added to the column
     * @param title   the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    private VBox createPrepColumn(List<String> nodeIds, Integer title) {
        VBox container = new VBox();
        container.setAlignment(Pos.TOP_CENTER);
        container.setSpacing(25);

        Label prepTitleLabel = new Label(title.toString());
        Label displayTitleLabel = new Label(title.toString());

        if (!depthPrepDisplayLabelMap.containsKey(title)) {
            depthPrepDisplayLabelMap.put(title, new Labels(prepTitleLabel, displayTitleLabel));
            displayOverlay.getChildren().add(displayTitleLabel);
            displayTitleLabel.setOpacity(0);
        } else {
            prepTitleLabel = depthPrepDisplayLabelMap.get(title).prepLabel;
            displayTitleLabel = depthPrepDisplayLabelMap.get(title).displayLabel;
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
     * Class representing two bound labels in the scene graph between the preparation and the display
     */
    static class Labels {
        Label prepLabel;
        Label displayLabel;

        Labels(Label prepLabel, Label displayLabel) {
            this.prepLabel = prepLabel;
            this.displayLabel = displayLabel;
        }
    }

    /**
     * Class representing the maps required to create a preparation section of the scene graph and mimic it in
     * a displayed portion of the scene graph
     */
    static class PreparationDisplayMaps {
        Map<Node, Node> prepToDisplay = new HashMap<>();
        Map<String, DataAndNodes> idNodeMap = new HashMap<>();
    }
}
