package com.justjournal.model.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjournal.model.Security;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SecurityTo implements Serializable {

    @Serial
    private static final long serialVersionUID = -3247158592308509915L;

    private int id = 0;
    private String name = "";

    @JsonCreator
    public SecurityTo() {
        super();
    }

    public SecurityTo(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public SecurityTo(Security security) {
        this.id = security.getId();
        this.name = security.getName();
    }

    @JsonIgnore
    public Security getSecurity() {
        return Security.fromValue(id);
    }
}