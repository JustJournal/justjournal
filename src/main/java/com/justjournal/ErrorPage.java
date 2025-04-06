/*
 * Copyright (c) 2003-2021 Lucas Holt
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
package com.justjournal;

/**
 * Prints out an error message in HTML.
 *
 * @author Lucas Holt
 */
public final class ErrorPage {
  private static final String SEVERE_STYLE =
      "width: 100%; height: 100px; margin-top: 1in; margin-left: 0; margin-right: 0;"
          + " position relative; text-align: center; background: maroon; color: white;";

  private ErrorPage() {
    super();
  }

  private static void headStyle(final String title, final StringBuilder sb) {
    sb.append(
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\""
            + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
    sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
    sb.append("<head>\n");
    sb.append("<title>");
    sb.append(title);
    sb.append("</title>\n");
    sb.append("</head>\n");
    sb.append("<body style=\"margin: 0;\">\n");
  }

  private static void footStyle(final StringBuilder sb) {
    sb.append("</body>\n");
    sb.append("</html>\n");
  }

  public static void display(final String ErrTitle, final String ErrMsg, final StringBuilder sb) {
    severe(ErrTitle, ErrMsg, sb);
  }

  public static void severe(final String ErrTitle, final String ErrMsg, final StringBuilder sb) {
    if (!sb.isEmpty()) {
      // reset the output to display the error.
      sb.delete(0, sb.length() - 1);
    }

    headStyle(ErrTitle, sb);
    sb.append("<div style=\"");
    sb.append(SEVERE_STYLE);
    sb.append("\">\n");
    sb.append("<h1 style=\"font: 72pt Arial, Helvetica, sans-serif; letter-spacing: .2in;\">")
        .append(ErrTitle)
        .append("</h1>\n");
    sb.append("</div>\n");

    sb.append("<div style=\"margin: 1in; font: 12pt Arial, Helvetica, sans-serif;\">\n");
    sb.append("<p>");
    sb.append(ErrMsg);
    sb.append("</p>\n");
    sb.append("</div>\n");
    footStyle(sb);
  }
}
