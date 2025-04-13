package com.justjournal.jsonfeed;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * JSON Feed 1.1 feed format.
 * https://en.wikipedia.org/wiki/JSON_Feed
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
// names are intentional, to match the jsonfeed spec.
public class Feed {

    private final String version = "https://jsonfeed.org/version/1.1";

    private String title;

    private String home_page_url;

    private String feed_url;

    private String description;

    private String user_comment;

    private String next_url;

    private String icon;

    private String favicon;

    private List<Author> authors;

    private String language;

    private List<Item> items;
}

/* example payload:
"version": "https://jsonfeed.org/version/1.1",
        "title": "My Example Feed",
        "home_page_url": "https://example.org/",
        "feed_url": "https://example.org/feed.json",
        "description": "Optional to provide more detail beyond the title.",
        "user_comment": "Optional and should be ignored by feed readers.",
        "next_url": "https://example.org/pagination?feed=feed.json&p=17",
        "icon": "https://example.org/favicon-timeline-512x512.png",
        "favicon": "https://example.org/favicon-sourcelist-64x64.png",
        "authors": [
        {
        "name": "Optional Author",
        "url": "https://example.org/authors/optional-author",
        "avatar": "https://example.org/authors/optional-author/avatar-512x512.png"
        }
        ],
        "language": "en-US",
        "items": [
        {
        "id": "2",
        "content_text": "This is a second item.",
        "url": "https://example.org/second-item",
        "language": "es-mx",
        "attachments": [
        {
        "url": "https://example.org/second-item/audio.ogg",
        "mime_type": "audio/ogg",
        "title": "Optional Title",
        "size_in_bytes": 31415927,
        "duration_in_seconds": 1800
        }
        ]
        },
        {
        "id": "required-unique-string-that-does-not-change: number, guid, url, etc.",
        "url": "https://example.org/initial-post",
        "external_url": "https://en.wikipedia.org/w/index.php?title=JSON_Feed",
        "title": "Optional Title",
        "content_html": "<p>Optional content for the feed reader. You may also use content_text or both at the same time.</p>",
        "content_text": "Optional text for simple feeds.",
        "summary": "Optional summary of the item.",
        "image": "https://example.org/initial-post/main-img.png",
        "banner_image": "https://example.org/initial-post/details-banner.png",
        "date_published": "2021-10-25T19:30:00-01:00",
        "date_modified": "2021-10-26T19:45:00-01:00",
        "authors": [
        {
        "name": "Optional Author",
        "url": "https://example.org/authors/optional-author",
        "avatar": "https://example.org/authors/optional-author/avatar-512x512.png"
        }
        ],
        "tags": [
        "Optional Tag",
        "Example"
        ],
        "language": "en-US"
        }
        ]
        }

 */