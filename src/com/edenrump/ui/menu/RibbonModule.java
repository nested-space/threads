/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.ui.menu;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * A class representing a module within the ribbon band of a program.
 * <p>
 * It typically contains Controls
 */
public class RibbonModule extends BorderPane {

    /**
     * The label stating the name of the module.
     */
    private Label moduleLabel = new Label();

    /**
     * The container for the module label
     */
    private StackPane labelPane = new StackPane();

    /**
     * The container for all controls in the module
     */
    private HBox controlHolder = new HBox();

    /**
     * Constructor. Creates ribbon module and adds styling
     */
    public RibbonModule() {
        //set overall style
        this.setId("menuGridHolder");
        this.getStylesheets().add("css/RibbonAndMenu.css");

        //set controlHolder style
        controlHolder.setId("menuHBox");
        controlHolder.getStylesheets().add("css/RibbonAndMenu.css");

        //set top and bottom
        this.setTop(controlHolder);
        this.setBottom(labelPane);

        //add label to labelPane
        labelPane.getChildren().add(moduleLabel);
    }

    /**
     * Method to get the name of the ribbon module
     *
     * @return the name of the module
     */
    public String getName() {
        return moduleLabel.getText();
    }

    /**
     * Method to set the name of the ribbon module
     *
     * @param name the name of the module
     */
    public void setName(String name) {
        moduleLabel.setText(name);
    }

    /**
     * Method to add a Control to the module
     *
     * @param control the Control to be added
     */
    public void add(Control control) {
        control.getStylesheets().add("css/RibbonAndMenu.css");
        controlHolder.getChildren().add(control);
    }

    /**
     * Method to remove a Control from this module
     *
     * @param control the Control to be removed
     */
    public void remove(Control control) {
        controlHolder.getChildren().remove(control);
    }

    /**
     * Method to disable all controls in the module
     */
    public void disableControls() {
        for (Node node : controlHolder.getChildren()) {
            if (node instanceof Control) {
                Control c = (Control) node;
                c.setDisable(true);
            }
        }
    }

    /**
     * Method to enable all controls in the module
     */
    public void enableControls() {
        for (Node node : controlHolder.getChildren()) {
            if (node instanceof Control) {
                Control c = (Control) node;
                c.setDisable(false);
            }
        }
    }
}
