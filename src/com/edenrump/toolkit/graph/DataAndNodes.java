/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.graph;

import com.edenrump.toolkit.models.Vertex;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Class representing the information necessary to link the vertex data with its preparation node and its
 * living node in the scene graph.
 */
public class DataAndNodes {
    private Vertex vertex;
    private Region preparationNode;
    private Region displayNode;

    public DataAndNodes(Vertex vd, Region p, Region d) {
        this.vertex = vd;
        this.preparationNode = p;
        this.displayNode = d;
    }

    public Vertex getVertex() {
        return vertex;
    }

    public Region getPreparationNode() {
        return preparationNode;
    }

    public Region getDisplayNode() {
        return displayNode;
    }
}
