/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2026 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal.utility;

import java.util.Locale;
import java.util.Set;

/**
 * Common English words that carry no meaning on their own, so they make poor tags.
 *
 * <p>Words that also stand for something as a tag are deliberately left out: "it" (information
 * technology), "us" (the country), "may" (the month), "will" and "can".
 *
 * @author Lucas Holt
 */
public final class StopWords {

  private static final Set<String> WORDS =
      Set.of(
          // articles and determiners
          "a", "an", "the", "this", "that", "these", "those", "some", "any", "each", "every",
          "either", "neither", "such", "own", "same", "other", "another",
          // pronouns
          "i", "me", "my", "mine", "myself", "we", "our", "ours", "ourselves", "you", "your",
          "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers",
          "herself", "its", "itself", "they", "them", "their", "theirs", "themselves", "what",
          "which", "who", "whom", "whose",
          // prepositions
          "about", "above", "across", "after", "against", "along", "among", "around", "as", "at",
          "before", "behind", "below", "beneath", "beside", "between", "beyond", "by", "down",
          "during", "for", "from", "in", "inside", "into", "near", "of", "off", "on", "onto",
          "out", "outside", "over", "per", "since", "through", "throughout", "till", "to",
          "toward", "towards", "under", "underneath", "until", "unto", "up", "upon", "via",
          "with", "within", "without",
          // conjunctions
          "and", "or", "but", "nor", "so", "yet", "if", "than", "then", "because", "although",
          "though", "unless", "while", "whether", "whereas",
          // auxiliary and linking verbs
          "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
          "having", "do", "does", "did", "doing", "shall", "should", "would", "could", "might",
          "must",
          // adverbs and other function words
          "not", "no", "yes", "very", "too", "also", "just", "only", "again", "further", "once",
          "here", "there", "when", "where", "why", "how", "all", "both", "few", "more", "most",
          "much", "many", "now", "ever", "never", "always", "often", "really", "quite",
          "rather", "almost", "already", "even", "still", "oh", "ok", "okay");

  private StopWords() {}

  /**
   * Check whether a word is an English stop word. Case and surrounding whitespace are ignored.
   *
   * @param word word to check
   * @return true if the word is a stop word
   */
  public static boolean isStopWord(final String word) {
    return word != null && WORDS.contains(word.trim().toLowerCase(Locale.ROOT));
  }
}
