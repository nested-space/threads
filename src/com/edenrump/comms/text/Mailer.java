/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.comms.text;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ed_Work on 07/03/2017.
 */
public class Mailer {

    /**
     * Method to spawnNewDisplay an email and launch the default email program
     * @param recipients the reciepient email addresses
     * @param subject the subject of the email
     * @param body the body of the email
     * @throws IOException
     * @throws URISyntaxException
     */
    public static void mailto(List<String> recipients, String subject, String body) throws IOException, URISyntaxException {
        String uriStr = String.format("mailto:%s?subject=%s&body=%s",
                join(",", recipients == null ? new ArrayList<>() : recipients), // use semicolon ";" for Outlook!
                urlEncode(subject),
                urlEncode(body));
        Desktop.getDesktop().browse(new URI(uriStr));
    }

    /**
     * Method to encode a string as a url
     * @param str the string to be encoded
     * @return the encoded string
     */
    private static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Utility method to join several strings with a separator except the last string
     * @param sep the separator
     * @param objs the objects to be joined
     * @return the joined string.
     */
    private static String join(String sep, Iterable<?> objs) {
        StringBuilder sb = new StringBuilder();
        for (Object obj : objs) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(obj);
        }
        return sb.toString();
    }
}
