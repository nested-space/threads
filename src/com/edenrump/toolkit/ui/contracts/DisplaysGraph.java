/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.contracts;

import com.edenrump.toolkit.models.Vertex;

public interface DisplaysGraph {

    public void addVertex(Vertex vertex);

    public void removeVertex(Vertex vertex);

    public void removeVertexById(String id);
}
