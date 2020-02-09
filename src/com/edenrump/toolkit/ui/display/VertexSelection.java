/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.display;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;

public class VertexSelection {

    private ObservableList<String> selectedVertexIds = FXCollections.observableArrayList();
    private String lastSelectedVertexId;

    public String getLastSelectedVertexId() {
        return lastSelectedVertexId;
    }

    public void setLastSelectedVertexId(String vertexId) {
        lastSelectedVertexId = vertexId;
    }

    public boolean isVertexSelected(String vertexId) {
        return selectedVertexIds.contains(vertexId);
    }

    public ObservableList<String> getSelectedVertexIdsObservable() {
        return selectedVertexIds;
    }

    public void removeSelectedVertexId(String vertexId) {
        selectedVertexIds.remove(vertexId);
    }

    public void setAllSelectedVertexIds(Collection<String> ids) {
        selectedVertexIds.setAll(new ArrayList<>(ids));
    }

    public void clearSelectedVertices() {
        selectedVertexIds.clear();
        lastSelectedVertexId = null;
    }

    public void addSelectedVertex(String id) {
        if (!selectedVertexIds.contains(id)) selectedVertexIds.add(id);
    }
}
