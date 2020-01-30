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
import com.edenrump.graph.DepthDirection;
import com.edenrump.graph.Graph;
import com.edenrump.models.VertexData;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
     * The plotting direction for the TreeDepthGraphDisplay (root nodes are displayed on the left)
     */
    private static final HorizontalDirection PLOTTING_DIRECTION = HorizontalDirection.RIGHT;

    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public TreeDepthGraphDisplay(ScrollPane display) {
        super(display, PLOTTING_DIRECTION);
        visibleNodesFilters.add(entry -> entry.getVertexData().getDepth() == 0);
        preparationContainer.setAlignment(Pos.TOP_LEFT);
    }

    /**
     * The selection filter to be applied whenever a layoutPass is requested
     */
    Predicate<? super DataAndNodes> selectorFilter;

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
    void addMouseActions(String vertexId, MouseEvent event) {
        if (allNodesIDMap.get(vertexId).getVertexData().getDepth() == 0) {
            visibleNodesFilters.clear();
            selectorFilter = entry -> Graph.unidirectionalFill(vertexId, DepthDirection.INCREASING_DEPTH, allNodesIDMap)
                    .stream()
                    .map(VertexData::getId).collect(Collectors.toList()).contains(entry.getVertexData().getId()) || entry.getVertexData().getDepth() == 0;
            visibleNodesFilters.add(selectorFilter);
            resetHighlightingOnAllNodes();
            requestLayoutPass();
        } else {
            selectorFilter = entry -> true;
        }
    }
}
