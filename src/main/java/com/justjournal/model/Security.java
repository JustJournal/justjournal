package com.justjournal.model;

import lombok.Getter;

@Getter
public enum Security {
    PRIVATE(0, "private"),
    FRIENDS(1, "friends"),
    PUBLIC(2, "public");

    private final int id;
    private final String name;

    Security(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Security fromValue(int id) {
        for (Security s : values()) {
            if (s.id == id) {
                return s;
            }
        }
        throw new IllegalArgumentException("Invalid security id: " + id);
    }
}