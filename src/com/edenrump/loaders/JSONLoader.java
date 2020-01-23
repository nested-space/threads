/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.loaders;

import com.edenrump.models.ThreadsData;
import com.google.gson.Gson;

import java.io.*;
import java.util.ArrayList;

public class JSONLoader {

    /**
     * Load a single ThreadsData from json file.
     * @param file the location of the file as a string
     * @return ThreadsData loaded from file
     */
    public static ThreadsData loadOneFromJSON(File file){
        try{
            BufferedReader r = new BufferedReader(new FileReader(file));
            return new Gson().fromJson(r, ThreadsData.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return new ThreadsData("NULL_NAME", "NULL_ID", new ArrayList<>());
        }
    }

    /**
     * Save a single ThreadsData instance to a json file
     * @param data the data to be saved
     * @param file the location of the file as a string
     * @return true if saved, false on error
     */
    public static boolean saveToJSON(ThreadsData data, File file){
        try{
            BufferedWriter w = new BufferedWriter(new FileWriter(file));
            String wData = new Gson().toJson(data);
            w.write(wData);
            w.close();
            return true;
        } catch (IOException e){
            e.printStackTrace();
            return false;
        }
    }

    public static String toJson(ThreadsData data){
        Gson gson = new Gson();
        return gson.toJson(data);
    }


}


