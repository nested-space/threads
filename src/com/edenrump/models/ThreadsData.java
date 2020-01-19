/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.models;

import java.util.List;

/**
 * Class representing the contents of a threads data set.
 */
public class ThreadsData {

    /**
     * The title of the dataset
     */
    private String name;

    /**
     * The id of the dataset
     */
    private String id;

    /**
     * The list of vertices in the dataset
     */
    private List<VertexData> vertices;
    //TODO: add edge data

    /**
     * Create ThreadsData object with all parameters
     * @param name the name of the datas et
     * @param id the id of the data set
     * @param vertices the vertices in the data set
     */
    public ThreadsData(String name, String id, List<VertexData> vertices){
        this.name = name;
        this.id = id;
        this.vertices = vertices;

    }
}