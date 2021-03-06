package com.fiendfyre.AdHell2.utils;

/**
 * Created by Matt on 19/01/2018.
 */
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlockUrlPatternsMatch {


    public static boolean wildcardValid (String domain){

        // Wildcard pattern to match
        String wildcardPattern = "(?i)^([\\*]?)([A-Z0-9-_.]+)([\\*]?)$";

        // Create a pattern object
        Pattern r = Pattern.compile(wildcardPattern);

        // Create a matcher object
        Matcher m = r.matcher(domain);

        // True or false
        boolean matchResult = m.matches();

        return matchResult;
    }

}
