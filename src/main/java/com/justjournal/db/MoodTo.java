/*
 * Copyright (c) 2004, 2014 Lucas Holt
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

package com.justjournal.db;

import java.io.Serializable;

/**
 * Represents a mood
 *
 * @author Lucas Holt
 */
public final class MoodTo implements Serializable {
    private int id;
    private int parent;  // parent mood
    private String name;

    public MoodTo() {

    }

    public MoodTo(int id, String name) {
        this.id = id;
        this.parent = 0;
        this.name = name;
    }
    public MoodTo(int id, int parent, String name) {
        this.id = id;
        this.parent = parent;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public int getParent() {
        return this.parent;
    }

    public void setParent(final int parent) {
        this.parent = parent;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MoodTo moodTo = (MoodTo) o;

        return id == moodTo.id && parent == moodTo.parent && name.equals(moodTo.name);
    }

    @Override
    public int hashCode() {
        int result;
        result = id;
        result = 29 * result + parent;
        result = 29 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "id: " + id + ", parent: " + parent + ", name: " + name;
    }
}
