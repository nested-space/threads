/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.display;

import com.edenrump.toolkit.graph.DataAndNodes;
import com.edenrump.toolkit.graph.Graph;
import com.edenrump.toolkit.models.Vertex;
import com.edenrump.toolkit.ui.components.TitledContentPane;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.edenrump.toolkit.config.Defaults.ANIMATION_LENGTH;

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
    private ScrollPane graphDisplay;
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
     * A map that contains all the edges that are connected to each node.
     */
    private Map<String, Set<Shape>> vertexIdToEdgesMap = new HashMap<>();

    /**
     * A map of IDs to preparation and display nodes for all nodes in memory
     */
    private Map<String, DataAndNodes> allNodesIDMap = new HashMap<>();
    private Set<String> currentlyVisibleVerticesById = new HashSet<>();
    /**
     * A list of nodes that are being prepared for removal from the display pane on the next pass
     */
    private Set<String> verticesToBeRemovedOnNextRefresh = new HashSet<>();

    /**
     * A list of the currently-selected vertices in the process display window
     */
    ObservableList<String> selectedVertexIds = FXCollections.observableArrayList();

    /**
     * Whether the display currently has content that is unsaved. Modification of the display switches the flag to
     * true (i.e. is unsaved). Controllers using this display should manually switch the flag back upon file save
     */
    private BooleanProperty hasUnsavedContent = new SimpleBooleanProperty(false);
    /**
     * The last-selected node in the display. Null if no node selected.
     */
    private String lastSelectedVertexId;
    private HorizontalDirection plottingDirection;

    /**
     * Filter determining which nodes are visible in the display
     */
    Set<Predicate<Vertex>> visibleNodesFilters = new HashSet<>(Collections.singleton(entry -> true));

    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public DepthGraphDisplay(ScrollPane display, HorizontalDirection plottingDirection) {
        this.plottingDirection = plottingDirection;
        this.graphDisplay = display;
        setStyleOnDisplayContainer();
        setStyleOnPreparationContainer();
        addMouseEventsToDisplayPane();
        wrapDisplayAndPreparationPanesInGraphDisplay();

        pauseAndThenRunMethod(Duration.seconds(2), this::setDisplayPaneToRefreshOnWindowResize); //pause is essential else stage is null
    }

    private void setStyleOnDisplayContainer() {
        graphDisplay.setPannable(true);
        graphDisplay.setFitToHeight(true);
        graphDisplay.setFitToWidth(true);
    }

    private void setStyleOnPreparationContainer() {
        preparationContainer.setAlignment(Pos.TOP_LEFT);
        preparationContainer.setPadding(new Insets(25, 25, 25, 35));
        preparationContainer.setSpacing(125);
        preparationContainer.setOpacity(0);
        preparationContainer.setMouseTransparent(true);
    }

    private void addMouseEventsToDisplayPane() {
        displayOverlay.setOnMouseClicked(event -> {
            unselectAllNodes();
        });
    }

    private void pauseAndThenRunMethod(Duration delay, Runnable runnable) {
        PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(event -> runnable.run());
        pause.play();
    }

    private void setDisplayPaneToRefreshOnWindowResize() {
        graphDisplay.heightProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisibleVerticesById).playFromStart());
        graphDisplay.widthProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisibleVerticesById).playFromStart());
    }

    private void wrapDisplayAndPreparationPanesInGraphDisplay() {
        graphDisplay.setContent(new StackPane(displayOverlay, preparationContainer));
    }

    private void unselectAllNodes() {
        resetHighlightingOnAllNodes();
        selectedVertexIds.clear();
        lastSelectedVertexId = null;
    }

    /**
     * Clear all objects from the display pane and the preparation pane.
     */
    public void clearDisplay() {
        clearNodes();
        removeVertexWithoutRefreshingDisplay();
    }


    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void createNewDisplayFromVertexData(List<Vertex> vertices) {
        vertices.stream()
                .map(Vertex::getDepth)
                .distinct()
                .forEach(depth -> vertices.stream()
                        .filter(v -> v.getDepth() == depth)
                        .forEach(vertexData -> {
                            DataAndNodes nodeData = generateNodes_LinkToData(vertexData);
                            addNodeWithoutRefreshingDisplay(vertexData.getId(), nodeData);
                        }));
    }


    /**
     * Method to allow external programs to show the context of the cached data on the display
     */
    public void show() {
        clearDisplayAndRecreateFromVertexData();
    }

    /**
     * Return the list of the currently selected vertices as an observable list
     *
     * @return an observable list of selected vertices
     */
    public ObservableList<String> getSelectedVertexIds() {
        return selectedVertexIds;
    }

    /**
     * Create a new preparation and display node set from the given vertex, add these to the node map, and add the
     * vertex to the display, if visible
     * <p>
     * Flag that the display has unsaved content.
     *
     * @param vertex the vertex to be added
     */
    public void addVertexToDisplay(Vertex vertex) {
        setUnsavedContentFlagToTrue();

        addNodeWithoutRefreshingDisplay(vertex.getId(), generateNodes_LinkToData(vertex));
        vertex.getConnectedVertices().forEach(id -> {
            getVertexById(id).addConnection(vertex.getId());
        });

        updateDisplay();
    }


    private void setUnsavedContentFlagToTrue() {
        hasUnsavedContent.set(true);
    }


    /**
     * Update the display nodes. If depth has changed, reset the preparation display. Update the vertex data
     *
     * @param id     the id of the vertex to update
     * @param vertex the vertexData object containing the updated information
     */
    public void updateVertexAndRefreshDisplay(String id, Vertex vertex) {
        setUnsavedContentFlagToTrue();

        updateNode(getPreparationNodeById(id), vertex);
        updateNode(getDisplayNodeById(id), vertex);

        updateDisplay();
    }

    /**
     * Remove a vertex from the graph. Schedule
     *
     * @param vertexId the id of the vertex to be removed
     */
    public void deleteVertexAndUpdateDisplay(String vertexId) {
        setUnsavedContentFlagToTrue();
        verticesToBeRemovedOnNextRefresh.add(vertexId);
        removeVertexFromMapsAndUnconnectOtherVertices(vertexId);
        unselectAllNodes();
        updateDisplay();
    }

    private void removeVertexFromMapsAndUnconnectOtherVertices(String vertexId) {
        removeNodeWithoutRefreshingDisplay(vertexId);
    }

    /**
     * Calculate the positions of vertices and re-spawnNewDisplay edges for the display based on data in the vertices list
     */
    private void clearDisplayAndRecreateFromVertexData() {
        clearNodes();
        updateDisplay();
    }

    public void updateDisplay() {
        updateLayout();
        updateColors();
    }

    NodeStatus visibilityStatusOfVertices;

    /**
     * Determine which nodes should be visible based on the node visibility filters applied to the display
     * <p>
     * Re-determine the position of visible nodes in the preparation display. Wait for 100 milliseconds to allow
     * a layout pass through the JavaFX Scene Graph to pass.
     * <p>
     * Fade in nodes that should be visible, fade out those that should not be visible. Move display nodes to their
     * correct locations.
     */
    public void updateLayout() {

        updateNodeVisibility();
        currentlyVisibleVerticesById = visibilityStatusOfVertices.shouldBeVisible;
        layoutPreparationDisplay(currentlyVisibleVerticesById);

        for (String vertexToAppear : visibilityStatusOfVertices.verticesToAppear) {
            getDisplayNodeById(vertexToAppear).setOpacity(0);
        }

        pauseAndThenRunMethod(Duration.millis(250), this::makeNodesVisibleAndMoveNodesToCorrectPositions);
    }

    public void makeNodesVisibleAndMoveNodesToCorrectPositions() {
        Timeline fadeOut = fadeOutNodes(visibilityStatusOfVertices.verticesToDisappear);
        Timeline fadeEdges = fadeOutEdges(visibilityStatusOfVertices.edgesToDisappear);
        fadeOut.getKeyFrames().addAll(fadeEdges.getKeyFrames());

        fadeOut.setOnFinished((removeNodes) -> {
            for (String id : visibilityStatusOfVertices.verticesToDisappear) {
                displayOverlay.getChildren().remove(getDisplayNodeById(id));
            }
            for (Node edge : visibilityStatusOfVertices.edgesToDisappear) {
                displayOverlay.getChildren().remove(edge);
            }
        });

        //TIMELINE 2 determine nodes that are visible RIGHT NOW and move them to their correct locations
        Set<String> visible = new HashSet<>(visibilityStatusOfVertices.shouldBeVisible);
        visible.removeAll(visibilityStatusOfVertices.verticesToAppear);
        Timeline moveNodes = reconcilePrepAndDisplay(visible);

        //TIMELINE 3 add nodes to display, move them to the correct location and then fade in
        Set<Node> appearing = new HashSet<>();
        for (String id : visibilityStatusOfVertices.verticesToAppear) {
            displayOverlay.getChildren().add(getDisplayNodeById(id));
            getDisplayNodeById(id).setLayoutX(ltsX(getPreparationNodeById(id)));
            getDisplayNodeById(id).setLayoutY(ltsY(getPreparationNodeById(id)));
            appearing.add(getDisplayNodeById(id));
        }
        Timeline appear = fadeInDesiredNodes(appearing);
        Timeline fadeInEdges = fadeInEdges(visibilityStatusOfVertices.edgesToAdd);

        displayOverlay.getChildren().addAll(visibilityStatusOfVertices.edgesToAdd);
        appear.getKeyFrames().addAll(fadeInEdges.getKeyFrames());

        fadeOut.play();

        moveNodes.setOnFinished((move_event) -> {
            appear.play();
            //after a suitable delay, recalculate the heights and positions again
            PauseTransition recalculateHeights = new PauseTransition(Duration.seconds(0.5));
            recalculateHeights.setOnFinished((wait_event) -> {
                reconcilePrepAndDisplay(currentlyVisibleVerticesById);
            });
            recalculateHeights.play();
            move_event.consume();
        });
        moveNodes.play();
    }

    /**
     * Update colours for all nodes based on the associated vertex data
     */
    private void updateColors() {
        for (String id : getAllVertexIds()) {
            Vertex vertex = getVertexById(id);
            if (vertex.hasProperty("color")) {
                Color color;
                try {
                    color = Color.web(vertex.getProperty("color"));
                } catch (IllegalArgumentException e) {
                    color = Color.BLACK;
                    //TODO: log error
                }

                TitledContentPane node = getDisplayNodeById(id);
                node.setHeaderColor(color);
                if (color.getBrightness() < 0.7) {
                    node.setTextColor(Color.WHITE);
                }
            }
        }
    }

    /**
     * Determine whether nodes should be visible based on the node visibility criteria in the node visibility filter
     * <p>
     * Compare the set of nodes that should be visible with the set that is currently visible.
     * <p>
     * Store in a NodeStatus object those which are not visible, but should be (appear), are visible but
     * should not be (disappear) and which are currently visible and should stay so (shouldBeVisible)
     *
     * @return an object containing a list of nodes which are not visible, but should be (appear), are visible but
     * should not be (disappear) and which are currently visible and should stay so (shouldBeVisible)
     */
    private void updateNodeVisibility() {
        visibilityStatusOfVertices = new NodeStatus();
        //find all nodes that should be visible
        visibilityStatusOfVertices.shouldBeVisible = new HashSet<>(getAllVertexIds());

        Predicate<Vertex> allPredicates = visibleNodesFilters.stream()
                .reduce(p -> true, Predicate::and);

        visibilityStatusOfVertices.shouldBeVisible = visibilityStatusOfVertices.shouldBeVisible.stream()
                .map(this::getVertexById)
                .filter(allPredicates)
                .map(Vertex::getId)
                .collect(Collectors.toSet());

        //nodes that should be visible but aren't
        visibilityStatusOfVertices.verticesToAppear = new HashSet<>(visibilityStatusOfVertices.shouldBeVisible);
        visibilityStatusOfVertices.verticesToAppear.removeAll(currentlyVisibleVerticesById);

        //iterate through nodes that are determined to be appearing and add a node for each.
        // NB: createEdge() curates maps but does not add them to the display - this is done separately.
        for (String appearing : visibilityStatusOfVertices.verticesToAppear) {
            for (String otherVertexId : getVertexById(appearing).getConnectedVertices()) {
                if (visibilityStatusOfVertices.shouldBeVisible.contains(otherVertexId)) {
                    visibilityStatusOfVertices.edgesToAdd.add(createEdge(otherVertexId, appearing));
                }
            }
        }

        //nodes that shouldn't be visible, but are
        for (String id : verticesToBeRemovedOnNextRefresh) {
            if (currentlyVisibleVerticesById.contains(id)) visibilityStatusOfVertices.verticesToDisappear.add(id);
        }
        for (String id : currentlyVisibleVerticesById) {
            if (!visibilityStatusOfVertices.shouldBeVisible.contains(id))
                visibilityStatusOfVertices.verticesToDisappear.add(id);
        }

        //from status.disappear, determine connected edges and add them to edgesToRemove
        List<Set<Shape>> edgeLists = vertexIdToEdgesMap.entrySet()
                .stream()
                .filter(entry -> visibilityStatusOfVertices.verticesToDisappear.contains(entry.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        for (Set<Shape> edgeList : edgeLists) {
            visibilityStatusOfVertices.edgesToDisappear.addAll(edgeList);
        }

        verticesToBeRemovedOnNextRefresh.clear();
    }

    /**
     * Create a timeline that fades out the nodes that should not be visible in the display
     *
     * @param disappear a set of nodes which should be faded out
     * @return a timeline that fades out the nodes that should not be visible in the display
     */
    private Timeline fadeOutNodes(Set<String> disappear) {
        Timeline fadeOut = new Timeline(30);
        for (String id : disappear) {
            fadeOut.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(getDisplayNodeById(id).opacityProperty(), getDisplayNodeById(id).getOpacity())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(getDisplayNodeById(id).opacityProperty(), 0)));
        }
        return fadeOut;
    }

    /**
     * Create a timeline that fades out the nodes that should not be visible in the display
     *
     * @param disappear a set of nodes which should be faded out
     * @return a timeline that fades out the nodes that should not be visible in the display
     */
    private Timeline fadeOutEdges(Set<Shape> disappear) {
        Timeline fadeOut = new Timeline(30);
        for (Shape shape : disappear) {
            fadeOut.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(shape.strokeWidthProperty(), shape.getStrokeWidth())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(shape.strokeWidthProperty(), 0)));
        }
        return fadeOut;
    }

    /**
     * Create a timeline that fades in the nodes that should be visible in the display
     *
     * @param appear a set of nodes which should be faded in
     * @return a timeline that fades in the nodes that should be visible in the display
     */
    private Timeline fadeInDesiredNodes(Set<Node> appear) {
        Timeline fadeIn = new Timeline(30);
        for (Node data : appear) {
            fadeIn.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(data.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(data.opacityProperty(), 1)));
        }
        return fadeIn;
    }

    /**
     * Create a timeline that fades in the nodes that should be visible in the display
     *
     * @param appear a set of nodes which should be faded in
     * @return a timeline that fades in the nodes that should be visible in the display
     */
    private Timeline fadeInEdges(Set<Shape> appear) {
        Timeline fadeIn = new Timeline(30);
        for (Shape shape : appear) {
            fadeIn.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(shape.strokeWidthProperty(), 0)),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(shape.strokeWidthProperty(), 1)));
        }
        return fadeIn;
    }

    /**
     * Create animations to move display nodes to the same scene-locations as the preparation nodes
     */
    private Timeline reconcilePrepAndDisplay(Set<String> nodes) {
        Timeline movementTimeline = new Timeline(30);
        for (String id : nodes) {
            TitledContentPane disp = getDisplayNodeById(id);
            TitledContentPane prep = getPreparationNodeById(id);
            disp.setMaxHeight(prep.getHeight());
            movementTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(disp.layoutXProperty(), disp.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(disp.layoutYProperty(), disp.getLayoutY())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(disp.layoutXProperty(), Math.max(ltsX(prep), 0))),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(disp.layoutYProperty(), Math.max(ltsY(prep), 0))));
        }
        for (Labels labels : depthPrepDisplayLabelMap.values()) {
            movementTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(labels.displayLabel.layoutXProperty(), labels.displayLabel.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(labels.displayLabel.layoutYProperty(), labels.displayLabel.getLayoutY())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(labels.displayLabel.layoutXProperty(), ltsX(labels.prepLabel))),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(labels.displayLabel.layoutYProperty(), ltsY(labels.prepLabel))));
        }
        movementTimeline.playFromStart();
        return movementTimeline;
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
        selectedVertexIds.clear();
        resetHighlightingOnAllNodes();
    }

    /**
     * Select all nodes
     */
    public void selectAll() {
        selectedVertexIds.setAll(allNodesIDMap.keySet());
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
    private DataAndNodes generateNodes_LinkToData(Vertex data) {
        //Create node for preparation area of display
        TitledContentPane prepNode = convertDataToNode(data);
        if (data.hasProperty("url")) prepNode.addTag("url", data.getProperty("url"));

        //Create node for display overlay
        TitledContentPane displayNode = convertDataToNode(data);
        if (data.hasProperty("url")) displayNode.addTag("url", data.getProperty("url"));

        displayNode.setLayoutX(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinX());
        displayNode.setLayoutY(prepNode.localToScene(prepNode.getBoundsInLocal()).getMinY());
        displayNode.setId(data.getId());
        displayNode.setOnContextMenuRequested(event -> {
            showContextMenu(displayNode, multipleSelectionContextMenu(data.getId()), event);
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
    private void layoutPreparationDisplay(Set<String> vertexMap) {
        preparationContainer.getChildren().clear();
        depthPrepDisplayLabelMap.clear();

        vertexMap.stream()
                .map(id -> getVertexById(id).getDepth())
                .distinct()
                .sorted(plottingDirection == HorizontalDirection.LEFT ? Comparator.reverseOrder() : Comparator.naturalOrder())
                .collect(Collectors.toList())
                .forEach(depth -> {
                    List<String> prepNodes = new ArrayList<>();
                    vertexMap.stream()
                            .filter(id -> getVertexById(id).getDepth() == depth)
                            .forEach(prepNodes::add);
                    preparationContainer.getChildren().add(createPrepColumn(prepNodes, depth));
                });
    }

    private boolean preventDefaultHighlight = false;

    /**
     * Select the vertex identified. Apply UX logic to determine whether to keep the current selection, remove it,
     * or expand it.
     *
     * @param vertexId the vertex selected
     * @param event    the mouse-event that triggered the selection
     */
    private void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && selectedVertexIds.contains(vertexId)) {
            event.consume();
            return;
        }

        boolean preventAdditionalActions = false;

        Vertex vertexClicked = getVertexById(vertexId);
        if (event.isShiftDown() && lastSelectedVertexId != null) {
            List<Vertex> vertices = Graph.findShortestPath(getVertexById(vertexId), vertexClicked, getAllVertexData());
            selectedVertexIds.setAll(vertices.stream().map(Vertex::getId).collect(Collectors.toList()));
            preventAdditionalActions = true;
        } else if (event.isControlDown()) {
            if (!selectedVertexIds.contains(vertexClicked.getId())) {
                selectedVertexIds.add(vertexClicked.getId());
                lastSelectedVertexId = vertexId;
            } else {
                selectedVertexIds.remove(vertexId);
            }
            preventAdditionalActions = true;
        } else {
            selectedVertexIds.setAll(vertexId);
            lastSelectedVertexId = vertexId;
        }

        if (!preventAdditionalActions) {
            addMouseActions(vertexId, event);
        }

        if (!preventDefaultHighlight) {
            highlightSelectedNodes();
            lowlightUnselectedNodes();
            preventDefaultHighlight = false;
        }

        event.consume();
    }

    /**
     * Accessor method. After standard mouse-specific actions have been handled in handleSelection, provide
     * additional actions to be taken.
     *
     * @param vertexId the id of the selected vertex
     * @param event    the mouse event that triggered the action
     */
    public void addMouseActions(String vertexId, MouseEvent event) {

    }

    /**
     * Separate vertex nodes by selection. Highlight selected nodes. Lowlight unselected nodes.
     */
    public void highlightSelectedNodes() {
        selectedVertexIds.stream()
                .map(this::getDisplayNodeById)
                .forEach(pane -> {
                    pane.highlightOne();
                    if (selectedVertexIds.size() == 1) {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, singleSelectedVertexContextMenu(pane.getId()), e));
                    } else {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, multipleSelectionContextMenu(pane.getId()), e));
                    }
                });

        if (lastSelectedVertexId != null) {
            TitledContentPane last = getDisplayNodeById(lastSelectedVertexId);
            last.highlightTwo();
        }
    }

    private void lowlightUnselectedNodes() {

        for(String id: getAllVertexIds()){
            if(selectedVertexIds.contains(id)){
                TitledContentPane disp = getDisplayNodeById(id);
                disp.lowlight();
                disp.setOnContextMenuRequested(null);
            }
        }
    }

    /**
     * Utility method to unhighlight all nodes in the idToNodeMap
     */
    private void resetHighlightingOnAllNodes() {
        for (String id: getAllVertexIds()) {
            TitledContentPane displayNode = getDisplayNodeById(id);
            displayNode.resetHighlighting();
        }
    }

    /**
     * Utility method. Ensure vertex data is consistently translated into a dispay object
     *
     * @param v the vertex to display
     * @return a consistent node to display on the scene graph
     */
    private TitledContentPane convertDataToNode(Vertex v) {
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
    public ContextMenu multipleSelectionContextMenu(String id) {
        return new ContextMenu();
    }

    public int calculatePriority(int depth, VerticalDirection topOrBottom) {
        int rowPriorityIncrement = 32000;

        int maxPriority = Integer.MIN_VALUE;
        int minPriority = Integer.MAX_VALUE;
        for (String id : getAllVerticesAtDepth(depth)) {
            minPriority = Math.min(minPriority, getVertexById(id).getDepth());
            maxPriority = Math.min(maxPriority, getVertexById(id).getDepth());
        }

        if (maxPriority == Integer.MIN_VALUE) return 0;
        return topOrBottom == VerticalDirection.DOWN ?
                maxPriority + rowPriorityIncrement :
                minPriority - rowPriorityIncrement;
    }


    /**
     * Create a context menu associated with a vertex where only a single vertex has been selected
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    public ContextMenu singleSelectedVertexContextMenu(String id) {
        return new ContextMenu();
    }

    /**
     * Create a node (line) which links two vertices in the display. Return the node.
     *
     * @param vertex1 the first vertex
     * @param vertex2 the second vertex
     */
    private Shape createEdge(String vertex1, String vertex2) {
        boolean v2_deeper_v1 = getVertexById(vertex2).getDepth() > getVertexById(vertex1).getDepth();

        Region startBox;
        Region endBox;
        if ((!v2_deeper_v1 && plottingDirection == HorizontalDirection.LEFT) || (v2_deeper_v1 && plottingDirection == HorizontalDirection.RIGHT)) {
            startBox = getDisplayNodeById(vertex1);
            endBox = getDisplayNodeById(vertex2);
        } else if ((v2_deeper_v1 && plottingDirection == HorizontalDirection.LEFT) || (!v2_deeper_v1 && plottingDirection == HorizontalDirection.RIGHT)) {
            startBox = getDisplayNodeById(vertex2);
            endBox = getDisplayNodeById(vertex1);
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
        edge.setStrokeWidth(0);
        edge.setStrokeLineCap(StrokeLineCap.ROUND);
        edge.setFill(Color.TRANSPARENT);

        linkVertexToEdge(vertex1, edge);
        linkVertexToEdge(vertex2, edge);

        return edge;
    }

    /**
     * Utility method. Maintain the vertexToEdgesMap to maintain links between edges in the display
     * and the vertices they're linked to
     *
     * @param vertex the vertex to which the edge should be bound
     * @param edge   the edge
     */
    private void linkVertexToEdge(String vertex, Shape edge) {
        vertexIdToEdgesMap.computeIfAbsent(vertex, k -> new HashSet<>());
        vertexIdToEdgesMap.get(vertex).add(edge);
    }

    /**
     * Update the node n with vertex information v. This method does not check whether the node and vertex
     * are correctly linked (i.e. using the idNodeMap). Callers should check that the correct node has been selected
     *
     * @param n the node to update
     * @param v the vertex containing the information to place inside the node.
     */
    private void updateNode(TitledContentPane n, Vertex v) {
        TitledContentPane tcp = (TitledContentPane) n;
        tcp.setTitle(v.getName());
        if (v.hasProperty("url")) {
            if (!tcp.hasTag("url")) {
                tcp.addTag("url", v.getProperty("url"));
            } else {
                tcp.updateTag("url", v.getProperty("url"));
            }
        } else {
            if (tcp.hasTag("url")) tcp.removeTag("url");
        }
    }

    /**
     * Utility method to create a consistent column in the preparation display
     *
     * @param nodeIds the nodes to be added to the column
     * @param title   the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    public VBox createPrepColumn(List<String> nodeIds, Integer title) {
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);

        for (String id : geAllVerticesSortedByPriority()) {
            if (nodeIds.contains(id)) {
                body.getChildren().add(getPreparationNodeById(id));
            }
        }

        return body;
    }

    public Map<String, DataAndNodes> getAllNodesIDMap() {
        return new HashMap<>(allNodesIDMap);
    }

    public void clearVisibilityFilters() {
        visibleNodesFilters.clear();
    }

    public void addVisibilityFilter(Predicate<Vertex> filter) {
        visibleNodesFilters.add(filter);
    }

    public void removeVisibilityFilter(Predicate<Vertex> filter) {
        visibleNodesFilters.remove(filter);
    }

    public void clearSelectedVertices() {
        selectedVertexIds.clear();
    }

    public void addSelectedVertex(String id) {
        selectedVertexIds.add(id);
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

    public WritableImage getSnapShot() {
        return displayOverlay.snapshot(new SnapshotParameters(), null);
    }

    /**
     * Class representing two bound labels in the scene graph between the preparation and the display
     */
    private static class Labels {
        Label prepLabel;
        Label displayLabel;

        Labels(Label prepLabel, Label displayLabel) {
            this.prepLabel = prepLabel;
            this.displayLabel = displayLabel;
        }
    }

    /**
     * Class representing the status of nodes in a display with respect to whether they should be visible or not.
     */
    private static class NodeStatus {
        Set<String> shouldBeVisible = new HashSet<>();
        Set<String> verticesToAppear = new HashSet<>();
        Set<String> verticesToDisappear = new HashSet<>();
        Set<Shape> edgesToDisappear = new HashSet<>();
        Set<Shape> edgesToAdd = new HashSet<>();

        NodeStatus() {
        }

        NodeStatus(Set<String> current) {
            shouldBeVisible = new HashSet<>(current);
        }
    }

    private void removeVertexWithoutRefreshingDisplay() {
        allNodesIDMap.clear();
    }


    private void addNodeWithoutRefreshingDisplay(String nodeId, DataAndNodes nodes) {
        allNodesIDMap.put(nodeId, nodes);
    }

    private void removeNodeWithoutRefreshingDisplay(String id) {
        allNodesIDMap.remove(id);
        for (String otherId : getAllVertexIds()) {
            getVertexById(otherId).getConnectedVertices().remove(id);
        }
    }

    private TitledContentPane getDisplayNodeById(String nodeId) {
        return (TitledContentPane) allNodesIDMap.get(nodeId).getDisplayNode();
    }

    private TitledContentPane getPreparationNodeById(String id) {
        return (TitledContentPane) allNodesIDMap.get(id).getPreparationNode();
    }

    private Set<String> getAllVertexIds() {
        return allNodesIDMap.keySet();
    }

    private Vertex getVertexById(String id) {
        return allNodesIDMap.get(id).getVertex();
    }

    private List<String> geAllVerticesSortedByPriority() {
        List<String> vertices = new ArrayList<>(getAllVertexIds());
        vertices.sort(Comparator.comparingInt(o -> getVertexById(o).getPriority()));
        return vertices;
    }

    private Set<String> getAllVerticesAtDepth(int depth) {
        Set<String> verticesAtDepth = new HashSet<>();
        for (String id : getAllVertexIds()) {
            if (getVertexById(id).getDepth() == depth) verticesAtDepth.add(id);
        }
        return verticesAtDepth;
    }

    public ReadOnlyObjectWrapper<Vertex> getReadOnlyVertex(String id) {
        return allNodesIDMap.get(id).getVertex().readOnly();
    }

    public List<Vertex> getAllVertexData() {
        return allNodesIDMap.values().stream().map(DataAndNodes::getVertex).collect(Collectors.toList());
    }

}
