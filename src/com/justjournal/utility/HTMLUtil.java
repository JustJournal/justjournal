package com.justjournal.utility;

/*---------------------------------------------------------------------------*\
  $Id: HTMLUtil.java,v 1.1 2006/03/14 16:26:23 laffer1 Exp $
  ---------------------------------------------------------------------------
  This software is released under a Berkeley-style license:

  Copyright (c) 2004-2005 Brian M. Clapper. All rights reserved.

  Redistribution and use in source and binary forms are permitted provided
  that: (1) source distributions retain this entire copyright notice and
  comment; and (2) modifications made to the software are prominently
  mentioned, and a copy of the original software (or a pointer to its
  location) are included. The name of the author may not be used to endorse
  or promote products derived from this software without specific prior
  written permission.

  THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
  WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.

  Effectively, this means you can do what you want with the software except
  remove this notice or take advantage of the author's name. If you modify
  the software and redistribute your modified version, you must indicate that
  your version is a modification of the original, and you must provide either
  a pointer to or a copy of the original.
\*---------------------------------------------------------------------------*/

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Static class containing miscellaneous HTML-related utility methods.
 *
 * @author Copyright &copy; 2004 Brian M. Clapper
 * @version <tt>$Revision: 1.1 $</tt>
 */
public final class HTMLUtil {
    /*----------------------------------------------------------------------*\
                            Private Constants
    \*----------------------------------------------------------------------*/

    /**
     * Resource bundle containing the character entity code mappings.
     */
    private static final String BUNDLE_NAME = "org.clapper.util.text.HTMLUtil";

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    private static ResourceBundle resourceBundle = null;

    /**
     * For regular expression substitution. Instantiated first time it's
     * needed.
     */
    private static Pattern entityPattern = null;

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    private HTMLUtil() {
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Removes all HTML element tags from a string, leaving just the character
     * data. This method does <b>not</b> touch any inline HTML character
     * entity codes. Use
     * {@link #convertCharacterEntities convertCharacterEntities()}
     * to convert HTML character entity codes.
     *
     * @param s the string to adjust
     * @return the resulting, possibly modified, string
     * @see #convertCharacterEntities
     */
    public static String stripHTMLTags(String s) {
        char[]         ch = s.toCharArray();
        boolean inElement = false;
        StringBuffer buf = new StringBuffer();

        for (int i = 0; i < ch.length; i++) {
            switch (ch[i]) {
                case '<':
                    inElement = true;
                    break;

                case '>':
                    if (inElement)
                        inElement = false;
                    else
                        buf.append(ch[i]);
                    break;

                default:
                    if (! inElement)
                        buf.append(ch[i]);
                    break;
            }
        }

        return buf.toString();
    }

    /**
     * Converts all inline HTML character entities (c.f.,
     * <a href="http://www.w3.org/TR/REC-html40/sgml/entities.html">http://www.w3.org/TR/REC-html40/sgml/entities.html</a>)
     * to their Unicode character counterparts, if possible.
     *
     * @param s the string to convert
     * @return the resulting, possibly modified, string
     * @see #stripHTMLTags
     */
    public static String convertCharacterEntities(String s) {
        // The resource bundle contains the mappings for symbolic entity
        // names like "amp". Note: Must protect matching and MatchResult in
        // a critical section, for thread-safety. See javadocs for
        // Perl5Util.

        synchronized (HTMLUtil.class) {
            try {
                if (entityPattern == null)
                    entityPattern = Pattern.compile("&(#?[^; \t]+);");
            }

            catch (PatternSyntaxException ex) {
                // Should not happen unless I've screwed up the pattern.
                // Throw a runtime error.

                assert (false);
            }
        }

        ResourceBundle bundle = getResourceBundle();
        StringBuffer buf = new StringBuffer();
        Matcher matcher = null;

        synchronized (HTMLUtil.class) {
            matcher = entityPattern.matcher(s);
        }

        for (; ;) {
            String match = null;
            String preMatch = null;
            String postMatch = null;

            if (! matcher.find())
                break;

            match = matcher.group(1);
            preMatch = s.substring(0, matcher.start(1) - 1);
            postMatch = s.substring(matcher.end(1) + 1);

            if (preMatch != null)
                buf.append(preMatch);

            if (match.charAt(0) == '#') {
                if (match.length() == 1)
                    buf.append('#');

                else {
                    // It might be a numeric entity code. Try to parse it
                    // as a number. If the parse fails, just put the whole
                    // string in the result, as is.

                    try {
                        int cc = Integer.parseInt(match.substring(1));

                        // It parsed. Is it a valid Unicode character?

                        if (Character.isDefined((char) cc))
                            buf.append((char) cc);
                        else
                            buf.append("&#" + match + ";");
                    }

                    catch (NumberFormatException ex) {
                        buf.append("&#" + match + ";");
                    }
                }
            } else {
                // Not a numeric entity. Try to find a matching symbolic
                // entity.

                try {
                    String rep = bundle.getString("html_" + match);
                    buf.append(rep);
                }

                catch (MissingResourceException ex) {
                    buf.append("&" + match + ";");
                }
            }

            if (postMatch == null)
                break;

            s = postMatch;
            matcher.reset(s);
        }

        if (s.length() > 0)
            buf.append(s);

        return buf.toString();
    }

    /**
     * Convenience method to convert embedded HTML to text. This method:
     * <p/>
     * <ul>
     * <li> Strips embedded HTML tags via a call to
     * {@link #stripHTMLTags #stripHTMLTags()}
     * <li> Uses {@link #convertCharacterEntities convertCharacterEntities()}
     * to convert HTML entity codes to appropriate Unicode characters.
     * </ul>
     *
     * @param s the string to parse
     * @return the resulting, possibly modified, string
     * @see #convertCharacterEntities
     * @see #stripHTMLTags
     */
    public static String textFromHTML(String s) {
        return convertCharacterEntities(stripHTMLTags(s));
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Load the resource bundle, if it hasn't already been loaded.
     */
    private static ResourceBundle getResourceBundle() {
        synchronized (HTMLUtil.class) {
            if (resourceBundle == null)
                resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME);
        }

        return resourceBundle;
    }
}

