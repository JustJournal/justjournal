package com.justjournal.jsonfeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.CustomObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class FeedTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testFeedSerialization() throws Exception {
        Feed feed = Feed.builder()
                .title("My Example Feed")
                .home_page_url("https://example.org/")
                .feed_url("https://example.org/feed.json")
                .description("Optional description")
                .authors(Collections.singletonList(
                        Author.builder()
                                .name("John Doe")
                                .url("https://example.org/johndoe")
                                .avatar("https://example.org/johndoe/avatar.png")
                                .build()
                ))
                .items(Arrays.asList(
                        Item.builder()
                                .id("1")
                                .content_text("This is the first item.")
                                .url("https://example.org/item1")
                                .build(),
                        Item.builder()
                                .id("2")
                                .content_text("This is the second item.")
                                .url("https://example.org/item2")
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(feed);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(json).isNotNull();
        softly.assertThat(json).contains("\"version\":\"https://jsonfeed.org/version/1.1\"");
        softly.assertThat(json).contains("\"title\":\"My Example Feed\"");
        softly.assertThat(json).contains("\"home_page_url\":\"https://example.org/\"");
        softly.assertThat(json).contains("\"feed_url\":\"https://example.org/feed.json\"");
        softly.assertThat(json).contains("\"description\":\"Optional description\"");
        softly.assertThat(json).contains("\"authors\":[{\"name\":\"John Doe\",\"url\":\"https://example.org/johndoe\",\"avatar\":\"https://example.org/johndoe/avatar.png\"}]");
        softly.assertThat(json).contains("\"id\":\"1\"", "\"content_text\":\"This is the first item.\"", "\"url\":\"https://example.org/item1\"");
        softly.assertThat(json).contains("\"id\":\"2\"", "\"content_text\":\"This is the second item.\"", "\"url\":\"https://example.org/item2\"");
        softly.assertAll();
    }

    @Test
    void testFeedDeserialization() throws Exception {
        String json = "{\"version\":\"https://jsonfeed.org/version/1.1\",\"title\":\"My Example Feed\",\"home_page_url\":\"https://example.org/\",\"feed_url\":\"https://example.org/feed.json\",\"items\":[{\"id\":\"1\",\"content_text\":\"This is an item.\",\"url\":\"https://example.org/item1\"}]}";

        Feed feed = objectMapper.readValue(json, Feed.class);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(feed).isNotNull();
        softly.assertThat(feed.getVersion()).isEqualTo("https://jsonfeed.org/version/1.1");
        softly.assertThat(feed.getTitle()).isEqualTo("My Example Feed");
        softly.assertThat(feed.getHome_page_url()).isEqualTo("https://example.org/");
        softly.assertThat(feed.getFeed_url()).isEqualTo("https://example.org/feed.json");
        softly.assertThat(feed.getItems()).hasSize(1);
        softly.assertThat(feed.getItems().get(0).getId()).isEqualTo("1");
        softly.assertThat(feed.getItems().get(0).getContent_text()).isEqualTo("This is an item.");
        softly.assertThat(feed.getItems().get(0).getUrl()).isEqualTo("https://example.org/item1");
        softly.assertAll();
    }

    @Test
    void testNullFieldsAreOmitted() throws Exception {
        Feed feed = Feed.builder()
                .title("My Feed")
                .home_page_url("https://example.org/")
                .build();

        String json = objectMapper.writeValueAsString(feed);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(json).isNotNull();
        softly.assertThat(json).contains("\"title\":\"My Feed\"");
        softly.assertThat(json).contains("\"home_page_url\":\"https://example.org/\"");
        softly.assertThat(json).doesNotContain("\"description\"");
        softly.assertThat(json).doesNotContain("\"user_comment\"");
        softly.assertThat(json).doesNotContain("\"next_url\"");
        softly.assertAll();
    }
}