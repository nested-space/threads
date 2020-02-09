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
import com.edenrump.toolkit.ui.layout.DepthLayout;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.geometry.HorizontalDirection;
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

    private ScrollPane graphDisplay;
    private AnchorPane displayOverlay = new AnchorPane();

    private Map<String, Set<Shape>> vertexIdToEdgesMap = new HashMap<>();

    private Set<String> currentlyVisibleVerticesById = new HashSet<>();

    private Set<String> verticesToBeRemovedOnNextRefresh = new HashSet<>();


    private BooleanProperty hasUnsavedContent = new SimpleBooleanProperty(false);
    private HorizontalDirection plottingDirection;

    Set<Predicate<Vertex>> visibleNodesFilters = new HashSet<>(Collections.singleton(entry -> true));

    public DepthGraphDisplay(ScrollPane display, HorizontalDirection plottingDirection) {
        this.graphDisplay = display;

        depthLayout = new DepthLayout(plottingDirection);
        depthLayout.setStyleOnPreparationContainer();

        this.plottingDirection = plottingDirection;

        setStyleOnDisplayContainer();
        addMouseEventsToDisplayPane();
        wrapDisplayAndPreparationPanesInGraphDisplay();

        pauseAndThenRunMethod(Duration.seconds(2), this::setDisplayPaneToRefreshOnWindowResize); //pause is essential else stage is null
    }

    private void setDisplayPaneToRefreshOnWindowResize() {
        graphDisplay.heightProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisibleVerticesById).playFromStart());
        graphDisplay.widthProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisibleVerticesById).playFromStart());
    }

    private void setStyleOnDisplayContainer() {
        graphDisplay.setPannable(true);
        graphDisplay.setFitToHeight(true);
        graphDisplay.setFitToWidth(true);
    }

    private void pauseAndThenRunMethod(Duration delay, Runnable runnable) {
        PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(event -> runnable.run());
        pause.play();
    }

    private void wrapDisplayAndPreparationPanesInGraphDisplay() {
        graphDisplay.setContent(new StackPane(displayOverlay, depthLayout.getContainer()));
    }

    private void unselectAllNodes() {
        resetHighlightingOnAllNodes();
        vertexSelection.clearSelectedVertices();
        vertexSelection.setLastSelectedVertexId(null);
    }

    public void clearDisplay() {
        clearNodes();
        removeAllVertices();
    }

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

    public void show() {
        clearDisplayAndRecreateFromVertexData();
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
            graph.getVertexById(id).addConnection(vertex.getId());
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

        updateNode(depthLayout.getPreparationNodeById(id), vertex);
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

        depthLayout.layoutPreparationDisplay(currentlyVisibleVerticesById.stream().map(id -> graph.getVertexById(id)).collect(Collectors.toSet()));

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
            getDisplayNodeById(id).setLayoutX(ltsX(depthLayout.getPreparationNodeById(id)));
            getDisplayNodeById(id).setLayoutY(ltsY(depthLayout.getPreparationNodeById(id)));
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
        for (String id : graph.getAllVertexIds()) {
            Vertex vertex = graph.getVertexById(id);
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
        visibilityStatusOfVertices.shouldBeVisible = new HashSet<>(graph.getAllVertexIds());

        Predicate<Vertex> allPredicates = visibleNodesFilters.stream()
                .reduce(p -> true, Predicate::and);

        visibilityStatusOfVertices.shouldBeVisible = visibilityStatusOfVertices.shouldBeVisible.stream()
                .map(graph::getVertexById)
                .filter(allPredicates)
                .map(Vertex::getId)
                .collect(Collectors.toSet());

        //nodes that should be visible but aren't
        visibilityStatusOfVertices.verticesToAppear = new HashSet<>(visibilityStatusOfVertices.shouldBeVisible);
        visibilityStatusOfVertices.verticesToAppear.removeAll(currentlyVisibleVerticesById);

        //iterate through nodes that are determined to be appearing and add a node for each.
        // NB: createEdge() curates maps but does not add them to the display - this is done separately.
        for (String appearing : visibilityStatusOfVertices.verticesToAppear) {
            for (String otherVertexId : graph.getVertexById(appearing).getConnectedVertices()) {
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
            TitledContentPane prep = depthLayout.getPreparationNodeById(id);
            disp.setMaxHeight(prep.getHeight());
            movementTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(disp.layoutXProperty(), disp.getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(disp.layoutYProperty(), disp.getLayoutY())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(disp.layoutXProperty(), Math.max(ltsX(prep), 0))),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(disp.layoutYProperty(), Math.max(ltsY(prep), 0))));
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
        vertexSelection.clearSelectedVertices();
        resetHighlightingOnAllNodes();
    }

    /**
     * Select all nodes
     */
    public void selectAll() {
        vertexSelection.setAllSelectedVertexIds(graph.getAllVertexIds());
        highlightSelectedNodes();
        lowlightUnselectedNodes();
    }

    private DataAndNodes generateNodes_LinkToData(Vertex data) {
        TitledContentPane prepNode = createTitledContentPaneFromVertex(data);
        TitledContentPane displayNode = createTitledContentPaneFromVertex(data);

        displayNode.setLayoutX(ltsX(prepNode));
        displayNode.setLayoutY(ltsY(prepNode));
        displayNode.setOnContextMenuRequested(event -> {
            showContextMenu(displayNode, defaultNodeContextMenu(data.getId()), event);
            event.consume();
        });
        displayNode.setOnMouseClicked(event -> handleSelection(data.getId(), event));

        return new DataAndNodes(data, prepNode, displayNode);
    }

    private void clearNodes() {
        depthLayout.removeNodesFromDisplay();
        displayOverlay.getChildren().clear();
    }

    private boolean preventDefaultHighlight = false;

    private void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && vertexSelection.isVertexSelected(vertexId)) {
            event.consume();
            return;
        }

        boolean preventAdditionalActions = false;

        Vertex vertexClicked = graph.getVertexById(vertexId);
        if (event.isShiftDown() && vertexSelection.getLastSelectedVertexId() != null) {
            List<Vertex> vertices = Graph.findShortestPath(graph.getVertexById(vertexId), vertexClicked, graph.getAllVertexData());
            vertexSelection.setAllSelectedVertexIds(vertices.stream().map(Vertex::getId).collect(Collectors.toList()));
            preventAdditionalActions = true;
        } else if (event.isControlDown()) {
            if (!vertexSelection.isVertexSelected(vertexClicked.getId())) {
                vertexSelection.addSelectedVertex(vertexClicked.getId());
                vertexSelection.setLastSelectedVertexId(vertexId);
            } else {
                vertexSelection.removeSelectedVertexId(vertexId);
            }
            preventAdditionalActions = true;
        } else {
            vertexSelection.setAllSelectedVertexIds(Collections.singletonList(vertexId));
            vertexSelection.setLastSelectedVertexId(vertexId);
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
        vertexSelection.getSelectedVertexIdsObservable().stream()
                .map(this::getDisplayNodeById)
                .forEach(pane -> {
                    pane.highlightOne();
                    if (vertexSelection.getSelectedVertexIdsObservable().size() == 1) {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, singleSelectedVertexContextMenu(pane.getId()), e));
                    } else {
                        pane.setOnContextMenuRequested(e -> showContextMenu(pane, defaultNodeContextMenu(pane.getId()), e));
                    }
                });

        if (vertexSelection.getLastSelectedVertexId() != null) {
            TitledContentPane last = getDisplayNodeById(vertexSelection.getLastSelectedVertexId());
            last.highlightTwo();
        }
    }

    private void lowlightUnselectedNodes() {

        for (String id : graph.getAllVertexIds()) {
            if (vertexSelection.isVertexSelected(id)) {
                TitledContentPane disp = getDisplayNodeById(id);
                disp.lowlight();
                disp.setOnContextMenuRequested(null);
            }
        }
    }

    private TitledContentPane createTitledContentPaneFromVertex(Vertex vertex) {
        TitledContentPane node = new TitledContentPane();
        node.setId(vertex.getId());
        node.addHeaderBox(vertex.getName(), vertex.getId(), Color.web("#D1DBE3"));
        if (vertex.hasProperty("url")) node.addTag("url", vertex.getProperty("url"));
        return node;
    }

    private ContextMenu currentlyShown = new ContextMenu();

    private void showContextMenu(Pane pane, ContextMenu c, ContextMenuEvent e) {
        currentlyShown.hide();
        currentlyShown = c;
        currentlyShown.show(pane, e.getScreenX(), e.getScreenY());

    }

    public ContextMenu defaultNodeContextMenu(String id) {
        return new ContextMenu();
    }

    public ContextMenu singleSelectedVertexContextMenu(String id) {
        return new ContextMenu();
    }

    private void updateNode(TitledContentPane node, Vertex v) {
        node.setTitle(v.getName());
        if (v.hasProperty("url")) {
            if (!node.hasTag("url")) {
                node.addTag("url", v.getProperty("url"));
            } else {
                node.updateTag("url", v.getProperty("url"));
            }
        } else {
            if (node.hasTag("url")) node.removeTag("url");
        }
    }

    private Double ltsX(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinX() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinX();
    }

    private Double ltsY(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinY() - displayOverlay.localToScene(displayOverlay.getBoundsInLocal()).getMinY();
    }

    public ObservableList<String> getSelectedVertexIdsObservable() {
        return vertexSelection.getSelectedVertexIdsObservable();
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
    }

    /* ***************************************************************************************************************
     *                                                  GRAPH METHODS
     *      //TODO: rewire this -- I don't particularly like having pass-through methods just to facilitate access
     ***************************************************************************************************************** */

    protected Graph graph = new Graph();

    public ReadOnlyObjectWrapper<Vertex> getReadOnlyVertex(String id) {
        return graph.getReadOnlyVertex(id);
    }

    public List<Vertex> getAllVertexData() {
        return graph.getAllVertexData();
    }

    /* ***************************************************************************************************************
     *                                          PREPARATION CONTAINER METHODS
     ***************************************************************************************************************** */

    DepthLayout depthLayout;

    /* ***************************************************************************************************************
     *                                                  SELECTION METHODS
     ***************************************************************************************************************** */

    protected VertexSelection vertexSelection = new VertexSelection();

    /* ***************************************************************************************************************
     *                                                  DISPLAY METHODS
     ***************************************************************************************************************** */



    private Map<String, TitledContentPane> displayNodesById = new HashMap<>();

    private void addMouseEventsToDisplayPane() {
        displayOverlay.setOnMouseClicked(event -> {
            unselectAllNodes();
        });
    }


    private void resetHighlightingOnAllNodes() {
        for (String id : graph.getAllVertexIds()) {
            TitledContentPane displayNode = getDisplayNodeById(id);
            displayNode.resetHighlighting();
        }
    }

    private Shape createEdge(String vertex1, String vertex2) {
        boolean v2_deeper_v1 = graph.getVertexById(vertex2).getDepth() > graph.getVertexById(vertex1).getDepth();

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

    private TitledContentPane getDisplayNodeById(String vertexId) {
        return displayNodesById.get(vertexId);
    }


    public Collection<TitledContentPane> getAllDisplayNodes() {
        return displayNodesById.values();
    }

    private void removeAllDisplayNodes() {
        displayNodesById.clear();
    }


    private void addDisplayNode(String vertexId, TitledContentPane node) {
        displayNodesById.put(vertexId, node);
    }

    private TitledContentPane getDisplayNodeByIdNew(String vertexId) {
        return displayNodesById.get(vertexId);
    }

    private void linkVertexToEdge(String vertex, Shape edge) {
        vertexIdToEdgesMap.computeIfAbsent(vertex, k -> new HashSet<>());
        vertexIdToEdgesMap.get(vertex).add(edge);
    }


    public WritableImage getSnapShot() {
        return displayOverlay.snapshot(new SnapshotParameters(), null);
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

    /* ***************************************************************************************************************
     *
     *
     *                                                  MAP METHODS
     *
     *
     ***************************************************************************************************************** */

    public Map<String, DataAndNodes> getAllNodesIDMap() {
        Map<String, DataAndNodes> temp = new HashMap<>();
        for (String id : graph.getAllVertexIds()) {
            temp.put(id, new DataAndNodes(graph.getVertexById(id), depthLayout.getPreparationNodeById(id), displayNodesById.get(id)));
        }
        return temp;
    }

    private void removeAllVertices() {
        depthLayout.removeNodesFromDisplay();
        graph.clearAll();
        removeAllDisplayNodes();
    }


    private void addNodeWithoutRefreshingDisplay(String nodeId, DataAndNodes nodes) {
        graph.addVertex(nodes.getVertex());
        depthLayout.addNode(nodeId, (TitledContentPane) nodes.getPreparationNode());
        displayNodesById.put(nodeId, (TitledContentPane) nodes.getDisplayNode());
    }

    private void removeNodeWithoutRefreshingDisplay(String id) {
        depthLayout.removeVertexById(id);
        graph.removeVertex(id);
    }
}
