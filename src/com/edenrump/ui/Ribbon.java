/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class Ribbon extends VBox {

    private Map<String, RibbonModule> moduleMap = new LinkedHashMap<>();
    private Map<RibbonModule, Boolean> positionMap = new LinkedHashMap<>();

    private HBox moduleHolder = new HBox();
    private HBox moduleEndHolder = new HBox();

    public Ribbon() {
        //set overall style
        setId("ribbonAndSearchBarVBox");
        getStylesheets().add("css/RibbonAndMenu.css");

        //set holder style
        moduleHolder.setId("ribbonModuleHolderHBox");
        moduleHolder.getStylesheets().add("css/RibbonAndMenu.css");
        moduleEndHolder.setId("ribbonModuleHolderHBox");
        moduleEndHolder.getStylesheets().add("css/RibbonAndMenu.css");
        HBox.setHgrow(moduleEndHolder, Priority.ALWAYS);
        moduleEndHolder.setAlignment(Pos.CENTER_RIGHT);
        HBox allModulesHolder = new HBox(moduleHolder, moduleEndHolder);
        this.getChildren().addAll(allModulesHolder);
    }

    public void disableModuleButtons(String moduleName) {
        if (moduleMap.containsKey(moduleName)) {
            moduleMap.get(moduleName).disableControls();
        } else if (moduleName.toLowerCase().trim().equals("search")) {
            throw new IllegalArgumentException("Search is keyword. Cannot be module name");
        } else {
            throw new IllegalArgumentException("Module not found");
        }
    }

    public void enableModuleButtons(String moduleName) {
        if (moduleMap.containsKey(moduleName)) {
            moduleMap.get(moduleName).enableControls();
        } else if (moduleName.toLowerCase().trim().equals("search")) {
            throw new IllegalArgumentException("Search is keyword. Cannot be module name");
        } else {
            throw new IllegalArgumentException("Module not found");
        }
    }

    public void addModule(String moduleName, boolean floatingRight) {
        if (moduleMap.containsKey(moduleName)) {
            System.out.println("Warning: module already exists");
        } else if (moduleName.toLowerCase().trim().equals("search")) {
            throw new IllegalArgumentException("Search is keyword. Cannot be module name");
        } else {
            //if module doesn't exist, add it
            RibbonModule ribbonModule = new RibbonModule();
            ribbonModule.setName(moduleName);
            moduleMap.put(moduleName, ribbonModule);
            positionMap.put(ribbonModule, floatingRight);
        }
    }

    public void removeModule(String moduleName) {
        if (moduleName.toLowerCase().trim().equals("search")) {
            throw new IllegalArgumentException("Search is keyword. Cannot be module name");
        } else if (moduleMap.containsKey(moduleName)) {
            //remove module
            moduleHolder.getChildren().remove(moduleMap.get(moduleName));
            moduleEndHolder.getChildren().remove(moduleMap.get(moduleName));
            moduleMap.remove(moduleName);
        }
    }

    public void addControlToModule(String moduleName, Control control) {
        //if module doesn't exist, throw exception
        if (!moduleMap.containsKey(moduleName)) {
            throw new IllegalArgumentException("Module not found");
        } else {
            moduleMap.get(moduleName).add(control);
        }

        refresh();
    }

    public void refresh() {
        //initially clear
        moduleHolder.getChildren().clear();
        moduleEndHolder.getChildren().clear();

        //rebuild children from Map
        for (String string : moduleMap.keySet()) {
            RibbonModule r = moduleMap.get(string);
            if(positionMap.get(r)){
                moduleEndHolder.getChildren().add(r);
            } else {
                moduleHolder.getChildren().add(r);
            }
        }
    }
}
