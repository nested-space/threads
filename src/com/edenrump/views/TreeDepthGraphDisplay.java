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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.ArrayList;
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
 * 1. It provides functionality to assign "root" status to all nodes that have a depth of zero
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
                .filter(id -> idToNodeMap.get(id).getVertexData().getDepth() == 0)
                .forEach(id -> visibleNodes.put(id, idToNodeMap.get(id)));

        visibleNodes.values().forEach(data -> displayOverlay.getChildren().add(data.getDisplayNode()));
        for (Node n : displayOverlay.getChildren()) n.setOpacity(0);

        resetPreparationDisplay(visibleNodes);
        addEdges(visibleNodes);

        //Delay to allow layout cascade to happen, then load the displayOverlay with nodes
        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
            t.setOnFinished(actionEvent -> {
                for (String id : visibleNodes.keySet()) {
                    visibleNodes.get(id).getDisplayNode().setLayoutX(visibleNodes.get(id).getPreparationNode().getLayoutX());
                    visibleNodes.get(id).getDisplayNode().setLayoutY(visibleNodes.get(id).getPreparationNode().getLayoutY() + 50);
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
            List<String> makeVisible = unidirectionalFill(vertexId, DepthDirection.INCREASING_DEPTH)
                    .stream().map(data -> idToNodeMap.get(data.getId()).getVertexData().getId()).collect(Collectors.toList());

            makeVisible.addAll(idToNodeMap.keySet().stream()
                    .filter(id -> idToNodeMap.get(id).getVertexData().getDepth()==0).collect(Collectors.toList()));

            List<DataAndNodes> toRemove = idToNodeMap.keySet().stream()
                    .filter(id -> !makeVisible.contains(id))
                    .filter(id -> idToNodeMap.get(id).getVertexData().getDepth() != 0)
                    .map(id -> idToNodeMap.get(id))
                    .collect(Collectors.toList());

            toRemove.forEach(dataAndNodes -> preparationDisplayMap.remove(dataAndNodes.getPreparationNode()));

            toBeRemovedOnNextPass.addAll(toRemove.stream().map(DataAndNodes::getDisplayNode).collect(Collectors.toList()));
            toRemove.stream()
                    .filter(dataAndNodes -> vertexToEdgesMap.containsKey(dataAndNodes.getDisplayNode()))
                    .forEach(dataAndNodes -> toBeRemovedOnNextPass.addAll(vertexToEdgesMap.get(dataAndNodes.getDisplayNode())));
            toBeRemovedOnNextPass = toBeRemovedOnNextPass.stream().distinct().collect(Collectors.toList()); //remove duplicate edges

            selectedVertices.setAll(vertexId);
            resetHighlightingOnAllNodes();

            makeVisible.stream()
                    .filter(id -> !preparationDisplayMap.containsValue(idToNodeMap.get(id).getDisplayNode()))
                    .forEach(id -> idToNodeMap.get(id).getDisplayNode().setOpacity(0));

            Map<String, DataAndNodes> collect = idToNodeMap.entrySet().stream()
                    .filter(x -> makeVisible.contains(x.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            resetPreparationDisplay(collect);

            collect.values().forEach(dataAndNodes -> {
                        if (!displayOverlay.getChildren().contains(dataAndNodes.getDisplayNode()))
                            displayOverlay.getChildren().add(dataAndNodes.getDisplayNode());
                    }
            );

            Platform.runLater(() -> {
                PauseTransition pause = new PauseTransition(Duration.millis(Defaults.DELAY_TIME));
                pause.setOnFinished(e -> reconcilePrepAndDisplay(DELAY_TIME));
                pause.play();
            });
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

        event.consume();

    }
}
