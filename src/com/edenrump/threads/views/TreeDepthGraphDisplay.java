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
import com.edenrump.toolkit.models.VertexData;
import com.edenrump.toolkit.ui.DepthGraphDisplay;
import javafx.collections.ObservableList;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.geometry.VerticalDirection;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
        addVisibilityFilter(entry -> entry.getVertexData().getDepth() == 0);
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
    public void addMouseActions(String vertexId, MouseEvent event) {
        if (getAllNodesIDMap().get(vertexId).getVertexData().getDepth() == 0) {
            selectedRootNode = getAllNodesIDMap().get(vertexId);
            clearVisibilityFilters();
            selectorFilter = entry -> Graph.unidirectionalFill(vertexId, DepthDirection.INCREASING_DEPTH, getAllNodesIDMap())
                    .stream()
                    .map(VertexData::getId).collect(Collectors.toList()).contains(entry.getVertexData().getId()) || entry.getVertexData().getDepth() == 0;
            addVisibilityFilter(selectorFilter);
            clearSelectedVertices();
            addSelectedVertex(vertexId);
            updateDisplay();
            event.consume();
        } else {
            selectorFilter = entry -> true;
        }
    }

    /**
     * Utility method to create a consistent column in the preparation display
     *
     * @param nodeIds the nodes to be added to the column
     * @param title   the title of the column
     * @return a consistently-styled VBox for use in the preparation display.
     */
    @Override
    public VBox createPrepColumn(List<String> nodeIds, Integer title) {
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);

        boolean notRootColumn = getAllNodesIDMap().get(nodeIds.get(0)).getVertexData().getDepth() != 0;

        if (selectedRootNode != null && notRootColumn) {
            List<DataAndNodes> rootNodes = getAllNodesIDMap().values().stream()
                    .filter(data -> data.getVertexData().getDepth() == 0)
                    .sorted(Comparator.comparingInt(o -> o.getVertexData().getPriority()))
                    .collect(Collectors.toList());
            int index = rootNodes.indexOf(selectedRootNode);
            if (index > (0.65 * rootNodes.size())) {
                body.setAlignment(Pos.BOTTOM_CENTER);
            } else if (index < 0.35 * rootNodes.size()) {
                body.setAlignment(Pos.TOP_CENTER);
            } else {
                body.setAlignment(Pos.CENTER);
            }
        }

        Comparator<VertexData> sortPriority = Comparator.comparingDouble(VertexData::getPriority);
        getAllNodesIDMap().keySet().stream()
                .filter(nodeIds::contains)
                .map(id -> getAllNodesIDMap().get(id).getVertexData())
                .sorted(sortPriority)
                .forEachOrdered(data -> body.getChildren().add(getAllNodesIDMap().get(data.getId()).getPreparationNode()));

        return body;
    }

    /**
     * Simplest context menu associated with a vertex
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    @Override
    public ContextMenu multipleSelectionContextMenu(String id) {
        final MenuItem title = new MenuItem("Node Options");

        Menu delMenu = new Menu("Delete");
        MenuItem delete = new MenuItem("Delete last selected");
        delete.setOnAction(event -> deleteVertex(id));
        MenuItem deleteAll = new MenuItem("Delete all");
        delete.setOnAction(event -> {
            for (String vertex : getSelectedVerticesObservableList()) {
                deleteVertex(vertex);
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
        ObservableList<String> vertices = getSelectedVerticesObservableList();
        for (String id : vertices) {
            VertexData v = getAllNodesIDMap().get(id).getVertexData();
            v.overwriteProperty("color", color);
            updateVertex(id, v);
        }

    }

    /**
     * Create a context menu associated with a vertex where only a single vertex has been selected
     *
     * @param id the id of the vertex
     * @return the context menu
     */
    @Override
    public ContextMenu singleSelectedVertexContextMenu(String id) {
        ContextMenu menu = multipleSelectionContextMenu(id);
        MenuItem addMoreDepth = new MenuItem("Add Node Right ->");
        addMoreDepth.setOnAction(event -> {
            int depth = getAllNodesIDMap().get(id).getVertexData().getDepth() + 1;
            createVertex(new VertexData("New Node", UUID.randomUUID().toString(), Collections.singletonList(id),
                    depth,
                    calculatePriority(depth, VerticalDirection.DOWN)));
        });
        Menu add = new Menu("Add");
        add.getItems().add(addMoreDepth);

        menu.getItems().add(add);
        return menu;
    }
}
