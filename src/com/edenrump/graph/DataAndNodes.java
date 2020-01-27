/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.graph;

import com.edenrump.models.VertexData;
import javafx.scene.Node;

/**
 * Class representing the information necessary to link the vertex data with its preparation node and its
 * living node in the scene graph.
 */
public class DataAndNodes {
    private VertexData vertexData;
    private Node preparationNode;

    public VertexData getVertexData() {
        return vertexData;
    }

    public Node getPreparationNode() {
        return preparationNode;
    }

    public Node getDisplayNode() {
        return displayNode;
    }

    private Node displayNode;

    public DataAndNodes(VertexData vd, Node p, Node d) {
        this.vertexData = vd;
        this.preparationNode = p;
        this.displayNode = d;
    }
}
