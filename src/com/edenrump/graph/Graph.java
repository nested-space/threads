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
import com.edenrump.views.DepthGraphDisplay;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class representing a graph.
 *
 * Currently only has utility methods, but should eventually be refactored to include all elements of storing and
 * retrieving
 */
public class Graph {

    /**
     * Collect the nodes with no upstream linkages. Return these as a list.
     *
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no upstream linkages
     */
    public static List<VertexData> getLeavesUnidirectional(DepthDirection searchDirection, List<VertexData> vertexDataList) {
        if (searchDirection == DepthDirection.INCREASING_DEPTH) {
            return vertexDataList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.max(vertexDataList
                            .stream()
                            .map(VertexData::getDepth)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        } else {
            return vertexDataList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.min(vertexDataList
                            .stream()
                            .map(VertexData::getDepth)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Determine the shortest path between two vertices in the given vertex data map. Implements Dijkstra's shortest path
     * No benefit to implementing A* because network topology will likely be low and computational time taken to
     * optimise priority queue will likely outweigh gains.
     *
     * @param startVertex      the statring vertex for the search
     * @param desinationVertex the destination vertex
     * @param allVertices      the vertex data map
     * @return an ordered list containing
     */
    public static List<VertexData> findShortestPath(VertexData startVertex, VertexData desinationVertex, List<VertexData> allVertices) {
        if (desinationVertex == startVertex) return new ArrayList<>(Collections.singletonList(startVertex));

        //if startVertex isn't in allVertices, return empty list because this isn't going to work...
        if (!allVertices.contains(startVertex)) return new ArrayList<>();

        //populate initial priority queue
        List<PriorityItem> priorityQueue = new LinkedList<>();
        Map<String, PriorityItem> idPriorityItemMap = new HashMap<>();
        for (VertexData v : allVertices) {
            PriorityItem item;
            if (v.equals(startVertex)) {
                item = new PriorityItem(0, v);
            } else {
                item = new PriorityItem(Integer.MAX_VALUE, v);
            }
            priorityQueue.add(item);
            idPriorityItemMap.put(v.getId(), item);
        }
        Collections.sort(priorityQueue);
        List<PriorityItem> visited = new ArrayList<>();

        int cycles = 0;
        boolean bestPathFound = false;
        while (cycles < 1500 && !bestPathFound) {
            PriorityItem currentItem = priorityQueue.get(0);
            visited.add(currentItem);
            priorityQueue.remove(currentItem);

            for (String id : currentItem.vertex.getConnectedVertices()) {
                PriorityItem adjacentItem = idPriorityItemMap.get(id);
                if (visited.contains(adjacentItem)) continue; //don't go back to nodes already visited
                if ((currentItem.distance + 1) < adjacentItem.distance) {
                    adjacentItem.distance = currentItem.distance + 1; //all edge lengths are 1 in this implementation
                    adjacentItem.previousItem = currentItem;
                }
            }

            cycles++;
            Collections.sort(priorityQueue);
            if (priorityQueue.get(0).vertex == desinationVertex) {
                bestPathFound = true;
            }
        }

        if (!bestPathFound) return new ArrayList<>(); //if maxed out cycles, return empty list rather than null

        List<VertexData> path = new LinkedList<>();
        PriorityItem retraceCaret = priorityQueue.get(0);
        while (retraceCaret.vertex != startVertex) {
            path.add(retraceCaret.vertex);
            retraceCaret = retraceCaret.previousItem;
        }
        path.add(retraceCaret.vertex);
        Collections.reverse(path);
        return path;
    }

    public static List<VertexData> unidirectionalFill(String vertexID, DepthDirection direction, Map<String, DataAndNodes> nodeMap) {
        if (!nodeMap.containsKey(vertexID)) return new ArrayList<>();

        VertexData currentVertex = nodeMap.get(vertexID).getVertexData();
        List<VertexData> unvisitedVertices = currentVertex.getConnectedVertices().stream()
                .map(id -> nodeMap.get(id).getVertexData())
                .filter(data -> {
                    if (direction == DepthDirection.INCREASING_DEPTH) {
                        return data.getDepth() > nodeMap.get(vertexID).getVertexData().getDepth();
                    } else {
                        return data.getDepth() < nodeMap.get(vertexID).getVertexData().getDepth();
                    }
                })
                .collect(Collectors.toList());

        List<VertexData> visitedVertices = new ArrayList<>(Collections.singletonList(currentVertex));
        while (unvisitedVertices.size() > 0) {
            currentVertex = unvisitedVertices.remove(0);
            visitedVertices.add(currentVertex);

            unvisitedVertices.addAll(currentVertex.getConnectedVertices().stream()
                    .map(id -> nodeMap.get(id).getVertexData())
                    .filter(data -> !visitedVertices.contains(data))
                    .filter(data -> {
                        if (direction == DepthDirection.INCREASING_DEPTH) {
                            return data.getDepth() > nodeMap.get(vertexID).getVertexData().getDepth();
                        } else {
                            return data.getDepth() < nodeMap.get(vertexID).getVertexData().getDepth();
                        }
                    }).collect(Collectors.toList()));
        }

        return visitedVertices;
    }

    /**
     * Class representing an item in the priority queue of a Dijkstra's shortest-path calculation.
     */
    private static class PriorityItem implements Comparable<PriorityItem> {

        int distance;
        private VertexData vertex;
        PriorityItem previousItem;

        PriorityItem(int distance, VertexData vertex) {
            this.distance = distance;
            this.vertex = vertex;
            previousItem = null;
        }

        /**
         * Compares this object with the specified object for order.  Returns a
         * negative integer, zero, or a positive integer as this object is less
         * than, equal to, or greater than the specified object.
         *
         * @param other the object to be compared.
         * @return a negative integer, zero, or a positive integer as this object
         * is less than, equal to, or greater than the specified object.
         * @throws NullPointerException if the specified object is null
         * @throws ClassCastException   if the specified object's type prevents it
         *                              from being compared to this object.
         */
        @Override
        public int compareTo(PriorityItem other) {
            return Integer.compare(this.distance, other.distance);
        }
    }

}
