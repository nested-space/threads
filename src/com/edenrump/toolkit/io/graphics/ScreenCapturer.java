/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.io.graphics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Class provides utility methods to capture the screen programatically
 */
public class ScreenCapturer {

	/**
	 * Unselectively capture the entire screen. Multiple screens are untested.
	 * @param fileName the full name and location of the file
	 * @throws AWTException Signals that an Abstract Window Toolkit exception has occurred
	 * @throws IOException Signals an error has occurred handling the file output
	 */
	private static void captureFullScreen(String fileName) throws AWTException, IOException {
		String format = "jpg";
		Robot robot = new Robot();
		Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		BufferedImage screenFullImage = robot.createScreenCapture(screenRect);
		ImageIO.write(screenFullImage, format, new File(fileName + format));
	};


}