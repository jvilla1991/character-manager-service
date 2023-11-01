package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "_pc")
public class PC implements Serializable {

    public PC() {}

    public PC(Builder builder) {
        this.id = builder.id;
        this.clazz = builder.clazz;
        this.level = builder.level;
        this.name = builder.name;
        this.playerName = builder.playerName;
        this.user = builder.user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String clazz;
    private Short level;
    private String name;
    private String playerName;

    @ManyToOne
    @JsonBackReference
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public static class Builder {
        private Long id;
        private String clazz;
        private Short level;
        private String name;
        private String playerName;
        private User user;

        public static Builder newInstance() {
            return new Builder();
        }

        private Builder() {}

        public Builder setId(Long id) {
            this.id = id;
            return this;
        }

        public Builder setClazz(String clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder setLevel(Short level) {
            this.level = level;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setPlayerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder setUser(User user) {
            this.user = user;
            return this;
        }

        public PC build() {
            return new PC(this);
        }
    }
}
