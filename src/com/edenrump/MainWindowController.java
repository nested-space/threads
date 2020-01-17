/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump;

import com.edenrump.config.Defaults;
import com.edenrump.models.VertexData;
import com.edenrump.ui.display.HolderRectangle;
import com.edenrump.ui.menu.Ribbon;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.*;

public class MainWindowController implements Initializable {

    /**
     * Top layer of application, base pane
     */
    public BorderPane borderBase;
    public HBox displayContainer;

    private List<VertexData> nodeInfoInMemory = new ArrayList<>();

    /**
     * A map of node depth to node data
     */
    private Map<Integer, List<VertexData>> nodeDepthMap = new HashMap<>();

    /**
     * A map of node containers to info nodes
     */
    private Map<Integer, Node> displayDepthMap = new HashMap<>();

    /**
     * Initial actions to load the ribbon of the display and start the user interaction process
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        loadRibbon(borderBase);
    }

    /**
     * User control ribbon
     */
    private Ribbon mRibbon = new Ribbon();

    /**
     * Method creates Ribbon node to facilitate user interaction and places it at top of BorderPane
     *
     * @param borderPane BorderPane at the top of which ribbon is to be placed
     */
    private void loadRibbon(BorderPane borderPane) {
        mRibbon.addModule(Defaults.LOAD_MODULE_NAME, false);

        Button button = new Button("", new ImageView("/img/folder.png"));
        button.setOnAction(actionEvent -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");
            File file = fileChooser.showOpenDialog(button.getScene().getWindow());
            if (file != null) loadFile(file);
            actionEvent.consume();
        });

        Button newFileButton = new Button("", new ImageView("/img/wool.png"));
        newFileButton.setOnAction(actionEvent -> {
            newButtonPressed();
        });

        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, button);
        mRibbon.addControlToModule(Defaults.LOAD_MODULE_NAME, newFileButton);

        borderPane.setTop(mRibbon);
    }

    /**
     * Determine whether a map is currently loaded. If yes, prompt user to close. If not, load new file.
     */
    private void newButtonPressed(){
        if(programState == ProgramState.Loaded) {
            if(promptUserClose()) createNew();
        } else {
            createNew();
        }
    }

    /**
     * Prompt user whether to close the current file
     * @return the users decision
     */
    private boolean promptUserClose() {
        //TODO: prompt user whether to close the current file. Possibly add save option
        return true;
    }

    /**
     * The current state of the program.
     */
    private ProgramState programState = ProgramState.Closed;

    /**
     * Method attempts to load the file, if successful displays the information, if unsuccessful, prompts user
     *
     * @param file the file to be loaded
     */
    private void loadFile(File file) {
    }

    /**
     * Create a new thread map and display the start node
     */
    private void createNew(){
        //Close everything down
        clearNodeInformation();
        clearNodeReals();
        programState = ProgramState.Closed;

        //Start up a new map
        VertexData startingNode = new VertexData("Starting Node");
        VertexData upstreamNode = new VertexData("Upstream Node", startingNode.getId());
        upstreamNode.addDownstream(startingNode.getId());
        startingNode.addUpstream(upstreamNode.getId());
        nodeInfoInMemory.addAll(Arrays.asList(startingNode, upstreamNode));
        nodeDepthMap = createNodeMapping(nodeInfoInMemory);
        displayDepthMap = createNodeDisplay();

        for(int i=0; i<displayDepthMap.keySet().size(); i++){
            displayContainer.getChildren().addAll(displayDepthMap.get(i));
        }
    }

    /**
     * For each level of depth in the map provided, create a container node. For each vertex within the nested map,
     * create a display node and add it to the container. Return a map of depths to containers
     * @return a map of depth level to container nodes
     */
    private Map<Integer, Node> createNodeDisplay() {
        Map<Integer, Node> depthMap = new HashMap<>();
        for(int depth=0; depth<nodeDepthMap.keySet().size(); depth++){
            VBox container = getStyledContainer();
            for(VertexData data : nodeDepthMap.get(depth)){
                HolderRectangle node = new HolderRectangle();
                node.addHeaderBox(data.getName(), data.getId(), Color.ALICEBLUE);
                container.getChildren().add(node);
            }
            depthMap.put(depth, container);
        }
        return depthMap;
    }

    /**
     * Clear the current display and return it to an unloaded state
     */
    private void clearNodeReals() {
        displayContainer.getChildren().clear();
    }

    /**
     * Clear the current node mapping
     */
    private void clearNodeInformation() {
        nodeInfoInMemory.clear();
        nodeDepthMap.clear();
    }

    /**
     * Create node positions from the node map currently in memory
     * @return a positional map of containers and nodes
     */
    private Map<Integer, List<VertexData>> createNodeMapping(List<VertexData> nodeInfo) {
        List<VertexData> liveNodes = new ArrayList<>(nodeInfo);
        Map<Integer, List<VertexData>> nodeMap = new HashMap<>();

        int depth = 0;
        nodeMap.put(depth, findDownsteamLeaves(nodeInfo));
        liveNodes.removeAll(nodeMap.get(depth++));

        while (liveNodes.size() > 0){
            List<VertexData> nodesAtCurrentDepth = new ArrayList<>();
            List<VertexData> nodesAtDepthAbove = new ArrayList<>(nodeMap.get(depth-1));
            for(VertexData ed : liveNodes){
                if(linksToDownstreamNode(ed, nodesAtDepthAbove)) {
                    nodesAtCurrentDepth.add(ed);
                    System.out.println("Nodes located! ");
                }
            }
            liveNodes.removeAll(nodesAtCurrentDepth);
            nodeMap.put(depth++, nodesAtCurrentDepth);
        }
        return nodeMap;
    }

    /**
     * Return whether the edge data (ed) has a direct upstream link to the node list provided.
     * @param ed the edge data
     * @param nodesAtDepthAbove the nodes against which to test upstream linkage
     * @return whether the edge data is linked directly to the node list. Upstream only.
     */
    private boolean linksToDownstreamNode(VertexData ed, List<VertexData> nodesAtDepthAbove) {
        for(VertexData downstream: nodesAtDepthAbove){
            for(String dsID: downstream.getUpstream()){
                if(dsID.equals(ed.getId())) return true;
            }
        }
        return false;

    }

    /**
     * Collect the nodes with no downstream linkages. Return these as a list.
     * @param vertexDataList the entire list of nodes to be parsed
     * @return a list of nodes with no downstream linkages
     */
    private List<VertexData> findDownsteamLeaves(List<VertexData> vertexDataList){
        List<VertexData> leaves = new ArrayList<>();
        for(VertexData ed : vertexDataList){
            if(ed.getDownstream().size() == 0) leaves.add(ed);
        }
        return leaves;
    }

    /**
     * Enum representing the possible states of the program.
     */
    private enum ProgramState{
        Loaded, Closed
    }

    private VBox getStyledContainer(){
        VBox container = new VBox();
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color: blue");
        return container;
    }
}
