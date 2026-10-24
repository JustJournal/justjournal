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
package com.justjournal.rss;

import static com.justjournal.core.Constants.endl;

import com.justjournal.utility.DNSUtil;
import com.justjournal.utility.Xml;
import java.io.IOException;
import java.io.StringReader;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.Policy;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

/**
 * Retrieves a RSS document using HTTP, parses the document, and converts it to HTML.
 *
 * @author Lucas Holt
 * @version $Id: HeadlineBean.java,v 1.7 2011/05/29 22:32:59 laffer1 Exp $
 * @since 1.0 User: laffer1 Date: Jul 22, 2003 Time: 12:19:17
 *     <p>Switch to cvs versioning.
 *     <p>1.4 Fixed bugs when certain rss features are missing.
 *     <p>1.3 now supports several RSS 2 features (non rdf format)
 *     <p>1.2 added several properties to the output including
 *     <p>the published date, and description.
 *     <p>1.1 optimized code
 *     <p>1.0 Initial release
 *     <p>
 */
@Slf4j
@Component
public class HeadlineBean {

  /**
   * Feed HTML comes from sites we don't control. The policy keeps basic formatting, links and
   * http(s) images, and drops scripts, event handlers, iframes and objects.
   */
  private static final Policy FEED_POLICY = loadPolicy("/antisamy-feed.xml");

  private final FeedFetcher feedFetcher;

  public HeadlineBean(final FeedFetcher feedFetcher) {
    this.feedFetcher = feedFetcher;
  }

  protected Optional<HeadlineContext> getRssDocument(@NonNull final String uri) throws Exception {

    final HeadlineContext hc = new HeadlineContext();

    try {
      final FeedFetcher.Result result = feedFetcher.fetch(uri);
      if (!result.isOk()) {
        log.warn("RSS fetch of {} returned status {}", uri, result.statusCode());
        return Optional.empty();
      }

      // Build document:
      hc.factory.setValidating(false);
      hc.factory.setIgnoringComments(true);
      hc.factory.setCoalescing(true);
      hc.factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      hc.builder = hc.factory.newDocumentBuilder();
      hc.document = hc.builder.parse(new InputSource(new StringReader(result.body())));

      return Optional.of(hc);
    } catch (final IOException e) {
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  /** Checks the feed address before it is used. The default resolves the host name. */
  protected boolean isValidFeedAddress(@NonNull final String url) {
    return DNSUtil.isUrlDomainValid(url);
  }

  /** Message shown when {@link #getRssDocument(String)} has nothing to render. */
  protected String unavailableMessage(@NonNull final String url) {
    return "<p>RSS Feed is unavailable for url: " + Xml.cleanString(url) + "</p>" + endl;
  }

  private static Policy loadPolicy(final String resource) {
    try (InputStream in = HeadlineBean.class.getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Missing AntiSamy policy " + resource);
      return Policy.getInstance(in);
    } catch (final Exception e) {
      throw new IllegalStateException("Could not load AntiSamy policy " + resource, e);
    }
  }

  /** Clean feed-supplied HTML for display; falls back to plain escaped text. */
  static String sanitizeHtml(final String html) {
    if (html == null || html.isBlank()) return "";
    try {
      return new AntiSamy().scan(html, FEED_POLICY).getCleanHTML();
    } catch (final Exception e) {
      log.warn("Could not sanitize feed HTML, showing it as text: {}", e.getMessage());
      return Xml.cleanString(html);
    }
  }

  /** An escaped http(s) URL safe for an href or src attribute, or null for anything else. */
  static String safeUrl(final String url) {
    if (url == null) return null;
    final String trimmed = url.trim();
    final String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null;
    return Xml.cleanString(trimmed);
  }

  /** A positive pixel size from the feed, or 0 when it is missing or not a number. */
  private static int pixels(final String value) {
    if (value == null) return 0;
    try {
      return Math.max(0, Integer.parseInt(value.trim()));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  /** Text of an element, or null when it is empty (e.g. {@code <guid/>}). */
  private static String textOf(final org.w3c.dom.Node node) {
    final String text = node.getTextContent();
    return text == null || text.isBlank() ? null : text;
  }

  public String parse(@NonNull final String url) {

    if (!isValidFeedAddress(url)) {
      return "<p>RSS Feed address is invalid</p>" + endl;
    }

    try {
      log.info("Starting parse");
      final Optional<HeadlineContext> hc = getRssDocument(url);

      if (hc.isEmpty()) {
        log.debug("No RSS content to show for {}", url);
        return unavailableMessage(url);
      }

      log.info("Fetched, now create");

      // output variable
      final StringBuilder sb = new StringBuilder();

      String contentTitle = "";
      String contentLink = "";
      String contentDescription = "";
      String contentLastBuildDate = "";
      String contentGenerator = "";

      log.info("Prepare xml nodelists");

      final org.w3c.dom.NodeList channelList = hc.get().document.getElementsByTagName("channel");
      final org.w3c.dom.NodeList chnodes = channelList.item(0).getChildNodes();

      String imageUrl = null;
      String imageTitle = null;
      String imageLink = "";
      String imageWidth = null;
      String imageHeight = null;

      log.debug("Iterate through the nodelists");

      for (int k = 0; k < chnodes.getLength(); k++) {
        final org.w3c.dom.Node curNode = chnodes.item(k);

        if (curNode.getNodeName().equals("title")) {
          contentTitle = textOf(curNode);
        } else if (curNode.getNodeName().equals("link")) {
          contentLink = textOf(curNode);
        } else if (curNode.getNodeName().equals("description")) {
          contentDescription = textOf(curNode);
        } else if (curNode.getNodeName().equals("lastBuildDate")) {
          contentLastBuildDate = textOf(curNode);
        } else if (curNode.getNodeName().equals("generator")) {
          contentGenerator = textOf(curNode);
        } else if (curNode.getNodeName().equals("image")) {

          org.w3c.dom.NodeList imageNodes = curNode.getChildNodes();

          for (int z = 0; z < imageNodes.getLength(); z++) {
            if (imageNodes.item(z).getNodeName().equals("url")) {
              imageUrl = textOf(imageNodes.item(z));
            } else if (imageNodes.item(z).getNodeName().equals("height")) {
              imageHeight = textOf(imageNodes.item(z));
            } else if (imageNodes.item(z).getNodeName().equals("width")) {
              imageWidth = textOf(imageNodes.item(z));
            } else if (imageNodes.item(z).getNodeName().equals("title")) {
              imageTitle = textOf(imageNodes.item(z));
            } else if (imageNodes.item(z).getNodeName().equals("link")) {
              imageLink = textOf(imageNodes.item(z));
            }
          }
        }
      }

      log.debug("Prepare HTML output");

      // everything below comes from the remote feed: escape text, allow only http(s) links
      final String safeImageUrl = safeUrl(imageUrl);
      final String safeImageLink = safeUrl(imageLink);
      final String safeContentLink = safeUrl(contentLink);
      final int width = pixels(imageWidth);
      final int height = pixels(imageHeight);

      // create header!
      sb.append(
          "<div style=\"width: 100%; padding: .1in; background: #F2F2F2;\""
              + " class=\"ljfhead\">");
      sb.append(endl);

      // "--" can't appear inside an HTML comment
      sb.append("<!-- Generator: ");
      sb.append(Xml.cleanString(contentGenerator).replace("--", "- -"));
      sb.append(" -->");
      sb.append(endl);

      if (safeImageUrl != null) {
        sb.append("<span style=\"padding: 5px; float:left; ");
        if (width > 0) {
          sb.append("width:").append(width).append("px; ");
        }
        if (height > 0) {
          sb.append("height:").append(height).append("px; ");
        }
        sb.append("position: relative;\">");

        if (safeImageLink != null) {
          sb.append("<a href=\"").append(safeImageLink).append("\">");
        }

        sb.append("<img src=\"");
        sb.append(safeImageUrl);
        if (width > 0 && height > 0) {
          // Only add height and width attributes if they are defined in the feed.
          sb.append("\" height=\"").append(height).append("\" width=\"").append(width);
        }
        sb.append("\" alt=\"");
        sb.append(Xml.cleanString(imageTitle));
        sb.append("\" title=\"");
        sb.append(Xml.cleanString(imageTitle));
        sb.append("\" />");
        if (safeImageLink != null) {
          sb.append("</a>");
        }
        sb.append("</span>");
        sb.append(endl);
      }

      sb.append("<h3 ");
      if (contentDescription != null) {
        sb.append("title=\"");
        sb.append(Xml.cleanString(contentDescription));
        sb.append("\"");
      }
      sb.append(">");
      sb.append(Xml.cleanString(contentTitle));
      sb.append("</h3>");
      sb.append(endl);

      // some rss feeds don't have a last build date
      if (contentLastBuildDate != null && !contentLastBuildDate.isEmpty()) {
        sb.append("<p>Updated: ");
        sb.append(Xml.cleanString(contentLastBuildDate));
        sb.append("<br />");
      } else {
        sb.append("<p>");
      }

      if (safeContentLink != null) {
        sb.append("<a href=\"")
            .append(safeContentLink)
            .append("\">[")
            .append(safeContentLink)
            .append("]</a>");
      }
      sb.append("</p>");
      sb.append(endl);

      sb.append("<div style=\"clear: both;\">&nbsp;</div>");
      sb.append(endl);

      sb.append("</div>");
      sb.append(endl);

      // Generate the NodeList
      final org.w3c.dom.NodeList nodeList = hc.get().document.getElementsByTagName("item");

      sb.append(
          "<div class=\"panel-group\" id=\"accordion\" role=\"tablist\""
              + " aria-multiselectable=\"true\">");

      for (int i = 0; i < nodeList.getLength() && i < 16; i++) {
        final org.w3c.dom.NodeList childList = nodeList.item(i).getChildNodes();
        // some of the properties of <items>
        String link = null;
        String title = null;
        String pubDate = null;
        String description = null;
        String guid = null;

        for (int j = 0; j < childList.getLength(); j++) {
          if (childList.item(j).getNodeName().equals("link")) {
            link = textOf(childList.item(j));
          } else if (childList.item(j).getNodeName().equals("title")) {
            title = textOf(childList.item(j));

          } else if (childList.item(j).getNodeName().equals("pubDate")) {
            pubDate = textOf(childList.item(j));

          } else if (childList.item(j).getNodeName().equals("description")) {
            description = textOf(childList.item(j));

          } else if (childList.item(j).getNodeName().equals("guid")) {
            guid = textOf(childList.item(j));
          }
        }

        // assert the basic properties are there.
        if (link != null && title != null) {
          // guid is optional in RSS; fall back to the link so element ids stay unique and stable
          final String itemId =
              i + "_" + Integer.toHexString((guid != null ? guid : link).hashCode());

          sb.append(
              "<div class=\"panel panel-default\"><div class=\"panel-heading\""
                  + " role=\"tab\" id=\"heading");
          sb.append(itemId);
          sb.append("\">");

          sb.append("<h4 class=\"panel-title\">");
          sb.append(
              "<a role=\"button\" data-toggle=\"collapse\""
                  + " data-parent=\"#accordion\" href=\"#collapse");
          sb.append(itemId);
          sb.append("\" aria-expanded=\"")
              .append(i == 0 ? "true" : "false")
              .append("\" aria-controls=\"collapse")
              .append(itemId)
              .append("\">");
          sb.append(Xml.cleanString(title));
          sb.append("</a></h4></div>");
          sb.append("<div id=\"collapse").append(itemId)
              .append("\" class=\"panel-collapse collapse");
          if (i == 0) sb.append("in");
          sb.append("\" role=\"tabpanel\" aria-labelledby=\"heading");
          sb.append(itemId);
          sb.append("\">");
          sb.append("<div class=\"panel-body\">");
          sb.append(sanitizeHtml(description));

          final String safeLink = safeUrl(link);
          if (safeLink != null) {
            sb.append(" <span class=\"RssReadMore\">");
            sb.append("<a href=\"");
            sb.append(safeLink);
            sb.append("\">");
            sb.append("Read More</a></span>");
            sb.append(endl);
          }

          // some rss versions don't have a pub date per entry
          if (pubDate != null) {
            sb.append(" <br /><span class=\"RssItemPubDate\">");
            sb.append(Xml.cleanString(pubDate));
            sb.append("</span>");
            sb.append(endl);
          }
          sb.append("</div></div></div>");
          sb.append(endl);
        }
      }

      sb.append("</div>");
      sb.append(endl);

      return sb.toString();
    } catch (final java.io.FileNotFoundException e404) {
        log.warn("Feed is not available {}{}", url, e404.getMessage(), e404);
      return "<p>404 Not Found. The feed is no longer available. " + Xml.cleanString(url) + endl;
    } catch (final org.xml.sax.SAXParseException sp) {
      if (sp.getMessage() != null && sp.getMessage().contains("Premature end of file"))
        return "<p>Feed is empty at " + Xml.cleanString(url);
      else {
          log.error("Bad feed {}", sp.getMessage(), sp);
        return "<p>Bad Feed " + Xml.cleanString(sp.toString()) + " for url: " + Xml.cleanString(url) + endl;
      }
    } catch (final Exception e) {
      log.error("Could not render feed {}", url, e);
      return "<p>Error, could not process request: "
          + Xml.cleanString(e.toString())
          + " for url: "
          + Xml.cleanString(url)
          + endl;
    }
  }
}
