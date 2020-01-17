/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Class representing a basic information node, which can have upstream and downstream links
 * and has an id and a string name
 */
public class VertexData implements Comparable<VertexData>{

    /**
     * List of upstream node ids
     */
    private List<String> upstream;

    /**
     * List of downstream node ids
     */
    private List<String> downstream;

    /**
     * THe name of this node
     */
    private String name;

    /**
     * The id of this node
     */
    private String id;

    public VertexData(String name){
        this(name, UUID.randomUUID().toString());
    }

    public VertexData(String name, String id){
        this(name, id, new ArrayList<>(), new ArrayList<>());
    }

    public VertexData(String name, String id, List<String> us, List<String> ds){
        this.name = name;
        this.id = id;
        this.upstream = us;
        this.downstream = ds;
    }

    /**
     * Return the ids of the upstream nodes
     * @return list of ids
     */
    public List<String> getUpstream() {
        return upstream;
    }

    /**
     * Add an integer id to the list of upstream nodes
     * @param upstream the id of the node to be added
     */
    public void addUpstream(String upstream) {
        this.upstream.add(upstream);
    }

    /**
     * Return the ids of the downstream nodes
     * @return list of ids
     */
    public List<String> getDownstream() {
        return downstream;
    }

    /**
     * Add an integer id to the list of downstream nodes
     * @param downstream the id of the node to be added
     */
    public void addDownstream(String downstream) {
        this.downstream.add(downstream);
    }

    /**
     * Return the name of the node
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the node
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the id of the node
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Set the id of the node
     * @param id integer id
     */
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public int compareTo(VertexData o) {
        return o.getId().equals(this.id) ? 1 : 0 ;
    }
}
