/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.views;

import javafx.geometry.HorizontalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class representing a display pane for a tree graph
 *
 * It contains vertex and edge information necessary to display a graph
 * It contains separate preparation and display portions of the scene graph.
 * Changes to vertex position in the graph are animated
 *
 * Animation is achieved by piggy-backing on JavaFX's own layout system.
 * It allows JavaFX to calculate layout bounds in the preparation area, and then animating
 * nodes in the display area to move from their current layout positions to the updated ones.
 *
 * By contrast to the DepthGraphDisplay, this object has several independant features:
 *      1. It provides functionality to assign "root" status to all nodes that have a depth of zero
 *      2. A depth of less than zero is not allowed (these vertices will be ignored)
 *      3. Non-root nodes are dynamically loaded and unloaded from the scene graph based on which root node is selected
 *      4. Multiple selections of root nodes is not by default supported.
 *
 */
public class TreeDepthGraphDisplay extends DepthGraphDisplay{

    /**
     * Create a new Process Display
     *
     * @param display the pane on which the processdisplay should be rendered
     */
    public TreeDepthGraphDisplay(ScrollPane display) {
        super(display, HorizontalDirection.RIGHT);
    }


}
