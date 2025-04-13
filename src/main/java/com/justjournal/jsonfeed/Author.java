package com.justjournal.jsonfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
// names are intentional, to match the jsonfeed spec.
public class Author {
    private String name;

    private String url;

    private String avatar;
}
