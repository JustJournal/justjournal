package com.justjournal.jsonfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * Attachment information for items.
 * <a href="https://jsonfeed.org/version/1.1#attachment-object">...</a>
 *
 * Note: The size_in_bytes and duration_in_seconds properties are optional and should only be included if available.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
// names are intentional, to match the jsonfeed spec.
public class Attachment {
    private String url;
    private String mime_type;
    private String title;
    private Integer size_in_bytes;
    private Integer duration_in_seconds;
}

/*
Example payload:
        {
         "url": "https://example.org/second-item/audio.ogg",
         "mime_type": "audio/ogg",
         "title": "Optional Title",
         "size_in_bytes": 31415927,
         "duration_in_seconds": 1800
         }

 */