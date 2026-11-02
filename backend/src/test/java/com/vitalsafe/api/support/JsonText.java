package com.vitalsafe.api.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads values out of JSON text without depending on any JSON library. */
public final class JsonText {

    private JsonText() {
    }

    /** The first numeric value of the named field, as text, or null. */
    public static String number(String body, String name) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Every string value of the named field, in order of appearance. */
    public static List<String> strings(String body, String name) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(body);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** How many times the text occurs. */
    public static int count(String body, String needle) {
        int count = 0;
        int from = 0;
        while ((from = body.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}