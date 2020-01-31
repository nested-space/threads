/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.io.text;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Utility class providing functionality for launching links
 */
public class Launcher {

    /**
     * Utility method providing functionality to launch a link that's already been formatted into a URL
     * @param formattedURL the url to be launched
     */
    public static void handleOpenHyperlink(String formattedURL) {
        new Application() {
            @Override
            public void start(Stage stage) throws Exception {

            }
        }.getHostServices().showDocument(formattedURL);
    }

}
