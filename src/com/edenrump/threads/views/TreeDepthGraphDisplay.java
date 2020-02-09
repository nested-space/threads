/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.threads.views;

import com.edenrump.toolkit.graph.DataAndNodes;
import com.edenrump.toolkit.graph.DepthDirection;
import com.edenrump.toolkit.graph.Graph;
import com.edenrump.toolkit.models.Vertex;
import com.edenrump.toolkit.ui.display.DepthGraphDisplay;
import javafx.collections.ObservableList;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.VerticalDirection;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import java.util.Collections;
import java.util.UUID;
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

    private DataAndNodes selectedRootNode = null;

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
        selectorFilter = vertex -> vertex.getDepth() == 0;
        addVisibilityFilter(selectorFilter);
    }

    /**
     * The selection filter to be applied whenever a layoutPass is requested
     */
    Predicate<Vertex> selectorFilter;

    @Override
    public void addMouseActions(String vertexId, MouseEvent event) {
        if (getAllNodesIDMap().get(vertexId).getVertex().getDepth() == 0) {
            selectedRootNode = getAllNodesIDMap().get(vertexId);

            removeVisibilityFilter(selectorFilter);
            selectorFilter = vertex -> Graph.unidirectionalFill(vertexId, DepthDirection.INCREASING_DEPTH, getAllVertexData())
                    .stream()
                    .map(Vertex::getId).collect(Collectors.toList()).contains(vertex.getId()) || vertex.getDepth() == 0;
            addVisibilityFilter(selectorFilter);

            vertexSelection.clearSelectedVertices();
            vertexSelection.addSelectedVertex(vertexId);

            updateDisplay();
            event.consume();
        }
    }

    @Override
    public ContextMenu defaultNodeContextMenu(String id) {
        final MenuItem title = new MenuItem("Node Options");

        Menu delMenu = new Menu("Delete");
        MenuItem delete = new MenuItem("Delete last selected");
        delete.setOnAction(event -> deleteVertexAndUpdateDisplay(id));
        MenuItem deleteAll = new MenuItem("Delete all");
        delete.setOnAction(event -> {
            for (String vertex : vertexSelection.getSelectedVertexIdsObservable()) {
                deleteVertexAndUpdateDisplay(vertex);
            }
        });
        delMenu.getItems().addAll(delete, deleteAll);

        Menu colorMenu = new Menu("Set color");
        Menu standardColors = new Menu("AZ Palette");
        Menu customColors = new Menu("Custom Color");

        MenuItem l = colorContextMenuItem("Lime", "#c4d600");
        MenuItem g = colorContextMenuItem("Gold", "#f0ab00");
        MenuItem lb = colorContextMenuItem("Light Blue", "#D1DBE3");
        MenuItem n = colorContextMenuItem("Navy", "#003865");
        MenuItem m = colorContextMenuItem("Mulberry", "#830051");
        MenuItem r = colorContextMenuItem("Red", "#EA3C53");
        MenuItem gr = colorContextMenuItem("Green", "#50C878");
        standardColors.getItems().addAll(m, n, l, g, lb, r, gr);

        ColorPicker cp = new ColorPicker();
        cp.setStyle("-fx-background-color: white;");
        final MenuItem colorPickerMenuItem = new MenuItem(null, cp);
        colorPickerMenuItem.setOnAction(event -> changeSelectedItemColors(cp.getValue().toString()));
        customColors.getItems().add(colorPickerMenuItem);

        colorMenu.getItems().addAll(standardColors, customColors);

        return new ContextMenu(title, delMenu, colorMenu);
    }

    private MenuItem colorContextMenuItem(String cName, String cValue) {
        MenuItem item = new MenuItem(cName);
        item.setId(cValue);
        item.setOnAction((e) -> changeSelectedItemColors(cValue));
        return item;
    }

    private void changeSelectedItemColors(String color) {
        ObservableList<String> vertices = vertexSelection.getSelectedVertexIdsObservable();
        for (String id : vertices) {
            Vertex v = getAllNodesIDMap().get(id).getVertex();
            v.overwriteProperty("color", color);
            updateVertexAndRefreshDisplay(id, v);
        }

    }

    @Override
    public ContextMenu singleSelectedVertexContextMenu(String id) {
        ContextMenu menu = defaultNodeContextMenu(id);
        MenuItem addMoreDepth = new MenuItem("Add Node Right ->");
        addMoreDepth.setOnAction(event -> {
            int depth = getAllNodesIDMap().get(id).getVertex().getDepth() + 1;
            addVertexToDisplay(new Vertex("New Node", UUID.randomUUID().toString(), Collections.singletonList(id),
                    depth,
                    graph.calculatePriority(depth, VerticalDirection.DOWN)));
        });
        Menu add = new Menu("Add");
        add.getItems().add(addMoreDepth);

        menu.getItems().add(add);
        return menu;
    }
}
