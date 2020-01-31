/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.io.text;

import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class providing methods to send content to the system clipboard
 */
public class Clipper {

    /**
     * Utility method sends object content of specified format to the system clipboard
     * @param format the format of the content
     * @param content the content
     */
    public static void pushToClipboard(DataFormat format, Object content){
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Map<DataFormat, Object> dataFormatObjectMap = new HashMap<>();
        dataFormatObjectMap.put(format, content);
        clipboard.setContent(dataFormatObjectMap);
    }
}
