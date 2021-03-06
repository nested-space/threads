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
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.VerticalDirection;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class representing a graph.
 *
 * Currently only has utility methods, but should eventually be refactored to include all elements of storing and
 * retrieving
 */
public class Graph {

    Map<String, Vertex> verticesById = new HashMap<>();


    public void addVertex(Vertex vertex) {
        verticesById.put(vertex.getId(), vertex);
    }

    public void removeVertex(String vertexId) {
        verticesById.remove(vertexId);
        for (String otherId : getAllVertexIds()) {
            getVertexById(otherId).getConnectedVertices().remove(vertexId);
        }
    }

    public Set<String> getAllVertexIds() {
        return verticesById.keySet();
    }

    public Vertex getVertexById(String id) {
        return verticesById.get(id);
    }

    public ReadOnlyObjectWrapper<Vertex> getReadOnlyVertex(String id) {
        return verticesById.get(id).readOnly();
    }

    public List<Vertex> getAllVertexData() {
        return new ArrayList<>(verticesById.values());
    }

    public int calculatePriority(int depth, VerticalDirection topOrBottom) {
        int rowPriorityIncrement = 32000;

        int maxPriority = calculateMaximumPriorityOfColumn(depth);
        int minPriority = calculateMinimumPriorityOfColumn(depth);

        if (maxPriority == Integer.MIN_VALUE) return 0;
        return topOrBottom == VerticalDirection.DOWN ?
                maxPriority + rowPriorityIncrement :
                minPriority - rowPriorityIncrement;
    }

    private int calculateMaximumPriorityOfColumn(int depth) {
        if(getAllVerticesAtDepth(depth).size()==0) return 0;
        int maxPriority = Integer.MIN_VALUE;
        for (String id : getAllVerticesAtDepth(depth)) {
            maxPriority = Math.min(maxPriority, getVertexById(id).getDepth());
        }
        return maxPriority;
    }

    private int calculateMinimumPriorityOfColumn(int depth) {
        if(getAllVerticesAtDepth(depth).size()==0) return 0;
        int minPriority = Integer.MAX_VALUE;
        for (String id : getAllVerticesAtDepth(depth)) {
            minPriority = Math.min(minPriority, getVertexById(id).getDepth());
        }
        return minPriority;
    }

    public List<String> geAllVerticesSortedByPriority() {
        List<String> vertices = new ArrayList<>(getAllVertexIds());
        vertices.sort(Comparator.comparingInt(o -> getVertexById(o).getPriority()));
        return vertices;
    }

    private Set<String> getAllVerticesAtDepth(int depth) {
        Set<String> verticesAtDepth = new HashSet<>();
        for (String id : getAllVertexIds()) {
            if (getVertexById(id).getDepth() == depth) verticesAtDepth.add(id);
        }
        return verticesAtDepth;
    }



    /**
     * Collect the nodes with no upstream linkages. Return these as a list.
     *
     * @param vertexList the entire list of nodes to be parsed
     * @return a list of nodes with no upstream linkages
     */
    public static List<Vertex> getLeavesUnidirectional(DepthDirection searchDirection, List<Vertex> vertexList) {
        if (searchDirection == DepthDirection.INCREASING_DEPTH) {
            return vertexList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.max(vertexList
                            .stream()
                            .map(Vertex::getDepth)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        } else {
            return vertexList
                    .stream()
                    .filter(v -> v.getDepth() == Collections.min(vertexList
                            .stream()
                            .map(Vertex::getDepth)
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
    public static List<Vertex> findShortestPath(Vertex startVertex, Vertex desinationVertex, List<Vertex> allVertices) {
        if (desinationVertex == startVertex) return new ArrayList<>(Collections.singletonList(startVertex));

        //if startVertex isn't in allVertices, return empty list because this isn't going to work...
        if (!allVertices.contains(startVertex)) return new ArrayList<>();

        //populate initial priority queue
        List<PriorityItem> priorityQueue = new LinkedList<>();
        Map<String, PriorityItem> idPriorityItemMap = new HashMap<>();
        for (Vertex v : allVertices) {
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

        List<Vertex> path = new LinkedList<>();
        PriorityItem retraceCaret = priorityQueue.get(0);
        while (retraceCaret.vertex != startVertex) {
            path.add(retraceCaret.vertex);
            retraceCaret = retraceCaret.previousItem;
        }
        path.add(retraceCaret.vertex);
        Collections.reverse(path);
        return path;
    }

    public static List<Vertex> unidirectionalFill(String vertexID, DepthDirection direction, List<Vertex> vertices) {
        Map<String, Vertex> nodeMap = new HashMap<>();
        for(Vertex vertex : vertices){
            nodeMap.put(vertex.getId(), vertex);
        }
        if (!nodeMap.containsKey(vertexID)) return new ArrayList<>();

        Vertex currentVertex = nodeMap.get(vertexID);
        List<Vertex> unvisitedVertices = currentVertex.getConnectedVertices().stream()
                .map(nodeMap::get)
                .filter(data -> {
                    if (direction == DepthDirection.INCREASING_DEPTH) {
                        return data.getDepth() > nodeMap.get(vertexID).getDepth();
                    } else {
                        return data.getDepth() < nodeMap.get(vertexID).getDepth();
                    }
                })
                .collect(Collectors.toList());

        List<Vertex> visitedVertices = new ArrayList<>(Collections.singletonList(currentVertex));
        while (unvisitedVertices.size() > 0) {
            currentVertex = unvisitedVertices.remove(0);
            visitedVertices.add(currentVertex);

            unvisitedVertices.addAll(currentVertex.getConnectedVertices().stream()
                    .map(nodeMap::get)
                    .filter(data -> !visitedVertices.contains(data))
                    .filter(data -> {
                        if (direction == DepthDirection.INCREASING_DEPTH) {
                            return data.getDepth() > nodeMap.get(vertexID).getDepth();
                        } else {
                            return data.getDepth() < nodeMap.get(vertexID).getDepth();
                        }
                    }).collect(Collectors.toList()));
        }

        return visitedVertices;
    }

    public void clearAll() {
        verticesById.clear();
    }

    /**
     * Class representing an item in the priority queue of a Dijkstra's shortest-path calculation.
     */
    private static class PriorityItem implements Comparable<PriorityItem> {

        int distance;
        private Vertex vertex;
        PriorityItem previousItem;

        PriorityItem(int distance, Vertex vertex) {
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
