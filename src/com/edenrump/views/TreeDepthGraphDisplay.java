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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.edenrump.config.Defaults.DELAY_TIME;

/**
 * Class representing a display pane for a tree graph
 * <p>
 * It contains vertex and edge information necessary to display a graph
 * It contains separate preparation and display portions of the scene graph.
 * Changes to vertex position in the graph are animated
 * <p>
 * Animation is achieved by piggy-backing on JavaFX's own layout system.
 * It allows JavaFX to calculate layout bounds in the preparation area, and then animating
 * nodes in the display area to move from their current layout positions to the updated ones.
 * <p>
 * By contrast to the DepthGraphDisplay, this object has several independant features:
 * 1. "Root" status is assigned to all nodes that have a depth of zero
 * 2. A depth of less than zero is not allowed (these vertices will be ignored)
 * 3. Non-root nodes are dynamically loaded and unloaded from the scene graph based on which root node is selected
 * 4. Multiple selections of root nodes is not by default supported.
 */
public class TreeDepthGraphDisplay extends DepthGraphDisplay {

    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public TreeDepthGraphDisplay(ScrollPane display) {
        super(display, HorizontalDirection.RIGHT);
        preparationContainer.setAlignment(Pos.TOP_LEFT);
    }

    /**
     * Calculate the positions of vertices and re-create edges for the display based on data in the vertices list
     */
    @Override
    void recastDisplayFromCachedData() {
        clearNodes();

        Map<String, DataAndNodes> visibleNodes = new HashMap<>();
        idToNodeMap.keySet().stream()
//                .filter(id -> idToNodeMap.get(id).getVertexData().getDepth() == 0)
                .forEach(id -> visibleNodes.put(id, idToNodeMap.get(id)));

        visibleNodes.values().forEach(data -> displayOverlay.getChildren().add(data.getDisplayNode()));
        for (Node n : displayOverlay.getChildren()) n.setOpacity(0);

        resetPreparationDisplay(visibleNodes);
        addEdges(visibleNodes, 0);

        //Delay to allow layout cascade to happen, then load the displayOverlay with nodes
        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                for (String id : visibleNodes.keySet()) {
                    visibleNodes.get(id).getDisplayNode().setLayoutX(ltsX(visibleNodes.get(id).getPreparationNode()));
                    visibleNodes.get(id).getDisplayNode().setLayoutY(ltsY(visibleNodes.get(id).getPreparationNode()));
                }


                for (String title : idPrepDisplayLabelMap.keySet()) {
                    Node displayLabel = idPrepDisplayLabelMap.get(title).getValue();
                    Node prepLabel = idPrepDisplayLabelMap.get(title).getKey();
                    displayLabel.setLayoutX(ltsX(prepLabel));
                    displayLabel.setLayoutY(ltsY(prepLabel));
                }

                reconcilePrepAndDisplay(DELAY_TIME);
            });
            t.playFromStart();
        });

    }

    /**
     * Determine whether the secondary button has launched the event. If so, return.
     * <p>
     * Determine whether a root node has been clicked. If so, reload the display with the correct branches and leaves
     * <p>
     * If a branch or leaf has been selected, apply normal selection rules
     *
     * @param vertexId the vertex selected
     * @param event    the mouse-event that triggered the selection
     */
    @Override
    void handleSelection(String vertexId, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && selectedVertices.contains(vertexId)) {
            event.consume();
            return;
        }

        if (idToNodeMap.get(vertexId).getVertexData().getDepth() == 0) {
            toBeRemovedOnNextPass.clear();

            //remove all nodes that aren't a root node
            toBeRemovedOnNextPass = idToNodeMap.values().stream()
                    .filter(data -> data.getVertexData().getDepth() != 0)
                    .map(DataAndNodes::getDisplayNode).collect(Collectors.toList());

            //start the make visible list by adding all nodes connected to the selected root node
            List<String> makeVisibleIds = unidirectionalFill(vertexId, DepthDirection.INCREASING_DEPTH)
                    .stream()
                    .map(VertexData::getId)
                    .collect(Collectors.toList());

            //remove from toBeRemovedOnNextPass any node that will still be visible
            toBeRemovedOnNextPass.removeAll(makeVisibleIds
                    .stream()
                    .map(id -> idToNodeMap.get(id).getDisplayNode())
                    .collect(Collectors.toList()));

            //complete the make visible list by adding all root nodes
            makeVisibleIds.addAll(idToNodeMap.keySet()
                    .stream()
                    .filter(id -> idToNodeMap.get(id).getVertexData().getDepth() == 0)
                    .collect(Collectors.toList()));

            //create an id-DAN map from nodes to make visible
            Map<String, DataAndNodes> makeVisibleMap = idToNodeMap.entrySet()
                    .stream()
                    .filter(x -> makeVisibleIds.contains(x.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            resetPreparationDisplay(makeVisibleMap);

            selectedVertices.setAll(vertexId);
            resetHighlightingOnAllNodes();
            highlightSelectedNodes();

            Platform.runLater(() -> {
                PauseTransition pause = new PauseTransition(Duration.millis(50));
                pause.setOnFinished(e -> {
                    //clear display overlay and add only labels
                    displayOverlay.getChildren().clear();

                    for(Pair<Label, Label> labels : idPrepDisplayLabelMap.values()){
                        displayOverlay.getChildren().add(labels.getValue());
                        labels.getValue().setLayoutX(ltsX(labels.getKey()));
                        labels.getValue().setLayoutY(ltsY(labels.getKey()));
                        labels.getValue().setOpacity(1);
                    }
                    //add non-root nodes to the display
                    displayOverlay.getChildren().addAll(makeVisibleIds
                            .stream()
                            .distinct()
                            .map(id -> idToNodeMap.get(id).getDisplayNode())
                            .collect(Collectors.toList()));
                    //add edges too
                    addEdges(makeVisibleMap, 1);

                    makeVisibleIds.stream()
                            .filter(id -> idToNodeMap.get(id).getVertexData().getDepth() != 0)
                            .map(id -> idToNodeMap.get(id).getPreparationNode())
                            .forEach(node -> {
                                preparationDisplayMap.get(node).setLayoutX(ltsX(node));
                                preparationDisplayMap.get(node).setLayoutY(ltsY(node));
                            });

                    reconcilePrepAndDisplay(DELAY_TIME);
                });
                pause.play();
            });
            event.consume();
            return;
        }

        VertexData vertexClicked = idToNodeMap.get(vertexId).getVertexData();
        if (event.isShiftDown() && lastSelected != null) {
            List<VertexData> vertices = findShortestPath(lastSelected, vertexClicked,
                    idToNodeMap.values().stream()
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
}
