/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.display;

import com.edenrump.toolkit.models.Vertex;
import com.edenrump.toolkit.ui.contracts.DisplaysGraph;

import javax.swing.plaf.synth.Region;
import java.util.HashMap;
import java.util.Map;

public class GraphDisplay implements DisplaysGraph {

    Map<String, Region> nodes;

    GraphDisplay() {
        nodes = new HashMap<>();
    }

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
        //TODO: remove vertex from display
    }


}
