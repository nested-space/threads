/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;

import java.util.*;

/**
 * Class representing a pane with a single text title
 */
public class TitledRectangle extends StackPane {

    /**
     * Shape providing background around the text in this pane
     */
    private Rectangle mBackgroundRectangle;
    /**
     * Label showing text in this pane
     */
    private Label mLabel = new Label();
    /**
     * The color of this pane
     */
    private Color color = Color.TRANSPARENT;

    /**
     * Return a new pane with no title and a randomly generated id
     */
    public TitledRectangle() {
        this("", UUID.randomUUID().toString());
    }

    /**
     * Return a new pane with a defied name and id
     * @param title the name to be associated with this pane
     * @param id the id to be associated with this pane
     */
    TitledRectangle(String title, String id) {
        setId(id);
        setLayout();
        setText(title);
    }

    /**
     * Set the width of the shape filling this pane
     * @param width the width of the shape filling this pane
     */
    private void setRectWidth(double width) {
        mBackgroundRectangle.setWidth(width);
    }

    /**
     * Set the height of the shape filling this pane
     * @param height the width of the shape filling this pane
     */
    private void setRectHeight(double height) {
        mBackgroundRectangle.setHeight(height);
    }

    /**
     * Get the title of this pane
     */
    public String getText() {
        return mLabel.getText();
    }

    /**
     * Set the title of this pane
     * @param name the title of this pane
     */
    public void setText(String name) {
        mLabel.setText(name);
    }

    /**
     * Set the text alignment of this pane
     * @param alignment the alignment
     */
    public void setTextAlignment(Pos alignment) {
        mLabel.setAlignment(alignment);
    }

    /**
     * Establish the default layout of this pane
     */
    private void setLayout() {
        //set up background
        double DEFAULT_HEIGHT = 50;
        double DEFAULT_WIDTH = 100;
        mBackgroundRectangle = new Rectangle(DEFAULT_WIDTH, DEFAULT_HEIGHT, Color.TRANSPARENT);
        mBackgroundRectangle.setArcHeight(DEFAULT_HEIGHT / 2);
        mBackgroundRectangle.setArcWidth(DEFAULT_HEIGHT / 2);

        //set up label
        mLabel.setPadding(new Insets(0, 5, 0, 10));
        mLabel.setWrapText(true);
        mLabel.setPrefWidth(90);
        mLabel.setTextAlignment(TextAlignment.CENTER);

        //add children
        getChildren().addAll(mBackgroundRectangle, mLabel);
    }

    /**
     * Set the color of this pane
     * @param colour the color to apply to this pane
     */
    public void setColor(Color colour) {
        this.setBackground(new Background(new BackgroundFill(colour, CornerRadii.EMPTY, Insets.EMPTY)));
    }
}
