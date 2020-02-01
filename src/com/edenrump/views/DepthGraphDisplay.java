/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.views;

import com.edenrump.graph.DataAndNodes;
import com.edenrump.graph.Graph;
import com.edenrump.models.VertexData;
import com.edenrump.ui.nodes.TitledContentPane;
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
import javafx.scene.shape.Shape;
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
     * A map that contains all the edges that are connected to each node.
     */
    private Map<DataAndNodes, Set<Shape>> displayNodeToEdgesMap = new HashMap<>();
    /**
     * A map of IDs to preparation and display nodes for all nodes in memory
     */
    Map<String, DataAndNodes> allNodesIDMap = new HashMap<>();
    private Set<DataAndNodes> currentlyVisible = new HashSet<>();
    /**
     * A list of nodes that are being prepared for removal from the display pane on the next pass
     */
    private Set<DataAndNodes> toBeRemoved = new HashSet<>();

    /**
     * A list of the currently-selected vertices in the process display window
     */
    ObservableList<String> selectedVertices = FXCollections.observableArrayList();

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
                linkMapDisplay.heightProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisible).playFromStart());
                linkMapDisplay.widthProperty().addListener((obs, o, n) -> reconcilePrepAndDisplay(currentlyVisible).playFromStart());
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
        preparationContainer.setOpacity(0);
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
    }

    /**
     * Create a display that shows all the vertices
     *
     * @param vertices the vertices to show in the display
     */
    public void spawnNewDisplay(List<VertexData> vertices) {
        vertices.stream()
                .map(VertexData::getDepth)
                .distinct()
                .forEach(depth -> vertices.stream()
                        .filter(v -> v.getDepth() == depth)
                        .forEach(vertexData -> {
                            DataAndNodes nodeData = generateNodes_LinkToData(vertexData);
                            allNodesIDMap.put(vertexData.getId(), nodeData);
                        }));
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

    /**
     * Create a new preparation and display node set from the given vertex, add these to the node map, and add the
     * vertex to the display, if visible
     * <p>
     * Flag that the display has unsaved content.
     *
     * @param data the vertex to be added
     */
    public void createVertex(VertexData data) {
        //curating saved
        hasUnsavedContent.set(true);

        //curating maps
        DataAndNodes newNodeData = generateNodes_LinkToData(data);
        allNodesIDMap.put(data.getId(), newNodeData);
        data.getConnectedVertices().forEach(id -> {
            //respect symmetrical connections
            allNodesIDMap.get(id).getVertexData().addConnection(data.getId());
        });

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

        //prepare objects to be removed
        toBeRemoved.add(toRemove);

        //curate maps
        allNodesIDMap.remove(id);
        allNodesIDMap.values().stream()
                .map(DataAndNodes::getVertexData)
                .forEach(vertex -> vertex.getConnectedVertices().remove(id));
        //curate user interface
        selectedVertices.clear();

        requestLayoutPass();
        resetHighlightingOnAllNodes();
    }

    /**
     * Calculate the positions of vertices and re-spawnNewDisplay edges for the display based on data in the vertices list
     */
    private void recastDisplayFromCachedData() {
        clearNodes();
        requestLayoutPass();
    }

    /**
     * Determine which nodes should be visible based on the node visibility filters applied to the display
     * <p>
     * Re-determine the position of visible nodes in the preparation display. Wait for 100 milliseconds to allow
     * a layout pass through the JavaFX Scene Graph to pass.
     * <p>
     * Fade in nodes that should be visible, fade out those that should not be visible. Move display nodes to their
     * correct locations.
     */
    void requestLayoutPass() {
        NodeStatus status = assessNodeVisibility();
        layoutPreparationDisplay(currentlyVisible);

        for (DataAndNodes data : status.appear) {
            data.getDisplayNode().setOpacity(0);
        }

        PauseTransition wait = new PauseTransition(Duration.millis(250));
        wait.setOnFinished((e) -> {
            //TIMELINE 1 fade out nodes and then remove them from the display
            Timeline fadeOut = fadeOutNodes(status.disappear);
            Timeline fadeEdges = fadeOutEdges(status.edgesToRemove);
            fadeOut.getKeyFrames().addAll(fadeEdges.getKeyFrames());

            fadeOut.setOnFinished((removeNodes) -> {
                System.out.println(status.disappear.size());
                for (DataAndNodes data : status.disappear) {
                    displayOverlay.getChildren().remove(data.getDisplayNode());
                }
                for (Node edge : status.edgesToRemove) {
                    displayOverlay.getChildren().remove(edge);
                }
            });

            //TIMELINE 2 determine nodes that are visible RIGHT NOW and move them to their correct locations
            Set<DataAndNodes> visible = new HashSet<>(status.shouldBeVisible);
            visible.removeAll(status.appear);
            Timeline moveNodes = reconcilePrepAndDisplay(visible);

            //TIMELINE 3 add nodes to display, move them to the correct location and then fade in
            Set<Node> appearing = new HashSet<>();
            for (DataAndNodes data : status.appear) {
                displayOverlay.getChildren().add(data.getDisplayNode());
                data.getDisplayNode().setLayoutX(ltsX(data.getPreparationNode()));
                data.getDisplayNode().setLayoutY(ltsY(data.getPreparationNode()));
                appearing.add(data.getDisplayNode());
            }
            Timeline appear = fadeInDesiredNodes(appearing);
            Timeline fadeInEdges = fadeInEdges(status.edgesToAdd);

            displayOverlay.getChildren().addAll(status.edgesToAdd);
            appear.getKeyFrames().addAll(fadeInEdges.getKeyFrames());

            fadeOut.play();

            moveNodes.setOnFinished((move_event) -> {
                appear.play();
                //after a suitable delay, recalculate the heights and positions again
                PauseTransition recalculateHeights = new PauseTransition(Duration.seconds(0.5));
                recalculateHeights.setOnFinished((wait_event) -> {
                    reconcilePrepAndDisplay(currentlyVisible);
                });
                recalculateHeights.play();
                move_event.consume();
            });
            moveNodes.play();
        });
        wait.play();
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
    private NodeStatus assessNodeVisibility() {
        NodeStatus status = new NodeStatus();
        //find all nodes that should be visible
        status.shouldBeVisible = new HashSet<>(allNodesIDMap.values());
        for (Predicate<? super DataAndNodes> filter : visibleNodesFilters) {
            status.shouldBeVisible = status.shouldBeVisible.stream().filter(filter).collect(Collectors.toSet());
        }

        //nodes that should be visible but aren't
        status.appear = new HashSet<>(status.shouldBeVisible);
        status.appear.removeAll(currentlyVisible);

        //iterate through nodes that are determined to be appearing and add a node for each.
        // NB: createEdge() curates maps but does not add them to the display - this is done separately.
        for (DataAndNodes appearing : status.appear) {
            for (String otherVertexId : appearing.getVertexData().getConnectedVertices()) {
                if (status.shouldBeVisible.stream().map(data -> data.getVertexData().getId()).collect(Collectors.toList()).contains(otherVertexId)) {
                    status.edgesToAdd.add(createEdge(allNodesIDMap.get(otherVertexId), appearing));
                }
            }
        }

        //nodes that shouldn't be visible, but are
        for (DataAndNodes data : toBeRemoved) {
            if (currentlyVisible.contains(data)) status.disappear.add(data);
        }
        for (DataAndNodes data : currentlyVisible) {
            if (!status.shouldBeVisible.contains(data)) status.disappear.add(data);
        }

        //from status.disappear, determine connected edges and add them to edgesToRemove
        List<Set<Shape>> edgeLists = displayNodeToEdgesMap.entrySet()
                .stream()
                .filter(entry -> status.disappear.contains(entry.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        for (Set<Shape> edgeList : edgeLists) {
            status.edgesToRemove.addAll(edgeList);
        }

        currentlyVisible = status.shouldBeVisible;
        toBeRemoved.clear();

        return status;
    }

    /**
     * Create a timeline that fades out the nodes that should not be visible in the display
     *
     * @param disappear a set of nodes which should be faded out
     * @return a timeline that fades out the nodes that should not be visible in the display
     */
    private Timeline fadeOutNodes(Set<DataAndNodes> disappear) {
        Timeline fadeOut = new Timeline(30);
        for (DataAndNodes data : disappear) {
            fadeOut.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(data.getDisplayNode().opacityProperty(), data.getDisplayNode().getOpacity())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(data.getDisplayNode().opacityProperty(), 0)));
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
    private Timeline reconcilePrepAndDisplay(Set<DataAndNodes> nodes) {
        Timeline movementTimeline = new Timeline(30);
        for (DataAndNodes data : nodes) {
            Region disp = (Region) data.getDisplayNode();
            Region prep = (Region) data.getPreparationNode();
            disp.setMaxHeight(prep.getHeight());
            movementTimeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(0), new KeyValue(data.getDisplayNode().layoutXProperty(), data.getDisplayNode().getLayoutX())),
                    new KeyFrame(Duration.millis(0), new KeyValue(data.getDisplayNode().layoutYProperty(), data.getDisplayNode().getLayoutY())),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(data.getDisplayNode().layoutXProperty(), Math.max(ltsX(data.getPreparationNode()), 0))),
                    new KeyFrame(Duration.millis(ANIMATION_LENGTH), new KeyValue(data.getDisplayNode().layoutYProperty(), Math.max(ltsY(data.getPreparationNode()), 0))));
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
        if (data.hasProperty("url")) prepNode.addTag("url", data.getProperty("url"));

        //Create node for display overlay
        TitledContentPane displayNode = convertDataToNode(data);
        if (data.hasProperty("url")) prepNode.addTag("url", data.getProperty("url"));

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

    boolean preventDefaultHighlight = false;

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
    void addMouseActions(String vertexId, MouseEvent event) {

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
                    calculatePriority(depth, VerticalDirection.DOWN)));
        });

        MenuItem addMoreDepth = new MenuItem(plottingDirection == HorizontalDirection.RIGHT ? "Add Node Right ->" : "<- Add Node Left");
        addMoreDepth.setOnAction(event -> {
            int depth = allNodesIDMap.get(id).getVertexData().getDepth() + 1;
            createVertex(new VertexData("New Node", UUID.randomUUID().toString(), Collections.singletonList(id),
                    depth,
                    calculatePriority(depth, VerticalDirection.DOWN)));
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
    private Shape createEdge(DataAndNodes vertex1, DataAndNodes vertex2) {
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
    private void linkVertexToEdge(DataAndNodes vertex, Shape edge) {
        displayNodeToEdgesMap.computeIfAbsent(vertex, k -> new HashSet<>());
        displayNodeToEdgesMap.get(vertex).add(edge);
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
    }

    /**
     * Utility method to create a consistent column in the preparation display
     *
     * @param nodeIds the nodes to be added to the column
     * @param title   the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    VBox createPrepColumn(List<String> nodeIds, Integer title) {
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);

        Comparator<VertexData> sortPriority = Comparator.comparingDouble(VertexData::getPriority);
        allNodesIDMap.keySet().stream()
                .filter(nodeIds::contains)
                .map(id -> allNodesIDMap.get(id).getVertexData())
                .sorted(sortPriority)
                .forEachOrdered(data -> body.getChildren().add(allNodesIDMap.get(data.getId()).getPreparationNode()));

        return body;
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
        Set<DataAndNodes> shouldBeVisible = new HashSet<>();
        Set<DataAndNodes> appear = new HashSet<>();
        Set<DataAndNodes> disappear = new HashSet<>();
        Set<Shape> edgesToRemove = new HashSet<>();
        Set<Shape> edgesToAdd = new HashSet<>();

        NodeStatus() {
        }

        NodeStatus(Set<DataAndNodes> current) {
            shouldBeVisible = new HashSet<>(current);
        }
    }
}
