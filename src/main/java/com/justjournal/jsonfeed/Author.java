package com.justjournal.jsonfeed;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
// names are intentional, to match the jsonfeed spec.
public class Author {
    private String name;

    private String url;

    private String avatar;
}
