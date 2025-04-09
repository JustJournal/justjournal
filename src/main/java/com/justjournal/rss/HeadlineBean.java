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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

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

  protected Optional<HeadlineContext> getRssDocument(@NonNull final String uri) throws Exception {

    final HeadlineContext hc = new HeadlineContext();

    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      final HttpGet httpGet = new HttpGet(uri);

      try (final CloseableHttpResponse response1 = httpClient.execute(httpGet)) {
        final HttpEntity entity1 = response1.getEntity();

        // Build document:
        hc.factory.setValidating(false);
        hc.factory.setIgnoringComments(true);
        hc.factory.setCoalescing(true);
        hc.factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        hc.builder = hc.factory.newDocumentBuilder();
        hc.document = hc.builder.parse(entity1.getContent());

        return Optional.of(hc);
      }
    } catch (final IOException e) {
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  public String parse(@NonNull final String url) {

    if (!DNSUtil.isUrlDomainValid(url)) {
      return "<p>RSS Feed address is invalid</p>" + endl;
    }

    try {
      log.info("Starting parse");
      final Optional<HeadlineContext> hc = getRssDocument(url);

      if (hc.isEmpty()) {
        log.error("Failed to fetch RSS content from {}", url);
        return "<p>RSS Feed is unavailable for url: " + url + "</p>" + endl;
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
          contentTitle = curNode.getChildNodes().item(0).getNodeValue();
        } else if (curNode.getNodeName().equals("link")) {
          contentLink = curNode.getChildNodes().item(0).getNodeValue();
        } else if (curNode.getNodeName().equals("description")) {
          contentDescription = curNode.getChildNodes().item(0).getNodeValue();
        } else if (curNode.getNodeName().equals("lastBuildDate")) {
          contentLastBuildDate = curNode.getChildNodes().item(0).getNodeValue();
        } else if (curNode.getNodeName().equals("generator")) {
          contentGenerator = curNode.getChildNodes().item(0).getNodeValue();
        } else if (curNode.getNodeName().equals("image")) {

          org.w3c.dom.NodeList imageNodes = curNode.getChildNodes();

          for (int z = 0; z < imageNodes.getLength(); z++) {
            if (imageNodes.item(z).getNodeName().equals("url")) {
              imageUrl = imageNodes.item(z).getChildNodes().item(0).getNodeValue();
            } else if (imageNodes.item(z).getNodeName().equals("height")) {
              imageHeight = imageNodes.item(z).getChildNodes().item(0).getNodeValue();
            } else if (imageNodes.item(z).getNodeName().equals("width")) {
              imageWidth = imageNodes.item(z).getChildNodes().item(0).getNodeValue();
            } else if (imageNodes.item(z).getNodeName().equals("title")) {
              imageTitle = imageNodes.item(z).getChildNodes().item(0).getNodeValue();
            } else if (imageNodes.item(z).getNodeName().equals("link")) {
              imageLink = imageNodes.item(z).getChildNodes().item(0).getNodeValue();
            }
          }
        }
      }

      log.debug("Prepare HTML output");

      // create header!
      sb.append(
          "<div style=\"width: 100%; padding: .1in; background: #F2F2F2;\""
              + " class=\"ljfhead\">");
      sb.append(endl);

      sb.append("<!-- Generator: ");
      sb.append(contentGenerator);
      sb.append(" -->");
      sb.append(endl);

      if (imageUrl != null) {
        sb.append("<span style=\"padding: 5px; float:left; ");
        if (imageWidth != null && Integer.parseInt(imageWidth) > 0) {
          sb.append("width:");
          sb.append(imageWidth);
          sb.append("px; ");
        }
        if (imageHeight != null && Integer.parseInt(imageHeight) > 0) {
          sb.append("height:");
          sb.append(imageHeight);
          sb.append("px; ");
        }
        sb.append("position: relative;\">");

        sb.append("<a href=\"");
        sb.append(imageLink);
        sb.append("\">");

        sb.append("<img src=\"");
        sb.append(imageUrl);
        if (imageHeight != null
            && imageWidth != null
            && Integer.parseInt(imageWidth) > 0
            && Integer.parseInt(imageHeight) > 0) {
          // Only add height and width attributes if they are defined in the feed.
          sb.append("\" height=\"");
          sb.append(imageHeight);
          sb.append("\" width=\"");
          sb.append(imageWidth);
        }
        sb.append("\" alt=\"");
        sb.append(imageTitle);
        sb.append("\" title=\"");
        sb.append(imageTitle);
        sb.append("\" /></a></span>");
        sb.append(endl);
      }

      sb.append("<h3 ");
      if (contentDescription != null) {
        sb.append("title=\"");
        sb.append(contentDescription);
        sb.append("\"");
      }
      sb.append(">");
      sb.append(contentTitle);
      sb.append("</h3>");
      sb.append(endl);

      // some rss feeds don't have a last build date
      if (contentLastBuildDate != null && !contentLastBuildDate.isEmpty()) {
        sb.append("<p>Updated: ");
        sb.append(contentLastBuildDate);
        sb.append("<br />");
      } else {
        sb.append("<p>");
      }

      if (contentLink != null) {
        sb.append("<a href=\"")
            .append(contentLink)
            .append("\">[")
            .append(contentLink)
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
            link = childList.item(j).getChildNodes().item(0).getNodeValue();
          } else if (childList.item(j).getNodeName().equals("title")) {
            title = childList.item(j).getChildNodes().item(0).getNodeValue();

          } else if (childList.item(j).getNodeName().equals("pubDate")) {
            pubDate = childList.item(j).getChildNodes().item(0).getNodeValue();

          } else if (childList.item(j).getNodeName().equals("description")) {
            description = childList.item(j).getChildNodes().item(0).getNodeValue();

          } else if (childList.item(j).getNodeName().equals("guid")) {
            guid = childList.item(j).getChildNodes().item(0).getNodeValue();
          }
        }

        // assert the basic properties are there.
        if (link != null && title != null) {
          sb.append(
              "<div class=\"panel panel-default\"><div class=\"panel-heading\""
                  + " role=\"tab\" id=\"heading");
            assert guid != null;
            sb.append(i).append("_").append(guid.hashCode());
          sb.append("\">");

          sb.append("<h4 class=\"panel-title\">");
          sb.append(
              "<a role=\"button\" data-toggle=\"collapse\""
                  + " data-parent=\"#accordion\" href=\"#collapse");
          sb.append(i).append("_").append(guid.hashCode());
          sb.append("\" aria-expanded=\"")
              .append(i == 0 ? "true" : "false")
              .append("\" aria-controls=\"collapse")
              .append(i)
              .append("_")
              .append(guid.hashCode())
              .append("\">");
          sb.append(Xml.cleanString(title));
          sb.append("</a></h4></div>");
          sb.append("<div id=\"collapse").append(i).append("_").append(guid.hashCode())
              .append("\" class=\"panel-collapse collapse");
          if (i == 0) sb.append("in");
          sb.append("\" role=\"tabpanel\" aria-labelledby=\"heading");
          sb.append(i).append("_").append(guid.hashCode());
          sb.append("\">");
          sb.append("<div class=\"panel-body\">");
          /*
             /<(script|noscript|object|embed|style|frameset|frame|iframe)[>\s\S]*<\/\\1>/i /<\/?!?(param|link|meta|doctype|div|font)[^>]*>/i /(class|style|id)=\"[^\"]*\"/i
          */
          Pattern p =
              Pattern.compile(
                  "/<(script|noscript|object|embed|style|frameset|frame|iframe|link)[>\\s\\S]*<\\/\\1>/i");
          Matcher m = p.matcher(description);
          final String result = m.replaceAll("");

          sb.append(result);

          sb.append(" <span class=\"RssReadMore\">");
          sb.append("<a href=\"");
          sb.append(link);
          sb.append("\">");
          sb.append("Read More</a></span>");
          sb.append(endl);

          // some rss versions don't have a pub date per entry
          if (pubDate != null) {
            sb.append(" <br /><span class=\"RssItemPubDate\">");
            sb.append(pubDate);
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
      return "<p>404 Not Found. The feed is no longer available. " + url + endl;
    } catch (final org.xml.sax.SAXParseException sp) {
      if (sp.getMessage().contains("Premature end of file")) return "<p>Feed is empty at " + url;
      else {
          log.error("Bad feed {}", sp.getMessage(), sp);
        return "<p>Bad Feed " + sp + " for url: " + url + endl;
      }
    } catch (final Exception e) {
      return "<p>Error, could not process request: " + e + " for url: " + url + endl;
    }
  }
}
