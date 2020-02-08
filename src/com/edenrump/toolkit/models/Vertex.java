/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.models;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.*;

/**
 * Class representing a basic information node, which can have upstream and downstream links
 * and has an id and a string name
 */
public class Vertex implements Comparable<Vertex> {

    /**
     * List of upstream node ids
     */
    private List<String> connectedVertices;
    /**
     * The depth of the vertex in the graph
     */
    private int depth;
    /**
     * The priority of the vertex in the graph
     */
    private int priority;
    /**
     * THe name of this node
     */
    private String name;
    /**
     * The id of this node
     */
    private ReadOnlyStringWrapper id;
    /**
     * the hyperlink associated with this vertex
     */
    private Map<String, String> propertiesMap = new HashMap<>();

    /**
     * Create VertexData fron a name and a random id
     *
     * @param name the name of the vertex
     */
    public Vertex(String name) {
        this(name, UUID.randomUUID().toString());
    }

    /**
     * Create VertexData fron a name and a random id
     *
     * @param name     the name of the vertex
     * @param depth    the depth of the vertex
     * @param priority the priority of the vertex
     */
    public Vertex(String name, int depth, int priority) {
        this(name, UUID.randomUUID().toString(), new ArrayList<>(), depth, priority);
    }

    /**
     * Create VertexData from a name and given id
     *
     * @param name the name of the vertex
     * @param id   the id of the vertex
     */
    public Vertex(String name, String id) {
        this(name, id, new ArrayList<>(), 0, 0);
    }

    /**
     * Create a VertexData object
     *
     * @param name      the name of the vertex
     * @param id        the id of the vertex
     * @param connected a list of connected vertices by id
     */
    public Vertex(String name, String id, List<String> connected, int depth, int priority) {
        this.name = name;
        this.id = new ReadOnlyStringWrapper(id);
        this.connectedVertices = new ArrayList<>(connected);
        this.depth = depth;
        this.priority = priority;
    }

    public ReadOnlyObjectWrapper<Vertex> readOnly() {
        return new ReadOnlyObjectWrapper<>(this);
    }

    /**
     * Return the depth of the node in the graph
     *
     * @return the depth of the node
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Set the depth of the node in the graph
     *
     * @param depth depth of the node in the graph
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Get the priority of the vertex
     *
     * @return the priority of the vertex
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the priority of the vertex.
     *
     * @param priority the priority value
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Return the ids of the upstream nodes
     *
     * @return list of ids
     */
    public List<String> getConnectedVertices() {
        return connectedVertices;
    }

    /**
     * Add an integer id to the list of upstream nodes
     *
     * @param connection the id of the node to be added
     */
    public void addConnection(String connection) {
        if (!this.connectedVertices.contains(connection)) this.connectedVertices.add(connection);
    }

    /**
     * Return the name of the node
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the node
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the id of the node
     *
     * @return the id
     */
    public String getId() {
        return id.get();
    }

    @Override
    public int compareTo(Vertex o) {
        return o.getId().equals(this.getId()) ? 1 : 0;
    }

    /**
     * Transcribe the properties of a vertex into this vertex. Do not change the id.
     *
     * @param vertex the vertex whose properties are to be copied
     */
    public void update(Vertex vertex) {
        this.name = vertex.getName();
        this.connectedVertices = new ArrayList<>(vertex.getConnectedVertices());
        this.depth = vertex.getDepth();
        this.priority = vertex.getPriority();
        this.propertiesMap = vertex.propertiesMap;
    }

    /**
     * If not already present, add a property to the vertex
     *
     * @param propertyName  the name of the property
     * @param propertyValue the value of the property
     * @return whether the property was added
     */
    public boolean addProperty(String propertyName, String propertyValue) {
        if (propertiesMap.containsKey(propertyName)) {
            return false;
        } else {
            propertiesMap.put(propertyName, propertyValue);
            return true;
        }
    }

    /**
     * Add a property to the vertex, overwriting any existing property of the same name
     *
     * @param propertyName  the name of the property
     * @param propertyValue the value of the property
     */
    public void overwriteProperty(String propertyName, String propertyValue) {
        propertiesMap.put(propertyName, propertyValue);
    }

    /**
     * Remove a property from the vertex
     *
     * @param propertyName the name of the property
     */
    public void removeProperty(String propertyName) {
        propertiesMap.remove(propertyName);
    }

    public String getProperty(String propertyName) {
        return propertiesMap.get(propertyName);
    }

    /**
     * Return whether the vertex has a property of the specified name
     *
     * @param propertyName the property name
     * @return whether the vertex has a property of the specified name
     */
    public boolean hasProperty(String propertyName) {
        return propertiesMap.containsKey(propertyName) && propertiesMap.getOrDefault(propertyName, "").length() > 0;
    }
}
