/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.layout;

import com.edenrump.toolkit.models.Vertex;
import com.edenrump.toolkit.ui.components.TitledContentPane;
import com.edenrump.toolkit.ui.contracts.DisplaysGraph;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

public class DepthLayout implements DisplaysGraph {

    private HBox preparationContainer = new HBox();

    Map<String, Region> nodesById;

    @Override
    public void addVertex(Vertex vertex) {
        //TODO: add vertex do display
    }

    @Override
    public void removeVertex(Vertex vertex) {
        removeVertexById(vertex.getId());
    }

    @Override
    public void removeVertexById(String id) {
        nodesById.remove(id);
    }

    HorizontalDirection plottingDirection;

    public DepthLayout(HorizontalDirection plottingDirection){
        nodesById = new HashMap<>();
        this.plottingDirection = plottingDirection;
    }

    public void setStyleOnPreparationContainer() {
        preparationContainer.setAlignment(Pos.TOP_LEFT);
        preparationContainer.setPadding(new Insets(25, 25, 25, 35));
        preparationContainer.setSpacing(125);
        preparationContainer.setOpacity(0);
        preparationContainer.setMouseTransparent(true);
    }

    public void removeNodesFromDisplay() {
        preparationContainer.getChildren().clear();
    }

    private void removeVertex(String vertexId) {
        nodesById.remove(vertexId);
    }

    public TitledContentPane getPreparationNodeById(String id) {
        return (TitledContentPane) nodesById.get(id);
    }

    public void layoutPreparationDisplay(Set<Vertex> vertices) {
        preparationContainer.getChildren().clear();
        vertices.stream()
                .map(Vertex::getDepth)
                .distinct()
                .sorted(plottingDirection == HorizontalDirection.LEFT ? Comparator.reverseOrder() : Comparator.naturalOrder())
                .collect(Collectors.toList())
                .forEach(depth -> {
                    List<Vertex> prepNodes = new ArrayList<>();
                    vertices.stream()
                            .filter(vertex -> vertex.getDepth() == depth)
                            .forEach(prepNodes::add);
                    preparationContainer.getChildren().add(createPrepColumn(prepNodes));
                });
    }

    private VBox createPrepColumn(List<Vertex> vertices) {
        VBox body = new VBox();
        body.setSpacing(35);
        body.setAlignment(Pos.TOP_CENTER);

        vertices.stream()
                .sorted(Comparator.comparingInt(Vertex::getPriority))
                .forEach(vertex -> {
                    body.getChildren().add(getPreparationNodeById(vertex.getId()));
                });

        return body;
    }

    public void addNode(String vertexId, TitledContentPane preparationNode) {
        preparationContainer.getChildren().add(preparationNode);
        nodesById.put(vertexId, preparationNode);
    }

    public Node getContainer() {
        return preparationContainer;
    }
}

