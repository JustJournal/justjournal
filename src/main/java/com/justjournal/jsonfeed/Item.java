package com.justjournal.jsonfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * Item information for the feed.
 * https://jsonfeed.org/version/1.1#item-object
 * Note: The attachments property is optional and should only be included if available.
 */
// names are intentional, to match the jsonfeed spec.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {
    private String id;
    private String url; // jj post url
    private String external_url; // external site linked
    private String title; // should map to jj entry title
    private String summary; // a summation of the post.
    private String language; // en-US
    private String image;
    private String banner_image;
    private List<Attachment> attachments;
    private String content_html; // either content_html or content_text is required. can be both
    private String content_text;
    private String date_published; // format: ISO 8601 2021-10-25T19:30:00-01:00
    private String date_modified; // format: ISO 8601 2021-10-25T19:30:00-01:00

    private List<String> tags;
}