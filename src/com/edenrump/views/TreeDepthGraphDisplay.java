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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

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
        getIdToNodeMap().keySet().stream()
                .filter(id -> getIdToNodeMap().get(id).getVertexData().getDepth() == 0)
                .forEach(id -> visibleNodes.put(id, getIdToNodeMap().get(id)));

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

                for (String title : getIdPrepDisplayLabelMap().keySet()) {
                    Node displayLabel = getIdPrepDisplayLabelMap().get(title).getValue();
                    Node prepLabel = getIdPrepDisplayLabelMap().get(title).getKey();
                    displayLabel.setLayoutX(prepLabel.getLayoutX());
                    displayLabel.setLayoutY(prepLabel.getLayoutY());
                }

                reconcilePrepAndDisplay(DELAY_TIME);
            });
            t.playFromStart();
        });

    }
}
