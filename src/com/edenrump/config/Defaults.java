/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.config;

public class Defaults {
    public static final String APPLICATION_NAME = "Threads";
    public static final double FADE_TIME = 200;
    public static final double DELAY_TIME = 350;

    public static String createTitle(String suffix) {
        return APPLICATION_NAME + " | " + suffix;
    }
}
