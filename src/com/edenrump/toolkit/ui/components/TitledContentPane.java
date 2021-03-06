/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing content with an id, title and tag content (tag name only). Can hold an image
 */
public class TitledContentPane extends VBox {

    Map<String, TitledRectangle> idNodeMap = new HashMap<>();
    /**
     * The title of the
     */
    private String title;
    /**
     * The id associated with this content
     */
    private String id;
    /**
     * The color of the pane
     */
    private Color color = Color.ALICEBLUE;
    /**
     * The pane holding the title of this pane
     */
    private TitledRectangle titlePane = new TitledRectangle();
    /**
     * The pane holding the image associated with this content
     */
    private ImageView image = new ImageView();
    /**
     * Pane that holds all tags
     */
    private VBox tagContainer = new VBox();

    private Color textColor;

    /**
     * Create a placeholder box with no title or id and with the default color
     */
    public TitledContentPane() {
        super();
        setBackground(new Background(new BackgroundFill(Color.web("#D1DBE3"), CornerRadii.EMPTY, Insets.EMPTY)));
        titlePane.setTextAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(titlePane, image, tagContainer);
        resetHighlighting();
    }

    /**
     * Style the box with a title, id and color
     * @param title the title of the box
     * @param id and id as a string
     * @param color the color to set the box
     */
    public void addHeaderBox(String title, String id, Color color) {
        this.title = title;
        this.id = id;
        this.color = color;
        titlePane.setText(title);
        setHeaderColor(color);
    }

    /**
     * Return the id of the pane
     * @return the id of the pane
     */
    public String getIdString() {
        return id;
    }

    /**
     * Return the title of the pane
     * @return the title of the pane
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the title of the pane
     * @param text the title text to display in the pane
     */
    public void setTitle(String text) {
        this.title = text;
        titlePane.setText(text);
    }

    /**
     * Return the color of the header of the content pane
     * @return the color of the header of the content pane
     */
    public Color getHeaderColor() {
        return color;
    }

    /**
     * Set the color of the title pane in the content pane
     * @param color the color of the title pane in the content pane
     */
    public void setHeaderColor(Color color) {
        this.color = color;
        this.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
        titlePane.setColor(color);
    }

    public void setTextColor(Color color){
        this.textColor = color;
        titlePane.setTextColor(color);
        for(Map.Entry<String, TitledRectangle> entry : idNodeMap.entrySet()){
            entry.getValue().setTextColor(color);
        }
    }

    /**
     * Add an image to the pane. The image will appear below the title but above any tags
     * @param image the image to add
     */
    public void addImage(Image image) {
        this.image.setImage(image);
    }

    /**
     * Remove the image displayed by the pane
     */
    public void removeImage() {
        image.setImage(null);
    }

    /**
     * Method to add named rectangle containing text to HolderRectangle.
     *
     * @param tagText       text to be displayed on child NamedRectangle
     * @param id identifier - used for retrieving later.
     */
    public void addTag(String id, String tagText) {
        if(idNodeMap.containsKey(id)) return;
        TitledRectangle child = new TitledRectangle(tagText, id);
        child.setTextAlignment(Pos.CENTER_LEFT);
        idNodeMap.put(id, child);
        tagContainer.getChildren().add(child);
    }

    public boolean hasTag(String id){
        return idNodeMap.containsKey(id);
    }

    public void removeTag(String id) {
        tagContainer.getChildren().remove(idNodeMap.get(id));
        idNodeMap.remove(id);
    }

    public void setTagColor(String id, Color color) {
        idNodeMap.get(id).setColor(color);
    }

    public void updateTag(String id, String text){
        idNodeMap.get(id).setText(text);
    }

    /**
     * Set the style for the node when highlighted
     */
    public void highlightOne() {
        setBorder(new Border(new BorderStroke(Color.web("#830051"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderStroke.THIN)));
        setOpacity(1);
    }

    public void highlightTwo() {
        setBorder(new Border(new BorderStroke(Color.web("#f0AB00"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderStroke.THIN)));
        setOpacity(1);

    }


    /**
     * Set the style for the node on occasions when you want it to be less visible within the scene graph
     */
    public void lowlight() {
        setBorder(new Border(new BorderStroke(Color.web("#003865"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderStroke.THIN)));
        setOpacity(0.3);
    }

    /**
     * Reset the style of the node to its default style
     */
    public void resetHighlighting(){
        setBorder(new Border(new BorderStroke(Color.web("#003865"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderStroke.THIN)));
        setOpacity(1);
    }

}
